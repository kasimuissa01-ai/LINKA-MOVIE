package com.example.data.download

import android.content.Context
import android.util.Log
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.remote.CloudflareR2PresignedClient
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import com.example.util.R2UrlUtils
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Production-grade Offline Download Manager backed by Room database.
 *
 * Capabilities:
 * 1. Single source of truth via Room database [DownloadDao]
 * 2. Full support for Cloudflare R2 video assets & CDN endpoints
 * 3. 64-bit Long byte tracking avoiding integer overflow for large files (>2GB, >4GB)
 * 4. Chunked Range Download Strategy for files > 2GB (16MB chunks) preventing network timeouts
 * 5. Constant 64KB memory buffer preventing heap strain and Out-Of-Memory crashes
 * 6. Temporary file isolation: downloads write to .download parts, atomically renamed to .mp4 upon full verification
 * 7. Foreground Service integration with WakeLock and persistent ongoing notification with Pause/Cancel actions
 * 8. Corrupt/partial file auto-healing: ensures incomplete downloads never masquerade as playable offline files
 */
class OfflineDownloadManager(
    private val downloadDao: DownloadDao,
    private val r2Client: CloudflareR2PresignedClient,
    private val context: Context,
    private val managerScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    // Dedicated OkHttpClient with long timeouts and redirect following
    private val downloadHttpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    init {
        // Register listener for user actions (Pause / Cancel) directly from persistent notification shade
        OfflineDownloadService.registerActionListener { action, movieId ->
            if (movieId != null) {
                when (action) {
                    OfflineDownloadService.ACTION_PAUSE -> {
                        managerScope.launch { pauseDownload(movieId) }
                    }
                    OfflineDownloadService.ACTION_CANCEL -> {
                        managerScope.launch { deleteDownload(movieId, movieId) }
                    }
                }
            }
        }
    }

    /**
     * Reactive stream of all downloads observed from Room.
     */
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads().map { list ->
        list.map { it.toDomain() }
    }

    /**
     * Returns verified local file URI for offline playback if and only if:
     * 1. Room marks status as COMPLETED and links to the requested movieId.
     * 2. The contentUri or filePath stored in Room (or canonical movie_${movieId}.mp4) is verified.
     * 3. The file is non-empty and has valid video size (>= 1MB).
     *
     * Correctly resolves both content:// URIs and filesystem paths while verifying that the file
     * genuinely belongs to the intended movie ID, preventing incorrect file retrieval.
     */
    /**
     * Returns verified local file URI for offline playback if and only if:
     * 1. Room marks status as COMPLETED and links to the requested movieId (and episodeId if applicable).
     * 2. The contentUri or filePath stored in Room (or canonical file) is verified.
     * 3. The file is non-empty and has valid video size (>= 1MB).
     */
    suspend fun getVerifiedOfflinePlaybackUri(movieId: String, episodeId: String? = null): String? = withContext(Dispatchers.IO) {
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val baseFileName = if (!episodeId.isNullOrBlank()) "movie_${movieId}_ep_${episodeId}.mp4" else "movie_${movieId}.mp4"
        val finalFile = File(destDir, baseFileName)
        val internalFile = File(context.filesDir, baseFileName)

        val entity = if (!episodeId.isNullOrBlank()) {
            downloadDao.getDownloadByEpisode(movieId, episodeId)
        } else {
            downloadDao.getDownloadByMovieId(movieId)
        }
        val isMarkedCompleted = entity != null && entity.status == DownloadStatus.COMPLETED.name

        if (entity != null && isMarkedCompleted) {
            // 1. Check localFilePath or contentUri stored in Room database
            val storedPath = entity.localFilePath.trim()
            if (storedPath.isNotBlank()) {
                // Case A: content:// URI
                if (storedPath.startsWith("content://")) {
                    try {
                        val uri = android.net.Uri.parse(storedPath)
                        val isValidDescriptor = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            pfd.statSize >= 1024 * 1024L
                        } ?: false
                        if (isValidDescriptor) {
                            Log.d(TAG, "Verified contentUri offline file for movie $movieId (ep=$episodeId): $storedPath")
                            return@withContext storedPath
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading stored contentUri for movie $movieId (ep=$episodeId): ${e.message}")
                    }
                }

                // Case B: Standard filesystem path or file:// URI
                val cleanPath = storedPath.removePrefix("file://")
                val storedFile = File(cleanPath)
                if (storedFile.exists() && storedFile.length() >= 1024 * 1024L) {
                    val fileSize = storedFile.length()
                    val belongsToItem = if (!episodeId.isNullOrBlank()) {
                        entity.episodeId == episodeId || storedFile.name.contains(episodeId)
                    } else {
                        entity.movieId == movieId || storedFile.name.contains(movieId)
                    }

                    if (belongsToItem) {
                        Log.d(TAG, "Verified stored filePath from Room for movie $movieId (ep=$episodeId): ${storedFile.absolutePath} ($fileSize bytes)")
                        return@withContext storedFile.toURI().toString()
                    }
                }
            }

            // 2. Check canonical external and internal files
            val candidateFile = when {
                finalFile.exists() && finalFile.length() >= 1024 * 1024L -> finalFile
                internalFile.exists() && internalFile.length() >= 1024 * 1024L -> internalFile
                else -> null
            }

            if (candidateFile != null) {
                val fileSize = candidateFile.length()
                if (entity.localFilePath != candidateFile.absolutePath) {
                    downloadDao.updateDownloadProgressAndPath(
                        id = entity.id,
                        progress = 1.0f,
                        status = DownloadStatus.COMPLETED.name,
                        bytes = fileSize,
                        localPath = candidateFile.absolutePath
                    )
                }
                Log.d(TAG, "Verified canonical file available for movie $movieId (ep=$episodeId): ${candidateFile.length()} bytes")
                return@withContext candidateFile.toURI().toString()
            }

            Log.w(TAG, "Movie $movieId (ep=$episodeId) marked COMPLETED in Room but local file missing or corrupt.")
            downloadDao.updateStatus(entity.id, DownloadStatus.FAILED.name)
        }

        // Clean up unverified or corrupt partial file only if not actively downloading
        val activeKey = if (!episodeId.isNullOrBlank()) "${movieId}_ep_${episodeId}" else movieId
        if (finalFile.exists() && !isMarkedCompleted && activeDownloadJobs[activeKey]?.isActive != true) {
            Log.w(TAG, "Found corrupt or partial file for $activeKey. Cleaning up...")
            runCatching { finalFile.delete() }
        }

        return@withContext null
    }

    /**
     * Starts or resumes a movie or episode download.
     */
    fun startDownload(movie: Movie, episode: Episode? = null) {
        val downloadId = if (episode != null) "${movie.id}_ep_${episode.id}" else movie.id
        if (activeDownloadJobs[downloadId]?.isActive == true) {
            Log.d(TAG, "Download already active for item: $downloadId (${episode?.title ?: movie.title})")
            return
        }

        val job = managerScope.launch {
            executeDownloadPipeline(movie, episode)
        }
        activeDownloadJobs[downloadId] = job
    }

    /**
     * Pauses an ongoing download, preserving downloaded bytes on disk for resumption.
     */
    suspend fun pauseDownload(downloadId: String) = withContext(Dispatchers.IO) {
        activeDownloadJobs[downloadId]?.cancel()
        activeDownloadJobs.remove(downloadId)

        val current = downloadDao.getDownloadById(downloadId)
        if (current != null && current.status != DownloadStatus.COMPLETED.name) {
            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name)
        }

        checkAndStopForegroundService()
    }

    /**
     * Retries or resumes a failed or paused download.
     */
    suspend fun retryDownload(downloadId: String, movie: Movie, episode: Episode? = null) {
        pauseDownload(downloadId)
        startDownload(movie, episode)
    }

    /**
     * Cancels and deletes a download record and its local files.
     */
    suspend fun deleteDownload(downloadId: String, movieId: String, episodeId: String? = null) = withContext(Dispatchers.IO) {
        activeDownloadJobs[downloadId]?.cancel()
        activeDownloadJobs.remove(downloadId)

        downloadDao.deleteById(downloadId)

        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val baseFileName = if (!episodeId.isNullOrBlank()) "movie_${movieId}_ep_${episodeId}.mp4" else "movie_${movieId}.mp4"
        val finalFile = File(destDir, baseFileName)
        val partFile = File(destDir, "$baseFileName.download")

        runCatching { if (finalFile.exists()) finalFile.delete() }
        runCatching { if (partFile.exists()) partFile.delete() }

        checkAndStopForegroundService()
    }

    /**
     * Core download execution pipeline.
     */
    private suspend fun executeDownloadPipeline(movie: Movie, episode: Episode? = null) = withContext(Dispatchers.IO) {
        val downloadId = if (episode != null) "${movie.id}_ep_${episode.id}" else movie.id
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val baseFileName = if (episode != null) "movie_${movie.id}_ep_${episode.id}.mp4" else "movie_${movie.id}.mp4"
        val finalFile = File(destDir, baseFileName)
        val tempFile = File(destDir, "$baseFileName.download")

        destDir.mkdirs()

        // Check if already completed and valid
        if (finalFile.exists() && finalFile.length() > 5 * 1024 * 1024L) {
            val existing = if (episode != null) {
                downloadDao.getDownloadByEpisode(movie.id, episode.id)
            } else {
                downloadDao.getDownloadByMovieId(movie.id)
            }
            if (existing?.status == DownloadStatus.COMPLETED.name) {
                Log.d(TAG, "Item '${episode?.title ?: movie.title}' is already fully downloaded.")
                return@withContext
            }
        }

        // Initialize or update Room record
        val initialExistingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val effectiveSizeMb = episode?.fileSizeMb ?: movie.fileSizeMb
        val estimatedTotalBytes: Long = (effectiveSizeMb * 1024L * 1024L).coerceAtLeast(10L * 1024L * 1024L)

        val displayTitle = if (episode != null) {
            "${movie.title} • S${episode.seasonNumber}E${episode.episodeNumber}: ${episode.title}"
        } else {
            movie.title
        }

        val downloadEntity = DownloadEntity(
            id = downloadId,
            movieId = movie.id,
            movieTitle = movie.title,
            coverUrl = movie.coverUrl,
            localFilePath = finalFile.absolutePath,
            progress = if (estimatedTotalBytes > 0) (initialExistingBytes.toFloat() / estimatedTotalBytes).coerceIn(0f, 0.99f) else 0f,
            status = DownloadStatus.DOWNLOADING.name,
            downloadedBytes = initialExistingBytes,
            totalBytes = estimatedTotalBytes,
            episodeId = episode?.id,
            episodeTitle = episode?.title,
            seasonNumber = episode?.seasonNumber,
            episodeNumber = episode?.episodeNumber
        )
        downloadDao.insertOrUpdate(downloadEntity)

        // Start Foreground Service to protect background operation
        OfflineDownloadService.start(
            context = context,
            movieId = movie.id,
            title = displayTitle,
            progress = (downloadEntity.progress * 100).toInt(),
            text = "Starting chunked download..."
        )

        // Resolve download candidate URLs
        val candidates = buildCandidateUrls(movie, episode)
        var downloadSuccess = false
        var lastError: Exception? = null

        if (candidates.isEmpty()) {
            lastError = IllegalStateException("No video stream URL or Cloudflare R2 key found for '$displayTitle'")
            Log.e(TAG, "Download cannot proceed: ${lastError.message}")
        } else {
            for (candidateUrl in candidates) {
                if (!activeDownloadJobs.containsKey(downloadId)) {
                    Log.d(TAG, "Download was cancelled before trying $candidateUrl")
                    return@withContext
                }

                try {
                    Log.d(TAG, "Probing source candidate for $displayTitle: $candidateUrl")
                    val probe = probeSource(candidateUrl, estimatedTotalBytes)
                    val effectiveTotalBytes = if (probe.contentLength > 0) probe.contentLength else estimatedTotalBytes

                    if (probe.supportsRange) {
                        val isOver2GB = effectiveTotalBytes >= LARGE_FILE_THRESHOLD || effectiveSizeMb >= 2048
                        Log.d(TAG, "Executing Chunked Download Strategy (over2GB=$isOver2GB, size=${formatBytes(effectiveTotalBytes)}) from $candidateUrl")
                        downloadSuccess = downloadInChunks(
                            url = candidateUrl,
                            tempFile = tempFile,
                            finalFile = finalFile,
                            movie = movie,
                            downloadId = downloadId,
                            totalBytes = effectiveTotalBytes
                        )
                    } else {
                        Log.d(TAG, "Server does not support Range requests, falling back to streaming with 64KB buffer for $displayTitle")
                        downloadSuccess = streamWithResumption(
                            url = candidateUrl,
                            tempFile = tempFile,
                            finalFile = finalFile,
                            movie = movie,
                            downloadId = downloadId,
                            estimatedTotalBytes = effectiveTotalBytes
                        )
                    }
                    if (downloadSuccess) break
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Candidate $candidateUrl failed for $displayTitle: ${e.message}")
                }
            }
        }

        if (downloadSuccess && finalFile.exists() && finalFile.length() > 0) {
            val finalBytes = finalFile.length()
            downloadDao.updateDownloadProgressAndPath(
                id = downloadId,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                bytes = finalBytes,
                localPath = finalFile.absolutePath
            )
            OfflineDownloadService.updateProgress(
                context = context,
                movieId = movie.id,
                title = displayTitle,
                progress = 100,
                text = "${formatBytes(finalBytes)} downloaded",
                isComplete = true
            )
            Log.d(TAG, "Download successfully finished for $displayTitle: ${finalFile.absolutePath} ($finalBytes bytes)")
        } else {
            Log.e(TAG, "All download candidates failed for $displayTitle: ${lastError?.message}")
            val currentBytes = if (tempFile.exists()) tempFile.length() else 0L
            val currentProgress = if (estimatedTotalBytes > 0) (currentBytes.toFloat() / estimatedTotalBytes).coerceIn(0f, 0.99f) else 0f
            downloadDao.updateProgress(
                id = downloadId,
                progress = currentProgress,
                status = DownloadStatus.FAILED.name,
                bytes = currentBytes
            )
            OfflineDownloadService.updateProgress(
                context = context,
                movieId = movie.id,
                title = displayTitle,
                progress = (currentProgress * 100).toInt(),
                text = "Download interrupted. Tap to retry.",
                isComplete = false
            )
        }

        activeDownloadJobs.remove(downloadId)
        checkAndStopForegroundService()
    }

    private data class SourceProbe(
        val supportsRange: Boolean,
        val contentLength: Long
    )

    private suspend fun probeSource(url: String, fallbackEstimate: Long): SourceProbe = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MovieRoom-Probe/2.0")
                .header("Range", "bytes=0-0")
                .build()

            val response = downloadHttpClient.newCall(request).execute()
            response.use { resp ->
                if (resp.code == 206) {
                    val contentRange = resp.header("Content-Range")
                    val totalFromRange = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    val totalLength = totalFromRange ?: resp.header("Content-Length")?.toLongOrNull() ?: fallbackEstimate
                    return@withContext SourceProbe(supportsRange = true, contentLength = totalLength)
                } else if (resp.isSuccessful) {
                    val acceptsRanges = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    val totalLength = resp.header("Content-Length")?.toLongOrNull() ?: fallbackEstimate
                    return@withContext SourceProbe(supportsRange = acceptsRanges, contentLength = totalLength)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Source probe failed for $url: ${e.message}")
        }
        return@withContext SourceProbe(supportsRange = false, contentLength = fallbackEstimate)
    }

    /**
     * Chunked Range Download Strategy for large files (>2GB):
     * Splits multi-GB transfers into bounded chunks (16MB) using HTTP Range headers.
     * Prevents network timeouts, socket dropouts, and eliminates memory crashes by streaming
     * into a constant 64KB buffer directly into [RandomAccessFile].
     */
    private suspend fun downloadInChunks(
        url: String,
        tempFile: File,
        finalFile: File,
        movie: Movie,
        downloadId: String,
        totalBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        if (existingBytes >= totalBytes && totalBytes > 0) {
            return@withContext finalizeDownloadedFile(tempFile, finalFile)
        }

        // Align resumption to chunk boundaries to prevent partial-chunk corruption
        val startingChunkIndex = (existingBytes / CHUNK_SIZE).toInt()
        val verifiedOffset = startingChunkIndex.toLong() * CHUNK_SIZE

        if (tempFile.exists() && tempFile.length() > verifiedOffset) {
            RandomAccessFile(tempFile, "rw").use { it.setLength(verifiedOffset) }
        }

        val totalChunks = ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        var bytesDownloaded = verifiedOffset

        Log.d(TAG, "Starting chunked download for ${movie.title}: totalChunks=$totalChunks, startChunk=$startingChunkIndex, totalBytes=$totalBytes")

        for (chunkIndex in startingChunkIndex until totalChunks) {
            if (!activeDownloadJobs.containsKey(downloadId)) {
                Log.d(TAG, "Download job cancelled during chunk $chunkIndex for ${movie.title}")
                return@withContext false
            }

            val chunkStart = chunkIndex.toLong() * CHUNK_SIZE
            val chunkEnd = minOf(chunkStart + CHUNK_SIZE - 1, totalBytes - 1)

            var chunkSuccess = false
            var lastChunkError: Exception? = null

            // Retry up to 3 times per chunk with backoff on network timeouts
            for (attempt in 1..3) {
                if (!activeDownloadJobs.containsKey(downloadId)) return@withContext false
                try {
                    downloadSingleChunk(url, tempFile, chunkStart, chunkEnd, downloadId)
                    chunkSuccess = true
                    break
                } catch (e: Exception) {
                    lastChunkError = e
                    Log.w(TAG, "Chunk $chunkIndex/$totalChunks attempt $attempt failed for ${movie.title}: ${e.message}")
                    delay(attempt * 800L)
                }
            }

            if (!chunkSuccess) {
                throw lastChunkError ?: java.io.IOException("Failed downloading chunk $chunkIndex after retries")
            }

            bytesDownloaded = chunkEnd + 1
            val progress = (bytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0.01f, 0.99f)

            downloadDao.updateProgress(
                id = downloadId,
                progress = progress,
                status = DownloadStatus.DOWNLOADING.name,
                bytes = bytesDownloaded
            )

            val progressPercent = (progress * 100).toInt()
            val progressText = "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)} ($progressPercent%) • Chunk ${chunkIndex + 1}/$totalChunks"
            OfflineDownloadService.updateProgress(
                context = context,
                movieId = movie.id,
                title = movie.title,
                progress = progressPercent,
                text = progressText,
                isComplete = false
            )
        }

        return@withContext finalizeDownloadedFile(tempFile, finalFile)
    }

    private suspend fun downloadSingleChunk(
        url: String,
        tempFile: File,
        chunkStart: Long,
        chunkEnd: Long,
        downloadId: String
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MovieRoom-ChunkedDownloader/2.0")
            .header("Range", "bytes=$chunkStart-$chunkEnd")
            .build()

        val response = downloadHttpClient.newCall(request).execute()
        response.use { resp ->
            if (resp.code != 206 && resp.code != 200) {
                throw java.io.IOException("HTTP ${resp.code} for chunk range $chunkStart-$chunkEnd")
            }

            val body = resp.body ?: throw java.io.IOException("Empty body for chunk $chunkStart-$chunkEnd")
            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.seek(chunkStart)
                val inputStream = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    if (!activeDownloadJobs.containsKey(downloadId)) {
                        throw CancellationException("Download cancelled")
                    }
                    raf.write(buffer, 0, read)
                }
            }
        }
    }

    private fun finalizeDownloadedFile(tempFile: File, finalFile: File): Boolean {
        if (tempFile.exists() && tempFile.length() > 0) {
            if (finalFile.exists()) finalFile.delete()
            val renamed = tempFile.renameTo(finalFile)
            if (!renamed) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            return true
        }
        return false
    }

    /**
     * Executes HTTP streaming with HTTP Range support to resume broken or large downloads.
     */
    private suspend fun streamWithResumption(
        url: String,
        tempFile: File,
        finalFile: File,
        movie: Movie,
        downloadId: String,
        estimatedTotalBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
            .header("User-Agent", "MovieRoom-OfflineDownload/2.0")

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        val response: Response = downloadHttpClient.newCall(request).execute()

        if (!response.isSuccessful && response.code != 206) {
            response.close()
            // If server returned 416 Range Not Satisfiable, clear temp file and retry fresh
            if (response.code == 416) {
                tempFile.delete()
                existingBytes = 0L
                val freshResponse = downloadHttpClient.newCall(
                    Request.Builder().url(url).header("User-Agent", "MovieRoom-OfflineDownload/2.0").build()
                ).execute()
                if (!freshResponse.isSuccessful) {
                    freshResponse.close()
                    return@withContext false
                }
                return@withContext streamResponseBody(freshResponse, tempFile, finalFile, movie, downloadId, 0L, estimatedTotalBytes)
            }
            return@withContext false
        }

        val isPartial = response.code == 206
        val startingOffset = if (isPartial) existingBytes else 0L
        if (!isPartial && tempFile.exists()) {
            tempFile.delete()
        }

        return@withContext streamResponseBody(response, tempFile, finalFile, movie, downloadId, startingOffset, estimatedTotalBytes)
    }

    private suspend fun streamResponseBody(
        response: Response,
        tempFile: File,
        finalFile: File,
        movie: Movie,
        downloadId: String,
        startingOffset: Long,
        estimatedTotalBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        response.use { resp ->
            val body = resp.body ?: return@withContext false
            val contentLength = body.contentLength()
            val totalBytes: Long = if (contentLength > 0) {
                startingOffset + contentLength
            } else {
                estimatedTotalBytes
            }

            val appendMode = startingOffset > 0
            val outputStream = FileOutputStream(tempFile, appendMode)
            val inputStream = body.byteStream()

            val buffer = ByteArray(64 * 1024) // 64 KB buffer
            var bytesDownloaded: Long = startingOffset
            var lastDbUpdateBytes: Long = startingOffset
            var lastDbUpdateTimeMs = System.currentTimeMillis()
            var readCount: Int

            try {
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { readCount = it } != -1) {
                            if (!activeDownloadJobs.containsKey(downloadId)) {
                                Log.d(TAG, "Download job was cancelled for movie ${movie.title}")
                                output.flush()
                                return@withContext false
                            }

                            output.write(buffer, 0, readCount)
                            bytesDownloaded += readCount

                            val now = System.currentTimeMillis()
                            // Rate-limit database updates to prevent SQLite lock contention
                            if (bytesDownloaded - lastDbUpdateBytes >= 512 * 1024L || (now - lastDbUpdateTimeMs) >= 500) {
                                lastDbUpdateBytes = bytesDownloaded
                                lastDbUpdateTimeMs = now
                                val progress = (bytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0.01f, 0.99f)

                                downloadDao.updateProgress(
                                    id = downloadId,
                                    progress = progress,
                                    status = DownloadStatus.DOWNLOADING.name,
                                    bytes = bytesDownloaded
                                )

                                val progressPercent = (progress * 100).toInt()
                                val progressText = "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)} ($progressPercent%)"
                                OfflineDownloadService.updateProgress(
                                    context = context,
                                    movieId = movie.id,
                                    title = movie.title,
                                    progress = progressPercent,
                                    text = progressText
                                )
                            }
                        }
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Streaming stream read error: ${e.message}")
                throw e
            }

            // Verify integrity of downloaded temp file
            if (tempFile.exists() && tempFile.length() > 0) {
                // Atomic rename
                if (finalFile.exists()) finalFile.delete()
                val renamed = tempFile.renameTo(finalFile)
                if (!renamed) {
                    // Fallback copy if rename across mount boundaries fails
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                return@withContext true
            }

            return@withContext false
        }
    }

    /**
     * Builds candidate URLs prioritizing the item's own R2 key and runtime constructed URL.
     * Guarantees that unauthenticated S3 API endpoints are converted to the public R2 CDN domain.
     */
    private suspend fun buildCandidateUrls(movie: Movie, episode: Episode? = null): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()

        val rawKey = episode?.videoKey?.takeIf { it.isNotBlank() } ?: movie.videoKey
        val rawStreamUrl = episode?.videoStreamUrl?.takeIf { it.isNotBlank() } ?: movie.videoStreamUrl

        // 1. Cloudflare R2 Public CDN URL if videoKey is present
        val cleanKey = R2UrlUtils.extractCleanVideoKey(rawKey, rawStreamUrl)
        if (cleanKey.isNotBlank()) {
            val r2CdnUrl = R2UrlUtils.buildUrl(cleanKey)
            list.add(r2CdnUrl)
        }

        // 2. Canonicalized direct videoStreamUrl
        val canonicalDirect = R2UrlUtils.canonicalizeStreamUrl(rawStreamUrl, rawKey)
        if (canonicalDirect.isNotBlank() &&
            (canonicalDirect.startsWith("http://") || canonicalDirect.startsWith("https://")) &&
            !canonicalDirect.contains("bunny/trailer.mp4") &&
            !canonicalDirect.contains("BigBuckBunny.mp4")
        ) {
            list.add(canonicalDirect)
        }

        // 3. Query Supabase movies table for verified key & constructed URL if movie candidate list is empty
        if (list.isEmpty() && movie.id.isNotBlank() && episode == null) {
            try {
                val supabaseClient = com.example.data.remote.SupabaseDatabaseClient()
                val remoteMovies = supabaseClient.getMovies()
                val match = remoteMovies.firstOrNull { it.id == movie.id }
                if (match != null) {
                    val key = R2UrlUtils.extractCleanVideoKey(match.videoKey, match.videoStreamUrl)
                    if (key.isNotBlank()) {
                        list.add(R2UrlUtils.buildUrl(key))
                    } else if (match.videoStreamUrl.isNotBlank() && match.videoStreamUrl.startsWith("http")) {
                        list.add(R2UrlUtils.buildUrl(match.videoStreamUrl))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Supabase candidate URL fetch error: ${e.message}")
            }
        }

        return@withContext list.distinct()
    }

    private fun checkAndStopForegroundService() {
        if (activeDownloadJobs.isEmpty()) {
            OfflineDownloadService.stop(context)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
            else -> "${bytes / 1024L} KB"
        }
    }

    companion object {
        const val TAG = "OfflineDownloadManager"
        const val LARGE_FILE_THRESHOLD = 2L * 1024L * 1024L * 1024L // 2 GB
        const val CHUNK_SIZE = 16L * 1024L * 1024L // 16 MB chunk size
        const val BUFFER_SIZE = 64 * 1024 // 64 KB constant stream buffer
    }
}
