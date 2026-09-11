package com.example.presentation.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.repository.MovieRepository
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Movie
import com.example.presentation.components.MoviePosterCard
import com.example.presentation.viewmodel.DownloadViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.VideoCacheManager
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun MovieDetailScreen(
    movie: Movie,
    allMovies: List<Movie> = emptyList(),
    downloadViewModel: DownloadViewModel,
    movieRepository: MovieRepository? = null,
    onBackClick: () -> Unit,
    onPlayFullscreenClick: (Movie) -> Unit,
    onSelectRecommendedMovie: (Movie) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val downloads by downloadViewModel.downloads.collectAsState()
    val downloadItem = downloads.find { it.movieId == movie.id }
    val context = LocalContext.current
    val activity = context as? Activity
    val scrollState = rememberScrollState()

    // Dedicated Inline ExoPlayer for instantaneous automatic playback
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }

    val inlinePlayer = remember(movie.id) {
        val player = try {
            VideoCacheManager.buildFastPlayer(context)
        } catch (e: Exception) {
            ExoPlayer.Builder(context).build()
        }
        player.apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Auto-hide player controls overlay after 3 seconds
    LaunchedEffect(showPlayerControls, isPlaying) {
        if (showPlayerControls && isPlaying) {
            delay(3500)
            showPlayerControls = false
        }
    }

    // Periodic time progress tracker
    LaunchedEffect(inlinePlayer, isPlaying) {
        while (true) {
            if (inlinePlayer.isPlaying) {
                currentPositionMs = inlinePlayer.currentPosition.coerceAtLeast(0L)
                durationMs = inlinePlayer.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    DisposableEffect(movie.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = inlinePlayer.duration.coerceAtLeast(0L)
                    streamError = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // If direct R2 key stream failed, try fallback to movie.videoStreamUrl
                if (movie.videoStreamUrl.isNotBlank() && movie.videoStreamUrl.startsWith("http")) {
                    inlinePlayer.setMediaItem(MediaItem.fromUri(movie.videoStreamUrl))
                    inlinePlayer.prepare()
                    inlinePlayer.play()
                } else {
                    streamError = "You look like you have no internet connection. Please check your network or watch from your downloaded movies."
                    isPlaying = false
                }
            }
        }
        inlinePlayer.addListener(listener)

        // Resolve uri and auto-start (checking local offline file first)
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val localFile = java.io.File(destDir, "movie_${movie.id}.mp4")
        val mediaUri = if (localFile.exists() && localFile.length() > 0) {
            localFile.toURI().toString()
        } else if (movie.videoKey.isNotBlank()) {
            "https://pub-5399f62037f94260b0f54c88a9297134.r2.dev/${movie.videoKey.trimStart('/')}"
        } else if (movie.videoStreamUrl.isNotBlank()) {
            movie.videoStreamUrl
        } else {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        }

        inlinePlayer.setMediaItem(MediaItem.fromUri(mediaUri))
        inlinePlayer.prepare()
        inlinePlayer.play()

        onDispose {
            inlinePlayer.removeListener(listener)
            inlinePlayer.release()
        }
    }

    // Filter recommended movies (matching genres or high rating, excluding current)
    val recommendations = remember(movie.id, allMovies) {
        val related = allMovies.filter { it.id != movie.id }
        val genreMatches = related.filter { other ->
            other.genres.any { g -> movie.genres.contains(g) }
        }
        if (genreMatches.isNotEmpty()) genreMatches else related.sortedByDescending { it.rating }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("movie_detail_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 60.dp)
        ) {
            // Top Video Player Area (Plays automatically)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showPlayerControls = !showPlayerControls
                    }
            ) {
                // ExoPlayer Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = inlinePlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Error message banner if R2 public link failed
                streamError?.let { err ->
                    Surface(
                        color = Color(0xDDCC1111),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Video stream unavailable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(err, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }

                // Video Controls Scrim Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showPlayerControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x77000000))
                    ) {
                        // Top row: Back button & Fullscreen / Rotate button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .testTag("detail_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Mute / Unmute
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        inlinePlayer.volume = if (isMuted) 0f else 1f
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x88000000))
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Fullscreen Rotate Button
                                IconButton(
                                    onClick = {
                                        onPlayFullscreenClick(movie)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(CinematicRed)
                                        .testTag("detail_fullscreen_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Watch Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Center Play / Pause toggle
                        IconButton(
                            onClick = {
                                if (inlinePlayer.isPlaying) {
                                    inlinePlayer.pause()
                                } else {
                                    inlinePlayer.play()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CinematicRed.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Bottom mini scrubber
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val duration = durationMs.coerceAtLeast(1L)
                            val current = currentPositionMs.coerceIn(0L, duration)

                            Slider(
                                value = (current.toFloat() / duration).coerceIn(0f, 1f),
                                onValueChange = { fraction ->
                                    inlinePlayer.seekTo((fraction * duration).toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = CinematicRed,
                                    activeTrackColor = CinematicRed,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDetailTime(current),
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = formatDetailTime(duration),
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Movie Details Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rating
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", movie.rating),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "${movie.releaseYear} • ${movie.durationMinutes} min • ${movie.fileSizeMb} MB",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Area: Download Button only (since video is already auto-playing) + Fullscreen prompt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download Button
                    Button(
                        onClick = {
                            if (downloadItem?.status != DownloadStatus.COMPLETED) {
                                downloadViewModel.startDownload(movie, context)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("detail_download_button")
                    ) {
                        when (downloadItem?.status) {
                            DownloadStatus.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    progress = downloadItem.progress,
                                    color = CinematicRed,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(downloadItem.progress * 100).toInt()}%",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            DownloadStatus.COMPLETED -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Downloaded",
                                    tint = Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Downloaded", fontWeight = FontWeight.SemiBold)
                            }
                            else -> {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Movie", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Rotate to Fullscreen Button
                    IconButton(
                        onClick = { onPlayFullscreenClick(movie) },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CinematicRed)
                            .testTag("detail_fullscreen_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Rotate to Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (downloadItem?.status == DownloadStatus.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = downloadItem.progress,
                        color = CinematicRed,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Genres
                Text(
                    text = "Genres",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    movie.genres.forEach { genre ->
                        Surface(
                            color = SurfaceElevated,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = genre,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Synopsis
                Text(
                    text = "Synopsis",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = movie.description,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )

                if (movie.cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Starring Cast",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = movie.cast.joinToString(", "),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }

                // Recommendations Section
                if (recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "More Like This",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.testTag("detail_recommendations_row")
                    ) {
                        items(recommendations, key = { it.id }) { recommendedMovie ->
                            MoviePosterCard(
                                movie = recommendedMovie,
                                onClick = {
                                    onSelectRecommendedMovie(recommendedMovie)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDetailTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
