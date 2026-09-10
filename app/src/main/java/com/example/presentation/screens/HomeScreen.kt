package com.example.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.Movie
import com.example.presentation.components.MoviePosterCard
import com.example.presentation.viewmodel.DownloadViewModel
import com.example.presentation.viewmodel.MovieViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    movieViewModel: MovieViewModel,
    downloadViewModel: DownloadViewModel,
    onMovieClick: (Movie) -> Unit,
    onPlayClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by movieViewModel.uiState.collectAsState()
    val context = LocalContext.current

    if (state.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = CinematicRed)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("home_screen_lazy_column"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Hero Carousel
        item {
            if (state.featuredMovies.isNotEmpty()) {
                HeroCarousel(
                    featuredMovies = state.featuredMovies,
                    onMovieClick = onMovieClick,
                    onPlayClick = onPlayClick,
                    onDownloadClick = { movie ->
                        downloadViewModel.startDownload(movie, context)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Popular Movies Row
        item {
            MovieRowSection(
                title = "Popular on MovieRoom",
                movies = state.popularMovies,
                onMovieClick = onMovieClick
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Sci-Fi & Cyberpunk Row
        item {
            MovieRowSection(
                title = "Sci-Fi & Cyberpunk",
                movies = state.sciFiMovies,
                onMovieClick = onMovieClick
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Action & Thrillers
        item {
            MovieRowSection(
                title = "Action & Thrillers",
                movies = state.actionMovies,
                onMovieClick = onMovieClick
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Cinematic Epics & Drama
        item {
            MovieRowSection(
                title = "Cinematic Epics",
                movies = state.dramaMovies,
                onMovieClick = onMovieClick
            )
        }
    }
}

@Composable
fun HeroCarousel(
    featuredMovies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onPlayClick: (Movie) -> Unit,
    onDownloadClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { featuredMovies.size })
    val coroutineScope = rememberCoroutineScope()

    // Auto rotate every 6 seconds
    LaunchedEffect(pagerState) {
        while (true) {
            delay(6000)
            if (featuredMovies.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % featuredMovies.size
                pagerState.animateScrollToPage(nextPage, animationSpec = tween(600))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(440.dp)
            .testTag("hero_carousel")
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = featuredMovies[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMovieClick(movie) }
            ) {
                AsyncImage(
                    model = movie.coverUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Cinematic dark gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x6609090C),
                                    Color(0xDD09090C),
                                    ObsidianBlack
                                ),
                                startY = 100f
                            )
                        )
                )

                // Bottom Content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    // Rating & Genre badges
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CinematicRed,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "FEATURED",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = AmberGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${movie.rating} • ${movie.releaseYear} • ${movie.durationMinutes}m",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = movie.title,
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = movie.description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Play and Download CTA Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPlayClick(movie) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CinematicRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("hero_play_button_${movie.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedButton(
                            onClick = { onDownloadClick(movie) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("hero_download_button_${movie.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Pager indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(featuredMovies.size) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(if (isSelected) 18.dp else 6.dp, label = "dot_width")
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(if (isSelected) CinematicRed else Color(0x66FFFFFF))
                )
            }
        }
    }
}

@Composable
fun MovieRowSection(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "See All",
                color = CinematicRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.testTag("movie_row_${title.replace(" ", "_")}")
        ) {
            items(movies, key = { it.id }) { movie ->
                MoviePosterCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}
