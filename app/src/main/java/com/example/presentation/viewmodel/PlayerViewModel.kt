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
    val currentPlaybackUrl: String = ""
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

    fun initializePlayer(context: Context, movie: Movie) {
        if (exoPlayer != null) return

        val player = try {
            VideoCacheManager.buildFastPlayer(context)
        } catch (e: Exception) {
            ExoPlayer.Builder(context).build()
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
                if (playbackState == Player.STATE_READY) {
                    _uiState.value = _uiState.value.copy(
                        durationMs = player.duration.coerceAtLeast(0L),
                        errorMessage = null
                    )
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // If stream failed on an R2 URL and we haven't fallen back to videoStreamUrl yet, try fallback
                val currentUrl = _uiState.value.currentPlaybackUrl
                if (movie.videoStreamUrl.isNotBlank() && currentUrl != movie.videoStreamUrl && movie.videoStreamUrl.startsWith("http")) {
                    android.util.Log.w("PlayerViewModel", "R2 playback failed, trying fallback stream: ${movie.videoStreamUrl}")
                    _uiState.value = _uiState.value.copy(
                        currentPlaybackUrl = movie.videoStreamUrl,
                        errorMessage = null
                    )
                    player.setMediaItem(MediaItem.fromUri(movie.videoStreamUrl))
                    player.prepare()
                    player.playWhenReady = true
                } else {
                    _uiState.value = _uiState.value.copy(
                        isPlaying = false,
                        errorMessage = "You look like you have no internet connection. Please check your network or watch from your downloaded movies."
                    )
                }
            }
        })

        viewModelScope.launch {
            val playbackUrl = repository.resolvePlaybackUri(movie, context)
            val isOffline = playbackUrl.startsWith("file://") || playbackUrl.startsWith("/")
            _uiState.value = _uiState.value.copy(
                isOfflinePlayback = isOffline,
                currentPlaybackUrl = playbackUrl,
                errorMessage = null
            )

            val mediaItem = MediaItem.fromUri(playbackUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true

            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _uiState.value = _uiState.value.copy(
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L)
                    )
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
        if (_uiState.value.isLocked) {
            // If locked, single tap briefly flashes the lock icon
            _uiState.value = _uiState.value.copy(controlsVisible = true)
            startControlsHideTimer()
            return
        }

        val newVisibility = !_uiState.value.controlsVisible
        _uiState.value = _uiState.value.copy(controlsVisible = newVisibility)
        if (newVisibility && (_uiState.value.isPlaying)) {
            startControlsHideTimer()
        }
    }

    fun showControls(keepVisible: Boolean = false) {
        _uiState.value = _uiState.value.copy(controlsVisible = true)
        if (!keepVisible && _uiState.value.isPlaying) {
            startControlsHideTimer()
        }
    }

    private fun startControlsHideTimer() {
        controlsTimeoutJob?.cancel()
        controlsTimeoutJob = viewModelScope.launch {
            delay(4000)
            if (_uiState.value.isPlaying && !_uiState.value.isLocked) {
                _uiState.value = _uiState.value.copy(controlsVisible = false)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
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

    fun releasePlayer() {
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
