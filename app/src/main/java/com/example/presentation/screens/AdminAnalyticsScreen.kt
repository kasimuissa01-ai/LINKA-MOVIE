package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.analytics.AdminAnalyticsSummary
import com.example.data.analytics.DateFilterRange
import com.example.data.analytics.MovieAnalyticsItem
import com.example.data.analytics.MovieSortOption
import com.example.data.repository.AuthRepository
import com.example.domain.model.UserRole
import com.example.presentation.viewmodel.AdminAnalyticsViewModel
import com.example.presentation.viewmodel.AnalyticsUiState
import com.example.presentation.viewmodel.AuthViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun AdminAnalyticsScreen(
    analyticsViewModel: AdminAnalyticsViewModel,
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Enforce Administrator-Only Access using trusted session verification
    val session by authViewModel.userSession.collectAsState()
    val isAdmin = session?.let { s ->
        s.role == UserRole.ADMIN ||
        (s.phoneNumber.isNotBlank() && AuthRepository.isAdminPhoneNumber(s.phoneNumber)) ||
        (s.email.contains("admin", ignoreCase = true))
    } ?: false

    if (!isAdmin) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unauthorized Access",
                    tint = CinematicRed,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Administrator Access Required",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This analytics dashboard is restricted to authorized founders and administrators.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed)
                ) {
                    Text("Return to Studio")
                }
            }
        }
        return
    }

    val uiState by analyticsViewModel.uiState.collectAsState()
    val selectedRange by analyticsViewModel.selectedRange.collectAsState()
    val selectedSortOption by analyticsViewModel.selectedSortOption.collectAsState()
    val searchQuery by analyticsViewModel.searchQuery.collectAsState()
    val selectedDrilldownMovie by analyticsViewModel.selectedMovieDrilldown.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .testTag("admin_analytics_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceDark)
                            .testTag("analytics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Analytics Studio",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real App Usage & Watch Time",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = { analyticsViewModel.refresh() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .testTag("analytics_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AmberGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date Range Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateFilterRange.values().forEach { range ->
                    val isSelected = selectedRange == range
                    FilterChip(
                        selected = isSelected,
                        onClick = { analyticsViewModel.setDateFilter(range) },
                        label = {
                            Text(
                                text = range.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CinematicRed,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) CinematicRed else Color(0x33FFFFFF)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("filter_range_${range.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Content Area
            when (val state = uiState) {
                is AnalyticsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CinematicRed, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Aggregating watch session metrics...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                is AnalyticsUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (state.isTableMissing) AmberGold.copy(alpha = 0.6f) else CinematicRed.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.isTableMissing) Icons.Default.Info else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (state.isTableMissing) AmberGold else CinematicRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (state.isTableMissing) "Supabase Setup Required" else "Analytics Error",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            if (state.isTableMissing) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "To enable analytics, run the 'supabase_analytics_migration.sql' script in your Supabase SQL Editor. It creates the additive analytics tables, indexes, and aggregation functions.",
                                    color = AmberGold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { analyticsViewModel.refresh() },
                                colors = ButtonDefaults.buttonColors(containerColor = CinematicRed)
                            ) {
                                Text("Retry Connection")
                            }
                        }
                    }
                }

                is AnalyticsUiState.Empty -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Analytics Events Recorded Yet",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Analytics tracking is active. As viewers open the app, stream movies, and accumulate watch time, stats will appear here automatically.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is AnalyticsUiState.Success -> {
                    AnalyticsDashboardContent(
                        summary = state.summary,
                        searchQuery = searchQuery,
                        selectedSortOption = selectedSortOption,
                        onSearchChange = { analyticsViewModel.setSearchQuery(it) },
                        onSortChange = { analyticsViewModel.setSortOption(it) },
                        onMovieClick = { analyticsViewModel.selectMovieForDrilldown(it) }
                    )
                }
            }
        }

        // Movie Detail Drilldown Modal
        selectedDrilldownMovie?.let { movieItem ->
            MovieAnalyticsDrilldownDialog(
                movie = movieItem,
                onDismiss = { analyticsViewModel.selectMovieForDrilldown(null) }
            )
        }
    }
}

