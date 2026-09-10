package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.Movie
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Calendar

@Composable
fun DailyMovieRecommendationPopup(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onPlayClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDismissed by remember { mutableStateOf(false) }
    var suggestedMovie by remember { mutableStateOf<Movie?>(null) }
    var timeSlotTitle by remember { mutableStateOf("Today's Cinema Pick") }
    var timeSlotSubtitle by remember { mutableStateOf("Suggested for you right now") }

    LaunchedEffect(movies) {
        if (movies.isNotEmpty() && suggestedMovie == null) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val (slot, sub) = when {
                hour in 5..11 -> "🌅 Morning Movie Pick" to "Start your morning with an epic movie!"
                hour in 12..17 -> "☀️ Afternoon Cinema Break" to "Take a quick break and enjoy a great movie."
                else -> "🌙 Night Movie Recommendation" to "Unwind tonight with our top-rated pick."
            }
            timeSlotTitle = slot
            timeSlotSubtitle = sub
            suggestedMovie = movies.random()
        }
    }

    val movie = suggestedMovie

    AnimatedVisibility(
        visible = !isDismissed && movie != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f)
        ) + fadeIn(tween(300)) + scaleIn(initialScale = 0.92f),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(200)) + scaleOut(targetScale = 0.95f),
        modifier = modifier
    ) {
        if (movie == null) return@AnimatedVisibility

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = AmberGold.copy(alpha = 0.25f), spotColor = AmberGold.copy(alpha = 0.35f))
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            AmberGold,
                            Color(0xFFFFB300),
                            CinematicRed
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("daily_movie_recommendation_popup")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF221A12),
                                SurfaceDark
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = AmberGold.copy(alpha = 0.2f),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = timeSlotTitle,
                                color = AmberGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = timeSlotSubtitle,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { isDismissed = true },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_dismiss_movie_recommendation")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Movie Content Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onMovieClick(movie) }
                        .padding(vertical = 4.dp)
                ) {
                    // Movie Thumbnail
                    AsyncImage(
                        model = movie.coverUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 85.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Movie Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = movie.title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${movie.rating}",
                                color = AmberGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${movie.releaseYear} • ${movie.genres.firstOrNull() ?: "Cinema"}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = movie.description,
                            color = TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Watch Now Button
                    Button(
                        onClick = { onPlayClick(movie) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinematicRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("btn_watch_recommended_movie")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
