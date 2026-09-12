@file:OptIn(
    androidx.media3.common.util.UnstableApi::class
)

package com.example.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.PlayerViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Premium Netflix-Inspired Video Player Screen.
 *
 * Characteristics:
 * - Deterministic Landscape Orientation: Automatically locks to landscape upon entry,
 *   and restores portrait orientation immediately upon back/exit.
 * - Modern Immersive Fullscreen: Completely hides system status bars, navigation bars,
 *   clock, battery, and notification icons using WindowInsetsControllerCompat.
 * - Minimalist Controls: When controls are hidden, 100% of the display shows only the movie.
 * - Auto-Hide: Controls auto-fade after ~2.8s of inactivity during playback.
 * - Netflix Triad: Center -10s, large Play/Pause, +10s rewind/forward.
 * - Scrubber & Time: Clean seek bar and timestamps with speed & aspect ratio controls.
 */
@Composable
fun VideoPlayerScreen(
    movie: Movie,
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by playerViewModel.uiState.collectAsState()

    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Helper to safely restore system UI and portrait orientation
    val restoreSystemUiAndOrientation: () -> Unit = {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                } catch (e: Exception) { }
            }
        }
    }

    // Deterministic Landscape + Immersive Fullscreen lifecycle
    DisposableEffect(Unit) {
        playerViewModel.initializePlayer(context, movie)

        activity?.let { act ->
            // 1. Force Landscape orientation (SENSOR_LANDSCAPE allows natural 180° flips if user turns device)
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            // 2. Hide all system bars for true edge-to-edge immersive playback
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())

            // 3. Extend video into display cutout / notch area
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            // 4. Enable HDR wide color gamut mode if device screen supports HDR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val isHdrDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        act.display?.isHdr == true
                    } else false
                    if (isHdrDisplay) {
                        window.colorMode = ActivityInfo.COLOR_MODE_HDR
                    }
                } catch (e: Exception) { }
            }
        }

        onDispose {
            playerViewModel.releasePlayer()
            restoreSystemUiAndOrientation()
        }
    }

    // Clean Back Handler:
    // If controls overlay is visible, dismiss controls first.
    // If controls are hidden, exit player and return to portrait screen.
    BackHandler {
        if (uiState.controlsVisible) {
            playerViewModel.hideControls()
        } else {
            restoreSystemUiAndOrientation()
            onBackClick()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
    ) {
        val totalWidth = constraints.maxWidth.toFloat()
        val totalHeight = constraints.maxHeight.toFloat()

        // 1. Fullscreen Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = uiState.resizeMode.exoMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = playerViewModel.exoPlayer
                }
            },
            update = { playerView ->
                playerView.player = playerViewModel.exoPlayer
                playerView.resizeMode = uiState.resizeMode.exoMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Gesture Detector (Single Tap to toggle controls, Double Tap to seek ±10s)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            playerViewModel.toggleControls()
                        },
                        onDoubleTap = { offset ->
                            if (offset.x > totalWidth / 2) {
                                playerViewModel.seekRelative(10, isForward = true)
                            } else {
                                playerViewModel.seekRelative(-10, isForward = false)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            val isRightSide = change.position.x > totalWidth / 2
                            val deltaRatio = -dragAmount.y / totalHeight
                            if (isRightSide) {
                                playerViewModel.onVolumeSwipe(deltaRatio, context)
                            } else {
                                playerViewModel.onBrightnessSwipe(deltaRatio, activity?.window)
                            }
                        }
                    )
                }
        )

        // 3. Double-tap Seek HUD (+10s / -10s)
        uiState.doubleTapSeek?.let { seekState ->
            val pulseAnim = remember { Animatable(0.7f) }
            LaunchedEffect(seekState) {
                pulseAnim.animateTo(
                    targetValue = 1.2f,
                    animationSpec = tween(250)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 80.dp),
                contentAlignment = if (seekState.isForward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    color = Color(0x99000000),
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (seekState.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = CinematicRed,
                            modifier = Modifier
                                .size(32.dp)
                                .scale(pulseAnim.value)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (seekState.isForward) "+10s" else "-10s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. Volume / Brightness HUD Overlays (Minimal & Sleek)
        AnimatedVisibility(
            visible = uiState.showVolumeHud,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp)
        ) {
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .width(48.dp)
                    .height(150.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = CinematicRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(uiState.volumeLevel.coerceIn(0f, 1f))
                                .align(Alignment.BottomCenter)
                                .background(CinematicRed)
                        )
                    }
                    Text(
                        text = "${(uiState.volumeLevel * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.showBrightnessHud,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
        ) {
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .width(48.dp)
                    .height(150.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = "Brightness",
                        tint = AmberGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(uiState.brightnessLevel.coerceIn(0f, 1f))
                                .align(Alignment.BottomCenter)
                                .background(AmberGold)
                        )
                    }
                    Text(
                        text = "${(uiState.brightnessLevel * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. Loading State Overlay (Displays only during initial buffer or network delay)
        if (uiState.isLoading && uiState.errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = CinematicRed,
                        strokeWidth = 3.5.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading stream...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 6. Error notice banner
        uiState.errorMessage?.let { errorText ->
            Surface(
                color = Color(0xCCB00020),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Playback Error",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            restoreSystemUiAndOrientation()
                            onBackClick()
                        }
                    ) {
                        Text(
                            text = "Back to Details",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 7. Minimal Netflix-Style Controls Overlay
        AnimatedVisibility(
            visible = uiState.controlsVisible && uiState.errorMessage == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color(0x33000000),
                                Color.Transparent,
                                Color(0x33000000),
                                Color(0xEE000000)
                            )
                        )
                    )
            ) {
                // TOP BAR: ← Back button + Movie Title & Metadata + Subtitles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = {
                                restoreSystemUiAndOrientation()
                                onBackClick()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = movie.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            val genreTag = if (movie.genres.isNotEmpty()) " • ${movie.genres.first()}" else ""
                            val metaText = "${movie.releaseYear} • ${movie.durationMinutes}m$genreTag"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = metaText,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                if (uiState.isOfflinePlayback) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0x334CAF50),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "OFFLINE",
                                            color = Color(0xFF4CAF50),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Top Right Action: Subtitles & Audio
                    IconButton(
                        onClick = {
                            showSubtitleSheet = true
                            playerViewModel.restartControlsHideTimer()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "Audio and Subtitles",
                            tint = if (uiState.selectedSubtitle != "Off") CinematicRed else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // CENTER: Netflix-Style Triad (Rewind 10s, Large Play/Pause, Forward 10s)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(44.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            playerViewModel.seekRelative(-10, isForward = false)
                            playerViewModel.restartControlsHideTimer()
                        },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Large Center Play / Pause
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { playerViewModel.togglePlayPause() }
                            .testTag("player_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            playerViewModel.seekRelative(10, isForward = true)
                            playerViewModel.restartControlsHideTimer()
                        },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // BOTTOM CONTROLS: Scrubber Bar + Timestamps + Speed / Aspect Ratio
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 18.dp)
                ) {
                    val duration = uiState.durationMs.coerceAtLeast(1L)
                    val current = uiState.currentPositionMs.coerceIn(0L, duration)

                    // Scrubber Bar
                    Slider(
                        value = (current.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            playerViewModel.seekTo((fraction * duration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = CinematicRed,
                            activeTrackColor = CinematicRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("player_time_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row beneath scrubber: Timestamps on left, Speed & Fit on right
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Current time & Total Duration
                        Text(
                            text = "${formatTime(current)} / ${formatTime(duration)}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Clean controls on bottom-right: Speed and Aspect Ratio
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Speed button
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clickable {
                                        showSpeedDialog = true
                                        playerViewModel.restartControlsHideTimer()
                                    }
                                    .testTag("player_speed_action_button")
                            ) {
                                Text(
                                    text = "${uiState.playbackSpeed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Aspect Ratio / Fit button
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clickable {
                                        playerViewModel.cycleResizeMode()
                                        playerViewModel.restartControlsHideTimer()
                                    }
                                    .testTag("player_fit_button")
                            ) {
                                Text(
                                    text = uiState.resizeMode.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Subtitle selection dialog
    if (showSubtitleSheet) {
        AlertDialog(
            onDismissRequest = { showSubtitleSheet = false },
            title = {
                Text(
                    text = "Subtitles & Audio",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    uiState.availableSubtitles.forEach { sub ->
                        val isSelected = uiState.selectedSubtitle == sub
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerViewModel.selectSubtitle(sub, context)
                                    showSubtitleSheet = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = sub,
                                color = if (isSelected) CinematicRed else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = CinematicRed)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSubtitleSheet = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // Playback speed selector dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    speeds.forEach { speed ->
                        val isSelected = uiState.playbackSpeed == speed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerViewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (isSelected) CinematicRed else TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
