@file:OptIn(
    androidx.media3.common.util.UnstableApi::class
)

package com.example.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.PlayerViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

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

    // Initialize player and lock to landscape or flexible mode
    DisposableEffect(Unit) {
        playerViewModel.initializePlayer(context, movie)
        onDispose {
            playerViewModel.releasePlayer()
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

        // Media3 ExoPlayer AndroidView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // Custom overlay handles all controls
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

        // Gesture detector overlay (tap to toggle, double-tap seek, vertical swipes)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.isLocked) {
                    detectTapGestures(
                        onTap = {
                            playerViewModel.toggleControls()
                        },
                        onDoubleTap = { offset ->
                            if (!uiState.isLocked) {
                                if (offset.x > totalWidth / 2) {
                                    playerViewModel.seekRelative(10, isForward = true)
                                } else {
                                    playerViewModel.seekRelative(-10, isForward = false)
                                }
                            }
                        }
                    )
                }
                .pointerInput(uiState.isLocked) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            if (!uiState.isLocked) {
                                val isRightSide = change.position.x > totalWidth / 2
                                val deltaRatio = -dragAmount.y / totalHeight // Drag up increases
                                if (isRightSide) {
                                    playerViewModel.onVolumeSwipe(deltaRatio, context)
                                } else {
                                    playerViewModel.onBrightnessSwipe(deltaRatio, activity?.window)
                                }
                            }
                        }
                    )
                }
        )

        // Double-tap Seek Animation HUD (+10s / -10s)
        uiState.doubleTapSeek?.let { seekState ->
            val pulseAnim = remember { Animatable(0.7f) }
            LaunchedEffect(seekState) {
                pulseAnim.animateTo(
                    targetValue = 1.25f,
                    animationSpec = tween(300)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 60.dp),
                contentAlignment = if (seekState.isForward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    color = Color(0xBB000000),
                    shape = CircleShape,
                    modifier = Modifier.size(90.dp)
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
                                .size(36.dp)
                                .scale(pulseAnim.value)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (seekState.isForward) "+10s" else "-10s",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Volume Swipe HUD
        AnimatedVisibility(
            visible = uiState.showVolumeHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp)
        ) {
            Surface(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .width(54.dp)
                    .height(180.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = CinematicRed,
                        modifier = Modifier.size(24.dp)
                    )

                    // Vertical bar indicator
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceElevated)
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

        // Brightness Swipe HUD
        AnimatedVisibility(
            visible = uiState.showBrightnessHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp)
        ) {
            Surface(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .width(54.dp)
                    .height(180.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = "Brightness",
                        tint = AmberGold,
                        modifier = Modifier.size(24.dp)
                    )

                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceElevated)
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

        // Aspect Ratio Mode Change Toast HUD
        uiState.resizeToast?.let { label ->
            Surface(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Text(
                    text = "Aspect Ratio: $label",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Full Interactive Controls Overlay
        AnimatedVisibility(
            visible = uiState.controlsVisible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color.Transparent,
                                Color(0xDD000000)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = movie.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.isOfflinePlayback) {
                                Text(
                                    text = "Playing from Offline Storage",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Aspect ratio cycle
                        IconButton(
                            onClick = { playerViewModel.cycleResizeMode() },
                            modifier = Modifier.testTag("player_aspect_ratio_button")
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                        }

                        // Subtitles
                        IconButton(
                            onClick = { showSubtitleSheet = true },
                            modifier = Modifier.testTag("player_subtitles_button")
                        ) {
                            Icon(
                                Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = if (uiState.selectedSubtitle != "Off") CinematicRed else Color.White
                            )
                        }

                        // Speed selector
                        IconButton(
                            onClick = { showSpeedDialog = true },
                            modifier = Modifier.testTag("player_speed_button")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                        }

                        // Lock Screen button
                        IconButton(
                            onClick = { playerViewModel.toggleLock() },
                            modifier = Modifier.testTag("player_lock_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock",
                                tint = if (uiState.isLocked) CinematicRed else Color.White
                            )
                        }
                    }
                }

                // Center Play/Pause & Quick Seek Buttons (Hidden if locked)
                if (!uiState.isLocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        // Seek backward 10s
                        IconButton(
                            onClick = { playerViewModel.seekRelative(-10, isForward = false) },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Play/Pause button
                        Surface(
                            color = CinematicRed,
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
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        // Seek forward 10s
                        IconButton(
                            onClick = { playerViewModel.seekRelative(10, isForward = true) },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Bottom Scrubber Bar & Time
                if (!uiState.isLocked) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        val duration = uiState.durationMs.coerceAtLeast(1L)
                        val current = uiState.currentPositionMs.coerceIn(0L, duration)

                        Slider(
                            value = (current.toFloat() / duration).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                playerViewModel.seekTo((fraction * duration).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = CinematicRed,
                                activeTrackColor = CinematicRed,
                                inactiveTrackColor = Color(0x55FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_time_slider")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(current),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "${uiState.playbackSpeed}x",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = formatTime(duration),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
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
                                    playerViewModel.selectSubtitle(sub)
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
            title = { Text("Playback Speed", color = TextPrimary) },
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
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
