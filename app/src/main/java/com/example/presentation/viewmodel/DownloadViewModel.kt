package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.EpisodeDownloadProgress
import com.example.data.download.EpisodeDownloadProgressManager
import com.example.data.repository.MovieRepository
import com.example.domain.model.DownloadItem
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val repository: MovieRepository,
    context: Context? = null
) : ViewModel() {

    private val progressManager: EpisodeDownloadProgressManager? = context?.applicationContext?.let {
        EpisodeDownloadProgressManager.getInstance(it)
    }

    val userSession: StateFlow<com.example.domain.model.UserSession> = repository.userSession

    val downloads: StateFlow<List<DownloadItem>> = repository.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val episodeProgressMap: StateFlow<Map<String, EpisodeDownloadProgress>> =
        (progressManager?.let { pm ->
            repository.userSession.flatMapLatest { session ->
                pm.observeAllEpisodeProgress(session.uid)
            }
        } ?: flowOf(emptyMap()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    fun startDownload(movie: Movie, context: Context, episode: Episode? = null) {
        viewModelScope.launch {
            if (episode != null) {
                val uid = repository.userSession.value.uid
                val manager = progressManager ?: EpisodeDownloadProgressManager.getInstance(context)
                manager.startEpisodeDownload(movie, episode, uid)
            } else {
                repository.startDownload(movie, context, null)
            }
        }
    }

    fun startEpisodeDownload(movie: Movie, episode: Episode, context: Context) {
        val uid = repository.userSession.value.uid
        val manager = progressManager ?: EpisodeDownloadProgressManager.getInstance(context)
        manager.startEpisodeDownload(movie, episode, uid)
    }

    fun cancelEpisodeDownload(movieId: String, episodeId: String, context: Context) {
        val manager = progressManager ?: EpisodeDownloadProgressManager.getInstance(context)
        manager.cancelEpisodeDownload(movieId, episodeId)
    }

    fun pauseEpisodeDownload(movieId: String, episodeId: String, context: Context) {
        val manager = progressManager ?: EpisodeDownloadProgressManager.getInstance(context)
        manager.pauseEpisodeDownload(movieId, episodeId)
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            repository.pauseDownload(downloadId)
        }
    }

    fun retryDownload(item: DownloadItem, context: Context) {
        viewModelScope.launch {
            val movie = repository.getMovieById(item.movieId)
            if (movie != null) {
                val episode = if (!item.episodeId.isNullOrBlank()) {
                    movie.episodes.firstOrNull { it.id == item.episodeId }
                } else null
                if (episode != null) {
                    startEpisodeDownload(movie, episode, context)
                } else {
                    repository.retryDownload(item.id, movie, context, null)
                }
            } else {
                android.util.Log.w("DownloadViewModel", "Cannot retry download: movie ${item.movieId} not found in database.")
            }
        }
    }

    fun deleteDownload(downloadId: String, movieId: String = downloadId, episodeId: String? = null, context: Context? = null) {
        if (!episodeId.isNullOrBlank() && context != null) {
            cancelEpisodeDownload(movieId, episodeId, context)
        }
        viewModelScope.launch {
            repository.deleteDownload(downloadId, movieId, episodeId)
        }
    }

    /**
     * Clears all downloaded movies and episodes from disk and local database.
     */
    fun clearAllDownloads(context: Context) {
        val manager = progressManager ?: EpisodeDownloadProgressManager.getInstance(context)
        manager.cancelAllEpisodeDownloads()
        viewModelScope.launch {
            repository.clearAllDownloads(context)
        }
    }

    /**
     * Computes the total storage used by downloaded movies on device in bytes for the current user.
     */
    fun getTotalStorageUsedBytes(context: Context, downloadList: List<DownloadItem>): Long {
        val currentUid = repository.userSession.value.uid
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val userDir = java.io.File(destDir, "users/$currentUid")
        var diskBytes = 0L
        if (userDir.exists() && userDir.isDirectory) {
            userDir.listFiles()?.forEach { file ->
                diskBytes += file.length()
            }
        }
        val dbBytes = downloadList.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
        return maxOf(diskBytes, dbBytes)
    }

    /**
     * Formats bytes into a human-readable storage string (e.g., "1.45 GB", "320.5 MB").
     */
    fun formatStorageSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.1f KB", kb)
        }
    }
}

