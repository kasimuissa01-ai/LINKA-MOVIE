package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.TmdbMovieResult
import com.example.data.remote.TmdbService
import com.example.data.repository.MovieRepository
import com.example.domain.model.Movie
import com.example.domain.model.UploadSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class UploadProgressState(
    val isUploading: Boolean = false,
    val session: UploadSession? = null,
    val overallProgress: Float = 0f,
    val currentPartIndex: Int = 0,
    val statusMessage: String = "",
    val isCompleted: Boolean = false,
    val error: String? = null
)

class AdminViewModel(
    private val repository: MovieRepository,
    private val tmdbService: TmdbService = TmdbService()
) : ViewModel() {

    val movies: StateFlow<List<Movie>> = repository.getAllMovies()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uploadState = MutableStateFlow(UploadProgressState())
    val uploadState: StateFlow<UploadProgressState> = _uploadState.asStateFlow()

    // TMDB Search integration states
    private val _tmdbSearchResults = MutableStateFlow<List<TmdbMovieResult>>(emptyList())
    val tmdbSearchResults: StateFlow<List<TmdbMovieResult>> = _tmdbSearchResults.asStateFlow()

    private val _isSearchingTmdb = MutableStateFlow(false)
    val isSearchingTmdb: StateFlow<Boolean> = _isSearchingTmdb.asStateFlow()

    private var tmdbSearchJob: Job? = null
    private var activeUploadJob: Job? = null

    fun searchTmdb(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _tmdbSearchResults.value = emptyList()
            _isSearchingTmdb.value = false
            return
        }

        tmdbSearchJob?.cancel()
        tmdbSearchJob = viewModelScope.launch {
            _isSearchingTmdb.value = true
            delay(200) // Debounce typing
            try {
                val results = tmdbService.searchMovies(trimmed)
                _tmdbSearchResults.value = results
            } catch (e: Exception) {
                _tmdbSearchResults.value = emptyList()
            } finally {
                _isSearchingTmdb.value = false
            }
        }
    }

    fun clearTmdbSearch() {
        tmdbSearchJob?.cancel()
        _tmdbSearchResults.value = emptyList()
        _isSearchingTmdb.value = false
    }

    fun addMovieWithMultipartUpload(
        title: String,
        description: String,
        genres: List<String>,
        coverUrl: String,
        fileSizeMb: Long,
        streamUrl: String = "",
        releaseYear: Int = 2026,
        rating: Double = 4.8,
        isFeatured: Boolean = false
    ) {
        val movieId = "m_adm_${UUID.randomUUID().toString().take(6)}"
        val videoKey = "movies/${title.lowercase().replace(" ", "_")}.mp4"
        val fallbackStream = if (streamUrl.isNotBlank()) streamUrl
        else "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

        val newMovie = Movie(
            id = movieId,
            title = title,
            description = description,
            genres = genres,
            coverUrl = if (coverUrl.isNotBlank()) coverUrl
            else "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80",
            videoKey = videoKey,
            videoStreamUrl = fallbackStream,
            durationMinutes = 118,
            fileSizeMb = fileSizeMb,
            releaseYear = releaseYear,
            rating = rating,
            cast = listOf("Movie Cast"),
            isFeatured = isFeatured
        )

        activeUploadJob?.cancel()
        activeUploadJob = viewModelScope.launch {
            try {
                _uploadState.value = UploadProgressState(
                    isUploading = true,
                    statusMessage = "Initiating R2 Multipart Upload with presigned parts..."
                )

                val session = repository.initiateMultipartUpload(newMovie, fileSizeMb)
                _uploadState.value = _uploadState.value.copy(
                    session = session,
                    statusMessage = "Uploading ${session.parts.size} chunks (10MB per part) to Cloudflare R2..."
                )

                var currentSession = session
                for (i in session.parts.indices) {
                    _uploadState.value = _uploadState.value.copy(
                        currentPartIndex = i + 1,
                        statusMessage = "Uploading chunk ${i + 1}/${session.parts.size} to R2..."
                    )

                    currentSession = repository.executePartUpload(currentSession, i) { partNum, partProgress ->
                        val overall = ((i + partProgress) / session.parts.size).coerceIn(0f, 1f)
                        _uploadState.value = _uploadState.value.copy(
                            overallProgress = overall
                        )
                    }
                    delay(120)
                }

                _uploadState.value = _uploadState.value.copy(
                    statusMessage = "Finalizing multipart upload & registering metadata in catalog..."
                )
                repository.completeMultipartUpload(currentSession)
                repository.insertMovie(newMovie)

                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    isCompleted = true,
                    overallProgress = 1.0f,
                    statusMessage = "Movie '${newMovie.title}' published successfully to Cloudflare R2!"
                )
            } catch (e: Exception) {
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    error = e.message ?: "Upload failed"
                )
            }
        }
    }

    fun cancelUpload() {
        activeUploadJob?.cancel()
        _uploadState.value = UploadProgressState(statusMessage = "Upload cancelled.")
    }

    fun resetUploadState() {
        _uploadState.value = UploadProgressState()
    }

    fun updateMovie(movie: Movie) {
        viewModelScope.launch {
            repository.updateMovie(movie)
        }
    }

    fun deleteMovie(movieId: String) {
        viewModelScope.launch {
            repository.deleteMovie(movieId)
        }
    }
}
