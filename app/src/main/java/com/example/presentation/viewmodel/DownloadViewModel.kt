package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.MovieRepository
import com.example.domain.model.DownloadItem
import com.example.domain.model.Movie
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.domain.model.Episode

class DownloadViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadItem>> = repository.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startDownload(movie: Movie, context: Context, episode: Episode? = null) {
        viewModelScope.launch {
            repository.startDownload(movie, context, episode)
        }
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
                repository.retryDownload(item.id, movie, context, episode)
            } else {
                android.util.Log.w("DownloadViewModel", "Cannot retry download: movie ${item.movieId} not found in database.")
            }
        }
    }

    fun deleteDownload(downloadId: String, movieId: String = downloadId, episodeId: String? = null) {
        viewModelScope.launch {
            repository.deleteDownload(downloadId, movieId, episodeId)
        }
    }
}
