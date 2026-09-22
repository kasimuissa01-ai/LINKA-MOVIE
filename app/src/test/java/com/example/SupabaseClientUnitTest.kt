package com.example

import com.example.data.remote.CloudflareR2PresignedClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseClientUnitTest {

    @Test
    fun testPresignedDownloadUrlGeneration() {
        val client = CloudflareR2PresignedClient()
        val url = client.getPresignedDownloadUrl("movies/neon_horizon.mp4")
        assertTrue(url.contains("get-download-url"))
        assertTrue(url.contains("r2ObjectKey=movies/neon_horizon.mp4"))
    }

    @Test
    fun testMultipartUploadInitiation() = runBlocking {
        val client = CloudflareR2PresignedClient()
        val session = client.initiateMultipartUpload(
            movieId = "m_test_123",
            movieTitle = "Test Movie",
            videoKey = "movies/test.mp4",
            totalBytes = 25 * 1024 * 1024L, // 25MB
            chunkSize = 10 * 1024 * 1024L // 10MB
        )

        assertNotNull(session.uploadId)
        assertEquals(3, session.parts.size) // 10MB + 10MB + 5MB = 3 parts
        assertEquals(1, session.parts[0].partNumber)
        assertEquals(2, session.parts[1].partNumber)
        assertEquals(3, session.parts[2].partNumber)
    }

    @Test
    fun testSupabaseMovieEntityMapping() {
        val entity = com.example.data.remote.model.SupabaseMovieEntity(
            id = "m_test_99",
            title = "Inception Protocol",
            description = "A mind-bending heist thriller.",
            coverKey = "posters/inception.jpg",
            coverUrl = "https://example.com/posters/inception.jpg",
            videoStreamUrl = "https://pub-5399f62037f94260b0f54c88a9297134.r2.dev/movies/inception.mp4",
            videoKey = "movies/inception.mp4",
            genres = listOf("Sci-Fi", "Action"),
            durationMinutes = 148,
            fileSizeMb = 1800,
            releaseYear = 2026,
            rating = 9.1
        )

        // Verify direct fields and convenience aliases
        assertEquals("m_test_99", entity.id)
        assertEquals("Inception Protocol", entity.title)
        assertEquals("A mind-bending heist thriller.", entity.description)
        assertEquals("https://movie-cdn.grapherkidd0.workers.dev/posters/inception.jpg", entity.thumbnailUrl)
        assertEquals("https://example.com/posters/inception.jpg", entity.coverUrl)
        assertEquals("https://movie-cdn.grapherkidd0.workers.dev/movies/inception.mp4", entity.r2StreamingUrl)
        assertEquals("https://pub-5399f62037f94260b0f54c88a9297134.r2.dev/movies/inception.mp4", entity.videoStreamUrl)

        // Verify domain conversion
        val domainMovie = entity.toDomain()
        assertEquals(entity.id, domainMovie.id)
        assertEquals(entity.title, domainMovie.title)
        assertEquals("https://movie-cdn.grapherkidd0.workers.dev/posters/inception.jpg", domainMovie.coverUrl)
        assertEquals("https://movie-cdn.grapherkidd0.workers.dev/movies/inception.mp4", domainMovie.videoStreamUrl)

        // Verify JSON roundtrip
        val json = entity.toJsonObject()
        val parsed = com.example.data.remote.model.SupabaseMovieEntity.fromJsonObject(json)
        assertEquals(entity.id, parsed.id)
        assertEquals(entity.title, parsed.title)
        assertEquals(entity.thumbnailUrl, parsed.thumbnailUrl)
        assertEquals(entity.r2StreamingUrl, parsed.r2StreamingUrl)
    }

    @Test
    fun testPlayerUiStateLoadingStages() {
        val initialState = com.example.presentation.viewmodel.PlayerUiState()
        assertTrue(initialState.isLoading)
        assertTrue(initialState.isResolvingStreamUrl)
        assertEquals("Buffering cinema stream...", initialState.loadingStage)

        val fetchingState = initialState.copy(
            loadingStage = "Fetching stream URL from Supabase repository..."
        )
        assertTrue(fetchingState.isResolvingStreamUrl)
        assertEquals("Fetching stream URL from Supabase repository...", fetchingState.loadingStage)

        val readyState = fetchingState.copy(
            isLoading = false,
            isResolvingStreamUrl = false,
            loadingStage = ""
        )
        assertFalse(readyState.isLoading)
        assertFalse(readyState.isResolvingStreamUrl)
        assertEquals("", readyState.loadingStage)
    }
}
