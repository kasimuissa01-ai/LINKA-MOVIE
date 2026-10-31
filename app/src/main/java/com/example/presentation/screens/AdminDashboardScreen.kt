package com.example.presentation.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.AdminViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    onBackClick: () -> Unit,
    onAddMovieClick: () -> Unit,
    onEditMovieClick: (Movie) -> Unit,
    onAnalyticsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val movies by adminViewModel.movies.collectAsState()
    val uploadState by adminViewModel.uploadState.collectAsState()
    val isRefreshingCatalog by adminViewModel.isRefreshingCatalog.collectAsState()
    val userSession by adminViewModel.userSession.collectAsState()
    val currentPhone = userSession.phoneNumber.filter { it.isDigit() }
    val isMainAdmin = com.example.data.repository.AuthRepository.isMainAdmin(currentPhone) || 
                      currentPhone == "255696102700" || 
                      currentPhone == "255716123283"
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var movieToDelete by remember { mutableStateOf<Movie?>(null) }
    var syncBannerMessage by remember { mutableStateOf<String?>(null) }
    var movieForCoverChange by remember { mutableStateOf<Movie?>(null) }
    var managingMovieEpisodes by remember { mutableStateOf<Movie?>(null) }
    var showManageAdminsDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        adminViewModel.refreshMovies()
    }

    val coverChangeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && movieForCoverChange != null) {
            val target = movieForCoverChange!!
            adminViewModel.updateMovieCoverDirect(context, target, uri) { success, msg ->
                syncBannerMessage = if (success) "Real cover successfully uploaded to Cloudflare R2 and updated in Supabase for '${target.title}'!"
                else "Failed to update cover: $msg"
            }
        }
    }

    val filteredMovies = movies.filter {
        searchQuery.isBlank() ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.genres.any { g -> g.contains(searchQuery, ignoreCase = true) }
    }

    val totalSizeMb = movies.sumOf { it.fileSizeMb }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Row 1: Back Button & Header Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceDark)
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
                            text = "Admin Studio",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cloudflare R2 & Supabase Backend",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Modern Scrollable Action Bar across top
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        if (isMainAdmin) {
                            Button(
                                onClick = { showManageAdminsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("admin_manage_admins_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Admins",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    item {
                        // Analytics Button
                        Button(
                            onClick = onAnalyticsClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("admin_analytics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = com.example.ui.theme.AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Analytics",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        // Refresh Catalog Button
                        Button(
                            onClick = {
                                adminViewModel.refreshMovies { count ->
                                    syncBannerMessage = "Movie catalog refreshed: $count movies loaded."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            enabled = !isRefreshingCatalog,
                            modifier = Modifier.testTag("admin_refresh_catalog_button")
                        ) {
                            if (isRefreshingCatalog) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRefreshingCatalog) "Syncing..." else "Refresh",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        // Sync & Repair R2 URLs button
                        Button(
                            onClick = {
                                adminViewModel.repairAndSyncAllMoviesToR2 { count ->
                                    syncBannerMessage = "Successfully repaired and updated $count movies with R2 URLs in your Supabase table!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uploadState.isUploading) SurfaceElevated else CinematicRed
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            enabled = !uploadState.isUploading,
                            modifier = Modifier.testTag("admin_sync_r2_button")
                        ) {
                            if (uploadState.isUploading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", color = Color.White, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync R2 URLs", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMovieClick,
                containerColor = CinematicRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("admin_add_movie_fab")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Upload")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Movie", fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = ObsidianBlack,
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_dashboard_screen")
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Sync Status Notification Banner
            syncBannerMessage?.let { msg ->
                item {
                    Surface(
                        color = Color(0x224CAF50),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x664CAF50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = msg,
                                color = Color(0xFF81C784),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { syncBannerMessage = null }) {
                                Text("OK", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Metrics Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("TOTAL MOVIES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${movies.size}", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("R2 STORAGE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${String.format("%.1f", totalSizeMb / 1024.0)} GB", color = CinematicRed, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter movies by title or tag...", color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
                        .testTag("admin_search_input")
                )
            }
                if (filteredMovies.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                if (isRefreshingCatalog) {
                                    CircularProgressIndicator(
                                        color = CinematicRed,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Syncing movie catalog from Supabase & Cloudflare R2...",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No movies found matching '$searchQuery'" else "No movies currently cached in studio",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap refresh to pull all live movies and seasons directly from your database",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            adminViewModel.refreshMovies { count ->
                                                syncBannerMessage = "Loaded $count movies from database."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sync Movie Catalog")
                                    }
                                }
                            }
                        }
                    }
                }

                items(filteredMovies, key = { it.id }) { movie ->
                    val canManage = adminViewModel.canModifyMovie(movie, context)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_movie_card_${movie.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 60.dp, height = 80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .clickable {
                                        if (canManage) {
                                            movieForCoverChange = movie
                                            coverChangeLauncher.launch(
                                                androidx.activity.result.PickVisualMediaRequest(
                                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        } else {
                                            syncBannerMessage = "Read-Only: Sub-admins can only change covers for movies they uploaded."
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = movie.coverUrl,
                                    contentDescription = movie.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = movie.title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "R2 Key: ${movie.videoKey}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = SurfaceElevated,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${movie.fileSizeMb} MB",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0x33E50914),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = movie.genres.firstOrNull() ?: "Cinema",
                                            color = CinematicRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (!isMainAdmin) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (canManage) {
                                            Surface(
                                                color = Color(0x3300C853),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Your Upload",
                                                    color = Color(0xFF69F0AE),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Surface(
                                                color = Color(0x33757575),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Read-Only",
                                                    color = Color(0xFFB0BEC5),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (movie.episodes.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0x3300C853),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable {
                                                managingMovieEpisodes = movie
                                            }
                                        ) {
                                            Text(
                                                text = "Series: ${movie.episodes.size} eps",
                                                color = Color(0xFF69F0AE),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (movie.isSwahiliTranslated) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0x33FFB300),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Imetafsiriwa",
                                                color = Color(0xFFFFB300),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (movie.country.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0x332196F3),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = movie.country,
                                                color = Color(0xFF64B5F6),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (movie.isTrending) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0x33FFD700),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "★ Trending",
                                                color = AmberGold,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (canManage) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // One-tap Trending toggle switch / icon button
                                    IconButton(
                                        onClick = {
                                            adminViewModel.toggleMovieTrending(movie, context) { success, msg ->
                                                syncBannerMessage = msg
                                            }
                                        },
                                        modifier = Modifier.testTag("admin_trending_${movie.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = if (movie.isTrending) "Remove from Trending" else "Add to Trending",
                                            tint = if (movie.isTrending) AmberGold else Color(0xFF616161)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            managingMovieEpisodes = movie
                                        },
                                        modifier = Modifier.testTag("admin_episodes_${movie.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.VideoLibrary,
                                            contentDescription = "Manage Episodes",
                                            tint = if (movie.episodes.isNotEmpty()) Color(0xFF69F0AE) else TextSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            movieForCoverChange = movie
                                            coverChangeLauncher.launch(
                                                androidx.activity.result.PickVisualMediaRequest(
                                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        },
                                        modifier = Modifier.testTag("admin_change_cover_${movie.id}")
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = "Change Cover", tint = com.example.ui.theme.ElectricBlue)
                                    }
                                    IconButton(
                                        onClick = { onEditMovieClick(movie) },
                                        modifier = Modifier.testTag("admin_edit_${movie.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
                                    }
                                    IconButton(
                                        onClick = { movieToDelete = movie },
                                        modifier = Modifier.testTag("admin_delete_${movie.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CinematicRed)
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            syncBannerMessage = "Read-Only: Sub-admins can view the catalog, but can only edit or delete movies they uploaded themselves."
                                        },
                                        modifier = Modifier.testTag("admin_locked_${movie.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Read-Only Movie",
                                            tint = TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // Manage Episodes Modal Dialog
    managingMovieEpisodes?.let { selectedMovie ->
        // Keep synced with latest version in movies state
        val liveMovie = movies.find { it.id == selectedMovie.id } ?: selectedMovie
        AdminManageEpisodesModal(
            movie = liveMovie,
            adminViewModel = adminViewModel,
            onDismiss = { managingMovieEpisodes = null }
        )
    }

    // Delete Confirmation Dialog
    movieToDelete?.let { movie ->
        AlertDialog(
            onDismissRequest = { movieToDelete = null },
            title = { Text("Delete Movie", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete '${movie.title}'? This action will permanently remove the metadata from the catalog and purge the binary object '${movie.videoKey}' from Cloudflare R2 storage.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteMovie(movie.id, context) { success, msg ->
                            syncBannerMessage = msg
                        }
                        movieToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { movieToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    if (showManageAdminsDialog) {
        ManageAdminsDialog(
            adminViewModel = adminViewModel,
            context = context,
            onDismiss = { showManageAdminsDialog = false }
        )
    }
}

@Composable
fun ManageAdminsDialog(
    adminViewModel: AdminViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    var newAdminPhone by remember { mutableStateOf("") }
    var subAdmins by remember { mutableStateOf(adminViewModel.getSubAdmins(context)) }
    var isOperating by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        adminViewModel.refreshMovies {
            subAdmins = adminViewModel.getSubAdmins(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Sub-Admins", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Main Admins (255696102700 & 255716123283) have full authority. Added sub-admins are synced in Firestore, can upload movies, and can delete only movies they uploaded themselves.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                statusFeedback?.let { msg ->
                    Surface(
                        color = Color(0x3300C853),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFF69F0AE),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = newAdminPhone,
                    onValueChange = { newAdminPhone = it },
                    label = { Text("Enter Sub-Admin Phone Number", color = TextSecondary) },
                    placeholder = { Text("e.g. 255XXXXXXXXX", color = TextTertiary) },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CinematicRed,
                        unfocusedBorderColor = SurfaceElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("sub_admin_phone_input")
                )

                Button(
                    onClick = {
                        val digits = newAdminPhone.filter { it.isDigit() }
                        if (digits.isNotBlank()) {
                            isOperating = true
                            statusFeedback = null
                            adminViewModel.addSubAdmin(context, digits) { success ->
                                isOperating = false
                                if (success) {
                                    subAdmins = adminViewModel.getSubAdmins(context)
                                    statusFeedback = "Sub-admin $digits registered in Firestore!"
                                    newAdminPhone = ""
                                } else {
                                    statusFeedback = "Failed to register sub-admin in Firestore."
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    enabled = !isOperating && newAdminPhone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("grant_admin_rights_button")
                ) {
                    if (isOperating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Updating Firestore...", fontSize = 13.sp)
                    } else {
                        Text("Grant Sub-Admin Rights", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Current Sub-Admins in Firestore:", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                if (subAdmins.isEmpty()) {
                    Text(text = "No sub-admins added yet.", color = TextSecondary, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(140.dp)
                    ) {
                        items(subAdmins) { phone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = phone, color = Color.White, fontSize = 13.sp)
                                TextButton(
                                    onClick = {
                                        isOperating = true
                                        statusFeedback = null
                                        adminViewModel.removeSubAdmin(context, phone) { success ->
                                            isOperating = false
                                            if (success) {
                                                subAdmins = adminViewModel.getSubAdmins(context)
                                                statusFeedback = "Sub-admin $phone revoked."
                                            } else {
                                                statusFeedback = "Failed to revoke sub-admin."
                                            }
                                        }
                                    },
                                    enabled = !isOperating
                                ) {
                                    Text("Revoke", color = CinematicRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
            ) {
                Text("Close", color = Color.White)
            }
        },
        containerColor = ObsidianBlack
    )
}
