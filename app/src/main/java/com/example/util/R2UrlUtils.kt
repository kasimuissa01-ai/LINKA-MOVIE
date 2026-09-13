package com.example.util

import android.util.Log

/**
 * Universal URL canonicalization and resolver utility for Cloudflare R2 and Supabase.
 *
 * Ensures all video URLs used by ExoPlayer, OfflineDownloadManager, and MovieDetailScreen
 * always use the public CDN domain (`pub-5399f62037f94260b0f54c88a9297134.r2.dev`)
 * rather than unauthenticated AWS S3 API endpoints (`*.r2.cloudflarestorage.com`)
 * which return HTTP 400 Bad Request when streamed directly.
 */
object R2UrlUtils {
    private const val TAG = "R2UrlUtils"
    const val PUBLIC_R2_DOMAIN = "pub-5399f62037f94260b0f54c88a9297134.r2.dev"
    const val BUCKET_NAME = "stories"

    /**
     * Resolves the canonical, streamable playback URL for a movie.
     */
    fun canonicalizeStreamUrl(streamUrl: String?, videoKey: String?): String {
        val trimmedStream = streamUrl?.trim().orEmpty()
        val trimmedKey = videoKey?.trim().orEmpty().trimStart('/')

        // 1. Local files and gallery content URIs
        if (trimmedStream.startsWith("content://") || trimmedStream.startsWith("file://") || trimmedStream.startsWith("/")) {
            return trimmedStream
        }

        // 2. Cloudflare R2 raw S3 storage API endpoint (e.g. https://<accountId>.r2.cloudflarestorage.com/stories/videos/...)
        // S3 API endpoints require SigV4 headers and return HTTP 400 when fetched via ExoPlayer.
        // Convert to public CDN URL immediately.
        if (trimmedStream.contains(".r2.cloudflarestorage.com")) {
            val keyFromUrl = extractKeyFromS3Url(trimmedStream)
            val effectiveKey = if (trimmedKey.isNotBlank()) trimmedKey else keyFromUrl
            if (effectiveKey.isNotBlank()) {
                val cdnUrl = "https://$PUBLIC_R2_DOMAIN/$effectiveKey"
                Log.d(TAG, "Transformed raw S3 endpoint to public R2 CDN: $cdnUrl")
                return cdnUrl
            }
        }

        // 3. Already a public R2.dev URL
        if (trimmedStream.contains("r2.dev")) {
            return if (trimmedStream.startsWith("http://") || trimmedStream.startsWith("https://")) {
                trimmedStream
            } else {
                "https://$trimmedStream"
            }
        }

        // 4. Other valid public HTTP/HTTPS URLs (e.g., standard CDN / web streams)
        if (trimmedStream.isNotBlank() &&
            (trimmedStream.startsWith("http://") || trimmedStream.startsWith("https://")) &&
            !trimmedStream.contains("bunny/trailer.mp4") &&
            !trimmedStream.contains("BigBuckBunny.mp4")
        ) {
            return trimmedStream
        }

        // 5. If videoKey is present, build canonical public R2 URL
        if (trimmedKey.isNotBlank()) {
            val cdnUrl = "https://$PUBLIC_R2_DOMAIN/$trimmedKey"
            Log.d(TAG, "Constructed public R2 CDN URL from videoKey '$trimmedKey': $cdnUrl")
            return cdnUrl
        }

        return trimmedStream
    }

    /**
     * Extracts the R2 object key from an S3 API endpoint URL.
     * Example: "https://fb27b7d70175201e4a9bc30bbe7ce866.r2.cloudflarestorage.com/stories/videos/123.mp4"
     * -> "videos/123.mp4"
     */
    fun extractKeyFromS3Url(s3Url: String): String {
        val pathAfterHost = s3Url.substringAfter(".r2.cloudflarestorage.com/").trimStart('/')
        // If path starts with bucket name (e.g. "stories/"), strip it
        return if (pathAfterHost.startsWith("$BUCKET_NAME/")) {
            pathAfterHost.removePrefix("$BUCKET_NAME/").trimStart('/')
        } else {
            pathAfterHost
        }
    }

    /**
     * Extracts or sanitizes the R2 object key for storage in database.
     */
    fun extractCleanVideoKey(videoKey: String?, streamUrl: String?): String {
        val trimmedKey = videoKey?.trim().orEmpty().trimStart('/')
        if (trimmedKey.isNotBlank()) {
            return trimmedKey
        }
        val trimmedStream = streamUrl?.trim().orEmpty()
        if (trimmedStream.contains(".r2.cloudflarestorage.com/")) {
            return extractKeyFromS3Url(trimmedStream)
        }
        if (trimmedStream.contains(".r2.dev/")) {
            return trimmedStream.substringAfter(".r2.dev/").trimStart('/')
        }
        return ""
    }
}
