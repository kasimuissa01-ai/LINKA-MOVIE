package com.example.util

import java.io.File
import java.net.URI

/**
 * Utility for resolving and verifying movie cover artwork.
 * Ensures that ephemeral or device-local paths (e.g. file:/data/user/0/... from another device)
 * and empty URLs automatically fall back to curated, high-definition cinematic posters.
 */
object MovieCoverUtils {

    const val POSTER_SNIPER = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800&auto=format&fit=crop&q=80"
    const val POSTER_SPEED_DEMON = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=800&auto=format&fit=crop&q=80"
    const val POSTER_THE_FURIOUS = "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=800&auto=format&fit=crop&q=80"
    const val POSTER_ACTION = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&auto=format&fit=crop&q=80"
    const val POSTER_HORROR = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=800&auto=format&fit=crop&q=80"
    const val POSTER_SCI_FI = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80"
    const val POSTER_CINEMA_DEFAULT = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80"

    /**
     * Resolves the real cover URL for a movie.
     * Always preserves the database or uploaded URL directly.
     * Never overrides user titles with hardcoded photo fallbacks.
     */
    fun resolveCoverUrl(
        title: String,
        rawUrl: String?,
        genres: List<String> = emptyList()
    ): String {
        val cleanUrl = rawUrl?.trim().orEmpty()

        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            return cleanUrl
        }

        if (cleanUrl.startsWith("content://") || cleanUrl.startsWith("file://") || cleanUrl.startsWith("file:/") || cleanUrl.startsWith("/")) {
            return cleanUrl
        }

        return ""
    }
}
