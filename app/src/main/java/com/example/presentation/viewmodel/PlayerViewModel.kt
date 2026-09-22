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
import com.example.domain.model.Movie
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

    fun initializePlayer(context: Context, movie: Movie, initialPositionMs: Long = 0L) {
        val isSameMovie = (currentMovieId == movie.id)
        currentMovieId = movie.id
        val targetStartPos = if (initialPositionMs > 0L) {
            saveMoviePosition(movie.id, initialPositionMs)
            initialPositionMs
        } else {
            getMovieLastPosition(movie.id)
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
                    startControlsHideTimer()
                }
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

                // 1. If it was playing a local offline file (file://) and failed:
                // The local file on disk is corrupted, partial, or unreadable.
                // Purge the bad file and seamlessly switch to online stream.
                if (currentUrl.startsWith("file://") || currentUrl.startsWith("/")) {
                    android.util.Log.w("PlayerViewModel", "Corrupted or unreadable local file detected. Purging file and seamlessly failing over to online stream...")
                    viewModelScope.launch {
                        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val localFile = java.io.File(destDir, "movie_${movie.id}.mp4")
                        runCatching { if (localFile.exists()) localFile.delete() }

                        val onlineUrl = repository.resolveOnlineStreamUri(movie)
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

                val diagnostic = when (httpCode) {
                    404 -> StreamDiagnosticInfo(
                        streamUrl = currentUrl,
                        httpStatusCode = 404,
                        errorCodeName = error.errorCodeName,
                        failureSummary = "HTTP 404 Not Found from Cloudflare R2",
                        brokenLayer = "Cloudflare R2 Storage (File missing or unfinished multipart upload)",
                        actionGuide = "The video file does not exist in your R2 bucket. In Cloudflare R2, uploads left in 'Ongoing' state are not playable until completed. Open Admin Studio -> Edit Movie -> Upload Video to finish."
                    )
                    403 -> StreamDiagnosticInfo(
                        streamUrl = currentUrl,
                        httpStatusCode = 403,
                        errorCodeName = error.errorCodeName,
                        failureSummary = "HTTP 403 Forbidden from Cloudflare R2",
                        brokenLayer = "Cloudflare R2 Permissions / CORS / Public Domain",
                        actionGuide = "Cloudflare R2 is denying read access. Verify Public Access / Custom Domain settings in Cloudflare Dashboard."
                    )
                    else -> {
                        val isNetwork = error.errorCodeName.contains("NETWORK", ignoreCase = true) ||
                                error.errorCodeName.contains("TIMEOUT", ignoreCase = true) ||
                                error.message?.contains("Unable to connect", ignoreCase = true) == true
                        val isParser = error.errorCodeName.contains("PARSING", ignoreCase = true) ||
                                error.errorCodeName.contains("CONTAINER", ignoreCase = true)
                        StreamDiagnosticInfo(
                            streamUrl = currentUrl,
                            httpStatusCode = httpCode,
                            errorCodeName = error.errorCodeName,
                            failureSummary = error.message ?: error.errorCodeName,
                            brokenLayer = when {
                                isNetwork -> "Network Connection / Bandwidth to R2 CDN"
                                isParser -> "Video Codec / MP4 Moov-Atom (Faststart) header"
                                else -> "Stream Resolver / Player Pipeline"
                            },
                            actionGuide = when {
                                isNetwork -> "Ensure active internet connection and check if ${R2UrlUtils.PUBLIC_R2_DOMAIN} is accessible."
                                isParser -> "The MP4 file has moov atom at the end of file instead of beginning. Re-encode video with 'faststart' or re-upload via Admin Studio."
                                else -> "Tap Retry to reconnect or edit the movie in Admin Studio to set a verified stream URL."
                            }
                        )
                    }
                }

                // 2. If it was playing an online stream, attempt failover via repository (excluding failed currentUrl):
                viewModelScope.launch {
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
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            isLoading = false,
                            errorMessage = diagnostic.failureSummary,
                            streamDiagnostic = diagnostic
                        )
                    }
                }
            }
        })

        // Check if verified offline file exists or direct stream is available for zero-delay start
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val localFile = java.io.File(destDir, "movie_${movie.id}.mp4")
        val internalFile = java.io.File(context.filesDir, "movie_${movie.id}.mp4")

        val directUrl = if (localFile.exists() && localFile.length() >= 1024 * 1024L) {
            localFile.toURI().toString()
        } else if (internalFile.exists() && internalFile.length() >= 1024 * 1024L) {
            internalFile.toURI().toString()
        } else {
            R2UrlUtils.canonicalizeStreamUrl(movie.videoStreamUrl, movie.videoKey)
        }

        if (directUrl.isNotBlank() && (directUrl.startsWith("http://") || directUrl.startsWith("https://") || directUrl.startsWith("file://") || directUrl.startsWith("content://"))) {
            val isOffline = directUrl.startsWith("file://") || directUrl.startsWith("content://") || directUrl.startsWith("/")
            _uiState.value = _uiState.value.copy(
                isOfflinePlayback = isOffline,
                currentPlaybackUrl = directUrl,
                isResolvingStreamUrl = false,
                isLoading = true,
                loadingStage = if (isOffline) "Preparing offline playback..." else "Buffering cinema stream...",
                errorMessage = null
            )
            player.setMediaItem(MediaItem.fromUri(directUrl))
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
                val playbackUrl = repository.resolvePlaybackUri(movie, context)
                if (playbackUrl.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isPlaying = false,
                        isLoading = false,
                        isResolvingStreamUrl = false,
                        errorMessage = "No video stream is available for '${movie.title}'. Please upload a video or configure a stream URL."
                    )
                    return@launch
                }
                val isOffline = playbackUrl.startsWith("file://") || playbackUrl.startsWith("/")
                _uiState.value = _uiState.value.copy(
                    isOfflinePlayback = isOffline,
                    currentPlaybackUrl = playbackUrl,
                    isResolvingStreamUrl = false,
                    loadingStage = if (isOffline) "Preparing offline playback..." else "Buffering cinema stream...",
                    errorMessage = null
                )

                val mediaItem = MediaItem.fromUri(playbackUrl)
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

    fun retryPlayback(context: Context, movie: Movie) {
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
                val playbackUrl = repository.resolvePlaybackUri(movie, context)
                val isOffline = playbackUrl.startsWith("file://") || playbackUrl.startsWith("/")
                val resumePos = getMovieLastPosition(movie.id)
                _uiState.value = _uiState.value.copy(
                    isOfflinePlayback = isOffline,
                    currentPlaybackUrl = playbackUrl,
                    isResolvingStreamUrl = false,
                    loadingStage = if (isOffline) "Preparing offline playback..." else "Buffering cinema stream...",
                    errorMessage = null
                )
                player.setMediaItem(MediaItem.fromUri(playbackUrl))
                if (resumePos > 0L) {
                    player.seekTo(resumePos)
                }
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    _uiState.value = _uiState.value.copy(
                        currentPositionMs = pos,
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L)
                    )
                    currentMovieId?.let { id ->
                        if (pos > 0L) moviePositions[id] = pos
                    }
                }
                delay(300)
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _uiState.value = _uiState.value.copy(isPlaying = false)
        showControls(keepVisible = true)
    }

    fun play() {
        exoPlayer?.play()
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
