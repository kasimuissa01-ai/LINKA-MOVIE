package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.OfflineStoragePaths
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieRoomDatabase
import com.example.data.local.WatchHistoryManager
import com.example.data.repository.MovieRepository
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import com.example.presentation.viewmodel.PlayerViewModel
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResumePlaybackTest {

    private lateinit var context: Context
    private lateinit var database: MovieRoomDatabase
    private lateinit var repository: MovieRepository
    private lateinit var historyManager: WatchHistoryManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("movieroom_watch_history", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        database = Room.inMemoryDatabaseBuilder(context, MovieRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = MovieRepository(
            movieDao = database.movieDao(),
            downloadDao = database.downloadDao(),
            uploadStateDao = database.uploadStateDao(),
            appContext = context
        )
        historyManager = WatchHistoryManager.getInstance(context)
        historyManager.setUser("guest")
    }

    @After
    fun teardown() {
        database.close()
        context.getSharedPreferences("movieroom_watch_history", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "offline_downloads").deleteRecursively()
    }

    private fun sampleMovie(id: String = "mov_25", streamUrl: String = ""): Movie {
        return Movie(
            id = id,
            title = "Midnight Horizon",
            description = "Offline & online test movie",
            genres = listOf("Sci-Fi"),
            coverUrl = "https://example.com/cover.jpg",
            videoKey = "videos/midnight.mp4",
            videoStreamUrl = streamUrl,
            durationMinutes = 120,
            fileSizeMb = 500L,
            releaseYear = 2025,
            rating = 8.8,
            cast = listOf("Actor A"),
            isFeatured = true
        )
    }

    @Test
    fun `watch progress at 25 minutes persists locally offline and recovers cleanly`() {
        val movie = sampleMovie(id = "mov_offline_25")
        val twentyFiveMinutesMs = 25L * 60L * 1000L // 1,500,000 ms
        val totalDurationMs = 120L * 60L * 1000L

        historyManager.recordWatchProgress(
            userId = "guest",
            movie = movie,
            episode = null,
            positionMs = twentyFiveMinutesMs,
            durationMs = totalDurationMs,
            commitNow = true
        )

        val savedPos = historyManager.getResumablePositionMs("guest", movie.id, null)
        assertEquals(twentyFiveMinutesMs, savedPos)
        assertEquals("25:00", WatchHistoryManager.formatTimestamp(savedPos))
        assertEquals("1:25:00", WatchHistoryManager.formatTimestamp((85L * 60L * 1000L)))
    }

    @Test
    fun `positions survive beyond 30 recent history items via position map`() {
        val twentyFiveMinutesMs = 25L * 60L * 1000L
        val firstMovie = sampleMovie(id = "mov_first_saved")

        historyManager.recordWatchProgress(
            userId = "user_1",
            movie = firstMovie,
            episode = null,
            positionMs = twentyFiveMinutesMs,
            durationMs = 90L * 60L * 1000L,
            commitNow = true
        )

        // Record 35 more movies to evict firstMovie from the 30-item ordered history list
        for (i in 1..35) {
            historyManager.recordWatchProgress(
                userId = "user_1",
                movie = sampleMovie(id = "mov_other_$i"),
                episode = null,
                positionMs = 120_000L,
                durationMs = 90L * 60L * 1000L,
                commitNow = true
            )
        }

        // First movie should STILL resume at 25:00 via local positions map
        val recovered = historyManager.getResumablePositionMs("user_1", firstMovie.id, null)
        assertEquals(twentyFiveMinutesMs, recovered)
    }

    @Test
    fun `completed movies and sub-3-second watches reset resume position to zero`() {
        val shortWatchMovie = sampleMovie(id = "mov_short")
        historyManager.recordWatchProgress(
            userId = "guest",
            movie = shortWatchMovie,
            episode = null,
            positionMs = 2_000L,
            durationMs = 60L * 60L * 1000L,
            commitNow = true
        )
        assertEquals(0L, historyManager.getResumablePositionMs("guest", shortWatchMovie.id, null))

        val finishedMovie = sampleMovie(id = "mov_done")
        val duration = 100L * 60L * 1000L
        historyManager.recordWatchProgress(
            userId = "guest",
            movie = finishedMovie,
            episode = null,
            positionMs = (duration * 0.97).toLong(),
            durationMs = duration,
            commitNow = true
        )
        assertEquals(0L, historyManager.getResumablePositionMs("guest", finishedMovie.id, null))
    }

    @Test
    fun `episodes maintain independent saved timestamps and synth_s1_e1 normalizes to movie`() {
        val series = sampleMovie(id = "series_1")
        val ep1Synth = Episode(id = "synth_s1_e1_series_1", movieId = "series_1", episodeNumber = 1, seasonNumber = 1, title = "Ep 1")
        val ep2 = Episode(id = "ep_2_id", movieId = "series_1", episodeNumber = 2, seasonNumber = 1, title = "Ep 2")

        historyManager.recordWatchProgress("guest", series, ep1Synth, 600_000L, 2_400_000L, commitNow = true)
        historyManager.recordWatchProgress("guest", series, ep2, 1_500_000L, 2_400_000L, commitNow = true)

        assertEquals(600_000L, historyManager.getResumablePositionMs("guest", series.id, null))
        assertEquals(600_000L, historyManager.getResumablePositionMs("guest", series.id, "synth_s1_e1_series_1"))
        assertEquals(1_500_000L, historyManager.getResumablePositionMs("guest", series.id, ep2.id))
    }

    @Test
    fun `PlayerViewModel prompts Resume at 25_00 for offline movie and clears on Start Over`() = runBlocking {
        val uid = repository.userSession.value.uid
        historyManager.setUser(uid)
        val userDir = OfflineStoragePaths.getUserDownloadDir(context, uid)
        userDir.mkdirs()
        val offlineFile = File(userDir, "movie_mov_offline_resume.mp4")
        RandomAccessFile(offlineFile, "rw").use { it.setLength(2L * 1024L * 1024L) }

        val movie = sampleMovie(id = "mov_offline_resume", streamUrl = offlineFile.absolutePath)
        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "${uid}_${movie.id}",
                movieId = movie.id,
                episodeId = null,
                movieTitle = movie.title,
                coverUrl = movie.coverUrl,
                localFilePath = offlineFile.absolutePath,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                downloadedBytes = offlineFile.length(),
                totalBytes = offlineFile.length(),
                userId = uid
            )
        )

        val twentyFiveMinutesMs = 25L * 60L * 1000L
        historyManager.recordWatchProgress(
            userId = uid,
            movie = movie,
            episode = null,
            positionMs = twentyFiveMinutesMs,
            durationMs = 120L * 60L * 1000L,
            commitNow = true
        )

        val viewModel = PlayerViewModel(repository)
        viewModel.initializePlayer(context, movie, initialPositionMs = 0L, episodeId = null)

        var attempts = 0
        while (viewModel.uiState.value.isResolvingStreamUrl && attempts < 60) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(30)
            attempts++
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val state = viewModel.uiState.value
        assertTrue("Offline playback should be active", state.isOfflinePlayback)
        assertTrue("Resume prompt should appear when reopening at saved 25:00 position", state.showResumePrompt)
        assertEquals(twentyFiveMinutesMs, state.pendingResumePositionMs)
        assertNull(state.resumedFromLabel)

        // Choosing Start Over resets saved local position to 0
        viewModel.restartPlaybackFromBeginning()
        assertFalse(viewModel.uiState.value.showResumePrompt)
        assertEquals(0L, historyManager.getResumablePositionMs(uid, movie.id, null))

        viewModel.releasePlayer()
    }

    @Test
    fun `PlayerViewModel auto-resumes with banner when opened from Continue Watching`() = runBlocking {
        val uid = repository.userSession.value.uid
        historyManager.setUser(uid)
        val userDir = OfflineStoragePaths.getUserDownloadDir(context, uid)
        userDir.mkdirs()
        val offlineFile = File(userDir, "movie_mov_continue_25.mp4")
        RandomAccessFile(offlineFile, "rw").use { it.setLength(2L * 1024L * 1024L) }

        val movie = sampleMovie(id = "mov_continue_25", streamUrl = offlineFile.absolutePath)
        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "${uid}_${movie.id}",
                movieId = movie.id,
                episodeId = null,
                movieTitle = movie.title,
                coverUrl = movie.coverUrl,
                localFilePath = offlineFile.absolutePath,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                downloadedBytes = offlineFile.length(),
                totalBytes = offlineFile.length(),
                userId = uid
            )
        )

        val twentyFiveMinutesMs = 25L * 60L * 1000L
        val viewModel = PlayerViewModel(repository)
        viewModel.initializePlayer(context, movie, initialPositionMs = twentyFiveMinutesMs, episodeId = null)

        var attempts = 0
        while (viewModel.uiState.value.isResolvingStreamUrl && attempts < 60) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(30)
            attempts++
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val state = viewModel.uiState.value
        assertTrue(state.isOfflinePlayback)
        assertFalse("Continue Watching launch auto-resumes without blocking prompt", state.showResumePrompt)
        assertEquals("Resumed from 25:00", state.resumedFromLabel)
        assertEquals(twentyFiveMinutesMs, state.currentPositionMs)

        viewModel.releasePlayer()
    }
}