@Composable
private fun AnalyticsDashboardContent(
    summary: AdminAnalyticsSummary,
    searchQuery: String,
    selectedSortOption: MovieSortOption,
    onSearchChange: (String) -> Unit,
    onSortChange: (MovieSortOption) -> Unit,
    onMovieClick: (MovieAnalyticsItem) -> Unit
) {
    val filteredMovies = remember(summary.movieStats, searchQuery, selectedSortOption) {
        summary.movieStats
            .filter {
                searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
            }
            .let { list ->
                when (selectedSortOption) {
                    MovieSortOption.WATCH_TIME -> list.sortedByDescending { it.totalWatchSeconds }
                    MovieSortOption.PLAYS -> list.sortedByDescending { it.totalPlays }
                    MovieSortOption.UNIQUE_VIEWERS -> list.sortedByDescending { it.uniqueViewers }
                    MovieSortOption.COMPLETION_RATE -> list.sortedByDescending { it.completionRate }
                }
            }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Overview Metric Cards Grid
        item {
            Text(
                text = "AUDIENCE & PLAYBACK OVERVIEW",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: Installations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "TOTAL INSTALLS",
                    value = summary.totalInstallations.toString(),
                    subtitle = "Pseudonymous devices",
                    icon = Icons.Default.Devices,
                    accentColor = ElectricBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "ACTIVE TODAY",
                    value = summary.activeToday.toString(),
                    subtitle = "DAU (${summary.active7d} 7D / ${summary.active30d} 30D)",
                    icon = Icons.Default.Visibility,
                    accentColor = Color(0xFF69F0AE),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Plays & Watch Hours
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "TOTAL PLAYS",
                    value = summary.totalPlays.toString(),
                    subtitle = "Started sessions",
                    icon = Icons.Default.PlayArrow,
                    accentColor = AmberGold,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "TOTAL WATCH TIME",
                    value = "${String.format("%.1f", summary.totalWatchHours)} hrs",
                    subtitle = "Avg: ${summary.avgWatchTimeFormatted}",
                    icon = Icons.Default.AccessTime,
                    accentColor = CinematicRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. Explanatory Note (Distinguishing installs from humans & watch time rules)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Metrics represent pseudonymous app installations and genuine active ExoPlayer watch time. Time spent paused, buffering, or seeking forward is strictly excluded.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 3. Movie Performance Section Header + Filters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CATALOG PERFORMANCE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${filteredMovies.size} Movies",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Filter by title...", color = TextTertiary, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = CinematicRed,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analytics_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sort Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MovieSortOption.values().forEach { sortOpt ->
                    val isSelected = selectedSortOption == sortOpt
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSortChange(sortOpt) },
                        label = { Text(sortOpt.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold.copy(alpha = 0.2f),
                            selectedLabelColor = AmberGold,
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AmberGold else Color(0x22FFFFFF)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // 4. Movie Performance Cards List
        if (filteredMovies.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No movies matching '$searchQuery'" else "No playback recorded for this time range.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredMovies, key = { it.movieId }) { movieItem ->
                MoviePerformanceCard(
                    item = movieItem,
                    onClick = { onMovieClick(movieItem) }
                )
            }
        }

        // 5. Recent Activity Feed
        if (summary.recentEvents.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "RECENT ACTIVITY STREAM",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            items(summary.recentEvents.take(15), key = { it.id }) { event ->
                RecentActivityRow(event = event)
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextTertiary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MoviePerformanceCard(
    item: MovieAnalyticsItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("movie_analytics_card_${item.movieId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
            ) {
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = TextTertiary)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.totalPlays} plays",
                        color = AmberGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(" • ", color = TextTertiary, fontSize = 11.sp)
                    Text(
                        text = "${item.uniqueViewers} viewers",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(" • ", color = TextTertiary, fontSize = 11.sp)
                    Text(
                        text = item.totalWatchHoursFormatted,
                        color = Color(0xFF69F0AE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Completion Rate Bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { (item.completionRate / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = CinematicRed,
                        trackColor = Color(0x33FFFFFF),
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${item.completionRate}% comp",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentActivityRow(event: com.example.data.analytics.RecentActivityItem) {
    val (badgeText, badgeColor) = when (event.eventName) {
        "movie_started" -> Pair("STARTED", AmberGold)
        "movie_completed" -> Pair("COMPLETED", Color(0xFF69F0AE))
        "first_open" -> Pair("NEW INSTALL", ElectricBlue)
        "playback_error" -> Pair("ERROR", CinematicRed)
        "movie_progress" -> Pair("PROGRESS", Color.Gray)
        else -> Pair(event.eventName.uppercase().take(8), TextSecondary)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (event.movieTitle.isNotBlank()) event.movieTitle else "App Lifecycle",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Device: ${event.installationId}",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }

            event.playbackPositionSeconds?.let { pos ->
                Text(
                    text = "${pos / 60}m ${pos % 60}s",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MovieAnalyticsDrilldownDialog(
    movie: MovieAnalyticsItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Movie Analytics Drilldown", color = AmberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 85.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated)
                    ) {
                        if (movie.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = movie.coverUrl,
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(movie.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Movie ID: ${movie.movieId}", color = TextTertiary, fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Detail Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatDetailRow(label = "Total Plays:", value = "${movie.totalPlays} sessions")
                        StatDetailRow(label = "Unique Viewers:", value = "${movie.uniqueViewers} devices")
                        StatDetailRow(label = "Accumulated Watch Time:", value = movie.totalWatchHoursFormatted)
                        StatDetailRow(label = "Average Viewing Duration:", value = movie.avgWatchMinutesFormatted)
                        StatDetailRow(label = "Completion Rate (>=90%):", value = "${movie.completionRate}%")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun StatDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
