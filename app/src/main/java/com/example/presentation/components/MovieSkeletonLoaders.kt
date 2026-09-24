package com.example.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated

/**
 * Reusable animated shimmer brush for modern, cinematic skeleton loaders.
 * Sweeps smoothly across elements using a diagonal gradient.
 */
@Composable
fun shimmerBrush(
    targetValue: Float = 1400f,
    durationMillis: Int = 1300
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF14141E),
            Color(0xFF262638),
            Color(0xFF14141E)
        ),
        start = Offset(translateAnim - 450f, 0f),
        end = Offset(translateAnim, 350f)
    )
}

/**
 * Modifier to apply shimmer effect to any shape.
 */
@Composable
fun Modifier.shimmerSkeleton(
    shape: Shape = RoundedCornerShape(8.dp),
    brush: Brush = shimmerBrush()
): Modifier = this
    .clip(shape)
    .background(brush)

/**
 * Skeleton loader for a single movie poster card in catalog rows or grids.
 */
@Composable
fun MoviePosterCardSkeleton(
    modifier: Modifier = Modifier,
    cardWidth: Int = 150,
    cardHeight: Int = 225,
    brush: Brush = shimmerBrush()
) {
    Column(
        modifier = modifier
            .width(cardWidth.dp)
            .testTag("movie_poster_card_skeleton")
    ) {
        // Main poster artwork placeholder
        Box(
            modifier = Modifier
                .width(cardWidth.dp)
                .height(cardHeight.dp)
                .shimmerSkeleton(RoundedCornerShape(12.dp), brush)
        ) {
            // Rating pill placeholder (top right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 44.dp, height = 22.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp))
                    .background(Color(0x33000000))
            )

            // 4K tag placeholder (top left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(width = 38.dp, height = 18.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp))
                    .background(Color(0x33000000))
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .shimmerSkeleton(RoundedCornerShape(4.dp), brush)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subtitle / Genre & Year row placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(11.dp)
                    .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
            )
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(11.dp)
                    .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
            )
        }
    }
}

/**
 * Skeleton loader for a horizontal movie section row (e.g., "Popular", "Sci-Fi").
 */
@Composable
fun MovieRowSectionSkeleton(
    modifier: Modifier = Modifier,
    cardCount: Int = 4,
    brush: Brush = shimmerBrush()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header title row placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(18.dp)
                    .shimmerSkeleton(RoundedCornerShape(4.dp), brush)
            )
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(12.dp)
                    .shimmerSkeleton(RoundedCornerShape(4.dp), brush)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal items row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(cardCount) {
                MoviePosterCardSkeleton(brush = brush)
            }
        }
    }
}

/**
 * Skeleton loader for the Hero Carousel at the top of the home screen.
 */
@Composable
fun HeroCarouselSkeleton(
    modifier: Modifier = Modifier,
    brush: Brush = shimmerBrush()
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_carousel_skeleton")
    ) {
        val screenWidth = maxWidth
        val isSmall = screenWidth < 390.dp
        val heroHeight = when {
            screenWidth < 350.dp -> 330.dp
            screenWidth < 390.dp -> 370.dp
            screenWidth < 600.dp -> 430.dp
            else -> 460.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .shimmerSkeleton(RectangleShape, brush)
        ) {
            // Subtle dark overlay gradient for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.4f),
                            0.5f to Color.Transparent,
                            1.0f to ObsidianBlack
                        )
                    )
            )

            // Top Floating Brand Bar Skeleton
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(
                        horizontal = if (isSmall) 16.dp else 20.dp,
                        vertical = if (isSmall) 8.dp else 12.dp
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSmall) 28.dp else 34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x44FFFFFF))
                )
                Spacer(modifier = Modifier.width(if (isSmall) 8.dp else 10.dp))
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x44FFFFFF))
                )
            }

            // Bottom Hero Content Info Skeleton
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                // Genres / Rating tag placeholders
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x44FFFFFF))
                    )
                    Box(
                        modifier = Modifier
                            .width(75.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x44FFFFFF))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x55FFFFFF))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons skeleton
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x55FFFFFF))
                    )
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x33FFFFFF))
                    )
                }
            }

            // Pager dot indicators skeleton
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (it == 0) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color(0x55FFFFFF))
                    )
                }
            }
        }
    }
}

