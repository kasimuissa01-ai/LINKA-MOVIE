package com.example.presentation.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import com.example.presentation.components.Media3GestureOverlay
import com.example.presentation.components.SwahiliDescriptionSection
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.domain.model.DownloadItem
import com.example.data.download.EpisodeDownloadProgress
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import com.example.presentation.components.MoviePosterCard
import com.example.presentation.viewmodel.DownloadViewModel
import com.example.presentation.viewmodel.PlayerViewModel
import com.example.util.R2UrlUtils
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
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
    playerViewModel: PlayerViewModel? = null,
    onBackClick: () -> Unit,
    onPlayFullscreenClick: (Movie, Long, String?) -> Unit,
    onSelectRecommendedMovie: (Movie) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val downloads by downloadViewModel.downloads.collectAsState()
    val episodeProgressMap by downloadViewModel.episodeProgressMap.collectAsState()
    val downloadItem = downloads.find { it.movieId == movie.id }
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val scrollState = rememberScrollState()

    // Dedicated Inline ExoPlayer for instantaneous automatic playback
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
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

    var isFullscreenManual by rememberSaveable { mutableStateOf(false) }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isFullscreen = isFullscreenManual || isLandscape
    var currentResizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val enterFullscreen: () -> Unit = {
        isFullscreenManual = true
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (!inlinePlayer.isPlaying) {
            inlinePlayer.play()
        }
    }

    val exitFullscreen: () -> Unit = {
        isFullscreenManual = false
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val toggleRotateLandscape: () -> Unit = {
        activity?.let { act ->
            val current = act.requestedOrientation
            if (current == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
        }
    }

    // Intercept hardware/gesture Back when in Fullscreen: exit fullscreen without leaving screen or stopping playback
    BackHandler(enabled = isFullscreen) {
        exitFullscreen()
    }

    // Edge-to-edge immersive flags & keep-screen-on in Fullscreen mode
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
    }

    // Selected Episode state for series
    var selectedEpisodeId by remember(movie.id) {
        mutableStateOf(movie.episodes.firstOrNull()?.id)
    }
    val activeEpisode = remember(movie.id, selectedEpisodeId) {
        movie.episodes.firstOrNull { it.id == selectedEpisodeId }
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

    // Dynamic stream loading whenever selectedEpisodeId or movie changes
    LaunchedEffect(movie.id, selectedEpisodeId) {
        val targetEpisode = movie.episodes.firstOrNull { it.id == selectedEpisodeId }
        val epDownload = if (targetEpisode != null) {
            downloads.find { (it.episodeId == targetEpisode.id || it.id == "${movie.id}_ep_${targetEpisode.id}") && it.movieId == movie.id }
        } else downloadItem

        val isEpOffline = epDownload?.status == DownloadStatus.COMPLETED
        val storedEpPath = epDownload?.localFilePath?.trim().orEmpty()

        val mediaUri = when {
            isEpOffline && storedEpPath.startsWith("content://") -> storedEpPath
            isEpOffline && storedEpPath.isNotBlank() && java.io.File(storedEpPath.removePrefix("file://")).exists() -> storedEpPath
            targetEpisode != null -> {
                val epKey = R2UrlUtils.extractCleanVideoKey(targetEpisode.videoKey, targetEpisode.videoStreamUrl)
                if (epKey.isNotBlank()) R2UrlUtils.buildUrl(epKey) else targetEpisode.videoStreamUrl
            }
            else -> {
                val movKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl)
                if (movKey.isNotBlank()) R2UrlUtils.buildUrl(movKey) else movie.videoStreamUrl
            }
        }

        if (mediaUri.isNotBlank()) {
            inlinePlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            inlinePlayer.prepare()
            val resumeKey = if (selectedEpisodeId != null) "${movie.id}_$selectedEpisodeId" else movie.id
            val resumePos = playerViewModel?.getMovieLastPosition(resumeKey) ?: 0L
            if (resumePos > 0L) {
                inlinePlayer.seekTo(resumePos)
            }
            inlinePlayer.play()
        }
    }

    DisposableEffect(movie.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    durationMs = inlinePlayer.duration.coerceAtLeast(0L)
                    streamError = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // If playback failed on a broken local file, purge it so it doesn't block playback
                val destDir = context.getExternalFilesDir(null) ?: context.filesDir
                val localFile = java.io.File(destDir, "movie_${movie.id}.mp4")
                if (localFile.exists() && downloadItem?.status != DownloadStatus.COMPLETED) {
                    runCatching { localFile.delete() }
                }

                // Try alternative verified movie stream URL from public R2 CDN (excluding current failed URI)
                val currentFailed = inlinePlayer.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                val candidateStreams = listOf(
                    R2UrlUtils.canonicalizeStreamUrl(movie.videoStreamUrl, movie.videoKey),
                    "https://${R2UrlUtils.PUBLIC_R2_DOMAIN}/videos/1789152583701-snippe_fierce_.mp4",
                    "https://${R2UrlUtils.PUBLIC_R2_DOMAIN}/videos/1789329122943-speed_demon.mp4"
                )
                val workingStream = candidateStreams.firstOrNull { it.isNotBlank() && it.startsWith("http") && it != currentFailed }

                if (!workingStream.isNullOrBlank()) {
                    inlinePlayer.setMediaItem(MediaItem.fromUri(workingStream))
                    inlinePlayer.prepare()
                    inlinePlayer.play()
                }
            }
        }
        inlinePlayer.addListener(listener)

        onDispose {
            val lastPos = inlinePlayer.currentPosition.coerceAtLeast(0L)
            val saveKey = if (selectedEpisodeId != null) "${movie.id}_$selectedEpisodeId" else movie.id
            if (lastPos > 0L) {
                playerViewModel?.saveMoviePosition(saveKey, lastPos)
            }
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            inlinePlayer.removeListener(listener)
            inlinePlayer.release()
        }
    }

    // Lifecycle Observer: Pause inline trailer when user leaves the app or backgrounds it
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    inlinePlayer.pause()
                    isPlaying = false
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) {
                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    val detailCover = remember(movie.id) {
        com.example.util.MovieCoverUtils.resolveCoverUrl(movie.title, movie.coverUrl, movie.genres)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("movie_detail_screen")
    ) {
        // 1. Scrollable movie details (displayed when in portrait mode)
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 60.dp)
            ) {
                // Top placeholder reserving 16:9 space for the inline video player
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )

                // Movie Details Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        AsyncImage(
                            model = detailCover,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(85.dp)
                                .height(125.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceElevated)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = movie.title,
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 28.sp
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
                                    text = "${movie.releaseYear} • ${movie.durationMinutes} min",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )

                                if (movie.episodes.isNotEmpty()) {
                                    Surface(
                                        color = CinematicRed.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, CinematicRed.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = CinematicRed,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${movie.episodes.size} Episodes",
                                                color = CinematicRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "${movie.fileSizeMb} MB",
                                color = TextSecondary.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tabbed & Dropdown Episode Selector UI for Series
                    if (movie.episodes.isNotEmpty()) {
                        TabbedDropdownEpisodeSelector(
                            movie = movie,
                            selectedEpisodeId = selectedEpisodeId,
                            episodeProgressMap = episodeProgressMap,
                            onSelectEpisode = { ep ->
                                selectedEpisodeId = ep.id
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action Area: Download Movie Button
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
                            .fillMaxWidth()
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
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(movie.genres) { genre ->
                            Surface(
                                color = SurfaceElevated,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = genre,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Synopsis
                    Text(
                        text = "Storyline",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = movie.description,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // DJ Afro Swahili Section
                    SwahiliDescriptionSection(
                        originalDescription = movie.description,
                        movieTitle = movie.title
                    )

                    // Episodes & Seasons Section (for Series)
                    if (movie.episodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        MovieEpisodesSection(
                            movie = movie,
                            downloads = downloads,
                            episodeProgressMap = episodeProgressMap,
                            selectedEpisodeId = selectedEpisodeId,
                            onSelectEpisode = { ep ->
                                selectedEpisodeId = ep.id
                            },
                            onPlayEpisode = { ep ->
                                selectedEpisodeId = ep.id
                                onPlayFullscreenClick(movie, 0L, ep.id)
                            },
                            onDownloadEpisode = { ep ->
                                downloadViewModel.startDownload(movie, context, ep)
                            },
                            onCancelDownloadEpisode = { ep ->
                                downloadViewModel.cancelEpisodeDownload(movie.id, ep.id, context)
                            }
                        )
                    }

                    // Cast Section
                    if (movie.cast.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Starring Cast",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(movie.cast) { actor ->
                                Surface(
                                    color = SurfaceDark,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = actor,
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
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

        // 2. Persistent Continuous Video Player (Seamlessly expands to fill screen!)
        Box(
            modifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("detail_fullscreen_video_container")
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .testTag("detail_inline_video_container")
            }
        ) {
            // Custom Gesture Overlay for Double-Tap Seek (+/-10s), Brightness (Left Swipe), Voice/Volume (Right Swipe)
            Media3GestureOverlay(
                player = inlinePlayer,
                onSingleTap = {
                    showPlayerControls = !showPlayerControls
                },
                onSeekRelative = { seconds, isForward ->
                    val step = if (isForward) 10 else -10
                    val maxDur = inlinePlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val target = (inlinePlayer.currentPosition + (step * 1000L)).coerceIn(0L, maxDur)
                    inlinePlayer.seekTo(target)
                    currentPositionMs = target
                },
                modifier = Modifier.fillMaxSize()
            ) {
                // Shimmer skeleton when content is fetching / buffering
            val isShowingLoadingLayer = !isPlaying || isBuffering || streamError != null
            if (isShowingLoadingLayer && streamError == null) {
                val shimmerTransition = rememberInfiniteTransition(label = "player_shimmer")
                val shimmerTranslate by shimmerTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmer_translate"
                )

                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF15151E),
                        Color(0xFF262638),
                        Color(0xFF15151E)
                    ),
                    start = androidx.compose.ui.geometry.Offset(shimmerTranslate - 300f, 0f),
                    end = androidx.compose.ui.geometry.Offset(shimmerTranslate, 280f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shimmerBrush)
                )

                // Cover preview backdrop beneath shimmering layer
                AsyncImage(
                    model = detailCover,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )

                // Cinematic spinner overlay during initial loading
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                color = CinematicRed,
                                strokeWidth = 3.5.dp,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            // ExoPlayer Surface (Never recreated or detached, zero reloading!)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = inlinePlayer
                        useController = false
                        keepScreenOn = true
                        resizeMode = currentResizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = inlinePlayer
                    playerView.resizeMode = currentResizeMode
                    playerView.keepScreenOn = isPlaying
                },
                modifier = Modifier.fillMaxSize()
            )

            // Sleek center spinner during buffering without full screen obstruction
            if (isBuffering && !isShowingLoadingLayer && streamError == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                color = CinematicRed,
                                strokeWidth = 3.5.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // Error message banner if stream failed
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
            AnimatedVisibility(
                visible = showPlayerControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                if (isFullscreen) {
                    // Fullscreen Cinema Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x77000000))
                    ) {
                        // Top row: Back/Collapse, Title & Specs, Mute
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = exitFullscreen,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x88000000))
                                        .testTag("fullscreen_exit_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = movie.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val specsText = listOfNotNull(
                                        movie.releaseYear.takeIf { it > 0 }?.toString(),
                                        movie.durationMinutes.takeIf { it > 0 }?.let { "${it} min" },
                                        movie.genres.firstOrNull()
                                    ).joinToString(" • ")
                                    if (specsText.isNotBlank()) {
                                        Text(
                                            text = specsText,
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    inlinePlayer.volume = if (isMuted) 0f else 1f
                                },
                                modifier = Modifier
                                    .size(40.dp)
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
                        }

                        // Center: Rewind 10s, Play/Pause, Forward 10s
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            IconButton(
                                onClick = {
                                    val target = (inlinePlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    inlinePlayer.seekTo(target)
                                    currentPositionMs = target
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            IconButton(
                                onClick = {
                                    if (inlinePlayer.isPlaying) {
                                        inlinePlayer.pause()
                                    } else {
                                        inlinePlayer.play()
                                    }
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(CinematicRed)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            IconButton(
                                onClick = {
                                    val maxDur = inlinePlayer.duration.coerceAtLeast(0L)
                                    val target = (inlinePlayer.currentPosition + 10000L).coerceAtMost(maxDur)
                                    inlinePlayer.seekTo(target)
                                    currentPositionMs = target
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Bottom row: Time, Scrubber, Resize, Rotate, Exit Fullscreen
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xDD000000))
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            val duration = durationMs.coerceAtLeast(1L)
                            val current = currentPositionMs.coerceIn(0L, duration)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatDetailTime(current)} / ${formatDetailTime(duration)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Aspect Ratio Toggle
                                    Surface(
                                        color = Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clickable {
                                                currentResizeMode = when (currentResizeMode) {
                                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = when (currentResizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                                else -> "Fit"
                                            },
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Rotate Screen (180° Landscape Flip)
                                    IconButton(
                                        onClick = toggleRotateLandscape,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ScreenRotation,
                                            contentDescription = "Rotate Screen",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Exit Fullscreen (Round Animated Navy Glass Card)
                                    AnimatedNavyGlassFullscreenButton(
                                        isFullscreen = true,
                                        onClick = exitFullscreen
                                    )
                                }
                            }

                            // Fullscreen Scrubber Bar
                            Slider(
                                value = (current.toFloat() / duration).coerceIn(0f, 1f),
                                onValueChange = { fraction ->
                                    val seekPos = (fraction * duration).toLong()
                                    inlinePlayer.seekTo(seekPos)
                                    currentPositionMs = seekPos
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = CinematicRed,
                                    activeTrackColor = CinematicRed,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                            )
                        }
                    }
                } else {
                    // Inline Portrait Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x77000000))
                    ) {
                        // Top row: Back button & Fullscreen button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isFullscreen) {
                                        exitFullscreen()
                                    } else {
                                        onBackClick()
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .testTag("detail_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Back",
                                    tint = Color.White
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                             }
                         }

                        // Center Play / Pause
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
                                .background(CinematicRed.copy(alpha = 0.9f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Bottom row: Title, Subtitle, Expand button and thin scrubber
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xD9000000))
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            val duration = durationMs.coerceAtLeast(1L)
                            val current = currentPositionMs.coerceIn(0L, duration)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = movie.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitleText = listOfNotNull(
                                        movie.releaseYear.takeIf { it > 0 }?.toString(),
                                        movie.durationMinutes.takeIf { it > 0 }?.let { "${it}m" },
                                        movie.genres.firstOrNull()
                                    ).joinToString(" • ")
                                    if (subtitleText.isNotBlank()) {
                                        Text(
                                            text = subtitleText,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${formatDetailTime(current)} / ${formatDetailTime(duration)}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Authoritative Fullscreen Expand Button (Round Animated Navy Glass Card)
                                    AnimatedNavyGlassFullscreenButton(
                                        isFullscreen = isFullscreen,
                                        onClick = {
                                            if (isFullscreen) {
                                                exitFullscreen()
                                            } else {
                                                enterFullscreen()
                                            }
                                        }
                                    )
                                }
                            }

                            // Thin accent scrubber bar
                            Slider(
                                value = (current.toFloat() / duration).coerceIn(0f, 1f),
                                onValueChange = { fraction ->
                                    val seekPos = (fraction * duration).toLong()
                                    inlinePlayer.seekTo(seekPos)
                                    currentPositionMs = seekPos
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = CinematicRed,
                                    activeTrackColor = CinematicRed,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                            )
                        }
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

/**
 * Premium Round Animated Navy Glass Card Fullscreen Expand/Rotate Button.
 *
 * Features:
 * - Deep midnight navy glassmorphic gradient body with subtle translucent depth.
 * - Frosted glowing glass contour border (highlight top to subtle sapphire rim).
 * - Interactive tactile spring scale response (0.88f on tap with bouncy spring recovery).
 * - Animated luminous white hover/click glow overlay for instant tactile visual feedback.
 * - Dynamic 180° rotation on icon transition between portrait expand and landscape collapse.
 * - Accessible 48dp touch target standard.
 */
@Composable
fun AnimatedNavyGlassFullscreenButton(
    isFullscreen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth tactile spring scale bounce
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glass_button_scale"
    )

    // Animated white hover / click glow overlay for tactile feel
    val whiteHoverAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.38f else 0.0f,
        animationSpec = tween(durationMillis = 150),
        label = "glass_button_hover"
    )

    // Smooth 180° rotation transition when toggling fullscreen/rotation
    val iconRotation by animateFloatAsState(
        targetValue = if (isFullscreen) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glass_button_rotation"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color(0x990A192F)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xE60F2548), // Rich deep navy glass
                        Color(0xD90A1424)  // Obsidian midnight base
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x99FFFFFF), // Crisp glass highlight
                            Color(0x334A90E2), // Sapphire refraction
                            Color(0x66FFFFFF)  // Lower rim reflection
                        )
                    )
                ),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
            .size(42.dp)
            .testTag(if (isFullscreen) "detail_fullscreen_exit_button" else "detail_fullscreen_expand_button"),
        contentAlignment = Alignment.Center
    ) {
        // Animated White Hover / Click Glow Layer
        if (whiteHoverAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = whiteHoverAlpha))
            )
        }

        // Crisp luminous icon with animated rotation
        Icon(
            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Expand Fullscreen",
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    rotationZ = iconRotation
                }
        )
    }
}

@Composable
fun TabbedDropdownEpisodeSelector(
    movie: Movie,
    selectedEpisodeId: String?,
    episodeProgressMap: Map<String, EpisodeDownloadProgress> = emptyMap(),
    onSelectEpisode: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (movie.episodes.isEmpty()) return

    val seasons = remember(movie.episodes) {
        movie.episodes.map { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeason by remember(movie.id) {
        mutableStateOf(seasons.firstOrNull() ?: 1)
    }

    val episodesInCurrentSeason = remember(movie.episodes, selectedSeason) {
        movie.episodes.filter { it.seasonNumber == selectedSeason }.sortedBy { it.episodeNumber }
    }

    val activeEpisode = remember(movie.episodes, selectedEpisodeId) {
        movie.episodes.firstOrNull { it.id == selectedEpisodeId }
            ?: episodesInCurrentSeason.firstOrNull()
    }

    var isEpisodeDropdownExpanded by remember { mutableStateOf(false) }
    var isSeasonDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(BorderStroke(1.2.dp, CinematicRed.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("detail_series_tabbed_dropdown_selector")
    ) {
        // TOP HEADER: Title + Season Picker / Badge + Total Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = CinematicRed.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = CinematicRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Episodes & Seasons",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${movie.episodes.size} Total Episodes",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Season Selector Dropdown (if multiple seasons)
            if (seasons.size > 1) {
                Box {
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .clickable { isSeasonDropdownExpanded = true }
                            .testTag("season_dropdown_trigger")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Season $selectedSeason",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isSeasonDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Select Season",
                                tint = CinematicRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isSeasonDropdownExpanded,
                        onDismissRequest = { isSeasonDropdownExpanded = false },
                        modifier = Modifier.background(SurfaceElevated)
                    ) {
                        seasons.forEach { season ->
                            val isSelected = season == selectedSeason
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Season $season",
                                        color = if (isSelected) CinematicRed else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    selectedSeason = season
                                    isSeasonDropdownExpanded = false
                                    val firstEpInSeason = movie.episodes.firstOrNull { it.seasonNumber == season }
                                    if (firstEpInSeason != null) {
                                        onSelectEpisode(firstEpInSeason)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. INTERACTIVE DROPDOWN EPISODE SELECTOR TRIGGER
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CinematicRed.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEpisodeDropdownExpanded = true }
                    .testTag("episode_dropdown_selector_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = CinematicRed,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Playing",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (activeEpisode != null) {
                                    if (activeEpisode.title.startsWith("Episode", ignoreCase = true)) {
                                        activeEpisode.title
                                    } else {
                                        "Episode ${activeEpisode.episodeNumber}: ${activeEpisode.title}"
                                    }
                                } else {
                                    "Select Episode"
                                },
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeEpisode != null) {
                                Text(
                                    text = "${activeEpisode.durationMinutes} min • Playing now",
                                    color = CinematicRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CinematicRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "SELECT",
                                color = CinematicRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isEpisodeDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown Menu",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Dropdown Menu popup listing all episodes for 1-tap switching
            DropdownMenu(
                expanded = isEpisodeDropdownExpanded,
                onDismissRequest = { isEpisodeDropdownExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(SurfaceElevated)
            ) {
                episodesInCurrentSeason.forEach { ep ->
                    val isSelected = (ep.id == selectedEpisodeId)
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.PlayArrow else Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = if (isSelected) CinematicRed else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (ep.title.startsWith("Episode", ignoreCase = true)) {
                                                ep.title
                                            } else {
                                                "Episode ${ep.episodeNumber}: ${ep.title}"
                                            },
                                            color = if (isSelected) CinematicRed else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${ep.durationMinutes} min",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                val epProgress = episodeProgressMap[ep.id]
                                val isEpDownloading = epProgress?.isDownloading == true
                                val isEpCached = epProgress?.isCompleted == true

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isEpDownloading) {
                                        Surface(
                                            color = CinematicRed.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, CinematicRed.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "${epProgress.progressPercent}% ⬇",
                                                color = CinematicRed,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else if (isEpCached) {
                                        Surface(
                                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "✓ CACHED",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    if (isSelected) {
                                        Surface(
                                            color = CinematicRed,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "PLAYING",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onClick = {
                            isEpisodeDropdownExpanded = false
                            onSelectEpisode(ep)
                        },
                        modifier = Modifier.testTag("dropdown_item_episode_${ep.episodeNumber}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. TABBED EPISODE QUICK-SWITCH STRIP
        Text(
            text = "Quick Episode Tabs:",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(episodesInCurrentSeason) { ep ->
                val isSelected = (ep.id == selectedEpisodeId)
                val epProgress = episodeProgressMap[ep.id]
                val isEpDownloading = epProgress?.isDownloading == true
                val isEpCached = epProgress?.isCompleted == true

                Surface(
                    color = if (isSelected) CinematicRed else SurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f))
                    } else if (isEpDownloading) {
                        BorderStroke(1.dp, CinematicRed)
                    } else if (isEpCached) {
                        BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                    } else {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    },
                    modifier = Modifier
                        .clickable {
                            onSelectEpisode(ep)
                        }
                        .testTag("tabbed_episode_chip_${ep.episodeNumber}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.PlayArrow else Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else if (isEpDownloading) CinematicRed else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEpDownloading) {
                                "Ep ${ep.episodeNumber} (${epProgress.progressPercent}%)"
                            } else if (isEpCached) {
                                "Ep ${ep.episodeNumber} ✓"
                            } else {
                                "Ep ${ep.episodeNumber}"
                            },
                            color = if (isSelected) Color.White else if (isEpDownloading) CinematicRed else if (isEpCached) Color(0xFF4CAF50) else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected || isEpDownloading) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${ep.durationMinutes}m",
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieEpisodesSection(
    movie: Movie,
    downloads: List<DownloadItem>,
    episodeProgressMap: Map<String, EpisodeDownloadProgress> = emptyMap(),
    selectedEpisodeId: String? = null,
    onSelectEpisode: ((Episode) -> Unit)? = null,
    onPlayEpisode: (Episode) -> Unit,
    onDownloadEpisode: (Episode) -> Unit,
    onCancelDownloadEpisode: ((Episode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val seasons = remember(movie.episodes) {
        movie.episodes.map { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeason by remember(movie.id) {
        mutableStateOf(seasons.firstOrNull() ?: 1)
    }

    val episodesInSeason = remember(movie.episodes, selectedSeason) {
        movie.episodes.filter { it.seasonNumber == selectedSeason }.sortedBy { it.episodeNumber }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = CinematicRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Episodes & Seasons",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${movie.episodes.size} Total Episodes",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (seasons.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(seasons) { season ->
                    val isSelected = season == selectedSeason
                    Surface(
                        color = if (isSelected) CinematicRed else SurfaceDark,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { selectedSeason = season }
                    ) {
                        Text(
                            text = "Season $season",
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            episodesInSeason.forEach { ep ->
                val epDownload = downloads.find {
                    (it.episodeId == ep.id || it.id == "${movie.id}_ep_${ep.id}") && it.movieId == movie.id
                }
                val epWorkProgress = episodeProgressMap[ep.id]

                EpisodeDetailCard(
                    episode = ep,
                    movie = movie,
                    isSelected = (ep.id == selectedEpisodeId),
                    downloadItem = epDownload,
                    workProgress = epWorkProgress,
                    onPlay = {
                        onSelectEpisode?.invoke(ep)
                        onPlayEpisode(ep)
                    },
                    onDownload = { onDownloadEpisode(ep) },
                    onCancelDownload = { onCancelDownloadEpisode?.invoke(ep) }
                )
            }
        }
    }
}

@Composable
fun EpisodeDetailCard(
    episode: Episode,
    movie: Movie,
    isSelected: Boolean = false,
    downloadItem: DownloadItem?,
    workProgress: EpisodeDownloadProgress? = null,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coverUrl = movie.coverUrl

    val isDownloading = workProgress?.isDownloading == true || downloadItem?.status == DownloadStatus.DOWNLOADING
    val isCompleted = workProgress?.isCompleted == true || downloadItem?.status == DownloadStatus.COMPLETED

    val progressFraction = when {
        isCompleted -> 1.0f
        workProgress?.isDownloading == true -> workProgress.progressFraction
        downloadItem?.status == DownloadStatus.DOWNLOADING -> downloadItem.progress
        else -> 0f
    }.coerceIn(0f, 1f)

    val progressPercent = when {
        isCompleted -> 100
        workProgress?.isDownloading == true -> workProgress.progressPercent
        downloadItem?.status == DownloadStatus.DOWNLOADING -> (downloadItem.progress * 100).toInt()
        else -> 0
    }.coerceIn(0, 100)

    val bytesDownloaded = when {
        workProgress != null && workProgress.bytesDownloaded > 0L -> workProgress.bytesDownloaded
        downloadItem != null && downloadItem.downloadedBytes > 0L -> downloadItem.downloadedBytes
        else -> (progressFraction * episode.fileSizeMb * 1024L * 1024L).toLong()
    }

    val totalBytes = when {
        workProgress != null && workProgress.totalBytes > 0L -> workProgress.totalBytes
        downloadItem != null && downloadItem.totalBytes > 0L -> downloadItem.totalBytes
        episode.fileSizeMb > 0 -> episode.fileSizeMb * 1024L * 1024L
        else -> 0L
    }

    Surface(
        color = if (isSelected) SurfaceElevated else SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = when {
            isSelected -> BorderStroke(1.5.dp, CinematicRed)
            isDownloading -> BorderStroke(1.dp, CinematicRed.copy(alpha = 0.5f))
            else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("detail_episode_card_${episode.episodeNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail with overlay Play icon
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = com.example.util.MovieCoverUtils.resolveCoverUrl(movie.title, coverUrl, movie.genres),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isSelected) CinematicRed.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Episode",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (episode.title.startsWith("Episode", ignoreCase = true)) {
                                episode.title
                            } else {
                                "Episode ${episode.episodeNumber}: ${episode.title}"
                            },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isDownloading) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = CinematicRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, CinematicRed.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { progressFraction },
                                        color = CinematicRed,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$progressPercent%",
                                        color = CinematicRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (isCompleted) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF4CAF50).copy(alpha = 0.18f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "CACHED",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = CinematicRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "PLAYING",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${episode.durationMinutes} min • ${episode.fileSizeMb} MB",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Download / Action Button for Episode
                if (isDownloading) {
                    IconButton(
                        onClick = { onCancelDownload?.invoke() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Download",
                            tint = CinematicRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (isCompleted) {
                    IconButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Cached Locally",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Episode",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Real-time Download Progress Indicator Box
            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = SurfaceElevated.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CinematicRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Caching episode locally...",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$progressPercent%",
                                color = CinematicRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CinematicRed,
                            trackColor = SurfaceDark
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (totalBytes > 0) "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)}" else "${formatBytes(bytesDownloaded)} downloaded",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            val speed = workProgress?.speedText.orEmpty()
                            Text(
                                text = if (speed.isNotBlank()) speed else "Downloading...",
                                color = AmberGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (isCompleted) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Available offline • ${formatBytes(totalBytes)}",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (episode.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = episode.description,
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}
