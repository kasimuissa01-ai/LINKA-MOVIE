package com.example.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * State holding double-tap seek indicators (+10s, +20s, -10s, etc.)
 */
data class DoubleTapSeekInfo(
    val isForward: Boolean,
    val seconds: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Custom Media3 / ExoPlayer Gesture Overlay.
 *
 * Features:
 * 1. Double-Tap Seeking:
 *    - Left 45%: -10s rewind with animated ripple wave and cascading rewind chevrons.
 *    - Right 45%: +10s forward with animated ripple wave and cascading forward chevrons.
 *    - Consecutive rapid taps accumulate (+20s, +30s, etc.) with spring bounce pulse.
 *    - Tactile haptic feedback on each seek event.
 *
 * 2. Vertical Swipes:
 *    - Left half: Smooth Screen Brightness adjustment with glowing Amber Sun HUD.
 *    - Right half: Smooth Voice / Volume adjustment with vibrant Sonic Cyan HUD.
 *
 * 3. Single Tap:
 *    - Triggers `onSingleTap` to cleanly toggle player controls without conflicting
 *      with double-tap detection.
 */
@OptIn(UnstableApi::class)
@Composable
fun Media3GestureOverlay(
    modifier: Modifier = Modifier,
    player: Player? = null,
    onSingleTap: () -> Unit = {},
    onSeekRelative: ((seconds: Int, isForward: Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Double-tap state
    var doubleTapSeek by remember { mutableStateOf<DoubleTapSeekInfo?>(null) }
    var seekDismissJob by remember { mutableStateOf<Job?>(null) }

    // Volume state
    var showVolumeHud by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(-1f) }
    var volumeDismissJob by remember { mutableStateOf<Job?>(null) }

    // Brightness state
    var showBrightnessHud by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(-1f) }
    var brightnessDismissJob by remember { mutableStateOf<Job?>(null) }

    // Tap tracking
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }

    val activity = remember(context) { context.findActivity() }

    // Function to apply double tap seek
    val triggerSeek: (Boolean) -> Unit = { isForward ->
        val step = if (isForward) 10 else -10
        val currentAccumulated = if (doubleTapSeek?.isForward == isForward) {
            (doubleTapSeek?.seconds ?: 0) + 10
        } else {
            10
        }

        doubleTapSeek = DoubleTapSeekInfo(isForward = isForward, seconds = currentAccumulated)

        // Haptic feedback
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}

        // Apply seek to player or callback
        if (onSeekRelative != null) {
            onSeekRelative(currentAccumulated, isForward)
        } else if (player != null) {
            val cur = player.currentPosition
            val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            val target = (cur + (step * 1000L)).coerceIn(0L, duration)
            player.seekTo(target)
        }

        // Auto dismiss after 750ms
        seekDismissJob?.cancel()
        seekDismissJob = coroutineScope.launch {
            delay(750)
            doubleTapSeek = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(player, activity) {
                val touchSlop = 16.dp.toPx()
                val doubleTapSlop = 140.dp.toPx()

                awaitEachGesture {
                    val downPointer = awaitFirstDown(requireUnconsumed = false)
                    val downPos = downPointer.position
                    val downTime = SystemClock.uptimeMillis()
                    val totalWidth = size.width.toFloat()
                    val totalHeight = size.height.toFloat()
                    val isLeftHalf = downPos.x < totalWidth * 0.5f

                    var isDragging = false
                    var totalDragY = 0f
                    var totalDragX = 0f
                    var lastY = downPos.y

                    // Vertical swipe tracking
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val currentPointer = event.changes.firstOrNull { it.id == downPointer.id } ?: break
                            if (!currentPointer.pressed) break

                            val currentPos = currentPointer.position
                            val deltaY = currentPos.y - lastY
                            totalDragY += abs(deltaY)
                            totalDragX += abs(currentPos.x - downPos.x)

                            if (!isDragging) {
                                if (totalDragY > touchSlop && totalDragY > totalDragX * 1.25f) {
                                    isDragging = true
                                    singleTapJob?.cancel()
                                    lastTapTime = 0L

                                    if (isLeftHalf) {
                                        // Initialize Brightness
                                        val window = activity?.window
                                        val curWin = window?.attributes?.screenBrightness ?: -1f
                                        if (brightnessLevel < 0f) {
                                            brightnessLevel = if (curWin < 0f) 0.5f else curWin.coerceIn(0.02f, 1f)
                                        }
                                        showBrightnessHud = true
                                        showVolumeHud = false
                                    } else {
                                        // Initialize Volume
                                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat()?.coerceAtLeast(1f) ?: 15f
                                        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 7f
                                        if (volumeLevel < 0f) {
                                            volumeLevel = (curVol / maxVol).coerceIn(0f, 1f)
                                        }
                                        showVolumeHud = true
                                        showBrightnessHud = false
                                    }
                                } else if (totalDragX > touchSlop) {
                                    break
                                }
                            }

                            if (isDragging) {
                                currentPointer.consume()
                                val dragRatio = (-deltaY / totalHeight) * 1.35f

                                if (isLeftHalf) {
                                    // Adjust Brightness
                                    val newBri = (brightnessLevel + dragRatio).coerceIn(0.01f, 1.0f)
                                    brightnessLevel = newBri
                                    activity?.window?.let { win ->
                                        val lp = win.attributes
                                        lp.screenBrightness = newBri
                                        win.attributes = lp
                                    }
                                    showBrightnessHud = true
                                } else {
                                    // Adjust Volume
                                    val newVol = (volumeLevel + dragRatio).coerceIn(0f, 1.0f)
                                    volumeLevel = newVol
                                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                    if (audioManager != null) {
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
                                        val targetIdx = (newVol * maxVol).roundToInt().coerceIn(0, maxVol.toInt())
                                        try {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIdx, 0)
                                        } catch (_: Exception) {}
                                    }
                                    try {
                                        player?.volume = newVol
                                    } catch (_: Exception) {}
                                    showVolumeHud = true
                                }
                                lastY = currentPos.y
                            }
                        }
                    } finally {
                        if (isDragging) {
                            if (isLeftHalf) {
                                brightnessDismissJob?.cancel()
                                brightnessDismissJob = coroutineScope.launch {
                                    delay(1100)
                                    showBrightnessHud = false
                                }
                            } else {
                                volumeDismissJob?.cancel()
                                volumeDismissJob = coroutineScope.launch {
                                    delay(1100)
                                    showVolumeHud = false
                                }
                            }
                        }
                    }

                    // Tap / Double-tap processing if not dragged
                    if (!isDragging) {
                        val duration = SystemClock.uptimeMillis() - downTime
                        if (duration < 420L) {
                            val now = SystemClock.uptimeMillis()
                            val activeSeek = doubleTapSeek
                            val isConsecutive = activeSeek != null && (
                                (activeSeek.isForward && !isLeftHalf) ||
                                (!activeSeek.isForward && isLeftHalf)
                            )
                            val isDoubleTap = (now - lastTapTime < 320L) &&
                                (hypot(downPos.x - lastTapPos.x, downPos.y - lastTapPos.y) < doubleTapSlop)

                            if (isConsecutive || isDoubleTap) {
                                singleTapJob?.cancel()
                                lastTapTime = now
                                lastTapPos = downPos
                                triggerSeek(!isLeftHalf)
                            } else {
                                lastTapTime = now
                                lastTapPos = downPos
                                singleTapJob?.cancel()
                                singleTapJob = coroutineScope.launch {
                                    delay(270)
                                    onSingleTap()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Child content (Video Surface, Controls, etc.)
        content()

        // ── 1. Double-Tap Seek Ripple Overlay ──
        doubleTapSeek?.let { seekInfo ->
            DoubleTapSeekIndicator(
                seekInfo = seekInfo,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 2. Screen Brightness HUD (Left Side) ──
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(tween(140)),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            BrightnessGaugeCard(level = brightnessLevel.coerceIn(0f, 1f))
        }

        // ── 3. Voice / Volume HUD (Right Side) ──
        AnimatedVisibility(
            visible = showVolumeHud,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(140)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            VolumeGaugeCard(level = volumeLevel.coerceIn(0f, 1f))
        }
    }
}

/**
 * Animated YouTube / Netflix-style Double-Tap Seek Indicator.
 *
 * Features:
 * - Curved gradient arc spreading across the tapped half of the display.
 * - 3 animated cascading chevrons with sequential ripple offsets.
 * - Prominent bouncy spring pill with +10s / -10s counter.
 */
@Composable
fun DoubleTapSeekIndicator(
    seekInfo: DoubleTapSeekInfo,
    modifier: Modifier = Modifier
) {
    val isForward = seekInfo.isForward
    val pulseAnim = remember { Animatable(0.85f) }

    LaunchedEffect(seekInfo.seconds, seekInfo.timestamp) {
        pulseAnim.snapTo(0.82f)
        pulseAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    // Cascading chevrons animation (infinite ripple during seek window)
    val infiniteTransition = rememberInfiniteTransition(label = "chevrons")
    val chevronPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // Curved halo glow on tapped half
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .clip(
                    if (isForward) RoundedCornerShape(topStart = 160.dp, bottomStart = 160.dp)
                    else RoundedCornerShape(topEnd = 160.dp, bottomEnd = 160.dp)
                )
                .background(
                    Brush.horizontalGradient(
                        colors = if (isForward) {
                            listOf(Color.Transparent, CinematicRed.copy(alpha = 0.28f))
                        } else {
                            listOf(CinematicRed.copy(alpha = 0.28f), Color.Transparent)
                        }
                    )
                )
        )

        // Glassmorphic Center Card
        Surface(
            color = Color(0xCC0D111A),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        CinematicRed.copy(alpha = 0.8f),
                        Color(0x44FFFFFF)
                    )
                )
            ),
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .scale(pulseAnim.value)
                .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = CinematicRed)
                .testTag(if (isForward) "seek_forward_indicator" else "seek_rewind_indicator")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                // Cascading 3 Chevron Ripple
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!isForward) {
                        for (i in 2 downTo 0) {
                            val alpha = computeChevronAlpha(chevronPhase, i)
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = null,
                                tint = CinematicRed.copy(alpha = alpha),
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { translationX = -(i * 2f) }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = "${if (isForward) "+" else "-"}${seekInfo.seconds}s",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )

                    if (isForward) {
                        Spacer(modifier = Modifier.width(8.dp))
                        for (i in 0..2) {
                            val alpha = computeChevronAlpha(chevronPhase, i)
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = CinematicRed.copy(alpha = alpha),
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { translationX = i * 2f }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isForward) "Fast Forward" else "Rewind",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Computes wave ripple alpha for the 3 chevrons
 */
private fun computeChevronAlpha(phase: Float, index: Int): Float {
    val diff = abs((phase - index) % 3f)
    return when {
        diff < 0.6f -> 1.0f
        diff < 1.4f -> 0.6f
        else -> 0.25f
    }
}

/**
 * Glassmorphic Brightness Gauge Card (Amber Solar Glow)
 */
@Composable
fun BrightnessGaugeCard(
    level: Float,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bri_level"
    )

    Surface(
        color = Color(0xD90D111A),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.2.dp,
            Brush.verticalGradient(
                colors = listOf(
                    AmberGold.copy(alpha = 0.7f),
                    Color(0x33FFFFFF)
                )
            )
        ),
        shadowElevation = 12.dp,
        modifier = modifier
            .width(56.dp)
            .height(180.dp)
            .testTag("brightness_hud")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(vertical = 14.dp)
        ) {
            val brightnessIcon = when {
                animatedLevel < 0.35f -> Icons.Default.BrightnessLow
                animatedLevel < 0.70f -> Icons.Default.BrightnessMedium
                else -> Icons.Default.BrightnessHigh
            }

            Icon(
                imageVector = brightnessIcon,
                contentDescription = "Brightness",
                tint = AmberGold,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = if (animatedLevel > 0.7f) 1.15f else 1.0f
                        scaleY = if (animatedLevel > 0.7f) 1.15f else 1.0f
                    }
            )

            // Fluid vertical level gauge
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x26FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedLevel.coerceIn(0.02f, 1f))
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFF176),
                                    AmberGold
                                )
                            )
                        )
                )
            }

            Text(
                text = "${(animatedLevel * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Glassmorphic Voice / Volume Gauge Card (Sonic Cyan Glow)
 */
@Composable
fun VolumeGaugeCard(
    level: Float,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "vol_level"
    )

    Surface(
        color = Color(0xD90D111A),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.2.dp,
            Brush.verticalGradient(
                colors = listOf(
                    ElectricBlue.copy(alpha = 0.7f),
                    Color(0x33FFFFFF)
                )
            )
        ),
        shadowElevation = 12.dp,
        modifier = modifier
            .width(56.dp)
            .height(180.dp)
            .testTag("volume_hud")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(vertical = 14.dp)
        ) {
            val volumeIcon = when {
                animatedLevel <= 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                animatedLevel < 0.45f -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }

            val volumeTint = if (animatedLevel <= 0.01f) Color(0xFFFF5252) else ElectricBlue

            Icon(
                imageVector = volumeIcon,
                contentDescription = "Volume",
                tint = volumeTint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = if (animatedLevel > 0.6f) 1.12f else 1.0f
                        scaleY = if (animatedLevel > 0.6f) 1.12f else 1.0f
                    }
            )

            // Fluid vertical level gauge
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x26FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedLevel.coerceIn(0f, 1f))
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF80EAFF),
                                    ElectricBlue
                                )
                            )
                        )
                )
            }

            Text(
                text = "${(animatedLevel * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Robust tail-recursive Activity extractor
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