/**
 * Complete Home Screen Skeleton displayed during initial catalog loading.
 */
@Composable
fun HomeScreenSkeleton(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
            .testTag("home_screen_skeleton")
    ) {
        HeroCarouselSkeleton(brush = brush)

        Spacer(modifier = Modifier.height(28.dp))

        MovieRowSectionSkeleton(brush = brush)

        Spacer(modifier = Modifier.height(28.dp))

        MovieRowSectionSkeleton(brush = brush)

        Spacer(modifier = Modifier.height(28.dp))

        MovieRowSectionSkeleton(brush = brush)
    }
}

/**
 * Grid skeleton for Search and Filter screens.
 */
@Composable
fun MovieGridSkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    itemCount: Int = 6,
    brush: Brush = shimmerBrush()
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .testTag("movie_grid_skeleton")
    ) {
        items(itemCount) {
            MoviePosterCardSkeleton(
                cardWidth = 165,
                cardHeight = 240,
                brush = brush
            )
        }
    }
}

/**
 * Full-screen skeleton loader for Movie Detail screen.
 * Perfectly mirrors the actual detail screen layout to eliminate visual layout shifts.
 */
@Composable
fun MovieDetailSkeletonScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("movie_detail_skeleton_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 60.dp)
        ) {
            // Top 16:9 media player container skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .shimmerSkeleton(RectangleShape, brush)
            ) {
                // Subtle back arrow button
                Surface(
                    color = Color(0x66000000),
                    shape = CircleShape,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 8.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }

            // Movie Details Info Skeleton
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Poster + Title + Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Poster thumbnail skeleton (85.dp x 125.dp)
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(125.dp)
                            .shimmerSkeleton(RoundedCornerShape(10.dp), brush)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Title & Specs Column
                    Column(modifier = Modifier.weight(1f)) {
                        // Title line 1
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(22.dp)
                                .shimmerSkeleton(RoundedCornerShape(4.dp), brush)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title line 2
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(18.dp)
                                .shimmerSkeleton(RoundedCornerShape(4.dp), brush)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Badges Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Rating pill
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(24.dp)
                                    .shimmerSkeleton(RoundedCornerShape(6.dp), brush)
                            )
                            // Year & Duration pill
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(24.dp)
                                    .shimmerSkeleton(RoundedCornerShape(6.dp), brush)
                            )
                            // Episodes badge
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(24.dp)
                                    .shimmerSkeleton(RoundedCornerShape(6.dp), brush)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // File size
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(12.dp)
                                .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Download Button Skeleton (full width, 50.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shimmerSkeleton(RoundedCornerShape(12.dp), brush)
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Genres Section Skeleton
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp)
                        .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(28.dp)
                            .shimmerSkeleton(RoundedCornerShape(8.dp), brush)
                    )
                    Box(
                        modifier = Modifier
                            .width(82.dp)
                            .height(28.dp)
                            .shimmerSkeleton(RoundedCornerShape(8.dp), brush)
                    )
                    Box(
                        modifier = Modifier
                            .width(74.dp)
                            .height(28.dp)
                            .shimmerSkeleton(RoundedCornerShape(8.dp), brush)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Storyline Synopsis Skeleton
                Box(
                    modifier = Modifier
                        .width(75.dp)
                        .height(14.dp)
                        .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(13.dp)
                        .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(13.dp)
                        .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(13.dp)
                        .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Cast Section Skeleton
                Box(
                    modifier = Modifier
                        .width(55.dp)
                        .height(14.dp)
                        .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .shimmerSkeleton(CircleShape, brush)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(10.dp)
                                    .shimmerSkeleton(RoundedCornerShape(3.dp), brush)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Recommendations Section Skeleton
                MovieRowSectionSkeleton(brush = brush)
            }
        }
    }
}
