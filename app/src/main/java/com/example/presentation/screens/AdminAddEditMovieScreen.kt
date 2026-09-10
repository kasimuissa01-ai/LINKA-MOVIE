package com.example.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.AdminViewModel
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
    var title by remember { mutableStateOf(existingMovie?.title ?: "") }
    var description by remember { mutableStateOf(existingMovie?.description ?: "") }
    val availableGenres = listOf("Sci-Fi", "Action", "Thriller", "Adventure", "Drama", "Cyberpunk", "Fantasy", "Nature")
    var selectedGenres by remember {
        mutableStateOf(existingMovie?.genres ?: listOf("Sci-Fi", "Action"))
    }
    var coverUrl by remember {
        mutableStateOf(
            existingMovie?.coverUrl
                ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80"
        )
    }
    var fileSizeMb by remember { mutableStateOf(existingMovie?.fileSizeMb?.toString() ?: "1200") }
    var releaseYear by remember { mutableStateOf(existingMovie?.releaseYear?.toString() ?: "2026") }
    var rating by remember { mutableStateOf(existingMovie?.rating?.toString() ?: "4.8") }

    val uploadProgressState by adminViewModel.uploadState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("admin_add_movie_screen")
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    adminViewModel.resetUploadState()
                    onBackClick()
                },
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

            Text(
                text = if (existingMovie != null) "Edit Movie" else "Upload to Cloudflare R2",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Cover Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Movie, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title Input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Movie Title") },
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
                .testTag("admin_input_title")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description Input
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Synopsis / Description") },
            maxLines = 4,
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
                .testTag("admin_input_description")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cover URL Input
        OutlinedTextField(
            value = coverUrl,
            onValueChange = { coverUrl = it },
            label = { Text("Cover Poster Image URL") },
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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Genre multi-select tags
        Text(
            text = "Select Genres",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            availableGenres.forEach { genre ->
                val isSelected = selectedGenres.contains(genre)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedGenres = if (isSelected) {
                            selectedGenres - genre
                        } else {
                            selectedGenres + genre
                        }
                    },
                    label = { Text(genre, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceDark,
                        labelColor = TextSecondary,
                        selectedContainerColor = CinematicRed,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File Size & Release Year Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = fileSizeMb,
                onValueChange = { fileSizeMb = it },
                label = { Text("Size (MB)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = CinematicRed,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = releaseYear,
                onValueChange = { releaseYear = it },
                label = { Text("Year") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = CinematicRed,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = rating,
                onValueChange = { rating = it },
                label = { Text("Rating") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = CinematicRed,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Multipart Upload Card & Status
        if (uploadProgressState.isUploading || uploadProgressState.isCompleted || uploadProgressState.error != null) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uploadProgressState.isCompleted) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            } else if (uploadProgressState.error != null) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CinematicRed)
                            } else {
                                CircularProgressIndicator(
                                    color = CinematicRed,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (uploadProgressState.isCompleted) "Upload Complete!"
                                else if (uploadProgressState.error != null) "Upload Error"
                                else "R2 Multipart Upload Active",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "${(uploadProgressState.overallProgress * 100).toInt()}%",
                            color = CinematicRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = uploadProgressState.overallProgress,
                        color = CinematicRed,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uploadProgressState.statusMessage,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    if (uploadProgressState.isUploading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { adminViewModel.cancelUpload() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CinematicRed)
                        ) {
                            Text("Cancel Upload")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action Buttons
        if (existingMovie != null) {
            Button(
                onClick = {
                    val updated = existingMovie.copy(
                        title = title,
                        description = description,
                        genres = selectedGenres,
                        coverUrl = coverUrl,
                        fileSizeMb = fileSizeMb.toLongOrNull() ?: 1200L,
                        releaseYear = releaseYear.toIntOrNull() ?: 2026,
                        rating = rating.toDoubleOrNull() ?: 4.8
                    )
                    adminViewModel.updateMovie(updated)
                    onBackClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_save_changes_button")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    adminViewModel.addMovieWithMultipartUpload(
                        title = title.ifBlank { "Untitled Sci-Fi Epic" },
                        description = description.ifBlank { "A thrilling new production streaming exclusively on MovieRoom." },
                        genres = selectedGenres.ifEmpty { listOf("Sci-Fi") },
                        coverUrl = coverUrl,
                        fileSizeMb = fileSizeMb.toLongOrNull() ?: 1400L,
                        releaseYear = releaseYear.toIntOrNull() ?: 2026,
                        rating = rating.toDoubleOrNull() ?: 4.8
                    )
                },
                enabled = !uploadProgressState.isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_submit_upload_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uploadProgressState.isUploading) "Uploading Chunks..." else "Start Multipart R2 Upload",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
