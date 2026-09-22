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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.domain.model.Movie
import com.example.presentation.components.CinematicLoadingOverlay
import com.example.presentation.viewmodel.PlayerViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
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
    initialPositionMs: Long = 0L,
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
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

    // Sync Activity orientation with PlayerViewModel's orientationMode
    LaunchedEffect(uiState.orientationMode) {
        activity?.let { act ->
            when (uiState.orientationMode) {
                com.example.presentation.viewmodel.OrientationMode.SENSOR -> {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
                com.example.presentation.viewmodel.OrientationMode.USER_LANDSCAPE -> {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                com.example.presentation.viewmodel.OrientationMode.USER_PORTRAIT -> {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
    }

    // Deterministic Landscape + Immersive Fullscreen lifecycle
    DisposableEffect(movie.id) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        playerViewModel.initializePlayer(context, movie, initialPositionMs)
        playerViewModel.setOrientationMode(com.example.presentation.viewmodel.OrientationMode.USER_LANDSCAPE)

        activity?.let { act ->
            // 1. Keep screen ON continuously while video player is active
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            playerViewModel.releasePlayer(movie.id)
            restoreSystemUiAndOrientation()
        }
    }

    // Lifecycle Observer: Stop/Pause playback when user exits or backgrounds the app
    val lifecycleOwner = LocalLifecycleOwner.current
    var wasPlayingBeforeStop by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlayingBeforeStop = playerViewModel.exoPlayer?.isPlaying == true || playerViewModel.exoPlayer?.playWhenReady == true
                    playerViewModel.pause()
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (wasPlayingBeforeStop) {
                        playerViewModel.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Clean Back Handler:
    // If controls overlay is visible, dismiss controls first.
    // If controls are hidden, exit player and return to portrait screen.
    BackHandler {
        if (uiState.controlsVisible) {
            playerViewModel.hideControls()
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        val totalHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // 1. Fullscreen Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = uiState.resizeMode.exoMode
                    keepScreenOn = true
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
                playerView.keepScreenOn = true
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Comprehensive Touch Gesture Layer:
        // - Double-tap edges to seek +/- 10s (Left = -10s, Right = +10s)
        // - Vertical drag on left side for brightness
        // - Vertical drag on right side for volume control
        // - Single tap toggles playback controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(totalWidth, totalHeight) {
                    var lastTapTime = 0L
                    var lastTapPosition = Offset.Zero
                    var singleTapJob: Job? = null

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = android.os.SystemClock.uptimeMillis()
                        val downPos = down.position
                        val isLeftHalf = downPos.x < totalWidth * 0.5f

                        var isDragging = false
                        var lastY = downPos.y
                        var wasPointerConsumed = false
                        val touchSlop = viewConfiguration.touchSlop

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val currentPointer = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!currentPointer.pressed) {
                                    wasPointerConsumed = currentPointer.isConsumed
                                    currentPointer.consume()
                                    break
                                }

                                val currentPos = currentPointer.position
                                val deltaY = currentPos.y - lastY
                                val totalDiffX = abs(currentPos.x - downPos.x)
                                val totalDiffY = abs(currentPos.y - downPos.y)

                                if (!isDragging) {
                                    if (totalDiffY > touchSlop && totalDiffY > totalDiffX * 1.1f) {
                                        isDragging = true
                                        singleTapJob?.cancel()
                                        lastTapTime = 0L
                                        if (isLeftHalf) {
                                            playerViewModel.onBrightnessDragStart(activity?.window)
                                        } else {
                                            playerViewModel.onVolumeDragStart(context)
                                        }
                                    } else if (totalDiffX > touchSlop) {
                                        break
                                    }
                                }

                                if (isDragging) {
                                    currentPointer.consume()
                                    val dragRatio = -deltaY / totalHeight
                                    if (isLeftHalf) {
                                        playerViewModel.onBrightnessSwipe(dragRatio, activity?.window)
                                    } else {
                                        playerViewModel.onVolumeSwipe(dragRatio, context)
                                    }
                                    lastY = currentPos.y
                                }
                            }
                        } finally {
                            if (isDragging) {
                                playerViewModel.onDragEnd()
                            }
                        }

                        // Process Tap & Double Tap gestures if no vertical drag occurred
                        if (!isDragging && !wasPointerConsumed) {
                            val tapDuration = android.os.SystemClock.uptimeMillis() - downTime
                            if (tapDuration < 450L) {
                                val now = android.os.SystemClock.uptimeMillis()
                                val activeSeek = playerViewModel.uiState.value.doubleTapSeek
                                val isConsecutiveSeek = activeSeek != null && (
                                    (activeSeek.isForward && !isLeftHalf) ||
                                    (!activeSeek.isForward && isLeftHalf)
                                )
                                val isDoubleTap = (now - lastTapTime < 340L) &&
                                        (hypot(downPos.x - lastTapPosition.x, downPos.y - lastTapPosition.y) < 130.dp.toPx())

                                if (isConsecutiveSeek || isDoubleTap) {
                                    singleTapJob?.cancel()
                                    lastTapTime = now
                                    lastTapPosition = downPos
                                    if (isLeftHalf) {
                                        playerViewModel.seekRelative(-10, isForward = false)
                                    } else {
                                        playerViewModel.seekRelative(10, isForward = true)
                                    }
                                } else {
                                    lastTapTime = now
                                    lastTapPosition = downPos
                                    singleTapJob?.cancel()
                                    singleTapJob = coroutineScope.launch {
                                        delay(260L)
                                        playerViewModel.toggleControls()
                                    }
                                }
                            }
                        }
                    }
                }
        )

        // 3. Double-tap Seek HUD (+10s / -10s on edges with pulse animation)
        uiState.doubleTapSeek?.let { seekState ->
            val pulseAnim = remember { Animatable(0.85f) }

            LaunchedEffect(seekState.seconds, seekState.isForward) {
                pulseAnim.snapTo(0.85f)
                pulseAnim.animateTo(
                    targetValue = 1.15f,
                    animationSpec = tween(280)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (seekState.isForward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    color = Color(0xCC111118),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.5.dp, CinematicRed.copy(alpha = 0.8f)),
                    modifier = Modifier.scale(pulseAnim.value)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!seekState.isForward) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = null,
                                    tint = CinematicRed,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "${if (seekState.isForward) "+" else "-"}${seekState.seconds}s",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (seekState.isForward) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = null,
                                    tint = CinematicRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (seekState.isForward) "Fast Forward" else "Rewind",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. Volume / Brightness HUD Overlays (Minimal, Sleek Glassmorphic)
        // Volume HUD (Right side vertical drag)
        AnimatedVisibility(
            visible = uiState.showVolumeHud,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp)
        ) {
            Surface(
                color = Color(0xCC111118),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .width(52.dp)
                    .height(170.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(vertical = 14.dp)
                ) {
                    val volumeIcon = when {
                        uiState.volumeLevel <= 0.01f -> Icons.Default.VolumeOff
                        uiState.volumeLevel < 0.5f -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    }
                    Icon(
                        imageVector = volumeIcon,
                        contentDescription = "Volume",
                        tint = CinematicRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(90.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(uiState.volumeLevel.coerceIn(0f, 1f))
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CinematicRed)
                        )
                    }
                    Text(
                        text = "${(uiState.volumeLevel * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Brightness HUD (Left side vertical drag)
        AnimatedVisibility(
            visible = uiState.showBrightnessHud,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 40.dp)
        ) {
            Surface(
                color = Color(0xCC111118),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .width(52.dp)
                    .height(170.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(vertical = 14.dp)
                ) {
                    val brightnessIcon = when {
                        uiState.brightnessLevel < 0.35f -> Icons.Default.BrightnessLow
                        uiState.brightnessLevel < 0.7f -> Icons.Default.BrightnessMedium
                        else -> Icons.Default.BrightnessHigh
                    }
                    Icon(
                        imageVector = brightnessIcon,
                        contentDescription = "Brightness",
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(90.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(uiState.brightnessLevel.coerceIn(0f, 1f))
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberGold)
                        )
                    }
                    Text(
                        text = "${(uiState.brightnessLevel * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. Cinematic Loading State Overlay (Displays ONLY while initially resolving stream URL)
        CinematicLoadingOverlay(
            visible = uiState.isResolvingStreamUrl && uiState.errorMessage == null,
            movie = movie,
            loadingStage = uiState.loadingStage,
            isResolvingStreamUrl = uiState.isResolvingStreamUrl,
            onBackClick = {
                restoreSystemUiAndOrientation()
                onBackClick()
            }
        )

        // 5b. Sleek unobtrusive center buffering spinner when stream is already resolved
        if (uiState.isLoading && !uiState.isResolvingStreamUrl && uiState.errorMessage == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            color = CinematicRed,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
        }

        // 6. Error notice banner with full diagnostics breakdown
        uiState.errorMessage?.let { errorText ->
            Surface(
                color = Color(0xEE1A1A1A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinematicRed.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 480.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CinematicRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Playback Pipeline Diagnostics",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    uiState.streamDiagnostic?.let { diag ->
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Status: ${diag.failureSummary}",
                                    color = AmberGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Broken Layer: ${diag.brokenLayer}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Stream URL: ${diag.streamUrl.take(60)}${if (diag.streamUrl.length > 60) "..." else ""}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "How to fix: ${diag.actionGuide}",
                                    color = ElectricBlue,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } ?: run {
                        Text(
                            text = errorText,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                playerViewModel.retryPlayback(context, movie)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Retry",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                restoreSystemUiAndOrientation()
                                onBackClick()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Back to Details",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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

                            Spacer(modifier = Modifier.width(10.dp))

                            // Rotate Screen Toggle Button (toggles 180° in landscape, or sensor)
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clickable {
                                        activity?.let { act ->
                                            val currentOrient = act.requestedOrientation
                                            if (currentOrient == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                playerViewModel.setOrientationMode(com.example.presentation.viewmodel.OrientationMode.USER_LANDSCAPE)
                                            } else {
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                            }
                                        }
                                        playerViewModel.restartControlsHideTimer()
                                    }
                                    .testTag("player_rotate_toggle_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Rotate Screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Rotate",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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
