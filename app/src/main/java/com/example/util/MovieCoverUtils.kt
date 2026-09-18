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
     * Resolves a guaranteed valid, visible image URL for any movie.
     *
     * 1. If [rawUrl] is a remote HTTP/HTTPS image, it is returned directly.
     * 2. If [rawUrl] is a local file URI (file:/ or /), verifies if the file exists on this device's storage.
     *    If missing (e.g. created on an admin's phone), falls back to a curated poster.
     * 3. If [rawUrl] is blank, provides a high-resolution poster tailored to the title or genre.
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

        if (cleanUrl.startsWith("content://")) {
            return cleanUrl
        }

        if (cleanUrl.startsWith("file://") || cleanUrl.startsWith("file:/") || cleanUrl.startsWith("/")) {
            val exists = runCatching {
                val path = if (cleanUrl.startsWith("file:")) {
                    URI(cleanUrl).path ?: cleanUrl.removePrefix("file:")
                } else {
                    cleanUrl
                }
                File(path).exists()
            }.getOrDefault(false)

            if (exists) {
                return cleanUrl
            }
        }

        // Curated title matches
        val lowerTitle = title.lowercase()
        return when {
            lowerTitle.contains("sniper") || lowerTitle.contains("fierce") -> POSTER_SNIPER
            lowerTitle.contains("speed demon") || lowerTitle.contains("demon") -> POSTER_SPEED_DEMON
            lowerTitle.contains("furious") -> POSTER_THE_FURIOUS
            genres.any { it.equals("Horror", ignoreCase = true) || it.equals("Thriller", ignoreCase = true) } -> POSTER_HORROR
            genres.any { it.equals("Sci-Fi", ignoreCase = true) || it.equals("Adventure", ignoreCase = true) } -> POSTER_SCI_FI
            genres.any { it.equals("Action", ignoreCase = true) } -> POSTER_ACTION
            else -> POSTER_CINEMA_DEFAULT
        }
    }
}
