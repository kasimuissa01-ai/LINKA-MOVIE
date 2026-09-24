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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val downloads: StateFlow<List<DownloadItem>> = repository.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val episodeProgressMap: StateFlow<Map<String, EpisodeDownloadProgress>> =
        (progressManager?.observeAllEpisodeProgress() ?: flowOf(emptyMap()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    fun startDownload(movie: Movie, context: Context, episode: Episode? = null) {
        viewModelScope.launch {
            if (episode != null) {
                // Route episode downloads through WorkManager for guaranteed background execution and progress
                val manager = progressManager ?: EpisodeDownloadProgressManager.getInstance(context)
                manager.startEpisodeDownload(movie, episode)
            } else {
                repository.startDownload(movie, context, null)
            }
        }
    }

    fun startEpisodeDownload(movie: Movie, episode: Episode, context: Context) {
        val manager = progressManager ?: EpisodeDownloadProgressManager.getInstance(context)
        manager.startEpisodeDownload(movie, episode)
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
}

