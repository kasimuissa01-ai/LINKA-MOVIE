package com.example.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.Movie
import com.example.presentation.components.HomeScreenSkeleton
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
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.presentation.viewmodel.AppUpdateViewModel
import com.example.util.VideoCacheManager

@Composable
fun HomeScreen(
    movieViewModel: MovieViewModel,
    downloadViewModel: DownloadViewModel,
    onMovieClick: (Movie) -> Unit,
    onPlayClick: (Movie) -> Unit,
    updateViewModel: AppUpdateViewModel? = null,
    onCategoryClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by movieViewModel.uiState.collectAsState()
    val updateInfo by updateViewModel?.updateInfo?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        movieViewModel.refreshCatalog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.featuredMovies) {
        state.featuredMovies.take(3).forEach { movie ->
            if (movie.videoStreamUrl.isNotBlank() && movie.videoStreamUrl.startsWith("http")) {
                VideoCacheManager.prefetchVideoHeader(context, movie.videoStreamUrl)
            }
        }
    }

    if (state.isLoading) {
        HomeScreenSkeleton(modifier = modifier)
        return
    }

    if (state.allMovies.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Movies in Catalog",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap refresh to sync your movies from the cloud library",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { movieViewModel.refreshCatalog() },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Catalog")
                }
            }
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

        item { Spacer(modifier = Modifier.height(14.dp)) }

        // Category Filter Tabs
        item {
            val categories = listOf("All", "Zilizotafsiriwa", "Trending", "Romance", "Zombies", "Action", "Horror", "Comedy", "Sci-Fi", "Animation")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("home_category_filter_row")
            ) {
                items(categories) { cat ->
                    val isSelected = if (cat == "All") state.selectedGenre == null else state.selectedGenre.equals(cat, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            movieViewModel.onGenreSelected(if (cat == "All") null else cat)
                        },
                        label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary,
                            selectedContainerColor = if (cat == "Zilizotafsiriwa") AmberGold else CinematicRed,
                            selectedLabelColor = if (cat == "Zilizotafsiriwa") Color.Black else Color.White
                        ),
                        shape = CircleShape
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Country Quick-Filter Chips
        item {
            val countries = listOf(
                "All Countries" to null,
                "USA 🇺🇸" to "USA",
                "India 🇮🇳" to "India",
                "Korea 🇰🇷" to "Korea",
                "China 🇨🇳" to "China",
                "Tanzania 🇹🇿" to "Tanzania",
                "Nigeria 🇳🇬" to "Nigeria",
                "UK 🇬🇧" to "UK",
                "Japan 🇯🇵" to "Japan",
                "Turkey 🇹🇷" to "Turkey"
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("home_country_filter_row")
            ) {
                items(countries) { (label, countryCode) ->
                    val isSelected = state.selectedCountry == countryCode
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            movieViewModel.onCountrySelected(countryCode)
                        },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary,
                            selectedContainerColor = Color(0xFF2196F3),
                            selectedLabelColor = Color.White
                        ),
                        shape = CircleShape
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }

        // If an explicit filter is active, show the filtered results section (10 per row max, continuing in next row)
        if (state.selectedGenre != null || state.selectedCountry != null) {
            val activeTitle = buildString {
                append("Filtered Movies")
                if (state.selectedGenre != null) append(" • ${state.selectedGenre}")
                if (state.selectedCountry != null) append(" • ${state.selectedCountry}")
            }
            renderChunkedMovieShelves(
                primaryTitle = activeTitle,
                categoryKey = state.selectedGenre ?: "Filtered",
                movies = state.searchResults,
                onMovieClick = onMovieClick,
                onCategoryClick = onCategoryClick
            )
        }

        // 0. Dedicated Swahili Translated Shelf ("Zilizotafsiriwa Kiswahili")
        renderChunkedMovieShelves(
            primaryTitle = "Zilizotafsiriwa Kiswahili (DJ Afro & Ma DJ)",
            categoryKey = "Zilizotafsiriwa",
            movies = state.swahiliMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 1. Trending Movie Shelf (Admin Curated)
        renderChunkedMovieShelves(
            primaryTitle = "Trending Movie",
            categoryKey = "Trending",
            movies = state.trendingMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 2. Zombies Shelf
        renderChunkedMovieShelves(
            primaryTitle = "🧟 Zombies 🧟",
            categoryKey = "Zombies",
            movies = state.zombieMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 3. 💖 Romance 💖 Shelf (Mutually Exclusive)
        renderChunkedMovieShelves(
            primaryTitle = "💖 Romance 💖",
            categoryKey = "Romance",
            movies = state.romanceMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 4. Action Shelf (Mutually Exclusive)
        renderChunkedMovieShelves(
            primaryTitle = "Action",
            categoryKey = "Action",
            movies = state.actionMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 5. Horror Shelf (Mutually Exclusive)
        renderChunkedMovieShelves(
            primaryTitle = "Horror",
            categoryKey = "Horror",
            movies = state.horrorMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 6. Comedies Shelf (Mutually Exclusive)
        renderChunkedMovieShelves(
            primaryTitle = "Comedies",
            categoryKey = "Comedy",
            movies = state.comedyMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 7. Animation Shelf (Mutually Exclusive)
        renderChunkedMovieShelves(
            primaryTitle = "Animation",
            categoryKey = "Animation",
            movies = state.animationMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 8. Sci-Fi & Cyberpunk Shelf (Mutually Exclusive)
        renderChunkedMovieShelves(
            primaryTitle = "Sci-Fi & Cyberpunk",
            categoryKey = "Sci-Fi",
            movies = state.sciFiMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )

        // 9. Cinematic Epics Shelf (Mutually Exclusive)
        renderChunkedMovieShelves(
            primaryTitle = "Cinematic Epics",
            categoryKey = "Drama",
            movies = state.dramaMovies,
            onMovieClick = onMovieClick,
            onCategoryClick = onCategoryClick
        )
    }
}

/**
 * Helper to render movie rows in clean chunks of at most 10 container cards per row.
 * Any continuing movies from the same category are placed into subsequent rows labeled "More [Category]".
 */
private fun androidx.compose.foundation.lazy.LazyListScope.renderChunkedMovieShelves(
    primaryTitle: String,
    categoryKey: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onCategoryClick: ((String) -> Unit)? = null
) {
    if (movies.isEmpty()) return
    val chunks = movies.chunked(10)
    chunks.forEachIndexed { index, chunkMovies ->
        val rowTitle = when (index) {
            0 -> primaryTitle
            1 -> "More $categoryKey"
            else -> "More $categoryKey (Part ${index + 1})"
        }
        item(key = "row_${categoryKey}_$index") {
            MovieRowSection(
                title = rowTitle,
                movies = chunkMovies,
                onMovieClick = onMovieClick,
                onMoreClick = { onCategoryClick?.invoke(categoryKey) }
            )
        }
        item(key = "spacer_${categoryKey}_$index") {
            Spacer(modifier = Modifier.height(20.dp))
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_carousel")
    ) {
        val screenWidth = maxWidth
        val isSmall = screenWidth < 390.dp
        val isVerySmall = screenWidth < 350.dp

        // Adaptive hero height based on screen width:
        // Compact devices (e.g., iPhone 7, small Android) preserve cinematic framing
        // so the movie artwork looks clean, cute, and never clipped.
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
                    val heroCover = com.example.util.MovieCoverUtils.resolveCoverUrl(movie.title, movie.coverUrl, movie.genres)
                    AsyncImage(
                        model = heroCover,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cinematic subtle dark gradient scrim
                    // Tuned so the artwork in the upper/middle area remains clear, cute, and unclipped
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = if (isSmall) 0.45f else 0.55f),
                                    0.22f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    0.80f to Color(0x9909090C),
                                    0.94f to Color(0xEE09090C),
                                    1.0f to ObsidianBlack
                                )
                            )
                    )

                    // Top Floating Brand Bar
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
                        AsyncImage(
                            model = com.example.MainActivity.APP_LOGO_URL,
                            contentDescription = "MovieRoom Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(if (isSmall) 28.dp else 34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(if (isSmall) 8.dp else 10.dp))
                        Text(
                            text = "MovieRoom",
                            color = Color.White,
                            fontSize = if (isSmall) 16.sp else 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Bottom Content
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                horizontal = if (isSmall) 16.dp else 20.dp,
                                vertical = if (isSmall) 16.dp else 24.dp
                            )
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
                                    fontSize = if (isSmall) 9.sp else 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = if (isSmall) 5.dp else 6.dp,
                                        vertical = if (isSmall) 2.dp else 3.dp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(if (isSmall) 6.dp else 8.dp))

                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = AmberGold,
                                modifier = Modifier.size(if (isSmall) 12.dp else 14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${movie.rating} • ${movie.releaseYear} • ${movie.durationMinutes}m",
                                color = TextSecondary,
                                fontSize = if (isSmall) 11.sp else 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isSmall) 4.dp else 8.dp))

                        Text(
                            text = movie.title,
                            color = TextPrimary,
                            fontSize = if (isVerySmall) 20.sp else if (isSmall) 22.sp else 28.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Attractive rounded glass label for Swahili Translation (description remains on Movie Detail screen only)
                        if (movie.isSwahiliTranslated) {
                            Spacer(modifier = Modifier.height(if (isSmall) 6.dp else 8.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)),
                                modifier = Modifier.testTag("carousel_swahili_label_${movie.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(if (isSmall) 13.dp else 15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Imetafsiriwa Kiswahili",
                                        color = Color.White,
                                        fontSize = if (isSmall) 11.sp else 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isSmall) 10.dp else 16.dp))

                        // Play and Download CTA Buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onPlayClick(movie) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CinematicRed,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = if (isSmall) 16.dp else 20.dp,
                                    vertical = if (isSmall) 8.dp else 10.dp
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.testTag("hero_play_button_${movie.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    modifier = Modifier.size(if (isSmall) 18.dp else 20.dp)
                                )
                                Spacer(modifier = Modifier.width(if (isSmall) 4.dp else 6.dp))
                                Text(
                                    text = "Play",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isSmall) 13.sp else 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(if (isSmall) 8.dp else 12.dp))

                            OutlinedButton(
                                onClick = { onDownloadClick(movie) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = if (isSmall) 14.dp else 18.dp,
                                    vertical = if (isSmall) 8.dp else 10.dp
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.testTag("hero_download_button_${movie.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    modifier = Modifier.size(if (isSmall) 16.dp else 18.dp)
                                )
                                Spacer(modifier = Modifier.width(if (isSmall) 4.dp else 6.dp))
                                Text(
                                    text = "Download",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = if (isSmall) 13.sp else 14.sp
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
                    .padding(
                        end = if (isSmall) 16.dp else 20.dp,
                        bottom = if (isSmall) 18.dp else 28.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(featuredMovies.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        if (isSelected) (if (isSmall) 14.dp else 18.dp) else (if (isSmall) 5.dp else 6.dp),
                        label = "dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .height(if (isSmall) 5.dp else 6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (isSelected) CinematicRed else Color(0x66FFFFFF))
                    )
                }
            }
        }
    }
}

@Composable
fun MovieRowSection(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null
) {
    if (movies.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onMoreClick?.invoke() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "More",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "More $title",
                    tint = TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("movie_row_${title.replace(" ", "_")}")
        ) {
            items(movies.take(10), key = { it.id }) { movie ->
                MoviePosterCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}
