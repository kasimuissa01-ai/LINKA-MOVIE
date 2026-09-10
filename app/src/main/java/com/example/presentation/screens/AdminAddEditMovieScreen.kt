package com.example.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.TmdbMovieResult
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.AdminViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAddEditMovieScreen(
    adminViewModel: AdminViewModel,
    existingMovie: Movie?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // TMDB Search State
    var tmdbQuery by remember { mutableStateOf("") }
    val tmdbResults by adminViewModel.tmdbSearchResults.collectAsState()
    val isSearchingTmdb by adminViewModel.isSearchingTmdb.collectAsState()
    var autoFillNotification by remember { mutableStateOf<String?>(null) }

    // Form fields
    var title by remember { mutableStateOf(existingMovie?.title ?: "") }
    var description by remember { mutableStateOf(existingMovie?.description ?: "") }
    val allGenres = listOf(
        "Action", "Sci-Fi", "Adventure", "Thriller", "Drama",
        "Horror", "Comedy", "Crime", "Fantasy", "Animation", "Cyberpunk"
    )
    var selectedGenres by remember {
        mutableStateOf(existingMovie?.genres ?: listOf("Action", "Sci-Fi"))
    }
    var coverUrl by remember {
        mutableStateOf(
            existingMovie?.coverUrl
                ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80"
        )
    }
    var streamUrl by remember {
        mutableStateOf(
            existingMovie?.videoStreamUrl
                ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )
    }
    var isFeaturedOnCarousel by remember {
        mutableStateOf(existingMovie?.isFeatured ?: true)
    }
    var fileSizeMb by remember { mutableStateOf(existingMovie?.fileSizeMb?.toString() ?: "480") }
    var releaseYear by remember { mutableStateOf(existingMovie?.releaseYear?.toString() ?: "2024") }
    var rating by remember { mutableStateOf(existingMovie?.rating?.toString() ?: "8.2") }

    // Video Gallery Picker State
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoFileName by remember { mutableStateOf<String?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            var displayName = "Selected_Video.mp4"
            var calculatedSizeMb = 0L

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                        if (sizeIndex != -1) {
                            val sizeBytes = cursor.getLong(sizeIndex)
                            calculatedSizeMb = (sizeBytes / (1024 * 1024)).coerceAtLeast(1L)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore cursor errors
            }

            selectedVideoFileName = displayName
            if (calculatedSizeMb > 0) {
                fileSizeMb = calculatedSizeMb.toString()
            }
            streamUrl = uri.toString()
        }
    }

    val uploadProgressState by adminViewModel.uploadState.collectAsState()
    val scrollState = rememberScrollState()

    fun applyTmdbMovie(tmdb: TmdbMovieResult) {
        focusManager.clearFocus()
        title = tmdb.title
        description = tmdb.overview
        coverUrl = tmdb.posterUrl
        releaseYear = tmdb.releaseYear.toString()
        rating = tmdb.rating.toString()
        if (tmdb.genres.isNotEmpty()) {
            selectedGenres = tmdb.genres
        }
        adminViewModel.clearTmdbSearch()
        tmdbQuery = ""
        autoFillNotification = "Auto-filled details for '${tmdb.title}'"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("admin_add_movie_screen")
    ) {
        // Top Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    adminViewModel.resetUploadState()
                    adminViewModel.clearTmdbSearch()
                    onBackClick()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceDark)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (existingMovie != null) "Edit Movie" else "Upload Movie",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cloudflare R2 Storage & TMDB Meta",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // TMDB Auto-fill Search Bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TMDB Auto-Fill Search",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tmdbQuery,
                    onValueChange = {
                        tmdbQuery = it
                        adminViewModel.searchTmdb(it)
                    },
                    placeholder = { Text("Search movie (e.g. Dune, Oppenheimer, Inception...)", color = TextTertiary, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AmberGold)
                    },
                    trailingIcon = {
                        if (isSearchingTmdb) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AmberGold,
                                strokeWidth = 2.dp
                            )
                        } else if (tmdbQuery.isNotBlank()) {
                            IconButton(onClick = {
                                tmdbQuery = ""
                                adminViewModel.clearTmdbSearch()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = SurfaceElevated,
                        unfocusedContainerColor = SurfaceElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tmdb_search_input")
                )

                // TMDB Live Results Dropdown
                if (tmdbResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap to Auto-Fill details:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tmdbResults.take(4).forEach { movie ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceElevated)
                                    .clickable { applyTmdbMovie(movie) }
                                    .padding(8.dp)
                            ) {
                                AsyncImage(
                                    model = movie.posterUrl,
                                    contentDescription = movie.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = movie.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = AmberGold,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${movie.rating} • ${movie.releaseYear} • ${movie.genres.joinToString()}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Auto Fill",
                                    tint = AmberGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Notification when auto-filled
                autoFillNotification?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AmberGold.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = msg, color = AmberGold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Poster Preview Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 75.dp, height = 110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title.ifBlank { "Movie Title Preview" },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$releaseYear • Rating: $rating • ${fileSizeMb}MB",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isFeaturedOnCarousel) {
                        Surface(
                            color = CinematicRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "HERO CAROUSEL ENABLED",
                                color = CinematicRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Movie Title Field
        Text(text = "Movie Title", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("e.g. Dune: Part Two", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, tint = CinematicRed) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinematicRed,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_movie_title")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Genres Multi-Selection Chips
        Text(text = "Genres & Categories", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            allGenres.forEach { genre ->
                val isSelected = selectedGenres.contains(genre)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedGenres = if (isSelected) {
                            if (selectedGenres.size > 1) selectedGenres - genre else selectedGenres
                        } else {
                            selectedGenres + genre
                        }
                    },
                    label = {
                        Text(
                            text = genre,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CinematicRed,
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceDark,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Description / Synopsis
        Text(text = "Description & Synopsis", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = { Text("Enter movie overview and synopsis...", color = TextTertiary) },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinematicRed,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_movie_description")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Video File Upload Card (Device Gallery Picker)
        Text(
            text = "Video File (Upload from Gallery)",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (selectedVideoUri != null) ElectricBlue else Color(0x33FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (selectedVideoUri != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ElectricBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoFile,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = selectedVideoFileName ?: "Video file selected",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ready to upload • ${fileSizeMb} MB",
                                    color = ElectricBlue,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_change_gallery_video")
                        ) {
                            Text("Change", fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select video from your gallery",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "MP4, MKV, or WebM video file",
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_pick_gallery_video")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Video", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0x1AFFFFFF))
                Spacer(modifier = Modifier.height(10.dp))

                // Direct Stream URL / Custom fallback
                Text(
                    text = "Video Stream Source / Path",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    placeholder = { Text("Gallery URI or R2 stream link", color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = ElectricBlue) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = Color(0x22FFFFFF),
                        focusedContainerColor = ObsidianBlack,
                        unfocusedContainerColor = ObsidianBlack,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_video_stream_url")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Cover Poster URL
        Text(text = "Cover Poster Image URL", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = coverUrl,
            onValueChange = { coverUrl = it },
            placeholder = { Text("https://image.tmdb.org/...", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = AmberGold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberGold,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_cover_url")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Show on Carousel Toggle Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = null,
                        tint = CinematicRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Featured Hero Carousel",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Show on main home screen rotating banner",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = isFeaturedOnCarousel,
                    onCheckedChange = { isFeaturedOnCarousel = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CinematicRed,
                        uncheckedTrackColor = SurfaceElevated
                    ),
                    modifier = Modifier.testTag("toggle_carousel_feature")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Year, Rating & File Size Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Release Year
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Release Year", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = releaseYear,
                    onValueChange = { releaseYear = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CinematicRed,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Rating
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "TMDB Rating", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = rating,
                    onValueChange = { rating = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // File Size MB
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Size (MB)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = fileSizeMb,
                    onValueChange = { fileSizeMb = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Upload Progress or Success UI
        if (uploadProgressState.isUploading) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Uploading to Cloudflare R2...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${(uploadProgressState.overallProgress * 100).toInt()}%",
                            color = CinematicRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { uploadProgressState.overallProgress },
                        color = CinematicRed,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uploadProgressState.statusMessage,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uploadProgressState.isCompleted) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3D2B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Upload Complete!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(uploadProgressState.statusMessage, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action Buttons
        Button(
            onClick = {
                val size = fileSizeMb.toLongOrNull() ?: 450L
                val year = releaseYear.toIntOrNull() ?: 2024
                val rate = rating.toDoubleOrNull() ?: 8.0

                if (existingMovie != null) {
                    val updated = existingMovie.copy(
                        title = title,
                        description = description,
                        genres = selectedGenres,
                        coverUrl = coverUrl,
                        videoStreamUrl = streamUrl,
                        durationMinutes = 115,
                        fileSizeMb = size,
                        releaseYear = year,
                        rating = rate,
                        isFeatured = isFeaturedOnCarousel
                    )
                    adminViewModel.updateMovie(updated)
                    onBackClick()
                } else {
                    adminViewModel.addMovieWithMultipartUpload(
                        title = title.ifBlank { "Untitled Movie" },
                        description = description.ifBlank { "A cinematic release." },
                        genres = selectedGenres,
                        coverUrl = coverUrl,
                        fileSizeMb = size,
                        streamUrl = streamUrl,
                        releaseYear = year,
                        rating = rate,
                        isFeatured = isFeaturedOnCarousel
                    )
                }
            },
            enabled = !uploadProgressState.isUploading,
            colors = ButtonDefaults.buttonColors(
                containerColor = CinematicRed,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_upload_movie_button")
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (existingMovie != null) "Update Movie Metadata" else "Publish & Upload to Cloudflare R2",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
