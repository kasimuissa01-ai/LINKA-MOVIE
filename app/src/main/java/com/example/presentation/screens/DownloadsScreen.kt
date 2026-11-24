package com.example.presentation.screens

import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.WatchHistoryItem
import com.example.data.local.WatchHistoryManager
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.DownloadViewModel
import com.example.presentation.viewmodel.MovieViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

data class DownloadedGroup(
    val movieId: String,
    val movieTitle: String,
    val coverUrl: String,
    val isSeries: Boolean,
    val items: List<DownloadItem>
) {
    val totalBytes: Long
        get() = items.sumOf { it.downloadedBytes.coerceAtLeast(it.totalBytes) }

    val formattedSize: String
        get() {
            val bytes = totalBytes
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(java.util.Locale.US, "%.1fGB", gb)
                mb >= 1.0 -> String.format(java.util.Locale.US, "%.1fMB", mb)
                else -> String.format(java.util.Locale.US, "%.1fKB", kb)
            }
        }
}

@Composable
fun DownloadsScreen(
    downloadViewModel: DownloadViewModel,
    movieViewModel: MovieViewModel,
    onPlayMovie: (Movie, Long?, String?) -> Unit,
    onBrowseCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by downloadViewModel.downloads.collectAsState()
    val movieState by movieViewModel.uiState.collectAsState()
    val userSession by downloadViewModel.userSession.collectAsState()
    val context = LocalContext.current

    val watchHistoryManager = remember { WatchHistoryManager.getInstance(context) }
    LaunchedEffect(userSession.uid) {
        watchHistoryManager.setUser(userSession.uid)
        downloadViewModel.scanAndVerifyOfflineStorage(context)
    }
    val watchHistory by watchHistoryManager.historyFlow.collectAsState()

    val totalStorageBytes = remember(downloads) {
        downloadViewModel.getTotalStorageUsedBytes(context, downloads)
    }
    val formattedStorage = remember(totalStorageBytes) {
        downloadViewModel.formatStorageSize(totalStorageBytes)
    }

    val availableBytes = remember {
        com.example.data.download.OfflineStoragePaths.getAvailableInternalStorageBytes(context)
    }
    val formattedAvailable = remember(availableBytes) {
        downloadViewModel.formatStorageSize(availableBytes)
    }

    val completedCount = remember(downloads) {
        downloads.count { isDownloadVerifiedComplete(it, context) }
    }

    var showClearStorageDialog by remember { mutableStateOf(false) }
    var selectedSeriesGroup by remember { mutableStateOf<DownloadedGroup?>(null) }

    val watchHistoryMap = remember(watchHistory) {
        watchHistory.associateBy { WatchHistoryManager.buildPositionKey(it.movieId, it.episodeId) }
    }

    val groupedDownloads = remember(downloads) {
        val result = mutableListOf<DownloadedGroup>()
        val seriesItems = downloads.filter { !it.episodeId.isNullOrBlank() }
        val seriesByMovie = seriesItems.groupBy { it.movieId }
        val movieItems = downloads.filter { it.episodeId.isNullOrBlank() }

        movieItems.forEach { item ->
            result.add(
                DownloadedGroup(
                    movieId = item.movieId,
                    movieTitle = item.movieTitle,
                    coverUrl = item.coverUrl,
                    isSeries = false,
                    items = listOf(item)
                )
            )
        }

        seriesByMovie.forEach { (movieId, epList) ->
            val first = epList.first()
            result.add(
                DownloadedGroup(
                    movieId = movieId,
                    movieTitle = first.movieTitle,
                    coverUrl = first.coverUrl,
                    isSeries = true,
                    items = epList.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 1 }))
                )
            )
        }
        result
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .testTag("downloads_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Offline Downloads",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                if (downloads.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearStorageDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("downloads_manage_button")
                    ) {
                        Text(
                            text = "Manage",
                            color = CinematicRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Top Watch History Row (Hidden if user has no watch history)
                if (watchHistory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Watch History",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "All >",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(watchHistory, key = { WatchHistoryManager.buildPositionKey(it.movieId, it.episodeId) }) { historyItem ->
                                    WatchHistoryCard(
                                        item = historyItem,
                                        onClick = {
                                            val matchingDownload = downloads.find { dl ->
                                                dl.movieId == historyItem.movieId &&
                                                    isDownloadVerifiedComplete(dl, context) &&
                                                    (if (!historyItem.episodeId.isNullOrBlank()) dl.episodeId == historyItem.episodeId else dl.episodeId.isNullOrBlank())
                                            }
                                            val movieToPlay = matchingDownload?.let {
                                                resolveOfflineMovie(it, context, movieState.allMovies)
                                            } ?: movieState.allMovies.find { it.id == historyItem.movieId } ?: Movie(
                                                id = historyItem.movieId,
                                                title = historyItem.movieTitle,
                                                description = "",
                                                genres = emptyList(),
                                                coverUrl = historyItem.coverUrl,
                                                videoKey = "",
                                                videoStreamUrl = "",
                                                durationMinutes = (historyItem.durationMs / 60000).toInt().coerceAtLeast(1),
                                                fileSizeMb = 100L,
                                                releaseYear = 2025,
                                                rating = 8.5,
                                                cast = emptyList(),
                                                isFeatured = false
                                            )
                                            val resumePos = watchHistoryManager.getResumablePositionMs(
                                                userId = userSession.uid,
                                                movieId = historyItem.movieId,
                                                episodeId = historyItem.episodeId
                                            )
                                            onPlayMovie(movieToPlay, resumePos, historyItem.episodeId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Downloaded Section Title
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Downloaded ($completedCount)",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Downloaded Items or Empty State
                if (downloads.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No movies downloaded yet",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Download movies and episodes to watch offline anytime with high quality streaming.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Button(
                                    onClick = onBrowseCatalog,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CinematicRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("Browse Movies", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    items(groupedDownloads, key = { "${it.movieId}_${it.isSeries}" }) { group ->
                        DownloadedGroupCard(
                            group = group,
                            watchHistoryMap = watchHistoryMap,
                            downloadViewModel = downloadViewModel,
                            onCardClick = {
                                if (group.isSeries) {
                                    selectedSeriesGroup = group
                                } else {
                                    val item = group.items.first()
                                    val isReady = isDownloadVerifiedComplete(item, context)
                                    if (isReady) {
                                        val movieToPlay = resolveOfflineMovie(item, context, movieState.allMovies)
                                        val resumePos = watchHistoryManager.getResumablePositionMs(
                                            userId = userSession.uid,
                                            movieId = item.movieId,
                                            episodeId = item.episodeId
                                        )
                                        onPlayMovie(movieToPlay, resumePos, item.episodeId)
                                    } else if (item.status == DownloadStatus.DOWNLOADING) {
                                        val pct = (item.progress * 100).toInt().coerceIn(0, 99)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Downloading ($pct%) — not complete yet for offline play.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val fallback = movieState.allMovies.find { it.id == item.movieId }
                                        downloadViewModel.retryDownload(item, context, fallback)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Download is not complete. Retrying download...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onRetryGroup = {
                                group.items.filter { !isDownloadVerifiedComplete(it, context) }.forEach { itm ->
                                    val fallback = movieState.allMovies.find { it.id == itm.movieId }
                                    downloadViewModel.retryDownload(itm, context, fallback)
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    "Retrying incomplete download...",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDeleteGroup = {
                                group.items.forEach { itm ->
                                    downloadViewModel.deleteDownload(itm.id, itm.movieId, itm.episodeId, context)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Bottom Cache Bar
        Surface(
            color = SurfaceDark,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Offline Storage: ",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = formattedStorage,
                    color = ElectricBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "  |  ",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
                Text(
                    text = "$formattedAvailable available",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }

    // Full-Window Season Episode Selector Dialog
    if (selectedSeriesGroup != null) {
        val group = selectedSeriesGroup!!
        Dialog(
            onDismissRequest = { selectedSeriesGroup = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Dialog Top Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        IconButton(onClick = { selectedSeriesGroup = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.movieTitle,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${group.items.size} downloaded files • ${group.formattedSize}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(group.items, key = { it.id }) { epItem ->
                            val historyItem = watchHistoryMap[WatchHistoryManager.buildPositionKey(epItem.movieId, epItem.episodeId)]
                            val epResumable = historyItem?.let {
                                it.lastPositionMs >= WatchHistoryManager.MIN_RESUME_POSITION_MS &&
                                    !WatchHistoryManager.isPlaybackCompleted(it.lastPositionMs, it.durationMs)
                            } == true
                            val epWatchPercent = if (epResumable) (historyItem?.progressPercent ?: 0) else 0
                            val isEpComplete = isDownloadVerifiedComplete(epItem, context)
                            val epPct = (epItem.progress * 100).toInt().coerceIn(0, 99)
                            var showEpMenu by remember { mutableStateOf(false) }

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isEpComplete) {
                                            val movieToPlay = resolveOfflineMovie(epItem, context, movieState.allMovies)
                                            val resumePos = watchHistoryManager.getResumablePositionMs(
                                                userId = userSession.uid,
                                                movieId = epItem.movieId,
                                                episodeId = epItem.episodeId
                                            )
                                            selectedSeriesGroup = null
                                            onPlayMovie(movieToPlay, resumePos, epItem.episodeId)
                                        } else if (epItem.status == DownloadStatus.DOWNLOADING) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Episode downloading ($epPct%) — wait until complete for offline play.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            val fallback = movieState.allMovies.find { it.id == epItem.movieId }
                                            downloadViewModel.retryDownload(epItem, context, fallback)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Episode download is not complete. Retrying...",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    // 16:9 Episode Thumbnail
                                    Box(
                                        modifier = Modifier
                                            .size(width = 110.dp, height = 62.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceElevated)
                                    ) {
                                        AsyncImage(
                                            model = epItem.coverUrl.ifBlank { group.coverUrl },
                                            contentDescription = epItem.episodeTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Season / Episode Badge on Top-Left
                                        val epNumber = epItem.episodeNumber ?: 1
                                        val sNumber = epItem.seasonNumber ?: 1
                                        Box(
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .align(Alignment.TopStart)
                                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "S${sNumber}E${epNumber}",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Watched Bar on Bottom of Thumbnail
                                        if (epWatchPercent > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .align(Alignment.BottomCenter)
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction = (epWatchPercent / 100f).coerceIn(0.02f, 1f))
                                                        .height(3.dp)
                                                        .background(CinematicRed)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = epItem.episodeTitle?.ifBlank { "Episode ${epItem.episodeNumber ?: 1}" } ?: "Episode ${epItem.episodeNumber ?: 1}",
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val epSize = downloadViewModel.formatStorageSize(epItem.downloadedBytes.coerceAtLeast(epItem.totalBytes))
                                        Text(
                                            text = epSize,
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        when {
                                            epItem.status == DownloadStatus.DOWNLOADING -> {
                                                Text(
                                                    text = "Downloading $epPct% • Not Complete",
                                                    color = CinematicRed,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            isEpComplete -> {
                                                Text(
                                                    text = if (epResumable && historyItem != null) {
                                                        "Resume ${historyItem.formattedPosition} • $epWatchPercent% watched"
                                                    } else {
                                                        "Ready for Offline Play"
                                                    },
                                                    color = Color(0xFF4CAF50),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            epItem.status == DownloadStatus.PAUSED -> {
                                                Text(
                                                    text = "Not Complete • Paused ($epPct%) • Tap to resume",
                                                    color = AmberGold,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            epItem.status == DownloadStatus.QUEUED -> {
                                                Text(
                                                    text = "Not Complete • Queued",
                                                    color = ElectricBlue,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = "Download Not Complete ($epPct%) • Tap to retry",
                                                    color = CinematicRed,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // Episode 3-Dots Menu
                                    Box {
                                        IconButton(onClick = { showEpMenu = true }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showEpMenu,
                                            onDismissRequest = { showEpMenu = false },
                                            modifier = Modifier.background(SurfaceElevated)
                                        ) {
                                            if (!isEpComplete && epItem.status != DownloadStatus.DOWNLOADING) {
                                                DropdownMenuItem(
                                                    text = { Text("Retry Download", color = AmberGold) },
                                                    onClick = {
                                                        showEpMenu = false
                                                        val fallback = movieState.allMovies.find { it.id == epItem.movieId }
                                                        downloadViewModel.retryDownload(epItem, context, fallback)
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberGold)
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Delete Episode", color = CinematicRed) },
                                                onClick = {
                                                    showEpMenu = false
                                                    downloadViewModel.deleteDownload(epItem.id, epItem.movieId, epItem.episodeId, context)
                                                    val remaining = group.items.filter { it.id != epItem.id }
                                                    if (remaining.isEmpty()) {
                                                        selectedSeriesGroup = null
                                                    } else {
                                                        selectedSeriesGroup = group.copy(items = remaining)
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = CinematicRed)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear All Dialog
    if (showClearStorageDialog) {
        AlertDialog(
            onDismissRequest = { showClearStorageDialog = false },
            title = {
                Text(
                    text = "Clear Offline Downloads?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all offline downloaded movies and series ($formattedStorage) from this device?",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        downloadViewModel.clearAllDownloads(context)
                        showClearStorageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinematicRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_clear_all_downloads_button")
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearStorageDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun WatchHistoryCard(
    item: WatchHistoryItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
            .testTag("watch_history_item_${item.movieId}")
    ) {
        Box(
            modifier = Modifier
                .width(136.dp)
                .height(78.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElevated)
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.movieTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top-Left Badge (Film reel / TV indicator)
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .padding(3.dp)
            ) {
                Icon(
                    imageVector = if (item.episodeId != null) Icons.Default.Tv else Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }

            // Bottom-Right Duration Badge
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.formattedDuration,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bottom Watched Progress Line
            if (item.progressPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (item.progressPercent / 100f).coerceIn(0.02f, 1f))
                            .height(3.dp)
                            .background(CinematicRed)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.movieTitle,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!item.episodeTitle.isNullOrBlank() && item.episodeTitle != item.movieTitle) {
            Text(
                text = item.episodeTitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DownloadedGroupCard(
    group: DownloadedGroup,
    watchHistoryMap: Map<String, WatchHistoryItem>,
    downloadViewModel: DownloadViewModel,
    onCardClick: () -> Unit,
    onRetryGroup: () -> Unit = {},
    onDeleteGroup: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val singleItem = if (!group.isSeries) group.items.firstOrNull() else null
    val historyItem = if (singleItem != null) {
        watchHistoryMap[WatchHistoryManager.buildPositionKey(singleItem.movieId, singleItem.episodeId)]
    } else {
        null
    }
    val singleResumable = historyItem?.let {
        it.lastPositionMs >= WatchHistoryManager.MIN_RESUME_POSITION_MS &&
            !WatchHistoryManager.isPlaybackCompleted(it.lastPositionMs, it.durationMs)
    } == true

    val watchedPercent = if (singleItem != null) {
        if (singleResumable) (historyItem?.progressPercent ?: 0) else 0
    } else {
        val seriesHistory = group.items.mapNotNull {
            watchHistoryMap[WatchHistoryManager.buildPositionKey(it.movieId, it.episodeId)]
        }.filter {
            it.lastPositionMs >= WatchHistoryManager.MIN_RESUME_POSITION_MS &&
                !WatchHistoryManager.isPlaybackCompleted(it.lastPositionMs, it.durationMs)
        }
        if (seriesHistory.isNotEmpty()) {
            (seriesHistory.sumOf { it.progressPercent } / seriesHistory.size)
        } else {
            0
        }
    }

    val readyCount = group.items.count { isDownloadVerifiedComplete(it, context) }
    val allItemsComplete = group.items.isNotEmpty() && readyCount == group.items.size
    val hasDownloadingItem = group.items.any { it.status == DownloadStatus.DOWNLOADING }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onCardClick)
            .testTag("downloaded_card_${group.movieId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            // 16:9 Landscape Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 114.dp, height = 66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
            ) {
                AsyncImage(
                    model = group.coverUrl,
                    contentDescription = group.movieTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-left icon badge
                Box(
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(3.dp)
                ) {
                    Icon(
                        imageVector = if (group.isSeries) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }

                // Bottom-right badge: duration for movie, or file count for series
                Box(
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (group.isSeries) "$readyCount/${group.items.size} ready" else (historyItem?.formattedDuration ?: "02:00:00"),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Watched progress bar on bottom of thumbnail
                if (watchedPercent > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (watchedPercent / 100f).coerceIn(0.02f, 1f))
                                .height(3.dp)
                                .background(CinematicRed)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.movieTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                if (group.isSeries) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${group.items.size} files • ${group.formattedSize}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    val sizeFormatted = singleItem?.let {
                        downloadViewModel.formatStorageSize(it.downloadedBytes.coerceAtLeast(it.totalBytes))
                    } ?: group.formattedSize
                    Text(
                        text = sizeFormatted,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Verified completeness status (never falsely report Ready for Offline Play)
                if (singleItem != null) {
                    val pct = (singleItem.progress * 100).toInt().coerceIn(0, 99)
                    when {
                        singleItem.status == DownloadStatus.DOWNLOADING -> {
                            Text(
                                text = "Downloading $pct% • Not Complete",
                                color = CinematicRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { singleItem.progress },
                                color = CinematicRed,
                                trackColor = SurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                            )
                        }
                        allItemsComplete -> {
                            Text(
                                text = if (singleResumable && historyItem != null) {
                                    "Resume ${historyItem.formattedPosition} • $watchedPercent% watched"
                                } else {
                                    "Ready for Offline Play"
                                },
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        singleItem.status == DownloadStatus.PAUSED -> {
                            Text(
                                text = "Not Complete • Paused ($pct%) • Tap to resume",
                                color = AmberGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (pct > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { singleItem.progress.coerceIn(0f, 0.99f) },
                                    color = AmberGold,
                                    trackColor = SurfaceElevated,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                )
                            }
                        }
                        singleItem.status == DownloadStatus.QUEUED -> {
                            Text(
                                text = "Not Complete • Queued ($pct%)",
                                color = ElectricBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            Text(
                                text = "Download Not Complete ($pct%) • Tap to retry",
                                color = CinematicRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (pct > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { singleItem.progress.coerceIn(0f, 0.99f) },
                                    color = CinematicRed,
                                    trackColor = SurfaceElevated,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                )
                            }
                        }
                    }
                } else {
                    when {
                        hasDownloadingItem -> {
                            Text(
                                text = "Downloading ($readyCount/${group.items.size} ready) • Not Complete",
                                color = CinematicRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        allItemsComplete -> {
                            Text(
                                text = if (watchedPercent > 0) "Ready for Offline Play • $watchedPercent% watched" else "Ready for Offline Play",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        readyCount > 0 -> {
                            Text(
                                text = "Not Complete ($readyCount/${group.items.size} episodes ready)",
                                color = AmberGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            Text(
                                text = "Download Not Complete • Tap to inspect",
                                color = CinematicRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 3-Dots Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    if (!allItemsComplete && !hasDownloadingItem) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Retry Incomplete Download",
                                    color = AmberGold
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRetryGroup()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = AmberGold
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (group.isSeries) "Delete All Episodes" else "Delete Download",
                                color = CinematicRed
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDeleteGroup()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = CinematicRed
                            )
                        }
                    )
                }
            }
        }
    }
}

fun isDownloadVerifiedComplete(item: DownloadItem, context: Context): Boolean {
    if (item.status != DownloadStatus.COMPLETED) return false
    val storedPath = item.localFilePath.trim()
    if (storedPath.startsWith("content://")) {
        try {
            val valid = context.contentResolver.openFileDescriptor(android.net.Uri.parse(storedPath), "r")?.use { pfd ->
                pfd.statSize >= com.example.data.download.OfflineStoragePaths.MIN_VALID_VIDEO_BYTES
            } == true
            if (valid) return true
        } catch (_: Exception) {
        }
    } else if (storedPath.isNotBlank()) {
        val storedFile = java.io.File(storedPath.removePrefix("file://"))
        if (storedFile.exists() && storedFile.length() >= com.example.data.download.OfflineStoragePaths.MIN_VALID_VIDEO_BYTES) return true
    }

    return com.example.data.download.OfflineStoragePaths.findVerifiedFileForUser(
        context = context,
        userId = item.userId,
        movieId = item.movieId,
        episodeId = item.episodeId
    ) != null
}

fun resolveOfflineMovie(item: DownloadItem, context: Context, allMovies: List<Movie>): Movie {
    val correspondingMovie = allMovies.find { it.id == item.movieId }
    val storedPath = item.localFilePath.trim()
    val storedFile = if (storedPath.isNotBlank() && !storedPath.startsWith("content://")) {
        java.io.File(storedPath.removePrefix("file://"))
    } else null

    val verifiedPath = when {
        storedPath.startsWith("content://") -> storedPath
        storedFile != null && storedFile.exists() && storedFile.length() >= com.example.data.download.OfflineStoragePaths.MIN_VALID_VIDEO_BYTES -> {
            com.example.data.download.OfflineStoragePaths.ensureNotInCache(
                context = context,
                sourceFile = storedFile,
                userId = item.userId,
                movieId = item.movieId,
                episodeId = item.episodeId
            ).absolutePath
        }
        else -> com.example.data.download.OfflineStoragePaths.findVerifiedFileForUser(
            context = context,
            userId = item.userId,
            movieId = item.movieId,
            episodeId = item.episodeId
        )?.absolutePath
    }

    return if (correspondingMovie != null) {
        if (verifiedPath != null) {
            correspondingMovie.copy(videoStreamUrl = verifiedPath)
        } else {
            correspondingMovie
        }
    } else {
        Movie(
            id = item.movieId,
            title = item.movieTitle,
            description = "Downloaded movie ready for offline playback",
            genres = emptyList(),
            coverUrl = item.coverUrl,
            videoKey = "",
            videoStreamUrl = verifiedPath ?: item.localFilePath,
            durationMinutes = 120,
            fileSizeMb = (item.totalBytes / (1024 * 1024L)).coerceAtLeast(100L),
            releaseYear = 2025,
            rating = 8.5,
            cast = emptyList(),
            isFeatured = false
        )
    }
}
