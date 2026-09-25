package com.example.presentation.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.AdminViewModel
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.UUID

@Composable
fun AdminEpisodeEditDialog(
    initialEpisode: Episode?,
    movieId: String,
    nextDefaultSeason: Int = 1,
    nextDefaultEpisodeNumber: Int = 1,
    onDismiss: () -> Unit,
    onSave: (Episode, Uri?) -> Unit
) {
    val context = LocalContext.current
    var seasonNumber by remember {
        mutableStateOf(initialEpisode?.seasonNumber?.toString() ?: nextDefaultSeason.toString())
    }
    var episodeNumber by remember {
        mutableStateOf(initialEpisode?.episodeNumber?.toString() ?: nextDefaultEpisodeNumber.toString())
    }
    var title by remember {
        mutableStateOf(initialEpisode?.title ?: "Episode $nextDefaultEpisodeNumber")
    }
    var description by remember {
        mutableStateOf(initialEpisode?.description ?: "")
    }
    var streamUrl by remember {
        mutableStateOf(initialEpisode?.videoStreamUrl ?: initialEpisode?.videoKey ?: "")
    }
    var durationMinutes by remember {
        mutableStateOf(initialEpisode?.durationMinutes?.toString() ?: "45")
    }
    var fileSizeMb by remember {
        mutableStateOf(initialEpisode?.fileSizeMb?.toString() ?: "250")
    }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoFileName by remember { mutableStateOf<String?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { }

            var displayName = "Episode_Video.mp4"
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
            } catch (e: Exception) { }

            selectedVideoFileName = displayName
            if (calculatedSizeMb > 0) {
                fileSizeMb = calculatedSizeMb.toString()
            }
            streamUrl = uri.toString()
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SurfaceElevated,
        unfocusedContainerColor = SurfaceElevated,
        focusedBorderColor = CinematicRed,
        unfocusedBorderColor = Color(0x33FFFFFF),
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialEpisode != null) "Edit Episode" else "Add New Episode",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Season & Episode number row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = seasonNumber,
                        onValueChange = { seasonNumber = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Season #") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = episodeNumber,
                        onValueChange = { episodeNumber = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Episode #") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Episode Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Episode Title") },
                    placeholder = { Text("e.g. Pilot, The Awakening...") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Episode Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Episode Synopsis (Optional)") },
                    placeholder = { Text("Brief overview of what happens in this episode...") },
                    minLines = 2,
                    maxLines = 4,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Duration & File Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Duration (mins)") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fileSizeMb,
                        onValueChange = { fileSizeMb = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Est. Size (MB)") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Episode Video File or Stream URL
                Text(
                    text = "Episode Video Source",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        videoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedVideoFileName ?: "Pick Episode Video from Gallery/Files",
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = {
                        streamUrl = it
                        selectedVideoUri = null
                        selectedVideoFileName = null
                    },
                    label = { Text("Or Enter R2 Key / Direct Video Stream URL") },
                    placeholder = { Text("e.g. videos/show_s1e1.mp4 or https://...") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            val sNum = seasonNumber.toIntOrNull() ?: nextDefaultSeason
                            val eNum = episodeNumber.toIntOrNull() ?: nextDefaultEpisodeNumber
                            val dur = durationMinutes.toIntOrNull() ?: 45
                            val size = fileSizeMb.toLongOrNull() ?: 250L
                            val epId = initialEpisode?.id ?: "${movieId}_s${sNum}_e${eNum}_${UUID.randomUUID().toString().take(4)}"

                            val createdEpisode = Episode(
                                id = epId,
                                movieId = movieId,
                                episodeNumber = eNum,
                                seasonNumber = sNum,
                                title = title.ifBlank { "Episode $eNum" },
                                description = description,
                                videoKey = if (selectedVideoUri == null && !streamUrl.startsWith("http")) streamUrl else initialEpisode?.videoKey ?: "",
                                videoStreamUrl = if (selectedVideoUri == null) streamUrl else "",
                                durationMinutes = dur,
                                fileSizeMb = size
                            )
                            onSave(createdEpisode, selectedVideoUri)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (initialEpisode != null) "Update Episode" else "Add Episode",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminManageEpisodesModal(
    movie: Movie,
    adminViewModel: AdminViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uploadState by adminViewModel.uploadState.collectAsState()
    val allMovies by adminViewModel.movies.collectAsState()
    val liveMovie = remember(allMovies, movie.id) {
        allMovies.find { it.id == movie.id } ?: movie
    }

    var episodeToEdit by remember { mutableStateOf<Episode?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var episodeToDelete by remember { mutableStateOf<Episode?>(null) }

    val seasons = remember(liveMovie.episodes) {
        val list = liveMovie.episodes.map { it.seasonNumber }.distinct().sorted()
        if (list.isEmpty()) listOf(1) else list
    }
    var selectedSeason by remember(liveMovie.id) {
        mutableStateOf(seasons.firstOrNull() ?: 1)
    }

    val episodesInSeason = remember(liveMovie.episodes, selectedSeason) {
        liveMovie.episodes.filter { it.seasonNumber == selectedSeason }.sortedBy { it.episodeNumber }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage Episodes",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${liveMovie.title} • ${liveMovie.episodes.size} Total Episodes",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Upload progress if actively uploading an episode
                if (uploadState.isUploading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uploadState.statusMessage,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${(uploadState.overallProgress * 100).toInt()}%",
                                    color = CinematicRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { uploadState.overallProgress },
                                color = CinematicRed,
                                trackColor = Color(0x33FFFFFF),
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Season Selector + Add Season Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(seasons) { s ->
                            val isSelected = s == selectedSeason
                            Surface(
                                color = if (isSelected) CinematicRed else SurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { selectedSeason = s }
                            ) {
                                Text(
                                    text = "Season $s",
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val nextSeason = (seasons.maxOrNull() ?: 1) + 1
                            selectedSeason = nextSeason
                            showAddDialog = true
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Season", color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Episodes in selected season
                if (episodesInSeason.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(SurfaceElevated, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No episodes yet in Season $selectedSeason",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Episode 1 to Season $selectedSeason", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 340.dp)
                    ) {
                        items(episodesInSeason, key = { it.id }) { ep ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = Color(0x33E50914),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "E${ep.episodeNumber}",
                                            color = CinematicRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ep.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${ep.durationMinutes} min • ${ep.fileSizeMb} MB" +
                                                    if (ep.videoKey.isNotBlank()) " • Key: ${ep.videoKey.takeLast(16)}" else "",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { episodeToEdit = ep },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = { episodeToDelete = ep },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CinematicRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Add Episode button
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val nextEpNum = (episodesInSeason.maxOfOrNull { it.episodeNumber } ?: 0) + 1
                    Text(
                        text = "Add Episode $nextEpNum to Season $selectedSeason",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Add Episode Dialog
    if (showAddDialog) {
        val nextEpNum = (episodesInSeason.maxOfOrNull { it.episodeNumber } ?: 0) + 1
        AdminEpisodeEditDialog(
            initialEpisode = null,
            movieId = liveMovie.id,
            nextDefaultSeason = selectedSeason,
            nextDefaultEpisodeNumber = nextEpNum,
            onDismiss = { showAddDialog = false },
            onSave = { ep, videoUri ->
                showAddDialog = false
                adminViewModel.addOrUpdateEpisode(context, liveMovie, ep, videoUri)
            }
        )
    }

    // Edit Episode Dialog
    episodeToEdit?.let { ep ->
        AdminEpisodeEditDialog(
            initialEpisode = ep,
            movieId = liveMovie.id,
            onDismiss = { episodeToEdit = null },
            onSave = { updatedEp, videoUri ->
                episodeToEdit = null
                adminViewModel.addOrUpdateEpisode(context, liveMovie, updatedEp, videoUri)
            }
        )
    }

    // Delete Confirmation Dialog
    episodeToDelete?.let { ep ->
        AlertDialog(
            onDismissRequest = { episodeToDelete = null },
            title = { Text("Delete Episode", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete Season ${ep.seasonNumber} Episode ${ep.episodeNumber} ('${ep.title}') from '${liveMovie.title}'?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteEpisode(liveMovie, ep.id)
                        episodeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { episodeToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}
