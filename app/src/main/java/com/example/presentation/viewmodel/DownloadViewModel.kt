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

class DownloadViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadItem>> = repository.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startDownload(movie: Movie, context: Context) {
        viewModelScope.launch {
            repository.startDownload(movie, context)
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
                repository.startDownload(movie, context)
            } else {
                val fallbackMovie = Movie(
                    id = item.movieId,
                    title = item.movieTitle,
                    description = "",
                    genres = emptyList(),
                    coverUrl = item.coverUrl,
                    videoKey = "",
                    videoStreamUrl = "https://media.w3.org/2010/05/bunny/trailer.mp4",
                    durationMinutes = 120,
                    fileSizeMb = (item.totalBytes / (1024 * 1024)).coerceAtLeast(50L),
                    releaseYear = 2024,
                    rating = 8.0,
                    cast = emptyList(),
                    isFeatured = false
                )
                repository.startDownload(fallbackMovie, context)
            }
        }
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch {
            repository.deleteDownload(downloadId)
        }
    }
}
