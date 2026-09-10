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

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch {
            repository.deleteDownload(downloadId)
        }
    }
}
