package com.example.data.download

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Single source of truth for MovieRoom's persistent internal offline download storage.
 *
 * Storage guarantees:
 * 1. All newly downloaded movies and episodes (plus `.download` temporary files) are written
 *    strictly inside private persistent internal storage:
 *    `<context.filesDir>/offline_downloads/users/<uid>/...`
 *    They are NEVER written to `cacheDir`, `externalCacheDir`, or removable external storage.
 * 2. Read/verification helpers prioritize persistent internal storage first while still checking
 *    legacy internal and external app-files directories so previously downloaded movies continue
 *    playing offline without requiring users to re-download them.
 */
object OfflineStoragePaths {

    private const val OFFLINE_DIR_NAME = "offline_downloads"
    private const val USERS_DIR_NAME = "users"
    const val MIN_VALID_VIDEO_BYTES = 1024L * 1024L

    fun cleanUserId(userId: String?): String = userId?.trim()?.ifBlank { null } ?: "guest"

    fun getDownloadFileName(movieId: String, episodeId: String? = null): String {
        return if (!episodeId.isNullOrBlank()) {
            "movie_${movieId}_ep_${episodeId}.mp4"
        } else {
            "movie_${movieId}.mp4"
        }
    }

    /**
     * Persistent internal root directory for offline videos (never cacheDir).
     */
    fun getPrimaryRootDir(context: Context): File {
        return File(context.filesDir, OFFLINE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Persistent internal directory scoped to a single user account.
     */
    fun getUserDownloadDir(context: Context, userId: String?): File {
        val cleanUid = cleanUserId(userId)
        return File(File(getPrimaryRootDir(context), USERS_DIR_NAME), cleanUid)
    }

    fun getPrimaryTargetFile(
        context: Context,
        userId: String?,
        movieId: String,
        episodeId: String? = null
    ): File {
        val userDir = getUserDownloadDir(context, userId)
        return File(userDir, getDownloadFileName(movieId, episodeId))
    }

    fun getPrimaryTempFile(
        context: Context,
        userId: String?,
        movieId: String,
        episodeId: String? = null
    ): File {
        val userDir = getUserDownloadDir(context, userId)
        return File(userDir, "${getDownloadFileName(movieId, episodeId)}.download")
    }

    /**
     * Ordered list of candidate file locations for an item, prioritizing persistent internal storage
     * first and retaining legacy external/root paths for backwards compatibility with existing downloads.
     */
    fun getCandidateReadFiles(
        context: Context,
        userId: String?,
        movieId: String,
        episodeId: String? = null
    ): List<File> {
        val cleanUid = cleanUserId(userId)
        val fileName = getDownloadFileName(movieId, episodeId)
        val candidates = mutableListOf<File>()

        // 1. Canonical persistent internal user folder
        candidates.add(File(File(File(getPrimaryRootDir(context), USERS_DIR_NAME), cleanUid), fileName))
        // 2. Previous internal user folder
        candidates.add(File(File(File(context.filesDir, USERS_DIR_NAME), cleanUid), fileName))
        // 3. Legacy internal root
        candidates.add(File(context.filesDir, fileName))

        // 4-5. Legacy external app-specific folders (for already-downloaded files from earlier versions)
        val externalDir = context.getExternalFilesDir(null)
        if (externalDir != null) {
            candidates.add(File(File(File(externalDir, USERS_DIR_NAME), cleanUid), fileName))
            candidates.add(File(externalDir, fileName))
        }

        return candidates.distinctBy { it.absolutePath }
    }

    /**
     * Finds a verified completed video file owned by this user across persistent internal and legacy folders.
     */
    fun findVerifiedFileForUser(
        context: Context,
        userId: String?,
        movieId: String,
        episodeId: String? = null
    ): File? {
        return getCandidateReadFiles(context, userId, movieId, episodeId)
            .firstOrNull { it.exists() && it.length() >= MIN_VALID_VIDEO_BYTES }
    }

    /**
     * Finds an already-downloaded verified file on device across any account or legacy storage
     * (used for fast local copy so another account doesn't re-download over network).
     */
    fun findExistingCompletedFileAnyAccount(
        context: Context,
        movieId: String,
        episodeId: String? = null
    ): File? {
        val fileName = getDownloadFileName(movieId, episodeId)
        val roots = buildList {
            add(getPrimaryRootDir(context))
            add(context.filesDir)
            context.getExternalFilesDir(null)?.let { add(it) }
        }

        for (root in roots) {
            val direct = File(root, fileName)
            if (direct.exists() && direct.length() >= MIN_VALID_VIDEO_BYTES) return direct

            val usersDir = File(root, USERS_DIR_NAME)
            if (usersDir.exists() && usersDir.isDirectory) {
                usersDir.listFiles()?.forEach { accountDir ->
                    if (accountDir.isDirectory) {
                        val candidate = File(accountDir, fileName)
                        if (candidate.exists() && candidate.length() >= MIN_VALID_VIDEO_BYTES) {
                            return candidate
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Returns all final and `.download` temp file locations that should be removed when deleting/cancelling.
     */
    fun getAllCleanupTargets(
        context: Context,
        userId: String?,
        movieId: String,
        episodeId: String? = null
    ): List<File> {
        val cleanUid = cleanUserId(userId)
        val fileName = getDownloadFileName(movieId, episodeId)
        val targets = mutableListOf<File>()

        val dirs = buildList {
            add(getUserDownloadDir(context, cleanUid))
            add(File(File(context.filesDir, USERS_DIR_NAME), cleanUid))
            context.getExternalFilesDir(null)?.let { ext ->
                add(File(File(ext, USERS_DIR_NAME), cleanUid))
            }
        }
        for (dir in dirs) {
            targets.add(File(dir, fileName))
            targets.add(File(dir, "$fileName.download"))
        }
        return targets.distinctBy { it.absolutePath }
    }

    /**
     * Deletes all offline download folders scoped to the given user across internal and legacy storage.
     */
    fun clearAllUserDirectories(context: Context, userId: String?) {
        val cleanUid = cleanUserId(userId)
        runCatching { getUserDownloadDir(context, cleanUid).deleteRecursively() }
        runCatching { File(File(context.filesDir, USERS_DIR_NAME), cleanUid).deleteRecursively() }
        runCatching {
            context.getExternalFilesDir(null)?.let { ext ->
                File(File(ext, USERS_DIR_NAME), cleanUid).deleteRecursively()
            }
        }
    }

    /**
     * Returns true if the given file resides inside a volatile cache directory (`cacheDir` or `externalCacheDir`).
     */
    fun isInsideCacheDir(context: Context, file: File): Boolean {
        val fileCanonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val cacheRoots = buildList {
            add(context.cacheDir)
            context.externalCacheDir?.let { add(it) }
        }
        return cacheRoots.any { root ->
            val rootCanonical = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
            fileCanonical == rootCanonical || fileCanonical.startsWith("$rootCanonical${File.separator}")
        }
    }

    /**
     * If a valid file accidentally resides in a cache directory, promotes it into persistent internal storage.
     */
    fun ensureNotInCache(
        context: Context,
        sourceFile: File,
        userId: String?,
        movieId: String,
        episodeId: String? = null
    ): File {
        if (!isInsideCacheDir(context, sourceFile)) return sourceFile
        val target = getPrimaryTargetFile(context, userId, movieId, episodeId)
        target.parentFile?.mkdirs()
        return try {
            if (target.exists() && target.length() >= MIN_VALID_VIDEO_BYTES) {
                target
            } else {
                sourceFile.copyTo(target, overwrite = true)
                runCatching { sourceFile.delete() }
                target
            }
        } catch (_: Exception) {
            sourceFile
        }
    }

    fun getUserStorageUsedBytes(context: Context, userId: String?): Long {
        val cleanUid = cleanUserId(userId)
        val dirs = buildList {
            add(getUserDownloadDir(context, cleanUid))
            add(File(File(context.filesDir, USERS_DIR_NAME), cleanUid))
            context.getExternalFilesDir(null)?.let { ext ->
                add(File(File(ext, USERS_DIR_NAME), cleanUid))
            }
        }.distinctBy { it.absolutePath }

        var total = 0L
        for (dir in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile) total += f.length()
                }
            }
        }
        return total
    }

    fun getAvailableInternalStorageBytes(context: Context): Long {
        return try {
            val root = getPrimaryRootDir(context)
            val stat = StatFs(root.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }
}
