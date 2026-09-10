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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.presentation.components.DailyMovieRecommendationPopup
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

import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ArrowForward
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
import com.example.presentation.viewmodel.AppUpdateViewModel

@Composable
fun HomeScreen(
    movieViewModel: MovieViewModel,
    downloadViewModel: DownloadViewModel,
    onMovieClick: (Movie) -> Unit,
    onPlayClick: (Movie) -> Unit,
    updateViewModel: AppUpdateViewModel? = null,
    modifier: Modifier = Modifier
) {
    val state by movieViewModel.uiState.collectAsState()
    val updateInfo by updateViewModel?.updateInfo?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

        // Daily Movie Recommendation In-App Popup
        if (state.allMovies.isNotEmpty()) {
            item {
                DailyMovieRecommendationPopup(
                    movies = state.allMovies,
                    onMovieClick = onMovieClick,
                    onPlayClick = onPlayClick,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Live App Update Available Banner
        if (updateInfo?.isUpdateAvailable == true) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CinematicRed.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { updateViewModel?.openUpdateDialog() }
                        .testTag("home_update_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CinematicRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Update Available (v${updateInfo?.latestVersion})",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap to download & install latest features",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = { updateViewModel?.openUpdateDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = CinematicRed, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                                    Color.Black.copy(alpha = 0.60f),
                                    Color.Transparent,
                                    Color(0x6609090C),
                                    Color(0xDD09090C),
                                    ObsidianBlack
                                ),
                                startY = 0f
                            )
                        )
                )

                // Top Floating Brand Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    AsyncImage(
                        model = com.example.MainActivity.APP_LOGO_URL,
                        contentDescription = "MovieRoom Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "MovieRoom",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

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
