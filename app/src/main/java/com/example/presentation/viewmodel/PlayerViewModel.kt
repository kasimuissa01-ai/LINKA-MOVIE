package com.example.presentation.viewmodel

import android.content.Context
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.repository.MovieRepository
import com.example.data.analytics.AppAnalyticsManager
import com.example.data.analytics.PlaybackWatchTracker
import com.example.domain.model.Movie
import com.example.domain.model.Episode
import com.example.util.R2UrlUtils
import com.example.util.VideoCacheManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
enum class OrientationMode {
    SENSOR,           // Follow sensor (portrait or landscape)
    USER_LANDSCAPE,   // Locked to landscape by user toggle
    USER_PORTRAIT     // Locked to portrait by user toggle
}

@OptIn(UnstableApi::class)
enum class ScreenResizeMode(val label: String, val exoMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Crop", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

data class DoubleTapSeekState(
    val isForward: Boolean = true,
    val visible: Boolean = false,
    val seconds: Int = 10
)

data class StreamDiagnosticInfo(
    val streamUrl: String = "",
    val httpStatusCode: Int? = null,
    val errorCodeName: String = "",
    val failureSummary: String = "",
    val brokenLayer: String = "",
    val actionGuide: String = ""
)

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val controlsVisible: Boolean = true,
    val isLocked: Boolean = false,
    val resizeMode: ScreenResizeMode = ScreenResizeMode.ZOOM,
    val resizeToast: String? = null,
    val playbackSpeed: Float = 1.0f,
    val doubleTapSeek: DoubleTapSeekState? = null,
    // Gesture HUDs
    val showVolumeHud: Boolean = false,
    val volumeLevel: Float = 0.5f,
    val showBrightnessHud: Boolean = false,
    val brightnessLevel: Float = 0.5f,
    // Subtitles
    val availableSubtitles: List<String> = listOf("Off", "English [CC]", "Spanish", "French"),
    val selectedSubtitle: String = "Off",
    val isOfflinePlayback: Boolean = false,
    val errorMessage: String? = null,
    val streamDiagnostic: StreamDiagnosticInfo? = null,
    val currentPlaybackUrl: String = "",
    val isResolvingStreamUrl: Boolean = true,
    val loadingStage: String = "Buffering cinema stream...",
    val isFullscreen: Boolean = true,
    val orientationMode: OrientationMode = OrientationMode.USER_LANDSCAPE
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    private var progressTrackerJob: Job? = null
    private var controlsTimeoutJob: Job? = null
    private var hudDismissJob: Job? = null
    private var doubleTapDismissJob: Job? = null

    private val moviePositions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var currentMovieId: String? = null

    private var accumulatedVolume: Float = -1f
    private var accumulatedBrightness: Float = -1f

    fun saveMoviePosition(movieId: String, positionMs: Long) {
        if (positionMs > 0L) {
            moviePositions[movieId] = positionMs
        }
    }

    fun getMovieLastPosition(movieId: String): Long {
        return moviePositions[movieId] ?: 0L
    }

    private var currentEpisodeId: String? = null
    private var activeMovie: Movie? = null
    private var activeEpisode: Episode? = null
    private var applicationContext: Context? = null
    private var watchTracker: PlaybackWatchTracker? = null

    fun initializePlayer(context: Context, movie: Movie, initialPositionMs: Long = 0L, episodeId: String? = null) {
        applicationContext = context.applicationContext
        activeMovie = movie
        val sortedEpisodes = movie.episodes.toMutableList().apply {
            if (isNotEmpty() && none { it.seasonNumber == 1 && it.episodeNumber == 1 } && movie.videoStreamUrl.isNotBlank()) {
                add(
                    Episode(
                        id = "synth_s1_e1_${movie.id}",
                        movieId = movie.id,
                        episodeNumber = 1,
                        seasonNumber = 1,
                        title = "Episode 1",
                        description = "Season 1, Episode 1",
                        videoStreamUrl = movie.videoStreamUrl,
                        videoKey = movie.videoKey
                    )
                )
            }
        }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

        val effectiveEpisodeId = episodeId ?: if (sortedEpisodes.isNotEmpty()) sortedEpisodes.firstOrNull()?.id else null
        val isSameMovie = (currentMovieId == movie.id && currentEpisodeId == effectiveEpisodeId)
        currentMovieId = movie.id
        currentEpisodeId = effectiveEpisodeId
        val positionKey = if (effectiveEpisodeId != null) "${movie.id}_ep_${effectiveEpisodeId}" else movie.id
        val targetStartPos = if (initialPositionMs > 0L) {
            saveMoviePosition(positionKey, initialPositionMs)
            initialPositionMs
        } else {
            getMovieLastPosition(positionKey)
        }

        // Initialize or update PlaybackWatchTracker for genuine watch time analytics
        val analyticsMgr = AppAnalyticsManager.getInstance(context)
        if (watchTracker == null || !isSameMovie) {
            watchTracker?.onRelease(exoPlayer)
            watchTracker = PlaybackWatchTracker(analyticsMgr).apply {
                startSession(movie.id, targetStartPos)
            }
        }

        // If player already exists for THIS SAME movie, seek to target and ensure playback is active
        if (exoPlayer != null && isSameMovie) {
            exoPlayer?.let { player ->
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                if (targetStartPos > 0L && Math.abs(player.currentPosition - targetStartPos) > 1500L) {
                    player.seekTo(targetStartPos)
                }
                player.playWhenReady = true
                player.play()
                _uiState.value = _uiState.value.copy(
                    isLoading = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE,
                    isResolvingStreamUrl = false,
                    loadingStage = "Buffering cinema stream...",
                    isPlaying = true,
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(0L),
                    errorMessage = null
                )
                startProgressTracker()
            }
            return
        }

        // If switching from another movie, release the previous player cleanly
        if (exoPlayer != null) {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exoPlayer = null
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isResolvingStreamUrl = true,
            loadingStage = "Preparing video stream...",
            errorMessage = null
        )

        val player = try {
            VideoCacheManager.buildFastPlayer(context)
        } catch (e: Exception) {
            val fallbackFactory = try { VideoCacheManager.buildRenderersFactory(context) } catch (_: Exception) { null }
            if (fallbackFactory != null) {
                ExoPlayer.Builder(context, fallbackFactory).build()
            } else {
                ExoPlayer.Builder(context).build()
            }
        }
        exoPlayer = player

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    watchTracker?.onPlay(player)
                    startControlsHideTimer()
                } else {
                    watchTracker?.onPause(player)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                watchTracker?.onSeekDiscontinuity(newPosition.positionMs)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isLoading = playbackState == Player.STATE_BUFFERING
                val isReady = playbackState == Player.STATE_READY
                _uiState.value = _uiState.value.copy(
                    isLoading = isLoading,
                    isResolvingStreamUrl = if (isReady) false else _uiState.value.isResolvingStreamUrl,
                    loadingStage = when {
                        isReady -> ""
                        isLoading && !_uiState.value.isResolvingStreamUrl -> "Buffering cinema stream..."
                        else -> _uiState.value.loadingStage
                    },
                    durationMs = if (isReady) player.duration.coerceAtLeast(0L) else _uiState.value.durationMs,
                    errorMessage = if (isReady) null else _uiState.value.errorMessage
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val currentUrl = _uiState.value.currentPlaybackUrl
                android.util.Log.e("PlayerViewModel", "Playback error on $currentUrl: ${error.message} (code: ${error.errorCodeName})")
                watchTracker?.onError(error.errorCodeName, error.message)

                // 1. If it was playing a local offline file (file://) and failed:
                // The local file on disk is corrupted, partial, or unreadable.
                // Purge the bad file and seamlessly switch to online stream.
                if (currentUrl.startsWith("file://") || currentUrl.startsWith("/")) {
                    android.util.Log.w("PlayerViewModel", "Corrupted or unreadable local file detected. Purging file and seamlessly failing over to online stream...")
                    viewModelScope.launch {
                        val currentUid = repository.userSession.value.uid
                        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val targetFilename = if (episodeId != null) "movie_${movie.id}_ep_${episodeId}.mp4" else "movie_${movie.id}.mp4"
                        val localFile = java.io.File(java.io.File(destDir, "users/$currentUid"), targetFilename)
                        val legacyFile = java.io.File(destDir, targetFilename)
                        runCatching { if (localFile.exists()) localFile.delete() }
                        runCatching { if (legacyFile.exists()) legacyFile.delete() }

                        val onlineUrl = if (episodeId != null) {
                            val ep = movie.episodes.firstOrNull { it.id == episodeId }
                            if (ep != null) repository.resolveEpisodeOnlineStreamUri(movie, ep) else repository.resolveOnlineStreamUri(movie)
                        } else {
                            repository.resolveOnlineStreamUri(movie)
                        }
                        _uiState.value = _uiState.value.copy(
                            isOfflinePlayback = false,
                            currentPlaybackUrl = onlineUrl,
                            errorMessage = null,
                            isLoading = true
                        )
                        player.setMediaItem(MediaItem.fromUri(onlineUrl))
                        player.prepare()
                        player.playWhenReady = true
                    }
                    return
                }

                // Analyze root cause and HTTP response codes
                var httpCode: Int? = null
                var currCause: Throwable? = error.cause
                while (currCause != null) {
                    if (currCause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpCode = currCause.responseCode
                        break
                    }
                    currCause = currCause.cause
                }

                val isNetworkError = error.errorCodeName.contains("NETWORK", ignoreCase = true) ||
                        error.errorCodeName.contains("TIMEOUT", ignoreCase = true) ||
                        error.message?.contains("Unable to connect", ignoreCase = true) == true ||
                        error.message?.contains("UnknownHost", ignoreCase = true) == true

                val diagnostic = when {
                    isNetworkError -> StreamDiagnosticInfo(
                        streamUrl = currentUrl,
                        httpStatusCode = httpCode,
                        errorCodeName = error.errorCodeName,
                        failureSummary = "Please check your internet connection.",
                        brokenLayer = "Network Connection",
                        actionGuide = "Make sure your device is connected to the internet and tap Retry."
                    )
                    httpCode == 404 -> StreamDiagnosticInfo(
                        streamUrl = currentUrl,
                        httpStatusCode = 404,
                        errorCodeName = error.errorCodeName,
                        failureSummary = "This video is currently unavailable.",
                        brokenLayer = "Video Stream",
                        actionGuide = "The video file cannot be reached online. You can select a local video file or try again later."
                    )
                    httpCode == 403 -> StreamDiagnosticInfo(
                        streamUrl = currentUrl,
                        httpStatusCode = 403,
                        errorCodeName = error.errorCodeName,
                        failureSummary = "Video playback is temporarily restricted.",
                        brokenLayer = "Stream Access",
                        actionGuide = "Please tap Retry in a moment or select another title."
                    )
                    else -> StreamDiagnosticInfo(
                        streamUrl = currentUrl,
                        httpStatusCode = httpCode,
                        errorCodeName = error.errorCodeName,
                        failureSummary = "Unable to play video right now.",
                        brokenLayer = "Video Playback",
                        actionGuide = "Please tap Retry or select a video file from your device."
                    )
                }

                // 2. If it was playing an online stream, attempt offline failover or alternative stream:
                viewModelScope.launch {
                    val fallbackOffline = try {
                        val currentUid = repository.userSession.value.uid
                        repository.getDownloadManager(context).getVerifiedOfflinePlaybackUri(movie.id, episodeId, currentUid)
                    } catch (_: Exception) { null }
                    if (fallbackOffline != null && fallbackOffline != currentUrl) {
                        android.util.Log.i("PlayerViewModel", "Switching to verified offline video: $fallbackOffline")
                        _uiState.value = _uiState.value.copy(
                            isOfflinePlayback = true,
                            currentPlaybackUrl = fallbackOffline,
                            errorMessage = null,
                            streamDiagnostic = null,
                            isLoading = true,
                            loadingStage = "Playing offline video..."
                        )
                        player.setMediaItem(MediaItem.fromUri(fallbackOffline))
                        player.prepare()
                        player.playWhenReady = true
                        return@launch
                    }

                    if (episodeId == null) {
                        val fallbackUrl = repository.resolveOnlineStreamUri(movie, excludeUrl = currentUrl)
                        if (fallbackUrl.isNotBlank() && fallbackUrl != currentUrl) {
                            android.util.Log.w("PlayerViewModel", "Switching to verified stream source: $fallbackUrl")
                            _uiState.value = _uiState.value.copy(
                                currentPlaybackUrl = fallbackUrl,
                                errorMessage = null,
                                streamDiagnostic = null,
                                isLoading = true,
                                loadingStage = "Buffering cinema stream..."
                            )
                            player.setMediaItem(MediaItem.fromUri(fallbackUrl))
                            player.prepare()
                            player.playWhenReady = true
                            return@launch
                        }
                    } else {
                        // For a specific episode: NEVER fall back to Episode 1 / Movie stream URL!
                        val targetEp = movie.episodes.firstOrNull { it.id == episodeId }
                        val epNumber = targetEp?.episodeNumber ?: 2
                        val epTitle = targetEp?.title ?: "Episode $epNumber"
                        val isLocal = currentUrl.startsWith("content:") || currentUrl.startsWith("file:")
                        val diagnosticMsg = if (isLocal) {
                            "Unable to read local video file for $epTitle. Tap 'Select Video' below to choose the file again from your device."
                        } else {
                            "Cloud stream for $epTitle is not available yet. Tap 'Select Video' to play from your device or wait for upload."
                        }
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            isLoading = false,
                            errorMessage = diagnosticMsg,
                            streamDiagnostic = diagnostic.copy(failureSummary = diagnosticMsg)
                        )
                        return@launch
                    }

                    _uiState.value = _uiState.value.copy(
                        isPlaying = false,
                        isLoading = false,
                        errorMessage = diagnostic.failureSummary,
                        streamDiagnostic = diagnostic
                    )
                }
            }
        })

        // 1. Check if verified offline file exists for zero-delay offline playback for the CURRENT USER
        val currentUid = repository.userSession.value.uid
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir

        val targetEpisode = if (effectiveEpisodeId != null) {
            sortedEpisodes.firstOrNull { it.id == effectiveEpisodeId }
        } else {
            sortedEpisodes.firstOrNull()
        }
        activeEpisode = targetEpisode

        val filename = if (effectiveEpisodeId != null) "movie_${movie.id}_ep_${effectiveEpisodeId}.mp4" else "movie_${movie.id}.mp4"
        val userFile = java.io.File(java.io.File(destDir, "users/$currentUid"), filename)
        val internalUserFile = java.io.File(java.io.File(context.filesDir, "users/$currentUid"), filename)

        val passedFile = if (targetEpisode == null && (movie.videoStreamUrl.startsWith("file:") || movie.videoStreamUrl.startsWith("/"))) {
            val f = java.io.File(movie.videoStreamUrl.removePrefix("file://"))
            if (f.exists() && f.length() >= 1024 * 1024L && (f.absolutePath.contains("/users/$currentUid/") || f.absolutePath.contains("/$currentUid/"))) f else null
        } else null

        val localCandidate = when {
            userFile.exists() && userFile.length() >= 1024 * 1024L -> userFile
            internalUserFile.exists() && internalUserFile.length() >= 1024 * 1024L -> internalUserFile
            passedFile != null -> passedFile
            else -> null
        }

        val directUrl = if (targetEpisode != null) {
            val epKey = targetEpisode.videoKey
            val epStream = targetEpisode.videoStreamUrl
            when {
                localCandidate != null -> android.net.Uri.fromFile(localCandidate).toString()
                epStream.startsWith("content:") || epStream.startsWith("file:") -> epStream
                epKey.isNotBlank() || epStream.isNotBlank() -> R2UrlUtils.canonicalizeStreamUrl(epStream, epKey)
                targetEpisode.seasonNumber == 1 && targetEpisode.episodeNumber == 1 && movie.videoStreamUrl.isNotBlank() -> {
                    R2UrlUtils.canonicalizeStreamUrl(movie.videoStreamUrl, movie.videoKey)
                }
                else -> ""
            }
        } else {
            when {
                localCandidate != null -> android.net.Uri.fromFile(localCandidate).toString()
                movie.videoStreamUrl.startsWith("content:") -> movie.videoStreamUrl
                else -> R2UrlUtils.canonicalizeStreamUrl(movie.videoStreamUrl, movie.videoKey)
            }
        }

        if (directUrl.isNotBlank() && (directUrl.startsWith("http://") || directUrl.startsWith("https://") || directUrl.startsWith("file:") || directUrl.startsWith("content:") || directUrl.startsWith("/"))) {
            val isOffline = directUrl.startsWith("file:") || directUrl.startsWith("content:") || directUrl.startsWith("/")
            val parsedMediaUri = if (directUrl.startsWith("/")) {
                android.net.Uri.fromFile(java.io.File(directUrl))
            } else {
                android.net.Uri.parse(directUrl)
            }
            _uiState.value = _uiState.value.copy(
                isOfflinePlayback = isOffline,
                currentPlaybackUrl = directUrl,
                isResolvingStreamUrl = false,
                isLoading = true,
                loadingStage = if (isOffline) "Preparing offline playback..." else "Buffering cinema stream...",
                errorMessage = null
            )
            player.setMediaItem(MediaItem.fromUri(parsedMediaUri))
            if (targetStartPos > 0L) {
                player.seekTo(targetStartPos)
                _uiState.value = _uiState.value.copy(currentPositionMs = targetStartPos)
            }
            player.prepare()
            player.playWhenReady = true
            player.play()
            startProgressTracker()
        } else {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isResolvingStreamUrl = true,
                    loadingStage = "Resolving video stream..."
                )
                val playbackUrl = repository.resolvePlaybackUri(movie, context, episodeId)
                if (playbackUrl.isBlank()) {
                    val label = targetEpisode?.title ?: movie.title
                    _uiState.value = _uiState.value.copy(
                        isPlaying = false,
                        isLoading = false,
                        isResolvingStreamUrl = false,
                        errorMessage = "No video stream is available for '$label'. Please upload a video or configure a stream URL."
                    )
                    return@launch
                }
                val isOffline = playbackUrl.startsWith("file:") || playbackUrl.startsWith("/") || playbackUrl.startsWith("content:")
                _uiState.value = _uiState.value.copy(
                    isOfflinePlayback = isOffline,
                    currentPlaybackUrl = playbackUrl,
                    isResolvingStreamUrl = false,
                    loadingStage = if (isOffline) "Preparing offline playback..." else "Buffering cinema stream...",
                    errorMessage = null
                )

                val mediaUri = if (playbackUrl.startsWith("/")) {
                    android.net.Uri.fromFile(java.io.File(playbackUrl))
                } else {
                    android.net.Uri.parse(playbackUrl)
                }
                val mediaItem = MediaItem.fromUri(mediaUri)
                player.setMediaItem(mediaItem)
                if (targetStartPos > 0L) {
                    player.seekTo(targetStartPos)
                    _uiState.value = _uiState.value.copy(currentPositionMs = targetStartPos)
                }
                player.prepare()
                player.playWhenReady = true
                player.play()

                startProgressTracker()
            }
        }
    }

    fun retryPlayback(context: Context, movie: Movie, episodeId: String? = currentEpisodeId) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isResolvingStreamUrl = true,
            loadingStage = "Reconnecting stream...",
            errorMessage = null
        )
        exoPlayer?.let { player ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    loadingStage = "Buffering cinema stream..."
                )
                val playbackUrl = repository.resolvePlaybackUri(movie, context, episodeId)
                if (playbackUrl.isBlank()) {
                    val targetEp = movie.episodes.firstOrNull { it.id == episodeId }
                    val epTitle = targetEp?.title ?: if (episodeId != null) "Episode ${targetEp?.episodeNumber ?: 2}" else movie.title
                    _uiState.value = _uiState.value.copy(
                        isPlaying = false,
                        isLoading = false,
                        isResolvingStreamUrl = false,
                        errorMessage = "No video stream is available for '$epTitle'. Tap 'Select Video' to pick a local file from your device."
                    )
                    return@launch
                }
                val isOffline = playbackUrl.startsWith("file:") || playbackUrl.startsWith("/") || playbackUrl.startsWith("content:")
                val positionKey = if (episodeId != null) "${movie.id}_ep_${episodeId}" else movie.id
                val resumePos = getMovieLastPosition(positionKey)
                _uiState.value = _uiState.value.copy(
                    isOfflinePlayback = isOffline,
                    currentPlaybackUrl = playbackUrl,
                    isResolvingStreamUrl = false,
                    loadingStage = if (isOffline) "Preparing offline playback..." else "Buffering cinema stream...",
                    errorMessage = null
                )
                val mediaUri = if (playbackUrl.startsWith("/")) {
                    android.net.Uri.fromFile(java.io.File(playbackUrl))
                } else {
                    android.net.Uri.parse(playbackUrl)
                }
                player.setMediaItem(MediaItem.fromUri(mediaUri))
                if (resumePos > 0L) {
                    player.seekTo(resumePos)
                }
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    fun playLocalEpisodeUri(context: Context, movie: Movie, episodeId: String, uri: android.net.Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { }

        viewModelScope.launch {
            val updatedEpisodes = movie.episodes.map { ep ->
                if (ep.id == episodeId) ep.copy(videoStreamUrl = uri.toString()) else ep
            }
            val updatedMovie = movie.copy(episodes = updatedEpisodes)
            try {
                repository.updateMovie(updatedMovie)
            } catch (e: Exception) {
                android.util.Log.w("PlayerViewModel", "Failed to persist local episode URI: ${e.message}")
            }

            currentMovieId = movie.id
            currentEpisodeId = episodeId
            activeMovie = movie
            activeEpisode = movie.episodes.firstOrNull { it.id == episodeId }
            _uiState.value = _uiState.value.copy(
                isOfflinePlayback = true,
                currentPlaybackUrl = uri.toString(),
                isResolvingStreamUrl = false,
                isLoading = true,
                loadingStage = "Playing local episode video...",
                errorMessage = null,
                streamDiagnostic = null
            )

            exoPlayer?.let { player ->
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = true
                player.play()
                startProgressTracker()
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            var tickCount = 0
            while (true) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    watchTracker?.onProgressTick(player)
                    _uiState.value = _uiState.value.copy(
                        currentPositionMs = pos,
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                        durationMs = dur
                    )
                    currentMovieId?.let { id ->
                        if (pos > 0L) moviePositions[id] = pos
                    }
                    // Record watch history periodically (every ~3 seconds = 10 ticks) or if near end
                    tickCount++
                    if (tickCount % 10 == 0 || (dur > 0L && pos >= dur - 1000L)) {
                        val m = activeMovie
                        val ctx = applicationContext
                        if (m != null && ctx != null && pos > 1000L) {
                            val currentUid = repository.userSession.value.uid
                            com.example.data.local.WatchHistoryManager.getInstance(ctx).recordWatchProgress(
                                userId = currentUid,
                                movie = m,
                                episode = activeEpisode,
                                positionMs = pos,
                                durationMs = dur
                            )
                        }
                    }
                }
                delay(300)
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
        exoPlayer?.let { player ->
            watchTracker?.onPause(player)
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            val m = activeMovie
            val ctx = applicationContext
            if (m != null && ctx != null && pos > 1000L) {
                val currentUid = repository.userSession.value.uid
                com.example.data.local.WatchHistoryManager.getInstance(ctx).recordWatchProgress(
                    userId = currentUid,
                    movie = m,
                    episode = activeEpisode,
                    positionMs = pos,
                    durationMs = dur
                )
            }
        }
        _uiState.value = _uiState.value.copy(isPlaying = false)
        showControls(keepVisible = true)
    }

    fun play() {
        exoPlayer?.play()
        exoPlayer?.let { watchTracker?.onPlay(it) }
        _uiState.value = _uiState.value.copy(isPlaying = true)
        startControlsHideTimer()
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }

    fun toggleControls() {
        val newVisibility = !_uiState.value.controlsVisible
        _uiState.value = _uiState.value.copy(controlsVisible = newVisibility)
        if (newVisibility && (_uiState.value.isPlaying)) {
            startControlsHideTimer()
        }
    }

    fun hideControls() {
        controlsTimeoutJob?.cancel()
        _uiState.value = _uiState.value.copy(controlsVisible = false)
    }

    fun showControls(keepVisible: Boolean = false) {
        _uiState.value = _uiState.value.copy(controlsVisible = true)
        if (!keepVisible && _uiState.value.isPlaying) {
            startControlsHideTimer()
        }
    }

    fun restartControlsHideTimer() {
        if (_uiState.value.isPlaying) {
            startControlsHideTimer()
        }
    }

    private fun startControlsHideTimer() {
        controlsTimeoutJob?.cancel()
        controlsTimeoutJob = viewModelScope.launch {
            delay(2800) // 2.8 seconds auto-hide
            if (_uiState.value.isPlaying) {
                _uiState.value = _uiState.value.copy(controlsVisible = false)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
        restartControlsHideTimer()
    }

    fun seekRelative(seconds: Int, isForward: Boolean) {
        exoPlayer?.let { player ->
            val maxDur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            val target = (player.currentPosition + (seconds * 1000L)).coerceIn(0L, maxDur)
            player.seekTo(target)

            val currentSeconds = if (_uiState.value.doubleTapSeek?.isForward == isForward) {
                ((_uiState.value.doubleTapSeek?.seconds ?: 0) + Math.abs(seconds)).coerceAtMost(90)
            } else {
                Math.abs(seconds)
            }

            _uiState.value = _uiState.value.copy(
                doubleTapSeek = DoubleTapSeekState(isForward = isForward, visible = true, seconds = currentSeconds),
                currentPositionMs = target
            )

            doubleTapDismissJob?.cancel()
            doubleTapDismissJob = viewModelScope.launch {
                delay(800)
                _uiState.value = _uiState.value.copy(doubleTapSeek = null)
            }
        }
    }

    // Vertical Gesture & Direct Controls (Volume & Brightness)
    fun onVolumeDragStart(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        accumulatedVolume = (currentVolume / maxVolume).coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(
            showVolumeHud = true,
            showBrightnessHud = false,
            volumeLevel = accumulatedVolume
        )
    }

    fun onVolumeSwipe(deltaRatio: Float, context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
        if (accumulatedVolume < 0f) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            accumulatedVolume = (currentVolume / maxVolume).coerceIn(0f, 1f)
        }

        // Apply smooth sensitivity multiplier
        accumulatedVolume = (accumulatedVolume + (deltaRatio * 1.35f)).coerceIn(0f, 1f)
        val targetIndex = Math.round(accumulatedVolume * maxVolume).toInt().coerceIn(0, maxVolume.toInt())

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        } catch (e: Exception) {
            android.util.Log.w("PlayerViewModel", "Error setting stream volume: ${e.message}")
        }
        try {
            exoPlayer?.volume = accumulatedVolume
        } catch (e: Exception) {
            // Ignore if volume setting not supported
        }

        _uiState.value = _uiState.value.copy(
            showVolumeHud = true,
            showBrightnessHud = false,
            volumeLevel = accumulatedVolume
        )
        scheduleHudDismiss()
    }

    fun setVolumeDirect(normalized: Float, context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
        accumulatedVolume = normalized.coerceIn(0f, 1f)
        val targetIndex = Math.round(accumulatedVolume * maxVolume).toInt().coerceIn(0, maxVolume.toInt())
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        } catch (e: Exception) {
            android.util.Log.w("PlayerViewModel", "Error direct volume: ${e.message}")
        }
        _uiState.value = _uiState.value.copy(
            showVolumeHud = true,
            showBrightnessHud = false,
            volumeLevel = accumulatedVolume
        )
        scheduleHudDismiss()
    }

    fun onBrightnessDragStart(window: Window?) {
        window ?: return
        val cur = window.attributes.screenBrightness
        accumulatedBrightness = if (cur < 0f) 0.5f else cur.coerceIn(0.05f, 1.0f)
        _uiState.value = _uiState.value.copy(
            showBrightnessHud = true,
            showVolumeHud = false,
            brightnessLevel = accumulatedBrightness
        )
    }

    fun onBrightnessSwipe(deltaRatio: Float, window: Window?) {
        window ?: return
        if (accumulatedBrightness < 0f) {
            val cur = window.attributes.screenBrightness
            accumulatedBrightness = if (cur < 0f) 0.5f else cur.coerceIn(0.05f, 1.0f)
        }

        accumulatedBrightness = (accumulatedBrightness + (deltaRatio * 1.25f)).coerceIn(0.02f, 1.0f)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = accumulatedBrightness
        window.attributes = layoutParams

        _uiState.value = _uiState.value.copy(
            showBrightnessHud = true,
            showVolumeHud = false,
            brightnessLevel = accumulatedBrightness
        )
        scheduleHudDismiss()
    }

    fun setBrightnessDirect(normalized: Float, window: Window?) {
        window ?: return
        accumulatedBrightness = normalized.coerceIn(0.02f, 1.0f)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = accumulatedBrightness
        window.attributes = layoutParams
        _uiState.value = _uiState.value.copy(
            showBrightnessHud = true,
            showVolumeHud = false,
            brightnessLevel = accumulatedBrightness
        )
        scheduleHudDismiss()
    }

    fun onDragEnd() {
        accumulatedVolume = -1f
        accumulatedBrightness = -1f
        scheduleHudDismiss()
    }

    private fun scheduleHudDismiss() {
        hudDismissJob?.cancel()
        hudDismissJob = viewModelScope.launch {
            delay(1400)
            _uiState.value = _uiState.value.copy(
                showVolumeHud = false,
                showBrightnessHud = false
            )
        }
    }

    // Aspect Ratio Cycling
    fun cycleResizeMode() {
        val nextMode = when (_uiState.value.resizeMode) {
            ScreenResizeMode.FIT -> ScreenResizeMode.FILL
            ScreenResizeMode.FILL -> ScreenResizeMode.ZOOM
            ScreenResizeMode.ZOOM -> ScreenResizeMode.FIT
        }
        _uiState.value = _uiState.value.copy(
            resizeMode = nextMode,
            resizeToast = nextMode.label
        )
        viewModelScope.launch {
            delay(1500)
            _uiState.value = _uiState.value.copy(resizeToast = null)
        }
    }

    // Subtitles
    fun selectSubtitle(subtitle: String, context: Context) {
        _uiState.value = _uiState.value.copy(selectedSubtitle = subtitle)
        exoPlayer?.let { player ->
            val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector
            if (subtitle == "Off") {
                trackSelector?.let { selector ->
                    val params = selector.buildUponParameters()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                        .build()
                    player.trackSelectionParameters = params
                }
            } else {
                trackSelector?.let { selector ->
                    val params = selector.buildUponParameters()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                        .setSelectUndeterminedTextLanguage(true)
                        .build()
                    player.trackSelectionParameters = params
                }
                android.widget.Toast.makeText(context, "Subtitles active: $subtitle", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Playback Speed
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    // Lock toggle
    fun toggleLock() {
        val next = !_uiState.value.isLocked
        _uiState.value = _uiState.value.copy(isLocked = next)
        if (!next) {
            showControls()
        } else {
            startControlsHideTimer()
        }
    }

    // Orientation State Machine & Fullscreen
    fun toggleFullscreen() {
        val currentFullscreen = _uiState.value.isFullscreen
        val nextFullscreen = !currentFullscreen
        val nextOrientation = if (nextFullscreen) OrientationMode.USER_LANDSCAPE else OrientationMode.USER_PORTRAIT
        _uiState.value = _uiState.value.copy(
            isFullscreen = nextFullscreen,
            orientationMode = nextOrientation
        )
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(
            isFullscreen = fullscreen,
            orientationMode = if (fullscreen) OrientationMode.USER_LANDSCAPE else OrientationMode.SENSOR
        )
    }

    fun setOrientationMode(mode: OrientationMode) {
        _uiState.value = _uiState.value.copy(
            orientationMode = mode,
            isFullscreen = (mode == OrientationMode.USER_LANDSCAPE)
        )
    }

    fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // If orientation was on sensor mode, follow sensor
        if (_uiState.value.orientationMode == OrientationMode.SENSOR) {
            _uiState.value = _uiState.value.copy(isFullscreen = isLandscape)
        }
    }

    fun releasePlayer(movieId: String? = null) {
        exoPlayer?.let { player ->
            val pos = player.currentPosition.coerceAtLeast(0L)
            if (pos > 0L) {
                movieId?.let { id -> moviePositions[id] = pos }
                currentMovieId?.let { id -> moviePositions[id] = pos }
            }
        }
        watchTracker?.onRelease(exoPlayer)
        watchTracker = null
        progressTrackerJob?.cancel()
        controlsTimeoutJob?.cancel()
        hudDismissJob?.cancel()
        doubleTapDismissJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        currentMovieId = null
        _uiState.value = PlayerUiState(
            orientationMode = OrientationMode.USER_LANDSCAPE,
            loadingStage = "Buffering cinema stream..."
        )
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
