package com.example.util

/**
 * Universal utility for resolving movie poster artwork.
 * Resolves uploaded user cover art, R2 CDN URLs, and provides fallback movie artwork
 * by title/genre so movie posters always render crisply without blank black placeholders.
 */
object MovieCoverUtils {

    /**
     * Resolves the real cover URL for a movie from its raw URL, R2 key, or title.
     * Always prioritizes the user's authentic uploaded image or CDN asset.
     */
    fun resolveCoverUrl(
        title: String? = null,
        rawUrl: String?,
        genres: List<String> = emptyList()
    ): String {
        val cleanUrl = rawUrl?.trim().orEmpty()

        // 1. Direct Content or File URI
        if (cleanUrl.startsWith("content://") || cleanUrl.startsWith("file://") || cleanUrl.startsWith("file:/") || (cleanUrl.startsWith("/") && !cleanUrl.startsWith("//"))) {
            return cleanUrl
        }

        // 2. Direct HTTP / HTTPS link
        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            // If it's an R2 storage endpoint or worker URL, canonicalize
            if (cleanUrl.contains(".r2.cloudflarestorage.com") || cleanUrl.contains(".r2.dev/")) {
                val key = R2UrlUtils.extractKeyFromAnyUrl(cleanUrl)
                if (key.isNotBlank()) {
                    return R2UrlUtils.buildUrl(key)
                }
            }
            return cleanUrl
        }

        // 3. R2 Key (e.g., "covers/image.jpg" or "videos/...")
        if (cleanUrl.isNotBlank()) {
            return R2UrlUtils.buildUrl(cleanUrl)
        }

        // 4. Intelligent fallback based on movie title
        val normalizedTitle = title?.trim()?.lowercase().orEmpty()
        if (normalizedTitle.isNotBlank()) {
            when {
                normalizedTitle.contains("lioness") || normalizedTitle.contains("special ops") ->
                    return "https://image.tmdb.org/t/p/w500/rDe0c5XW4Y9k33W6v60V9l7fEee.jpg"

                normalizedTitle.contains("furious") ->
                    return "https://image.tmdb.org/t/p/w500/fiVW06jE7z9YBoOCpVe4B9I8P3n.jpg"

                normalizedTitle.contains("speed") ->
                    return "https://image.tmdb.org/t/p/w500/6wL9jQ8aV5w9G4L6eMhGq6V7Z6D.jpg"

                normalizedTitle.contains("dune") ->
                    return "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg"

                normalizedTitle.contains("oppenheimer") ->
                    return "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"

                normalizedTitle.contains("cyber") || normalizedTitle.contains("runner") ->
                    return "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop"

                normalizedTitle.contains("space") || normalizedTitle.contains("star") ->
                    return "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop"

                normalizedTitle.contains("shadow") || normalizedTitle.contains("dark") ->
                    return "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop"

                normalizedTitle.contains("abyss") || normalizedTitle.contains("ocean") ->
                    return "https://images.unsplash.com/photo-1682687220063-4742bd7fd538?w=600&auto=format&fit=crop"
            }
        }

        // 5. Genre-based aesthetic poster fallback
        val primaryGenre = genres.firstOrNull()?.lowercase() ?: ""
        return when {
            primaryGenre.contains("action") -> "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop"
            primaryGenre.contains("sci-fi") -> "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=600&auto=format&fit=crop"
            primaryGenre.contains("horror") || primaryGenre.contains("thriller") -> "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=600&auto=format&fit=crop"
            primaryGenre.contains("drama") -> "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&auto=format&fit=crop"
            primaryGenre.contains("comedy") -> "https://images.unsplash.com/photo-1514306191717-452ec28c7814?w=600&auto=format&fit=crop"
            else -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&auto=format&fit=crop"
        }
    }
}
