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

    // Render Backend Connection Status (null = untested, true = online, false = offline)
    private val _supabaseConnectionStatus = MutableStateFlow<Pair<Boolean?, String>>(null to "Untested")
    val supabaseConnectionStatus: StateFlow<Pair<Boolean?, String>> = _supabaseConnectionStatus.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    init {
        checkBackendConnection()
    }

    fun checkSupabaseConnection() {
        checkBackendConnection()
    }

    fun checkBackendConnection() {
        viewModelScope.launch {
            _isCheckingConnection.value = true
            try {
                val (connected, details) = repository.checkRenderConnection()
                _supabaseConnectionStatus.value = connected to details
            } catch (e: Exception) {
                _supabaseConnectionStatus.value = false to (e.message ?: "Failed to connect to Render")
            } finally {
                _isCheckingConnection.value = false
            }
        }
    }

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
        context: android.content.Context,
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
        val sanitizedTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "_").replace(Regex("_+"), "_")
        val videoKey = "movies/${sanitizedTitle}.mp4"
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
            val isLocalVideoUri = streamUrl.startsWith("content://") || streamUrl.startsWith("file://")

            if (isLocalVideoUri) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        overallProgress = 0f,
                        statusMessage = "Initiating direct R2 upload via Render..."
                    )

                    val videoUri = android.net.Uri.parse(streamUrl)
                    val preferredFilename = "${sanitizedTitle}.mp4"

                    // Upload directly to Cloudflare R2 using presigned URLs from Render
                    val uploadResult = repository.uploadMovieVideoWithRender(
                        context = context,
                        videoUri = videoUri,
                        customFilename = preferredFilename
                    ) { progressPct, statusMsg ->
                        val fraction = (progressPct / 100f).coerceIn(0f, 1f)
                        _uploadState.value = _uploadState.value.copy(
                            overallProgress = fraction,
                            statusMessage = statusMsg
                        )
                    }

                    // Save the resulting video key and URL in the movie database
                    val movieToSave = newMovie.copy(
                        videoKey = uploadResult.key,
                        videoStreamUrl = if (uploadResult.url.isNotBlank()) uploadResult.url else fallbackStream
                    )
                    repository.insertMovie(movieToSave)

                    _uploadState.value = _uploadState.value.copy(
                        isUploading = false,
                        isCompleted = true,
                        overallProgress = 1.0f,
                        statusMessage = "Movie '${movieToSave.title}' uploaded directly to Cloudflare R2 and published successfully!"
                    )
                } catch (e: Exception) {
                    // Clearly display upload failure to user - do not mark as completed
                    _uploadState.value = _uploadState.value.copy(
                        isUploading = false,
                        isCompleted = false,
                        overallProgress = 0f,
                        error = e.message ?: "Upload failed",
                        statusMessage = "Upload failed: ${e.message}"
                    )
                }
            } else {
                // Direct stream URL / catalog entry
                try {
                    repository.insertMovie(newMovie)
                    _uploadState.value = UploadProgressState(
                        isUploading = false,
                        isCompleted = true,
                        overallProgress = 1.0f,
                        statusMessage = "Movie '${newMovie.title}' saved to catalog with stream link!"
                    )
                } catch (e: Exception) {
                    _uploadState.value = UploadProgressState(
                        isUploading = false,
                        isCompleted = false,
                        overallProgress = 0f,
                        error = e.message ?: "Failed to save movie",
                        statusMessage = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun publishMovieDirectly(
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

        viewModelScope.launch {
            try {
                repository.insertMovie(newMovie)
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    isCompleted = true,
                    overallProgress = 1.0f,
                    statusMessage = "Movie '${newMovie.title}' saved and published immediately to catalog!"
                )
            } catch (e: Exception) {
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    error = e.message ?: "Failed to save movie"
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
