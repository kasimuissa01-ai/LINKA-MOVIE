package com.example.util

import android.util.Log

/**
 * Universal URL canonicalization and resolver utility for Cloudflare R2 and Supabase.
 *
 * Architecture Rules:
 * - Supabase stores: ONLY the R2 object key (e.g. "videos/timestamp-file.mp4" or "covers/timestamp-cover.jpg")
 * - Android client constructs the full URL at runtime: "{R2_PUBLIC_BASE_URL}/{key}"
 * - R2_PUBLIC_BASE_URL is a single centralized constant used everywhere.
 */
object R2UrlUtils {
    private const val TAG = "R2UrlUtils"
    const val R2_PUBLIC_BASE_URL = "https://movie-cdn.grapherkidd0.workers.dev"
    const val PUBLIC_R2_DOMAIN = "movie-cdn.grapherkidd0.workers.dev"
    const val BUCKET_NAME = "stories"

    /**
     * Constructs the full public CDN URL dynamically at runtime from an R2 object key.
     * Example: "videos/1695123456789-movie.mp4" -> "https://movie-cdn.grapherkidd0.workers.dev/videos/1695123456789-movie.mp4"
     */
    fun buildUrl(key: String?): String {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isBlank()) return ""

        // 1. Local files and gallery content URIs
        if (trimmed.startsWith("content://") || trimmed.startsWith("file://") || trimmed.startsWith("/")) {
            return trimmed
        }

        // 2. Already full HTTP/HTTPS URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            // If it is a raw unauthenticated S3 API URL (*.r2.cloudflarestorage.com), convert to worker CDN URL
            if (trimmed.contains(".r2.cloudflarestorage.com")) {
                val cleanKey = extractKeyFromS3Url(trimmed)
                return if (cleanKey.isNotBlank()) "$R2_PUBLIC_BASE_URL/$cleanKey" else trimmed
            }
            // If it is an old r2.dev URL, convert to worker CDN URL for better performance and range support
            if (trimmed.contains(".r2.dev/")) {
                val cleanKey = trimmed.substringAfter(".r2.dev/").trimStart('/')
                return if (cleanKey.isNotBlank()) "$R2_PUBLIC_BASE_URL/$cleanKey" else trimmed
            }
            return trimmed
        }

        // 3. Pure R2 object key (e.g. "videos/1695123456789-movie.mp4" or "covers/...")
        val cleanKey = trimmed.trimStart('/')
        return "$R2_PUBLIC_BASE_URL/$cleanKey"
    }

    /**
     * Resolves the canonical, streamable playback URL for a movie.
     */
    fun canonicalizeStreamUrl(streamUrl: String?, videoKey: String?): String {
        val trimmedKey = videoKey?.trim().orEmpty().trimStart('/')
        if (trimmedKey.isNotBlank()) {
            return buildUrl(trimmedKey)
        }

        val trimmedStream = streamUrl?.trim().orEmpty()
        if (trimmedStream.isBlank()) return ""

        if (trimmedStream.startsWith("content://") || trimmedStream.startsWith("file://") || trimmedStream.startsWith("/")) {
            return trimmedStream
        }

        if (trimmedStream.contains(".r2.cloudflarestorage.com")) {
            val keyFromUrl = extractKeyFromS3Url(trimmedStream)
            if (keyFromUrl.isNotBlank()) {
                return buildUrl(keyFromUrl)
            }
        }

        if (trimmedStream.contains(".r2.dev/")) {
            val keyFromUrl = trimmedStream.substringAfter(".r2.dev/").trimStart('/')
            return buildUrl(keyFromUrl)
        }

        if (trimmedStream.contains("movie-cdn.grapherkidd0.workers.dev/")) {
            val keyFromUrl = trimmedStream.substringAfter("movie-cdn.grapherkidd0.workers.dev/").trimStart('/')
            return buildUrl(keyFromUrl)
        }

        return buildUrl(trimmedStream)
    }

    /**
     * Extracts the R2 object key from an S3 API endpoint URL.
     * Example: "https://fb27b7d70175201e4a9bc30bbe7ce866.r2.cloudflarestorage.com/stories/videos/123.mp4"
     * -> "videos/123.mp4"
     */
    fun extractKeyFromS3Url(s3Url: String): String {
        val pathAfterHost = s3Url.substringAfter(".r2.cloudflarestorage.com/").trimStart('/')
        return if (pathAfterHost.startsWith("$BUCKET_NAME/")) {
            pathAfterHost.removePrefix("$BUCKET_NAME/").trimStart('/')
        } else {
            pathAfterHost
        }
    }

    /**
     * Extracts or sanitizes the pure R2 object key for storage in Supabase database.
     * Guaranteed to return only the key (e.g. "videos/1695123456789-movie.mp4"), NEVER a full URL.
     */
    fun extractCleanVideoKey(videoKey: String?, streamUrl: String? = null): String {
        val trimmedKey = videoKey?.trim().orEmpty().trimStart('/')
        if (trimmedKey.isNotBlank()) {
            val sanitized = extractKeyFromAnyUrl(trimmedKey)
            if (sanitized.isNotBlank()) return sanitized
        }
        val trimmedStream = streamUrl?.trim().orEmpty()
        if (trimmedStream.isNotBlank()) {
            return extractKeyFromAnyUrl(trimmedStream)
        }
        return ""
    }

    /**
     * Extracts pure object key from any URL or key string.
     */
    fun extractKeyFromAnyUrl(raw: String): String {
        val trimmed = raw.trim().trimStart('/')
        if (trimmed.contains(".r2.cloudflarestorage.com/")) {
            return extractKeyFromS3Url(trimmed)
        }
        if (trimmed.contains(".r2.dev/")) {
            return trimmed.substringAfter(".r2.dev/").trimStart('/')
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val path = trimmed.substringAfter("://").substringAfter("/", "")
            return path.trimStart('/')
        }
        return trimmed
    }
}
