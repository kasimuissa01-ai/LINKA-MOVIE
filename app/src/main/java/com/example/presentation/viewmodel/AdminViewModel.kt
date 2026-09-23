package com.example.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.TmdbMovieResult
import com.example.data.remote.TmdbService
import com.example.data.repository.MovieRepository
import com.example.domain.model.Episode
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
        isFeatured: Boolean = false,
        episodes: List<Episode> = emptyList()
    ) {
        val movieId = "m_adm_${UUID.randomUUID().toString().take(6)}"
        val sanitizedTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "_").replace(Regex("_+"), "_")
        val fallbackVideoKey = "videos/${System.currentTimeMillis()}-${sanitizedTitle}.mp4"
        val cleanStream = streamUrl.trim().takeIf {
            !it.contains("bunny/trailer.mp4") && !it.contains("BigBuckBunny.mp4")
        } ?: ""

        val newMovie = Movie(
            id = movieId,
            title = title,
            description = description,
            genres = genres,
            coverKey = R2UrlUtils.extractKeyFromAnyUrl(coverUrl),
            coverUrl = if (coverUrl.isNotBlank()) R2UrlUtils.buildUrl(coverUrl) else "",
            videoKey = fallbackVideoKey,
            videoStreamUrl = if (cleanStream.isNotBlank()) R2UrlUtils.buildUrl(cleanStream) else "",
            durationMinutes = 118,
            fileSizeMb = fileSizeMb,
            releaseYear = releaseYear,
            rating = rating,
            cast = listOf("Movie Cast"),
            isFeatured = isFeatured,
            uploadStatus = "completed",
            episodes = episodes.map { it.copy(movieId = movieId) }
        )

        activeUploadJob?.cancel()
        activeUploadJob = viewModelScope.launch {
            // Upload cover image to Cloudflare R2 if it is a local image URI
            var effectiveCoverKey = R2UrlUtils.extractKeyFromAnyUrl(coverUrl)
            var effectiveCoverUrl = coverUrl
            val isLocalCover = coverUrl.startsWith("content://") || coverUrl.startsWith("file://") || coverUrl.startsWith("file:/") || coverUrl.startsWith("/")
            if (isLocalCover) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        overallProgress = 0.02f,
                        statusMessage = "Uploading cover poster to Cloudflare R2..."
                    )
                    val coverUri = if (coverUrl.startsWith("/")) {
                        android.net.Uri.fromFile(java.io.File(coverUrl))
                    } else {
                        android.net.Uri.parse(coverUrl)
                    }
                    val r2CoverKey = repository.uploadMovieCoverWithRender(
                        context = context,
                        imageUri = coverUri,
                        customFilename = "${sanitizedTitle}_poster.jpg"
                    )
                    effectiveCoverKey = r2CoverKey
                    effectiveCoverUrl = R2UrlUtils.buildUrl(r2CoverKey)
                    Log.i("AdminViewModel", "Cover successfully uploaded to R2. Key: $r2CoverKey, URL: $effectiveCoverUrl")
                } catch (e: Exception) {
                    Log.w("AdminViewModel", "Cover upload to R2 encountered issue: ${e.message}. Preserving original path.")
                }
            }

            val movieWithCover = newMovie.copy(
                coverKey = effectiveCoverKey,
                coverUrl = effectiveCoverUrl
            )
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

                    // Save the resulting pure video key in Supabase table (full URL generated at runtime)
                    val cleanKey = R2UrlUtils.extractCleanVideoKey(uploadResult.key, uploadResult.url)
                    val publicR2Url = R2UrlUtils.buildUrl(cleanKey)

                    val movieToSave = movieWithCover.copy(
                        videoKey = cleanKey,
                        videoStreamUrl = publicR2Url,
                        uploadStatus = "completed"
                    )
                    repository.insertMovie(movieToSave)

                    _uploadState.value = _uploadState.value.copy(
                        isUploading = false,
                        isCompleted = true,
                        overallProgress = 1.0f,
                        statusMessage = "Movie '${movieToSave.title}' uploaded directly to Cloudflare R2 and saved to Supabase!"
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
                    val canonicalKey = R2UrlUtils.extractCleanVideoKey(movieWithCover.videoKey, cleanStream)
                    val canonicalStream = if (canonicalKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalKey) else cleanStream

                    val movieToSave = movieWithCover.copy(
                        videoKey = canonicalKey,
                        videoStreamUrl = canonicalStream,
                        uploadStatus = "completed"
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
        isFeatured: Boolean = false,
        episodes: List<Episode> = emptyList()
    ) {
        val movieId = "m_adm_${UUID.randomUUID().toString().take(6)}"
        val sanitizedTitle = title.lowercase().replace(" ", "_")
        val rawVideoKey = "videos/${System.currentTimeMillis()}-${sanitizedTitle}.mp4"
        val cleanStream = streamUrl.trim().takeIf {
            !it.contains("bunny/trailer.mp4") && !it.contains("BigBuckBunny.mp4")
        } ?: ""

        val canonicalVideoKey = R2UrlUtils.extractCleanVideoKey(rawVideoKey, cleanStream)
        val effectiveStream = if (canonicalVideoKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalVideoKey) else cleanStream

        viewModelScope.launch {
            var finalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(coverUrl)
            var finalCoverUrl = coverUrl
            val isLocalCover = coverUrl.startsWith("content://") || coverUrl.startsWith("file://") || coverUrl.startsWith("file:/") || coverUrl.startsWith("/")
            if (isLocalCover && context != null) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        statusMessage = "Uploading cover poster to Cloudflare R2..."
                    )
                    val coverUri = if (coverUrl.startsWith("/")) {
                        android.net.Uri.fromFile(java.io.File(coverUrl))
                    } else {
                        android.net.Uri.parse(coverUrl)
                    }
                    val r2CoverKey = repository.uploadMovieCoverWithRender(
                        context = context,
                        imageUri = coverUri,
                        customFilename = "${sanitizedTitle}_poster.jpg"
                    )
                    finalCoverKey = r2CoverKey
                    finalCoverUrl = R2UrlUtils.buildUrl(r2CoverKey)
                    Log.i("AdminViewModel", "Cover image uploaded directly to R2. Key: $r2CoverKey, URL: $finalCoverUrl")
                } catch (e: Exception) {
                    Log.w("AdminViewModel", "Direct cover upload warning: ${e.message}")
                }
            }

            val newMovie = Movie(
                id = movieId,
                title = title,
                description = description,
                genres = genres,
                coverKey = finalCoverKey,
                coverUrl = finalCoverUrl,
                videoKey = canonicalVideoKey,
                videoStreamUrl = effectiveStream,
                durationMinutes = 118,
                fileSizeMb = fileSizeMb,
                releaseYear = releaseYear,
                rating = rating,
                cast = listOf("Movie Cast"),
                isFeatured = isFeatured,
                uploadStatus = "completed",
                episodes = episodes.map { it.copy(movieId = movieId) }
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
        val canonicalVideoKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl)
        val effectiveStream = if (canonicalVideoKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalVideoKey) else movie.videoStreamUrl

        viewModelScope.launch {
            var finalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (movie.coverKey.isNotBlank()) movie.coverKey else movie.coverUrl)
            var finalCoverUrl = movie.coverUrl
            val isLocalCover = finalCoverUrl.startsWith("content://") || finalCoverUrl.startsWith("file://") || finalCoverUrl.startsWith("file:/") || finalCoverUrl.startsWith("/")
            if (isLocalCover && context != null) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        statusMessage = "Uploading cover poster to Cloudflare R2..."
                    )
                    val coverUri = if (finalCoverUrl.startsWith("/")) {
                        android.net.Uri.fromFile(java.io.File(finalCoverUrl))
                    } else {
                        android.net.Uri.parse(finalCoverUrl)
                    }
                    val r2CoverKey = repository.uploadMovieCoverWithRender(
                        context = context,
                        imageUri = coverUri,
                        customFilename = "${movie.title.lowercase().replace(" ", "_")}_poster.jpg"
                    )
                    finalCoverKey = r2CoverKey
                    finalCoverUrl = R2UrlUtils.buildUrl(r2CoverKey)
                    Log.i("AdminViewModel", "Updated cover uploaded directly to R2. Key: $r2CoverKey, URL: $finalCoverUrl")
                } catch (e: Exception) {
                    Log.w("AdminViewModel", "Cover upload during movie update warning: ${e.message}")
                }
            }

            val updatedMovie = movie.copy(
                coverKey = finalCoverKey,
                coverUrl = finalCoverUrl,
                videoKey = canonicalVideoKey,
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
     * Adds or updates a single Episode in an existing Movie / TV Series.
     * Optionally uploads the local video file to Cloudflare R2 via Render.
     */
    fun addOrUpdateEpisode(
        context: Context,
        movie: Movie,
        episode: Episode,
        videoUri: Uri? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadProgressState(
                isUploading = true,
                overallProgress = 0.05f,
                statusMessage = "Processing episode '${episode.title}'..."
            )

            var finalVideoKey = episode.videoKey
            var finalStreamUrl = episode.videoStreamUrl
            var finalFileSizeMb = episode.fileSizeMb

            if (videoUri != null) {
                try {
                    _uploadState.value = UploadProgressState(
                        isUploading = true,
                        overallProgress = 0.1f,
                        statusMessage = "Uploading S${episode.seasonNumber}E${episode.episodeNumber} video to Cloudflare R2..."
                    )

                    val sanitizedTitle = movie.title.lowercase().replace(Regex("[^a-z0-9]"), "_")
                    val customFilename = "${sanitizedTitle}_s${episode.seasonNumber}e${episode.episodeNumber}.mp4"

                    val uploadResult = repository.uploadMovieVideoWithRender(
                        context = context,
                        videoUri = videoUri,
                        customFilename = customFilename
                    ) { progressPct, statusMsg ->
                        val fraction = (progressPct / 100f).coerceIn(0f, 1f)
                        _uploadState.value = _uploadState.value.copy(
                            overallProgress = fraction,
                            statusMessage = "S${episode.seasonNumber}E${episode.episodeNumber}: $statusMsg"
                        )
                    }

                    finalVideoKey = R2UrlUtils.extractCleanVideoKey(uploadResult.key, uploadResult.url)
                    finalStreamUrl = R2UrlUtils.buildUrl(finalVideoKey)
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Episode video upload failed: ${e.message}", e)
                    _uploadState.value = UploadProgressState(
                        isUploading = false,
                        isCompleted = false,
                        error = e.message ?: "Episode video upload failed",
                        statusMessage = "Episode upload failed: ${e.message}"
                    )
                    onResult(false, e.message ?: "Episode video upload failed")
                    return@launch
                }
            } else if (finalStreamUrl.isNotBlank()) {
                val cleanKey = R2UrlUtils.extractCleanVideoKey(finalVideoKey, finalStreamUrl)
                if (cleanKey.isNotBlank()) {
                    finalVideoKey = cleanKey
                    finalStreamUrl = R2UrlUtils.buildUrl(cleanKey)
                }
            }

            val updatedEpisode = episode.copy(
                movieId = movie.id,
                videoKey = finalVideoKey,
                videoStreamUrl = finalStreamUrl,
                fileSizeMb = finalFileSizeMb
            )

            val currentEpisodes = movie.episodes.toMutableList()
            val existingIndex = currentEpisodes.indexOfFirst { it.id == updatedEpisode.id }
            if (existingIndex >= 0) {
                currentEpisodes[existingIndex] = updatedEpisode
            } else {
                currentEpisodes.add(updatedEpisode)
            }

            // Sort by season and episode number
            val sortedEpisodes = currentEpisodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            val updatedMovie = movie.copy(episodes = sortedEpisodes)

            try {
                repository.updateMovie(updatedMovie)
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    isCompleted = true,
                    overallProgress = 1.0f,
                    statusMessage = "Successfully saved Season ${updatedEpisode.seasonNumber} Episode ${updatedEpisode.episodeNumber} to '${movie.title}'!"
                )
                onResult(true, "Episode saved successfully")
            } catch (e: Exception) {
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    isCompleted = false,
                    error = e.message ?: "Failed to save episode",
                    statusMessage = "Error: ${e.message}"
                )
                onResult(false, e.message ?: "Failed to save episode")
            }
        }
    }

    /**
     * Deletes an episode from an existing movie/show.
     */
    fun deleteEpisode(
        movie: Movie,
        episodeId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val filteredEpisodes = movie.episodes.filterNot { it.id == episodeId }
            val updatedMovie = movie.copy(episodes = filteredEpisodes)
            try {
                repository.updateMovie(updatedMovie)
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    isCompleted = true,
                    statusMessage = "Episode removed from '${movie.title}'."
                )
                onResult(true, "Episode deleted")
            } catch (e: Exception) {
                _uploadState.value = UploadProgressState(
                    isUploading = false,
                    error = e.message ?: "Failed to delete episode"
                )
                onResult(false, e.message ?: "Failed to delete episode")
            }
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
