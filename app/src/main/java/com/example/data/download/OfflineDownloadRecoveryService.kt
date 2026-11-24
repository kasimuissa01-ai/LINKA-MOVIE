package com.example.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieDao
import com.example.data.local.MovieEntity
import com.example.domain.model.DownloadStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Helper service that scans the app's internal file system (and legacy/cache directories)
 * to verify previously downloaded movie and episode files on disk and map their verified
 * paths to Room `downloads` database entries so they are immediately playable offline.
 */
class OfflineDownloadRecoveryService(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val movieDao: MovieDao? = null
) {

    data class OfflineRecoveryReport(
        val scannedFilesCount: Int = 0,
        val verifiedEntriesCount: Int = 0,
        val repairedPathsCount: Int = 0,
        val recoveredOrphanCount: Int = 0,
        val invalidatedMissingCount: Int = 0
    )

    internal data class DiscoveredVideoFile(
        val file: File,
        val ownerUidFromPath: String?,
        val movieId: String?,
        val episodeId: String?,
        val isFromCache: Boolean
    )

    internal data class ParsedFileIds(
        val movieId: String,
        val episodeId: String?
    )

    private val scanMutex = Mutex()

    suspend fun scanAndReconcileOfflineFiles(
        activeUserId: String = "",
        isActiveDownload: (String) -> Boolean = { false }
    ): OfflineRecoveryReport = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val now = System.currentTimeMillis()
            val cleanActiveUid = OfflineStoragePaths.cleanUserId(activeUserId)
            val hasExplicitActiveUser = activeUserId.isNotBlank() && activeUserId.trim() != "guest"

            val knownMovies = try {
                movieDao?.getAllMoviesList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val moviesById = knownMovies.associateBy { it.id }

            // 1. Scan internal storage (plus legacy external app files & accidental cache files)
            val discoveredFiles = scanFileSystemForVideos(knownMovies)
            val discoveredByName = discoveredFiles.groupBy { it.file.name }

            val existingDownloads = downloadDao.getAllDownloadsRaw()
            val mappedPaths = mutableSetOf<String>()
            val coveredKeys = mutableSetOf<Triple<String, String, String?>>()

            var verifiedEntriesCount = 0
            var repairedPathsCount = 0
            var invalidatedMissingCount = 0

            // 2. Verify & reconcile every existing Room DownloadEntity against files on disk
            for (entity in existingDownloads) {
                if (isActiveDownload(entity.id)) {
                    if (entity.localFilePath.isNotBlank()) {
                        mappedPaths.add(canonicalPathOrRaw(File(entity.localFilePath.removePrefix("file://"))))
                    }
                    coveredKeys.add(Triple(OfflineStoragePaths.cleanUserId(entity.userId), entity.movieId, entity.episodeId))
                    continue
                }

                // Reset stale DOWNLOADING state for non-episode downloads when process restarted with no active job
                if (entity.episodeId.isNullOrBlank() && entity.status == DownloadStatus.DOWNLOADING.name) {
                    downloadDao.updateStatus(entity.id, DownloadStatus.PAUSED.name)
                }

                val entityUid = if (entity.userId.isBlank() && hasExplicitActiveUser) {
                    cleanActiveUid
                } else {
                    OfflineStoragePaths.cleanUserId(entity.userId)
                }

                val storedPath = entity.localFilePath.trim()
                if (storedPath.startsWith("content://")) {
                    val isValidUri = try {
                        context.contentResolver.openFileDescriptor(Uri.parse(storedPath), "r")?.use { pfd ->
                            pfd.statSize >= OfflineStoragePaths.MIN_VALID_VIDEO_BYTES
                        } == true
                    } catch (_: Exception) {
                        false
                    }
                    if (isValidUri) {
                        if (!entity.isFileVerified || entity.userId != entityUid) {
                            downloadDao.insertOrUpdate(
                                entity.copy(
                                    userId = entityUid,
                                    isFileVerified = true,
                                    lastVerifiedAt = now
                                )
                            )
                            repairedPathsCount++
                        }
                        verifiedEntriesCount++
                        coveredKeys.add(Triple(entityUid, entity.movieId, entity.episodeId))
                        continue
                    }
                }

                // Attempt to locate the real valid video file on disk for this Room record
                val matchedFile = findMatchingFileForEntity(
                    entity = entity,
                    targetUid = entityUid,
                    discoveredFiles = discoveredFiles,
                    discoveredByName = discoveredByName
                )

                if (matchedFile != null) {
                    val safeFile = OfflineStoragePaths.ensureNotInCache(
                        context = context,
                        sourceFile = matchedFile,
                        userId = entityUid,
                        movieId = entity.movieId,
                        episodeId = entity.episodeId
                    )
                    val fileSize = safeFile.length()
                    val canonicalSafePath = canonicalPathOrRaw(safeFile)
                    mappedPaths.add(canonicalSafePath)
                    coveredKeys.add(Triple(entityUid, entity.movieId, entity.episodeId))

                    val movieMeta = moviesById[entity.movieId]?.toDomain()
                    val epMeta = if (!entity.episodeId.isNullOrBlank()) {
                        movieMeta?.episodes?.firstOrNull { it.id == entity.episodeId }
                    } else null

                    val targetTitle = entity.movieTitle.ifBlank { movieMeta?.title ?: "Offline Movie" }
                    val targetCover = entity.coverUrl.ifBlank { movieMeta?.coverUrl.orEmpty() }
                    val targetTotal = maxOf(entity.totalBytes, fileSize)

                    val needsUpdate = entity.localFilePath != safeFile.absolutePath ||
                        entity.status != DownloadStatus.COMPLETED.name ||
                        !entity.isFileVerified ||
                        entity.downloadedBytes != fileSize ||
                        entity.userId != entityUid ||
                        entity.movieTitle != targetTitle ||
                        entity.coverUrl != targetCover

                    if (needsUpdate) {
                        downloadDao.insertOrUpdate(
                            entity.copy(
                                userId = entityUid,
                                movieTitle = targetTitle,
                                coverUrl = targetCover,
                                localFilePath = safeFile.absolutePath,
                                progress = 1.0f,
                                status = DownloadStatus.COMPLETED.name,
                                downloadedBytes = fileSize,
                                totalBytes = targetTotal,
                                episodeTitle = entity.episodeTitle ?: epMeta?.title,
                                seasonNumber = entity.seasonNumber ?: epMeta?.seasonNumber,
                                episodeNumber = entity.episodeNumber ?: epMeta?.episodeNumber,
                                isFileVerified = true,
                                lastVerifiedAt = now
                            )
                        )
                        repairedPathsCount++
                    }
                    verifiedEntriesCount++
                } else {
                    // Clean corrupt stub if stored path points to a tiny broken file
                    if (storedPath.isNotBlank() && !storedPath.startsWith("content://")) {
                        val broken = File(storedPath.removePrefix("file://"))
                        if (broken.exists() && broken.isFile && broken.length() < OfflineStoragePaths.MIN_VALID_VIDEO_BYTES) {
                            runCatching { broken.delete() }
                        }
                    }

                    if (entity.status == DownloadStatus.COMPLETED.name || entity.isFileVerified) {
                        Log.w(TAG, "Marking missing/incomplete offline file as FAILED for ${entity.id} (${entity.movieTitle})")
                        downloadDao.markFileMissing(entity.id, DownloadStatus.FAILED.name, now)
                        invalidatedMissingCount++
                    }
                }
            }

            // 3. Map any unmapped ("orphan") valid video files on disk into Room download entries
            var recoveredOrphanCount = 0
            for (discovered in discoveredFiles) {
                val canonicalDiscovered = canonicalPathOrRaw(discovered.file)
                if (mappedPaths.contains(canonicalDiscovered)) continue

                val movieId = discovered.movieId ?: continue
                val episodeId = discovered.episodeId
                val ownerUid = discovered.ownerUidFromPath ?: cleanActiveUid
                val coverageKey = Triple(ownerUid, movieId, episodeId)
                if (coveredKeys.contains(coverageKey)) continue

                val safeFile = OfflineStoragePaths.ensureNotInCache(
                    context = context,
                    sourceFile = discovered.file,
                    userId = ownerUid,
                    movieId = movieId,
                    episodeId = episodeId
                )
                if (!safeFile.exists() || safeFile.length() < OfflineStoragePaths.MIN_VALID_VIDEO_BYTES) {
                    continue
                }

                val fileSize = safeFile.length()
                val movieMeta = moviesById[movieId]?.toDomain()
                val epMeta = if (!episodeId.isNullOrBlank()) {
                    movieMeta?.episodes?.firstOrNull { it.id == episodeId }
                } else null

                val downloadId = if (!episodeId.isNullOrBlank()) {
                    "${ownerUid}_${movieId}_ep_${episodeId}"
                } else {
                    "${ownerUid}_${movieId}"
                }

                val existingSameId = downloadDao.getDownloadById(downloadId)
                if (existingSameId != null && isActiveDownload(existingSameId.id)) {
                    continue
                }

                val recoveredEntity = DownloadEntity(
                    id = downloadId,
                    movieId = movieId,
                    movieTitle = movieMeta?.title?.ifBlank { null } ?: "Offline Movie ($movieId)",
                    coverUrl = movieMeta?.coverUrl.orEmpty(),
                    localFilePath = safeFile.absolutePath,
                    progress = 1.0f,
                    status = DownloadStatus.COMPLETED.name,
                    downloadedBytes = fileSize,
                    totalBytes = maxOf(existingSameId?.totalBytes ?: 0L, fileSize),
                    episodeId = episodeId,
                    episodeTitle = epMeta?.title ?: episodeId?.let { "Episode" },
                    seasonNumber = epMeta?.seasonNumber ?: if (episodeId != null) 1 else null,
                    episodeNumber = epMeta?.episodeNumber,
                    userId = ownerUid,
                    isFileVerified = true,
                    lastVerifiedAt = now
                )
                downloadDao.insertOrUpdate(recoveredEntity)
                mappedPaths.add(canonicalPathOrRaw(safeFile))
                coveredKeys.add(coverageKey)
                recoveredOrphanCount++
                verifiedEntriesCount++
                Log.d(TAG, "Recovered unmapped offline file -> Room [$downloadId]: ${safeFile.absolutePath} ($fileSize bytes)")
            }

            val report = OfflineRecoveryReport(
                scannedFilesCount = discoveredFiles.size,
                verifiedEntriesCount = verifiedEntriesCount,
                repairedPathsCount = repairedPathsCount,
                recoveredOrphanCount = recoveredOrphanCount,
                invalidatedMissingCount = invalidatedMissingCount
            )
            Log.d(
                TAG,
                "Offline filesystem scan complete: scanned=${report.scannedFilesCount}, verified=${report.verifiedEntriesCount}, " +
                    "repaired=${report.repairedPathsCount}, recoveredOrphans=${report.recoveredOrphanCount}, missing=${report.invalidatedMissingCount}"
            )
            report
        }
    }

    private fun findMatchingFileForEntity(
        entity: DownloadEntity,
        targetUid: String,
        discoveredFiles: List<DiscoveredVideoFile>,
        discoveredByName: Map<String, List<DiscoveredVideoFile>>
    ): File? {
        val storedPath = entity.localFilePath.trim()
        if (storedPath.isNotBlank() && !storedPath.startsWith("content://")) {
            val directFile = File(storedPath.removePrefix("file://"))
            if (directFile.exists() && directFile.isFile && directFile.length() >= OfflineStoragePaths.MIN_VALID_VIDEO_BYTES) {
                return directFile
            }
        }

        // Standard candidate locations for this user (persistent internal first, then legacy)
        val userCandidate = OfflineStoragePaths.findVerifiedFileForUser(
            context = context,
            userId = targetUid,
            movieId = entity.movieId,
            episodeId = entity.episodeId
        )
        if (userCandidate != null) return userCandidate

        // Match discovered files parsed to the exact (movieId, episodeId) owned by this user or unscoped root
        val parsedMatch = discoveredFiles.firstOrNull { discovered ->
            discovered.movieId == entity.movieId &&
                discovered.episodeId == entity.episodeId &&
                (discovered.ownerUidFromPath == null || discovered.ownerUidFromPath == targetUid)
        }
        if (parsedMatch != null) return parsedMatch.file

        // Finally, if the stored path had a non-standard filename inside an internal directory, match by filename
        if (storedPath.isNotBlank() && !storedPath.startsWith("content://")) {
            val expectedName = File(storedPath.removePrefix("file://")).name
            if (expectedName.isNotBlank()) {
                val byName = discoveredByName[expectedName]?.firstOrNull { discovered ->
                    discovered.ownerUidFromPath == null || discovered.ownerUidFromPath == targetUid
                }
                if (byName != null) return byName.file
            }
        }

        return null
    }

    private fun scanFileSystemForVideos(knownMovies: List<MovieEntity>): List<DiscoveredVideoFile> {
        val results = mutableListOf<DiscoveredVideoFile>()
        val seenCanonicalPaths = mutableSetOf<String>()

        val scanRoots = buildList<Pair<File, Boolean>> {
            // Priority 1: Persistent internal app storage (offline_downloads & whole filesDir)
            add(OfflineStoragePaths.getPrimaryRootDir(context) to false)
            add(context.filesDir to false)
            // Priority 2: Legacy external app-specific files directory
            context.getExternalFilesDir(null)?.let { add(it to false) }
            // Priority 3: Cache directories (to rescue/promote any accidentally cached movie_*.mp4)
            add(context.cacheDir to true)
            context.externalCacheDir?.let { add(it to true) }
        }

        for ((root, isCacheRoot) in scanRoots) {
            if (!root.exists() || !root.isDirectory) continue
            walkVideoDirectory(
                dir = root,
                isCacheRoot = isCacheRoot,
                knownMovies = knownMovies,
                seenCanonicalPaths = seenCanonicalPaths,
                out = results
            )
        }

        return results
    }

    private fun walkVideoDirectory(
        dir: File,
        isCacheRoot: Boolean,
        knownMovies: List<MovieEntity>,
        seenCanonicalPaths: MutableSet<String>,
        out: MutableList<DiscoveredVideoFile>
    ) {
        val children = dir.listFiles() ?: return
        for (entry in children) {
            if (entry.isDirectory) {
                val name = entry.name
                // Never scan Media3 SimpleCache chunks or Coil image cache directories
                if (name == "movieroom_video_cache" || name == "prefetched_headers" || name == "image_cache") {
                    continue
                }
                walkVideoDirectory(entry, isCacheRoot, knownMovies, seenCanonicalPaths, out)
            } else if (entry.isFile) {
                val name = entry.name
                val lower = name.lowercase()
                if (!lower.endsWith(".mp4")) continue

                if (entry.length() < OfflineStoragePaths.MIN_VALID_VIDEO_BYTES) {
                    if (name.startsWith("movie_")) {
                        runCatching { entry.delete() }
                    }
                    continue
                }

                // In cache directories only rescue explicit movie_*.mp4 downloads
                if (isCacheRoot && !name.startsWith("movie_")) continue

                val canonical = canonicalPathOrRaw(entry)
                if (!seenCanonicalPaths.add(canonical)) continue

                val parsedIds = parseDownloadFileName(name, knownMovies)
                val ownerUid = extractUserIdFromFile(entry)
                out.add(
                    DiscoveredVideoFile(
                        file = entry,
                        ownerUidFromPath = ownerUid,
                        movieId = parsedIds?.movieId,
                        episodeId = parsedIds?.episodeId,
                        isFromCache = isCacheRoot
                    )
                )
            }
        }
    }

    internal fun parseDownloadFileName(fileName: String, knownMovies: List<MovieEntity>): ParsedFileIds? {
        if (!fileName.startsWith("movie_") || !fileName.endsWith(".mp4", ignoreCase = true)) {
            return null
        }
        val stem = fileName.removePrefix("movie_").dropLast(4)
        if (stem.isBlank()) return null

        // Exact match against known MovieEntity IDs first so underscores inside IDs never split ambiguously
        for (movie in knownMovies) {
            if (stem == movie.id) {
                return ParsedFileIds(movieId = movie.id, episodeId = null)
            }
            val epPrefix = "${movie.id}_ep_"
            if (stem.startsWith(epPrefix) && stem.length > epPrefix.length) {
                return ParsedFileIds(
                    movieId = movie.id,
                    episodeId = stem.removePrefix(epPrefix)
                )
            }
        }

        // Generic fallback parsing for offline startup before catalog metadata sync
        val epIndex = stem.indexOf("_ep_")
        return if (epIndex > 0 && epIndex + 4 < stem.length) {
            ParsedFileIds(
                movieId = stem.substring(0, epIndex),
                episodeId = stem.substring(epIndex + 4)
            )
        } else {
            ParsedFileIds(movieId = stem, episodeId = null)
        }
    }

    private fun extractUserIdFromFile(file: File): String? {
        var current: File? = file.parentFile
        while (current != null) {
            val parent = current.parentFile
            if (parent?.name == "users" && current.name.isNotBlank()) {
                return current.name
            }
            current = parent
        }
        return null
    }

    private fun canonicalPathOrRaw(file: File): String {
        return runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
    }

    companion object {
        private const val TAG = "OfflineRecoveryService"
    }
}
