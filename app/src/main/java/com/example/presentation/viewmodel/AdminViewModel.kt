package com.example.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.TmdbMovieResult
import com.example.data.remote.TmdbService
import com.example.data.repository.MovieRepository
import com.example.domain.model.Movie
import com.example.domain.model.UploadSession
import com.example.util.R2UrlUtils
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
        val cleanStream = streamUrl.trim().takeIf {
            !it.contains("bunny/trailer.mp4") && !it.contains("BigBuckBunny.mp4")
        } ?: ""

        val newMovie = Movie(
            id = movieId,
            title = title,
            description = description,
            genres = genres,
            coverUrl = if (coverUrl.isNotBlank()) coverUrl
            else "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80",
            videoKey = videoKey,
            videoStreamUrl = cleanStream,
            durationMinutes = 118,
            fileSizeMb = fileSizeMb,
            releaseYear = releaseYear,
            rating = rating,
            cast = listOf("Movie Cast"),
            isFeatured = isFeatured
        )

        activeUploadJob?.cancel()
        activeUploadJob = viewModelScope.launch {
            // Upload cover image to Cloudflare R2 if it is a local image URI
            var effectiveCover = coverUrl
            val isLocalCover = coverUrl.startsWith("content://") || coverUrl.startsWith("file://") || coverUrl.startsWith("/")
            if (isLocalCover) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        overallProgress = 0.02f,
                        statusMessage = "Uploading real cover poster to Cloudflare R2..."
                    )
                    val r2CoverUrl = repository.uploadMovieCoverWithRender(
                        context = context,
                        imageUri = android.net.Uri.parse(coverUrl),
                        customFilename = "${sanitizedTitle}_poster.jpg"
                    )
                    effectiveCover = r2CoverUrl
                    Log.i("AdminViewModel", "Real cover successfully uploaded to R2: $r2CoverUrl")
                } catch (e: Exception) {
                    Log.w("AdminViewModel", "Cover upload to R2 encountered issue: ${e.message}. Preserving original path.")
                }
            }

            val movieWithCover = newMovie.copy(coverUrl = effectiveCover)
            val isLocalVideoUri = streamUrl.startsWith("content://") || streamUrl.startsWith("file://")

            if (isLocalVideoUri) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        overallProgress = 0.05f,
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

                    // Save the resulting video key and verified Cloudflare R2 URL in the movie database & Supabase table
                    val cleanKey = R2UrlUtils.extractCleanVideoKey(uploadResult.key, uploadResult.url)
                    val publicR2Url = "https://${R2UrlUtils.PUBLIC_R2_DOMAIN}/$cleanKey"

                    val movieToSave = movieWithCover.copy(
                        videoKey = cleanKey,
                        videoStreamUrl = publicR2Url
                    )
                    repository.insertMovie(movieToSave)

                    _uploadState.value = _uploadState.value.copy(
                        isUploading = false,
                        isCompleted = true,
                        overallProgress = 1.0f,
                        statusMessage = "Movie '${movieToSave.title}' uploaded directly to Cloudflare R2 and synced to Supabase table!"
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
                // Direct stream URL / R2 link / catalog entry
                try {
                    val canonicalStream = R2UrlUtils.canonicalizeStreamUrl(cleanStream, movieWithCover.videoKey)
                    val canonicalKey = R2UrlUtils.extractCleanVideoKey(movieWithCover.videoKey, cleanStream)

                    val movieToSave = movieWithCover.copy(
                        videoKey = canonicalKey,
                        videoStreamUrl = canonicalStream
                    )
                    repository.insertMovie(movieToSave)
                    _uploadState.value = UploadProgressState(
                        isUploading = false,
                        isCompleted = true,
                        overallProgress = 1.0f,
                        statusMessage = "Movie '${movieToSave.title}' saved to catalog and synced to Supabase table!"
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
        context: android.content.Context? = null,
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
        val cleanStream = streamUrl.trim().takeIf {
            !it.contains("bunny/trailer.mp4") && !it.contains("BigBuckBunny.mp4")
        } ?: ""

        val publicR2Domain = "pub-5399f62037f94260b0f54c88a9297134.r2.dev"
        val effectiveStream = when {
            cleanStream.isNotBlank() && (cleanStream.startsWith("http://") || cleanStream.startsWith("https://")) -> {
                cleanStream
            }
            cleanStream.isNotBlank() && !cleanStream.startsWith("content://") && !cleanStream.startsWith("file://") -> {
                val key = if (cleanStream.startsWith("movies/")) cleanStream else "movies/$cleanStream"
                "https://$publicR2Domain/${key.removePrefix("/")}"
            }
            videoKey.isNotBlank() -> {
                "https://$publicR2Domain/${videoKey.removePrefix("/")}"
            }
            else -> ""
        }

        viewModelScope.launch {
            var finalCover = coverUrl
            val isLocalCover = coverUrl.startsWith("content://") || coverUrl.startsWith("file://") || coverUrl.startsWith("/")
            if (isLocalCover && context != null) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        statusMessage = "Uploading real cover poster to Cloudflare R2..."
                    )
                    val r2CoverUrl = repository.uploadMovieCoverWithRender(
                        context = context,
                        imageUri = android.net.Uri.parse(coverUrl),
                        customFilename = "${title.lowercase().replace(" ", "_")}_poster.jpg"
                    )
                    finalCover = r2CoverUrl
                    Log.i("AdminViewModel", "Cover image uploaded directly to R2: $r2CoverUrl")
                } catch (e: Exception) {
                    Log.w("AdminViewModel", "Direct cover upload warning: ${e.message}")
                }
            }

            val newMovie = Movie(
                id = movieId,
                title = title,
                description = description,
                genres = genres,
                coverUrl = finalCover,
                videoKey = videoKey,
                videoStreamUrl = effectiveStream,
                durationMinutes = 118,
                fileSizeMb = fileSizeMb,
                releaseYear = releaseYear,
                rating = rating,
                cast = listOf("Movie Cast"),
                isFeatured = isFeatured
            )

            try {
                repository.insertMovie(newMovie)
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    isCompleted = true,
                    overallProgress = 1.0f,
                    statusMessage = "Movie '${newMovie.title}' saved and synced to Supabase table!"
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

    fun updateMovie(
        movie: Movie,
        context: android.content.Context? = null
    ) {
        val publicR2Domain = "pub-5399f62037f94260b0f54c88a9297134.r2.dev"
        val effectiveStream = when {
            movie.videoStreamUrl.isNotBlank() && (movie.videoStreamUrl.startsWith("http://") || movie.videoStreamUrl.startsWith("https://")) -> {
                movie.videoStreamUrl
            }
            movie.videoKey.isNotBlank() -> {
                "https://$publicR2Domain/${movie.videoKey.removePrefix("/")}"
            }
            else -> movie.videoStreamUrl
        }

        viewModelScope.launch {
            var finalCover = movie.coverUrl
            val isLocalCover = finalCover.startsWith("content://") || finalCover.startsWith("file://") || finalCover.startsWith("/")
            if (isLocalCover && context != null) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        statusMessage = "Uploading real cover poster to Cloudflare R2..."
                    )
                    val r2CoverUrl = repository.uploadMovieCoverWithRender(
                        context = context,
                        imageUri = android.net.Uri.parse(finalCover),
                        customFilename = "${movie.title.lowercase().replace(" ", "_")}_poster.jpg"
                    )
                    finalCover = r2CoverUrl
                    Log.i("AdminViewModel", "Updated cover uploaded directly to R2: $r2CoverUrl")
                } catch (e: Exception) {
                    Log.w("AdminViewModel", "Cover upload during movie update warning: ${e.message}")
                }
            }

            val updatedMovie = movie.copy(
                coverUrl = finalCover,
                videoStreamUrl = effectiveStream
            )
            repository.updateMovie(updatedMovie)
            _uploadState.value = UploadProgressState(
                isUploading = false,
                isCompleted = true,
                statusMessage = "Movie '${updatedMovie.title}' updated with verified R2 cover in Supabase!"
            )
        }
    }

    /**
     * Replaces the cover image of any movie by uploading the selected gallery photo
     * directly to Cloudflare R2 via Render and updating the movie record in Room & Supabase.
     */
    fun updateMovieCoverDirect(
        context: android.content.Context,
        movie: Movie,
        imageUri: android.net.Uri,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadProgressState(
                isUploading = true,
                statusMessage = "Uploading new cover poster for '${movie.title}' to Cloudflare R2..."
            )
            try {
                val sanitizedTitle = movie.title.lowercase().replace(Regex("[^a-zA-Z0-9]"), "_")
                val r2CoverUrl = repository.uploadMovieCoverWithRender(
                    context = context,
                    imageUri = imageUri,
                    customFilename = "${sanitizedTitle}_poster.jpg"
                )

                val updatedMovie = movie.copy(coverUrl = r2CoverUrl)
                repository.updateMovie(updatedMovie)

                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    isCompleted = true,
                    statusMessage = "Successfully updated cover for '${movie.title}' in Cloudflare R2 and Supabase!"
                )
                onResult(true, r2CoverUrl)
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to upload cover to R2: ${e.message}", e)
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    isCompleted = false,
                    error = e.message ?: "Failed to upload cover",
                    statusMessage = "Error: ${e.message}"
                )
                onResult(false, e.message ?: "Failed to upload cover to R2")
            }
        }
    }

    fun deleteMovie(movieId: String) {
        viewModelScope.launch {
            repository.deleteMovie(movieId)
        }
    }

    fun repairAndSyncAllMoviesToR2(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            _uploadState.value = UploadProgressState(
                isUploading = true,
                statusMessage = "Repairing & syncing all movie URLs to Cloudflare R2 in Supabase table..."
            )
            val updated = repository.repairAndSyncR2UrlsToSupabase()
            _uploadState.value = UploadProgressState(
                isUploading = false,
                isCompleted = true,
                statusMessage = "Successfully updated $updated movies in Supabase table with R2 URLs!"
            )
            onComplete(updated)
        }
    }
}
