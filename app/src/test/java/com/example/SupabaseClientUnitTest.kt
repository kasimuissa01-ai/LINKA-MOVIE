package com.example

import com.example.data.remote.CloudflareR2PresignedClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
