package com.example.util

/**
 * Utility for resolving the exact uploaded movie cover artwork.
 * Strictly preserves the authentic uploaded image without any fake fallback images.
 */
object MovieCoverUtils {

    /**
     * Resolves the real cover URL for a movie from its raw URL or R2 key.
     * Always returns the authentic user-uploaded URL or R2 CDN URL.
     * Never injects mock, placeholder, or fake images.
     */
    fun resolveCoverUrl(
        title: String? = null,
        rawUrl: String?,
        genres: List<String> = emptyList()
    ): String {
        val cleanUrl = rawUrl?.trim().orEmpty()
        if (cleanUrl.isBlank()) return ""

        // 1. Direct Content or File URI
        if (cleanUrl.startsWith("content://") || cleanUrl.startsWith("file://") || cleanUrl.startsWith("file:/") || cleanUrl.startsWith("/")) {
            return cleanUrl
        }

        // 2. Direct HTTP / HTTPS link
        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            // If it's an R2 storage endpoint, convert to the public worker URL
            if (cleanUrl.contains(".r2.cloudflarestorage.com") || cleanUrl.contains(".r2.dev/")) {
                val key = R2UrlUtils.extractKeyFromAnyUrl(cleanUrl)
                if (key.isNotBlank()) {
                    return R2UrlUtils.buildUrl(key)
                }
            }
            return cleanUrl
        }

        // 3. R2 Key (e.g., "covers/image.jpg")
        return R2UrlUtils.buildUrl(cleanUrl)
    }
}
