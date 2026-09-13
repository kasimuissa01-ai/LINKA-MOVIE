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
import com.example.util.VideoCacheManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val controlsVisible: Boolean = true,
    val isLocked: Boolean = false,
    val resizeMode: ScreenResizeMode = ScreenResizeMode.FIT,
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
    val currentPlaybackUrl: String = "",
    val isResolvingStreamUrl: Boolean = true,
    val loadingStage: String = "Connecting to Supabase repository..."
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

    fun saveMoviePosition(movieId: String, positionMs: Long) {
        if (positionMs > 0L) {
            moviePositions[movieId] = positionMs
        }
    }

    fun getMovieLastPosition(movieId: String): Long {
        return moviePositions[movieId] ?: 0L
    }

    fun initializePlayer(context: Context, movie: Movie, initialPositionMs: Long = 0L) {
        currentMovieId = movie.id
        val targetStartPos = if (initialPositionMs > 0L) {
            saveMoviePosition(movie.id, initialPositionMs)
            initialPositionMs
        } else {
            getMovieLastPosition(movie.id)
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isResolvingStreamUrl = true,
            loadingStage = "Connecting to Supabase repository...",
            errorMessage = null
        )

        if (exoPlayer != null) {
            if (targetStartPos > 0L && Math.abs((exoPlayer?.currentPosition ?: 0L) - targetStartPos) > 1500L) {
                exoPlayer?.seekTo(targetStartPos)
            }
            return
        }

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
                val isLoading = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
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

                // 2. If it was playing an online stream, attempt failover via repository (including Supabase edge URL):
                viewModelScope.launch {
                    val fallbackUrl = repository.resolveOnlineStreamUri(movie)
                    if (fallbackUrl.isNotBlank() && fallbackUrl != currentUrl) {
                        android.util.Log.w("PlayerViewModel", "Switching to verified stream source: $fallbackUrl")
                        _uiState.value = _uiState.value.copy(
                            currentPlaybackUrl = fallbackUrl,
                            errorMessage = null,
                            isLoading = true
                        )
                        player.setMediaItem(MediaItem.fromUri(fallbackUrl))
                        player.prepare()
                        player.playWhenReady = true
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            isLoading = false,
                            errorMessage = "Stream playback failed for '${movie.title}'. Please verify the video URL or network connection."
                        )
                    }
                }
            }
        })

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingStreamUrl = true,
                loadingStage = "Fetching stream URL from Supabase repository..."
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
                loadingStage = if (isOffline) "Preparing offline playback..." else "Connecting to Cloudflare R2 stream...",
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

            startProgressTracker()
        }
    }

    fun retryPlayback(context: Context, movie: Movie) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isResolvingStreamUrl = true,
            loadingStage = "Reconnecting to Supabase repository...",
            errorMessage = null
        )
        exoPlayer?.let { player ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    loadingStage = "Fetching stream URL from Supabase repository..."
                )
                val playbackUrl = repository.resolvePlaybackUri(movie, context)
                val isOffline = playbackUrl.startsWith("file://") || playbackUrl.startsWith("/")
                val resumePos = getMovieLastPosition(movie.id)
                _uiState.value = _uiState.value.copy(
                    isOfflinePlayback = isOffline,
                    currentPlaybackUrl = playbackUrl,
                    isResolvingStreamUrl = false,
                    loadingStage = if (isOffline) "Preparing offline playback..." else "Connecting to Cloudflare R2 stream...",
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

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                showControls(keepVisible = true)
            } else {
                player.play()
                startControlsHideTimer()
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
            val target = (player.currentPosition + (seconds * 1000L)).coerceIn(0L, player.duration.coerceAtLeast(0L))
            player.seekTo(target)

            _uiState.value = _uiState.value.copy(
                doubleTapSeek = DoubleTapSeekState(isForward = isForward, visible = true, seconds = seconds)
            )

            doubleTapDismissJob?.cancel()
            doubleTapDismissJob = viewModelScope.launch {
                delay(700)
                _uiState.value = _uiState.value.copy(doubleTapSeek = null)
            }
        }
    }

    // Vertical Gesture Controls
    fun onVolumeSwipe(deltaRatio: Float, context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

        val newVolume = (currentVolume + (deltaRatio * maxVolume)).coerceIn(0f, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)

        val normalized = newVolume / maxVolume
        _uiState.value = _uiState.value.copy(
            showVolumeHud = true,
            volumeLevel = normalized
        )
        scheduleHudDismiss()
    }

    fun onBrightnessSwipe(deltaRatio: Float, window: Window?) {
        window ?: return
        val current = if (window.attributes.screenBrightness < 0) 0.5f else window.attributes.screenBrightness
        val newBrightness = (current + deltaRatio).coerceIn(0.05f, 1.0f)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams

        _uiState.value = _uiState.value.copy(
            showBrightnessHud = true,
            brightnessLevel = newBrightness
        )
        scheduleHudDismiss()
    }

    private fun scheduleHudDismiss() {
        hudDismissJob?.cancel()
        hudDismissJob = viewModelScope.launch {
            delay(1200)
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
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
