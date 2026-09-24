package com.example.data.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.MovieRoomDatabase
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real-time feedback model for episodes being cached locally.
 */
data class EpisodeDownloadProgress(
    val episodeId: String,
    val movieId: String,
    val episodeTitle: String = "",
    val movieTitle: String = "",
    val progressPercent: Int = 0,
    val progressFraction: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedText: String = "",
    val isCachedLocally: Boolean = false,
    val errorMessage: String? = null
) {
    val isDownloading: Boolean get() = status == DownloadStatus.DOWNLOADING
    val isCompleted: Boolean get() = status == DownloadStatus.COMPLETED || isCachedLocally
    val isPaused: Boolean get() = status == DownloadStatus.PAUSED
    val isFailed: Boolean get() = status == DownloadStatus.FAILED

    fun formattedProgressText(): String {
        return when {
            isCompleted -> "Cached Locally"
            isDownloading && totalBytes > 0 -> {
                val downloadedMb = bytesDownloaded / (1024.0 * 1024.0)
                val totalMb = totalBytes / (1024.0 * 1024.0)
                String.format("%.1f / %.1f MB (%d%%)", downloadedMb, totalMb, progressPercent)
            }
            isDownloading -> "$progressPercent% Caching..."
            isPaused -> "Paused ($progressPercent%)"
            isFailed -> "Download Failed"
            else -> "Not Downloaded"
        }
    }
}

/**
 * Download Progress Manager using AndroidX WorkManager.
 *
 * Coordinates WorkManager background jobs with Room persistence,
 * offering reactive streams for real-time progress percentage feedback
 * in the episode list and player controls.
 */
class EpisodeDownloadProgressManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val workManager = WorkManager.getInstance(context)
    private val downloadDao = MovieRoomDatabase.getInstance(context).downloadDao()

    companion object {
        private const val TAG = "EpDownloadProgManager"
        const val TAG_ALL_EPISODES = "tag_episode_download"

        fun getWorkName(movieId: String, episodeId: String): String =
            "episode_cache_${movieId}_${episodeId}"

        @Volatile
        private var INSTANCE: EpisodeDownloadProgressManager? = null

        fun getInstance(context: Context): EpisodeDownloadProgressManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EpisodeDownloadProgressManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * Enqueues an episode download task via WorkManager with network constraints.
     */
    fun startEpisodeDownload(movie: Movie, episode: Episode) {
        val workName = getWorkName(movie.id, episode.id)
        val inputData = EpisodeDownloadWorker.createInputData(movie, episode)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(TAG_ALL_EPISODES)
            .addTag("movie_${movie.id}")
            .addTag("episode_${episode.id}")
            .build()

        Log.d(TAG, "Enqueuing unique WorkManager task: $workName for ${episode.title}")
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Cancels the active WorkManager job and cleans up database/temp files.
     */
    fun cancelEpisodeDownload(movieId: String, episodeId: String) {
        val workName = getWorkName(movieId, episodeId)
        val downloadId = "${movieId}_ep_${episodeId}"
        Log.d(TAG, "Cancelling WorkManager task: $workName")

        workManager.cancelUniqueWork(workName)

        scope.launch {
            downloadDao.deleteById(downloadId)
            val destDir = context.getExternalFilesDir(null) ?: context.filesDir
            val baseFileName = "movie_${movieId}_ep_${episodeId}.mp4"
            val tempFile = File(destDir, "$baseFileName.download")
            val finalFile = File(destDir, baseFileName)
            runCatching { if (tempFile.exists()) tempFile.delete() }
            runCatching { if (finalFile.exists()) finalFile.delete() }
        }
    }

    /**
     * Cancels all ongoing WorkManager episode downloads.
     */
    fun cancelAllEpisodeDownloads() {
        Log.d(TAG, "Cancelling all WorkManager episode downloads")
        workManager.cancelAllWorkByTag(TAG_ALL_EPISODES)
    }

    /**
     * Pauses the active WorkManager job.
     */
    fun pauseEpisodeDownload(movieId: String, episodeId: String) {
        val workName = getWorkName(movieId, episodeId)
        val downloadId = "${movieId}_ep_${episodeId}"
        Log.d(TAG, "Pausing WorkManager task: $workName")

        workManager.cancelUniqueWork(workName)
        scope.launch {
            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name)
        }
    }

    /**
     * Observes real-time progress for all episodes belonging to a specific movie.
     * Combines WorkManager's live WorkInfo stream with Room database state.
     */
    fun observeMovieEpisodesProgress(movieId: String): Flow<Map<String, EpisodeDownloadProgress>> {
        val workInfoFlow = workManager.getWorkInfosByTagFlow("movie_$movieId")
        val roomDownloadsFlow = downloadDao.observeDownloadsForMovie(movieId)

        return combine(workInfoFlow, roomDownloadsFlow) { workInfoList, roomList ->
            val progressMap = mutableMapOf<String, EpisodeDownloadProgress>()

            // 1. Seed from Room DB (handles completed offline items & previous sessions)
            for (entity in roomList) {
                val epId = entity.episodeId ?: continue
                val status = runCatching { DownloadStatus.valueOf(entity.status) }.getOrDefault(DownloadStatus.QUEUED)
                val isCompleted = status == DownloadStatus.COMPLETED
                val pct = (entity.progress * 100).toInt().coerceIn(0, 100)

                progressMap[epId] = EpisodeDownloadProgress(
                    episodeId = epId,
                    movieId = entity.movieId,
                    episodeTitle = entity.episodeTitle.orEmpty(),
                    movieTitle = entity.movieTitle,
                    progressPercent = pct,
                    progressFraction = entity.progress,
                    bytesDownloaded = entity.downloadedBytes,
                    totalBytes = entity.totalBytes,
                    status = status,
                    isCachedLocally = isCompleted
                )
            }

            // 2. Overlay live WorkManager real-time progress
            for (workInfo in workInfoList) {
                val epId = workInfo.tags.firstOrNull { it.startsWith("episode_") }?.removePrefix("episode_")
                    ?: workInfo.progress.getString(EpisodeDownloadWorker.KEY_EPISODE_ID)
                    ?: continue

                val mapped = mapWorkInfoToProgress(workInfo, epId, movieId, progressMap[epId])
                progressMap[epId] = mapped
            }

            progressMap
        }
    }

    /**
     * Observes real-time progress for all episodes across all movies.
     */
    fun observeAllEpisodeProgress(): Flow<Map<String, EpisodeDownloadProgress>> {
        val workInfoFlow = workManager.getWorkInfosByTagFlow(TAG_ALL_EPISODES)
        val roomDownloadsFlow = downloadDao.getAllDownloads()

        return combine(workInfoFlow, roomDownloadsFlow) { workInfoList, roomList ->
            val progressMap = mutableMapOf<String, EpisodeDownloadProgress>()

            // 1. Seed from Room DB
            for (entity in roomList) {
                val epId = entity.episodeId ?: continue
                val status = runCatching { DownloadStatus.valueOf(entity.status) }.getOrDefault(DownloadStatus.QUEUED)
                val isCompleted = status == DownloadStatus.COMPLETED

                progressMap[epId] = EpisodeDownloadProgress(
                    episodeId = epId,
                    movieId = entity.movieId,
                    episodeTitle = entity.episodeTitle.orEmpty(),
                    movieTitle = entity.movieTitle,
                    progressPercent = (entity.progress * 100).toInt().coerceIn(0, 100),
                    progressFraction = entity.progress,
                    bytesDownloaded = entity.downloadedBytes,
                    totalBytes = entity.totalBytes,
                    status = status,
                    isCachedLocally = isCompleted
                )
            }

            // 2. Overlay active WorkInfo
            for (workInfo in workInfoList) {
                val epId = workInfo.tags.firstOrNull { it.startsWith("episode_") }?.removePrefix("episode_")
                    ?: workInfo.progress.getString(EpisodeDownloadWorker.KEY_EPISODE_ID)
                    ?: continue
                val movieId = workInfo.tags.firstOrNull { it.startsWith("movie_") }?.removePrefix("movie_")
                    ?: workInfo.progress.getString(EpisodeDownloadWorker.KEY_MOVIE_ID)
                    ?: ""

                val mapped = mapWorkInfoToProgress(workInfo, epId, movieId, progressMap[epId])
                progressMap[epId] = mapped
            }

            progressMap
        }
    }

    private fun mapWorkInfoToProgress(
        workInfo: WorkInfo,
        episodeId: String,
        movieId: String,
        existing: EpisodeDownloadProgress?
    ): EpisodeDownloadProgress {
        val progressData = workInfo.progress
        val outputData = workInfo.outputData

        val progressPercent = progressData.getInt(
            EpisodeDownloadWorker.KEY_PROGRESS_PERCENT,
            outputData.getInt(EpisodeDownloadWorker.KEY_PROGRESS_PERCENT, existing?.progressPercent ?: 0)
        )
        val progressFraction = progressData.getFloat(
            EpisodeDownloadWorker.KEY_PROGRESS_FRACTION,
            outputData.getFloat(EpisodeDownloadWorker.KEY_PROGRESS_FRACTION, existing?.progressFraction ?: 0f)
        )
        val bytesDownloaded = progressData.getLong(
            EpisodeDownloadWorker.KEY_BYTES_DOWNLOADED,
            outputData.getLong(EpisodeDownloadWorker.KEY_BYTES_DOWNLOADED, existing?.bytesDownloaded ?: 0L)
        )
        val totalBytes = progressData.getLong(
            EpisodeDownloadWorker.KEY_TOTAL_BYTES,
            outputData.getLong(EpisodeDownloadWorker.KEY_TOTAL_BYTES, existing?.totalBytes ?: 0L)
        )
        val speedText = progressData.getString(EpisodeDownloadWorker.KEY_SPEED_TEXT).orEmpty()

        val derivedStatus = when (workInfo.state) {
            WorkInfo.State.RUNNING -> DownloadStatus.DOWNLOADING
            WorkInfo.State.SUCCEEDED -> DownloadStatus.COMPLETED
            WorkInfo.State.FAILED -> DownloadStatus.FAILED
            WorkInfo.State.CANCELLED -> DownloadStatus.PAUSED
            WorkInfo.State.ENQUEUED -> DownloadStatus.QUEUED
            WorkInfo.State.BLOCKED -> DownloadStatus.QUEUED
        }

        val isCompleted = derivedStatus == DownloadStatus.COMPLETED || existing?.isCachedLocally == true

        return EpisodeDownloadProgress(
            episodeId = episodeId,
            movieId = movieId,
            episodeTitle = existing?.episodeTitle.orEmpty(),
            movieTitle = existing?.movieTitle.orEmpty(),
            progressPercent = if (isCompleted) 100 else progressPercent.coerceIn(0, 100),
            progressFraction = if (isCompleted) 1.0f else progressFraction.coerceIn(0f, 1f),
            bytesDownloaded = if (isCompleted && totalBytes > 0) totalBytes else bytesDownloaded,
            totalBytes = totalBytes,
            status = derivedStatus,
            speedText = speedText,
            isCachedLocally = isCompleted,
            errorMessage = outputData.getString(EpisodeDownloadWorker.KEY_ERROR_MESSAGE)
        )
    }
}
