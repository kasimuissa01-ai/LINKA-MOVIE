package com.example.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieRoomDatabase
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import com.example.util.R2UrlUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Production-grade WorkManager CoroutineWorker that caches episodes locally.
 *
 * Features:
 * 1. Real-time progress broadcasting via WorkManager setProgress()
 * 2. Background persistence protected via ForegroundInfo / DATA_SYNC service
 * 3. Range-based HTTP resumption for partial downloads (.download temp file)
 * 4. Room database synchronization with DownloadDao
 * 5. Automatic memory bounds with 64KB stream buffers
 */
class EpisodeDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return@withContext Result.failure()
        val movieId = inputData.getString(KEY_MOVIE_ID) ?: return@withContext Result.failure()
        val episodeTitle = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"
        val movieTitle = inputData.getString(KEY_MOVIE_TITLE) ?: "Movie"
        val seasonNum = inputData.getInt(KEY_SEASON_NUMBER, 1)
        val epNum = inputData.getInt(KEY_EPISODE_NUMBER, 1)
        val fileSizeMb = inputData.getLong(KEY_FILE_SIZE_MB, 0L)
        val videoKey = inputData.getString(KEY_VIDEO_KEY).orEmpty()
        val videoStreamUrl = inputData.getString(KEY_STREAM_URL).orEmpty()
        val coverUrl = inputData.getString(KEY_COVER_URL).orEmpty()

        val downloadId = "${movieId}_ep_${episodeId}"
        val displayTitle = "$movieTitle • S${seasonNum}E$epNum: $episodeTitle"
        val notificationId = (downloadId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1000)

        val database = MovieRoomDatabase.getInstance(context)
        val downloadDao = database.downloadDao()

        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        destDir.mkdirs()
        val finalFileName = "movie_${movieId}_ep_${episodeId}.mp4"
        val finalFile = File(destDir, finalFileName)
        val tempFile = File(destDir, "$finalFileName.download")

        // 1. Verify if already cached locally
        if (finalFile.exists() && finalFile.length() >= 1024 * 1024L) {
            val completedBytes = finalFile.length()
            downloadDao.updateDownloadProgressAndPath(
                id = downloadId,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                bytes = completedBytes,
                localPath = finalFile.absolutePath
            )
            val successData = workDataOf(
                KEY_EPISODE_ID to episodeId,
                KEY_MOVIE_ID to movieId,
                KEY_PROGRESS_PERCENT to 100,
                KEY_PROGRESS_FRACTION to 1.0f,
                KEY_BYTES_DOWNLOADED to completedBytes,
                KEY_TOTAL_BYTES to completedBytes,
                KEY_STATUS to DownloadStatus.COMPLETED.name
            )
            setProgress(successData)
            return@withContext Result.success(successData)
        }

        // 2. Resolve download candidate URLs
        val candidateUrls = resolveCandidateUrls(videoKey, videoStreamUrl)
        if (candidateUrls.isEmpty()) {
            Log.e(TAG, "No valid video URL or R2 key found for $displayTitle")
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED.name)
            return@withContext Result.failure(
                workDataOf(
                    KEY_EPISODE_ID to episodeId,
                    KEY_ERROR_MESSAGE to "No playable stream URL or R2 storage key found"
                )
            )
        }

        val estimatedTotalBytes = if (fileSizeMb > 0) {
            fileSizeMb * 1024L * 1024L
        } else {
            50L * 1024L * 1024L // fallback 50MB estimate
        }

        val initialExistingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val initialProgress = if (estimatedTotalBytes > 0) {
            (initialExistingBytes.toFloat() / estimatedTotalBytes).coerceIn(0f, 0.99f)
        } else 0f

        // 3. Upsert Room download entity
        val entity = DownloadEntity(
            id = downloadId,
            movieId = movieId,
            movieTitle = movieTitle,
            coverUrl = coverUrl,
            localFilePath = finalFile.absolutePath,
            progress = initialProgress,
            status = DownloadStatus.DOWNLOADING.name,
            downloadedBytes = initialExistingBytes,
            totalBytes = estimatedTotalBytes,
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            seasonNumber = seasonNum,
            episodeNumber = epNum
        )
        downloadDao.insertOrUpdate(entity)

        // 4. Promote to foreground service for resilient background execution
        try {
            val foregroundInfo = createForegroundInfo(notificationId, displayTitle, (initialProgress * 100).toInt(), "Caching episode...")
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground info for WorkManager: ${e.message}")
        }

        // Initial progress event
        setProgress(
            workDataOf(
                KEY_EPISODE_ID to episodeId,
                KEY_MOVIE_ID to movieId,
                KEY_PROGRESS_PERCENT to (initialProgress * 100).toInt(),
                KEY_PROGRESS_FRACTION to initialProgress,
                KEY_BYTES_DOWNLOADED to initialExistingBytes,
                KEY_TOTAL_BYTES to estimatedTotalBytes,
                KEY_STATUS to DownloadStatus.DOWNLOADING.name
            )
        )

        // 5. Execute download attempts across candidates
        var downloadSuccess = false
        var lastException: Exception? = null

        for (url in candidateUrls) {
            if (isStopped) {
                Log.d(TAG, "Worker stopped before attempting url: $url")
                downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name)
                return@withContext Result.retry()
            }

            try {
                Log.d(TAG, "Starting episode download from $url for $displayTitle")
                downloadSuccess = executeHttpDownload(
                    url = url,
                    tempFile = tempFile,
                    finalFile = finalFile,
                    downloadId = downloadId,
                    episodeId = episodeId,
                    movieId = movieId,
                    displayTitle = displayTitle,
                    notificationId = notificationId,
                    estimatedTotalBytes = estimatedTotalBytes
                )
                if (downloadSuccess) break
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Candidate $url failed for $displayTitle: ${e.message}")
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

            // Final notification
            notifyCompleted(notificationId, displayTitle)

            val successData = workDataOf(
                KEY_EPISODE_ID to episodeId,
                KEY_MOVIE_ID to movieId,
                KEY_PROGRESS_PERCENT to 100,
                KEY_PROGRESS_FRACTION to 1.0f,
                KEY_BYTES_DOWNLOADED to finalBytes,
                KEY_TOTAL_BYTES to finalBytes,
                KEY_STATUS to DownloadStatus.COMPLETED.name
            )
            setProgress(successData)
            Log.d(TAG, "Episode successfully cached: ${finalFile.absolutePath} ($finalBytes bytes)")
            Result.success(successData)
        } else {
            val status = if (isStopped) DownloadStatus.PAUSED else DownloadStatus.FAILED
            downloadDao.updateStatus(downloadId, status.name)
            Log.e(TAG, "Episode download failed or paused: ${lastException?.message}")
            if (isStopped) Result.retry() else Result.failure(
                workDataOf(
                    KEY_EPISODE_ID to episodeId,
                    KEY_ERROR_MESSAGE to (lastException?.message ?: "Download failed")
                )
            )
        }
    }

    private suspend fun executeHttpDownload(
        url: String,
        tempFile: File,
        finalFile: File,
        downloadId: String,
        episodeId: String,
        movieId: String,
        displayTitle: String,
        notificationId: Int,
        estimatedTotalBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "MovieRoom-WorkManagerCache/1.0")

        if (existingBytes > 0) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        val response: Response = httpClient.newCall(reqBuilder.build()).execute()

        if (!response.isSuccessful && response.code != 206) {
            response.close()
            if (response.code == 416) {
                // Range Not Satisfiable: wipe corrupt/excess temp file and restart
                tempFile.delete()
                existingBytes = 0L
                val freshResponse = httpClient.newCall(
                    Request.Builder().url(url).header("User-Agent", "MovieRoom-WorkManagerCache/1.0").build()
                ).execute()
                if (!freshResponse.isSuccessful) {
                    freshResponse.close()
                    return@withContext false
                }
                return@withContext processResponseBody(
                    resp = freshResponse,
                    tempFile = tempFile,
                    finalFile = finalFile,
                    downloadId = downloadId,
                    episodeId = episodeId,
                    movieId = movieId,
                    displayTitle = displayTitle,
                    notificationId = notificationId,
                    startingOffset = 0L,
                    fallbackTotalBytes = estimatedTotalBytes
                )
            }
            return@withContext false
        }

        val isPartial = response.code == 206
        val startingOffset = if (isPartial) existingBytes else 0L
        if (!isPartial && tempFile.exists()) {
            tempFile.delete()
        }

        return@withContext processResponseBody(
            resp = response,
            tempFile = tempFile,
            finalFile = finalFile,
            downloadId = downloadId,
            episodeId = episodeId,
            movieId = movieId,
            displayTitle = displayTitle,
            notificationId = notificationId,
            startingOffset = startingOffset,
            fallbackTotalBytes = estimatedTotalBytes
        )
    }

    private suspend fun processResponseBody(
        resp: Response,
        tempFile: File,
        finalFile: File,
        downloadId: String,
        episodeId: String,
        movieId: String,
        displayTitle: String,
        notificationId: Int,
        startingOffset: Long,
        fallbackTotalBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val database = MovieRoomDatabase.getInstance(context)
        val downloadDao = database.downloadDao()

        resp.use { response ->
            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) startingOffset + contentLength else fallbackTotalBytes

            val appendMode = startingOffset > 0
            val outputStream = FileOutputStream(tempFile, appendMode)
            val inputStream = body.byteStream()

            val buffer = ByteArray(64 * 1024) // 64 KB constant buffer
            var bytesDownloaded = startingOffset
            var lastUpdateBytes = startingOffset
            var lastUpdateTimeMs = System.currentTimeMillis()
            var speedBytesSample = 0L
            var speedTimeSample = System.currentTimeMillis()
            var speedFormatted = ""
            var readCount: Int

            try {
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { readCount = it } != -1) {
                            if (isStopped) {
                                output.flush()
                                Log.d(TAG, "Download worker cancelled for $episodeId")
                                return@withContext false
                            }

                            output.write(buffer, 0, readCount)
                            bytesDownloaded += readCount
                            speedBytesSample += readCount

                            val now = System.currentTimeMillis()
                            // Calculate speed sample every 1s
                            if (now - speedTimeSample >= 1000) {
                                val seconds = (now - speedTimeSample) / 1000.0
                                val speedBps = (speedBytesSample / seconds).toLong()
                                speedFormatted = formatSpeed(speedBps)
                                speedBytesSample = 0L
                                speedTimeSample = now
                            }

                            // Throttle progress updates to ~300ms to avoid UI/DB overload
                            if (bytesDownloaded - lastUpdateBytes >= 256 * 1024L || (now - lastUpdateTimeMs) >= 300) {
                                lastUpdateBytes = bytesDownloaded
                                lastUpdateTimeMs = now

                                val progressFraction = (bytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0.01f, 0.99f)
                                val progressPercent = (progressFraction * 100).toInt()

                                // 1. Update WorkManager real-time progress
                                val progressWorkData = workDataOf(
                                    KEY_EPISODE_ID to episodeId,
                                    KEY_MOVIE_ID to movieId,
                                    KEY_PROGRESS_PERCENT to progressPercent,
                                    KEY_PROGRESS_FRACTION to progressFraction,
                                    KEY_BYTES_DOWNLOADED to bytesDownloaded,
                                    KEY_TOTAL_BYTES to totalBytes,
                                    KEY_STATUS to DownloadStatus.DOWNLOADING.name,
                                    KEY_SPEED_TEXT to speedFormatted
                                )
                                setProgress(progressWorkData)

                                // 2. Update Room DB
                                downloadDao.updateProgress(
                                    id = downloadId,
                                    progress = progressFraction,
                                    status = DownloadStatus.DOWNLOADING.name,
                                    bytes = bytesDownloaded
                                )

                                // 3. Update Foreground Notification
                                updateNotification(
                                    notificationId = notificationId,
                                    title = displayTitle,
                                    progress = progressPercent,
                                    progressText = "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)} ($progressPercent%)${if (speedFormatted.isNotBlank()) " • $speedFormatted" else ""}"
                                )
                            }
                        }
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException || isStopped) {
                    Log.d(TAG, "Download interrupted gracefully for $episodeId")
                    return@withContext false
                }
                throw e
            }

            // Atomic file finalization
            if (tempFile.exists() && tempFile.length() > 0) {
                if (finalFile.exists()) finalFile.delete()
                val renamed = tempFile.renameTo(finalFile)
                if (!renamed) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                return@withContext true
            }

            return@withContext false
        }
    }

    private fun resolveCandidateUrls(videoKey: String, videoStreamUrl: String): List<String> {
        val candidates = mutableListOf<String>()
        val cleanKey = R2UrlUtils.extractCleanVideoKey(videoKey, videoStreamUrl)

        // 1. Direct canonical R2 CDN URL
        if (cleanKey.isNotBlank()) {
            val cdnUrl = R2UrlUtils.buildUrl(cleanKey)
            if (cdnUrl.isNotBlank() && (cdnUrl.startsWith("http://") || cdnUrl.startsWith("https://"))) {
                candidates.add(cdnUrl)
            }
        }

        // 2. Canonicalized stream URL
        val canonicalStream = R2UrlUtils.canonicalizeStreamUrl(videoStreamUrl, videoKey)
        if (canonicalStream.isNotBlank() &&
            (canonicalStream.startsWith("http://") || canonicalStream.startsWith("https://")) &&
            !candidates.contains(canonicalStream)
        ) {
            candidates.add(canonicalStream)
        }

        // 3. Raw stream URL if distinct
        if (videoStreamUrl.isNotBlank() &&
            (videoStreamUrl.startsWith("http://") || videoStreamUrl.startsWith("https://")) &&
            !candidates.contains(videoStreamUrl)
        ) {
            candidates.add(videoStreamUrl)
        }

        return candidates
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String,
        progress: Int,
        contentText: String
    ): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotification(notificationId: Int, title: String, progress: Int, progressText: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(progressText)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun notifyCompleted(notificationId: Int, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Cached: $title")
            .setContentText("Ready for offline playback")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Episode Offline Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for episodes being cached locally"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return ""
        val kbps = bytesPerSec / 1024.0
        return if (kbps >= 1024) {
            String.format("%.1f MB/s", kbps / 1024.0)
        } else {
            String.format("%.0f KB/s", kbps)
        }
    }

    companion object {
        const val TAG = "EpisodeDownloadWorker"
        const val CHANNEL_ID = "movie_room_episode_downloads"

        // Input Keys
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_MOVIE_ID = "movie_id"
        const val KEY_EPISODE_TITLE = "episode_title"
        const val KEY_MOVIE_TITLE = "movie_title"
        const val KEY_SEASON_NUMBER = "season_number"
        const val KEY_EPISODE_NUMBER = "episode_number"
        const val KEY_FILE_SIZE_MB = "file_size_mb"
        const val KEY_VIDEO_KEY = "video_key"
        const val KEY_STREAM_URL = "video_stream_url"
        const val KEY_COVER_URL = "cover_url"

        // Progress / Output Keys
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_PROGRESS_FRACTION = "progress_fraction"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_STATUS = "status"
        const val KEY_SPEED_TEXT = "speed_text"
        const val KEY_ERROR_MESSAGE = "error_message"

        fun createInputData(movie: Movie, episode: Episode): Data {
            return workDataOf(
                KEY_EPISODE_ID to episode.id,
                KEY_MOVIE_ID to movie.id,
                KEY_EPISODE_TITLE to episode.title,
                KEY_MOVIE_TITLE to movie.title,
                KEY_SEASON_NUMBER to episode.seasonNumber,
                KEY_EPISODE_NUMBER to episode.episodeNumber,
                KEY_FILE_SIZE_MB to episode.fileSizeMb,
                KEY_VIDEO_KEY to episode.videoKey,
                KEY_STREAM_URL to episode.videoStreamUrl,
                KEY_COVER_URL to movie.coverUrl
            )
        }
    }
}
