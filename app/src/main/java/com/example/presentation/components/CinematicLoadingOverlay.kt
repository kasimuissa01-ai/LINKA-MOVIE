package com.example.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.Movie
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.CinematicRedLight
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom Cinematic Spinner inspired by a 35mm film reel and glowing camera aperture.
 *
 * Features:
 * - Dual concentric counter-rotating luminous rings.
 * - 8 radial film sprocket notches.
 * - Breathing central lens iris with radial glow.
 */
@Composable
fun CinematicSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cinematic_spinner")

    // Clockwise rotation for outer film reel ring (2000ms)
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    // Counter-clockwise rotation for inner aperture ring (2600ms)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    // Pulsing breathing scale for central halo (0.94f to 1.06f)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Pulsing alpha for atmospheric glow
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .testTag("cinematic_spinner"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = (this.size.minDimension / 2f) - 6.dp.toPx()
            val innerRadius = outerRadius * 0.68f

            // 1. Atmospheric Ambient Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CinematicRed.copy(alpha = 0.35f * pulseAlpha),
                        AmberGold.copy(alpha = 0.12f * pulseAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius * 1.35f
                ),
                radius = outerRadius * 1.35f,
                center = center
            )

            // 2. Track background rings
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Outer Film Reel Ring (Clockwise)
            rotate(outerRotation, pivot = center) {
                // Segment 1: Cinematic Red to Amber Gold sweep
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to CinematicRed,
                        0.35f to CinematicRedLight,
                        0.7f to AmberGold,
                        1.0f to Color.Transparent
                    ),
                    startAngle = 0f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // 8 Film Sprocket Notches along outer perimeter
                val sprocketCount = 8
                val sprocketRadius = outerRadius + 4.dp.toPx()
                for (i in 0 until sprocketCount) {
                    val angleRad = Math.toRadians((i * (360.0 / sprocketCount)))
                    val dotX = center.x + (sprocketRadius * cos(angleRad)).toFloat()
                    val dotY = center.y + (sprocketRadius * sin(angleRad)).toFloat()
                    drawCircle(
                        color = AmberGold.copy(alpha = 0.6f),
                        radius = 1.6.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                }
            }

            // 4. Inner Aperture Ring (Counter-Clockwise)
            rotate(innerRotation, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to ElectricBlue,
                        0.45f to CinematicRed,
                        1.0f to Color.Transparent
                    ),
                    startAngle = 60f,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 5. Central glowing lens iris
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        CinematicRed.copy(alpha = 0.8f),
                        CinematicRedLight.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = 12.dp.toPx()
                ),
                radius = (10.dp.toPx() * pulseScale),
                center = center
            )
        }

        // Center mini cinema play emblem with subtle pulse
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier
                .size(18.dp)
                .scale(pulseScale)
        )
    }
}

/**
 * Fullscreen Cinematic Loading Overlay displayed while fetching the streaming URL
 * from the Supabase repository or preparing ExoPlayer playback.
 *
 * Provides ultra-smooth crossfade transitions and rich theatrical context.
 */
@Composable
fun CinematicLoadingOverlay(
    visible: Boolean,
    movie: Movie,
    loadingStage: String,
    isResolvingStreamUrl: Boolean = true,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(400, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(650, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 1.04f, animationSpec = tween(650, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .testTag("cinematic_loading_overlay")
        ) {
            // 1. Ambient blurred movie backdrop
            if (movie.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = movie.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.1f)
                )
            }

            // 2. High-contrast cinematic scrim & vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xBB000000),
                                Color(0xF209090C),
                                ObsidianBlack
                            ),
                            radius = 1400f
                        )
                    )
            )

            // 3. Subtle Cancel/Back Button at top-left
            if (onBackClick != null) {
                Surface(
                    color = Color(0x66000000),
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(start = 24.dp, top = 20.dp)
                        .align(Alignment.TopStart)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel & Return",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 4. Center Cinematic Card & Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            ) {
                // The Cinematic Spinner
                CinematicSpinner(size = 96.dp)

                Spacer(modifier = Modifier.height(26.dp))

                // Movie Title
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Animated Stage Status Pill with Pulsing Indicator
                Surface(
                    color = Color(0x551C1C24),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        // Pulsing status dot
                        val dotInfiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
                        val dotAlpha by dotInfiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_alpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isResolvingStreamUrl) ElectricBlue.copy(alpha = dotAlpha)
                                    else CinematicRed.copy(alpha = dotAlpha)
                                )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Smooth crossfade between stage messages
                        AnimatedContent(
                            targetState = loadingStage.ifBlank { "Fetching stream URL from Supabase repository..." },
                            transitionSpec = {
                                fadeIn(animationSpec = tween(280)) togetherWith
                                        fadeOut(animationSpec = tween(200))
                            },
                            label = "status_text_transition"
                        ) { targetStatus ->
                            Text(
                                text = targetStatus,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Technical Stream Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CinemaBadge(text = "SUPABASE REPO", accentColor = ElectricBlue)
                    CinemaBadge(text = "CLOUDFLARE R2", accentColor = AmberGold)
                    CinemaBadge(text = "4K ULTRA HD", accentColor = CinematicRed)
                    CinemaBadge(text = "DOLBY ATMOS", accentColor = Color(0xFFB388FF))
                }
            }
        }
    }
}

@Composable
private fun CinemaBadge(
    text: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(4.dp)
            )
            .background(
                color = accentColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = accentColor.copy(alpha = 0.9f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
