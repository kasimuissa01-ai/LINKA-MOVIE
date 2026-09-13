package com.example.data.remote.model

import com.example.domain.model.Movie
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kotlin entity model mapping directly to the Supabase PostgreSQL `movies` table.
 *
 * Database Table: `public.movies`
 * Provides direct mapping for:
 * - [id]: Primary key
 * - [title]: Movie title
 * - [description]: Plot synopsis
 * - [videoStreamUrl] / [r2StreamingUrl]: Public verified Cloudflare R2 streaming URL
 * - [coverUrl] / [thumbnailUrl]: High-resolution movie poster / thumbnail URL
 * - [videoKey]: Cloudflare R2 object key (e.g. "movies/title.mp4")
 * - [genres], [castMembers], [durationMinutes], [fileSizeMb], [releaseYear], [rating]
 */
@JsonClass(generateAdapter = true)
data class SupabaseMovieEntity(
    @Json(name = "id")
    val id: String,

    @Json(name = "title")
    val title: String,

    @Json(name = "description")
    val description: String = "",

    @Json(name = "cover_url")
    val coverUrl: String = "",

    @Json(name = "video_stream_url")
    val videoStreamUrl: String = "",

    @Json(name = "video_key")
    val videoKey: String = "",

    @Json(name = "genres")
    val genres: List<String> = emptyList(),

    @Json(name = "duration_minutes")
    val durationMinutes: Int = 120,

    @Json(name = "file_size_mb")
    val fileSizeMb: Long = 500L,

    @Json(name = "release_year")
    val releaseYear: Int = 2026,

    @Json(name = "rating")
    val rating: Double = 8.0,

    @Json(name = "cast_members")
    val castMembers: List<String> = emptyList(),

    @Json(name = "is_featured")
    val isFeatured: Boolean = false,

    @Json(name = "view_count")
    val viewCount: Long = 0L,

    @Json(name = "created_at")
    val createdAt: String? = null,

    @Json(name = "updated_at")
    val updatedAt: String? = null
) {
    /**
     * Explicit alias for the movie thumbnail / poster image URL.
     */
    val thumbnailUrl: String
        get() = coverUrl

    /**
     * Explicit alias for the verified Cloudflare R2 streaming URL.
     */
    val r2StreamingUrl: String
        get() = videoStreamUrl

    /**
     * Converts this Supabase table entity to the app's domain [Movie] model.
     */
    fun toDomain(): Movie {
        return Movie(
            id = id,
            title = title,
            description = description,
            genres = genres,
            coverUrl = coverUrl,
            videoKey = videoKey,
            videoStreamUrl = videoStreamUrl,
            durationMinutes = durationMinutes,
            fileSizeMb = fileSizeMb,
            releaseYear = releaseYear,
            rating = rating,
            cast = castMembers,
            isFeatured = isFeatured
        )
    }

    /**
     * Serializes this entity into a [JSONObject] matching Supabase REST API schema.
     */
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("description", description)
            put("genres", JSONArray(genres))
            put("cover_url", coverUrl)
            put("video_key", videoKey)
            put("video_stream_url", videoStreamUrl)
            put("duration_minutes", durationMinutes)
            put("file_size_mb", fileSizeMb)
            put("release_year", releaseYear)
            put("rating", rating)
            put("cast_members", JSONArray(castMembers))
            put("is_featured", isFeatured)
            put("view_count", viewCount)
        }
    }

    companion object {
        private const val DEFAULT_R2_DOMAIN = "pub-5399f62037f94260b0f54c88a9297134.r2.dev"

        /**
         * Creates a [SupabaseMovieEntity] from a domain [Movie] model,
         * automatically resolving the Cloudflare R2 stream URL if needed.
         */
        fun fromDomain(movie: Movie): SupabaseMovieEntity {
            val resolvedKey = movie.videoKey.ifBlank { "movies/${movie.id}.mp4" }.trim()
            val resolvedStreamUrl = when {
                movie.videoStreamUrl.isNotBlank() && (movie.videoStreamUrl.startsWith("http://") || movie.videoStreamUrl.startsWith("https://")) -> {
                    movie.videoStreamUrl.trim()
                }
                resolvedKey.isNotBlank() -> {
                    "https://$DEFAULT_R2_DOMAIN/${resolvedKey.removePrefix("/")}"
                }
                else -> movie.videoStreamUrl.trim()
            }

            return SupabaseMovieEntity(
                id = movie.id,
                title = movie.title,
                description = movie.description,
                coverUrl = movie.coverUrl,
                videoStreamUrl = resolvedStreamUrl,
                videoKey = resolvedKey,
                genres = movie.genres,
                durationMinutes = movie.durationMinutes,
                fileSizeMb = movie.fileSizeMb,
                releaseYear = movie.releaseYear,
                rating = movie.rating,
                castMembers = movie.cast,
                isFeatured = movie.isFeatured
            )
        }

        /**
         * Parses a JSON object directly received from Supabase REST response.
         */
        fun fromJsonObject(obj: JSONObject): SupabaseMovieEntity {
            val genresList = mutableListOf<String>()
            val genresJson = obj.optJSONArray("genres")
            if (genresJson != null) {
                for (j in 0 until genresJson.length()) {
                    genresList.add(genresJson.optString(j))
                }
            }

            val castList = mutableListOf<String>()
            val castJson = obj.optJSONArray("cast_members")
            if (castJson != null) {
                for (j in 0 until castJson.length()) {
                    castList.add(castJson.optString(j))
                }
            }

            return SupabaseMovieEntity(
                id = obj.optString("id"),
                title = obj.optString("title"),
                description = obj.optString("description", ""),
                coverUrl = obj.optString("cover_url", ""),
                videoStreamUrl = obj.optString("video_stream_url", ""),
                videoKey = obj.optString("video_key", ""),
                genres = genresList,
                durationMinutes = obj.optInt("duration_minutes", 120),
                fileSizeMb = obj.optLong("file_size_mb", 500L),
                releaseYear = obj.optInt("release_year", 2026),
                rating = obj.optDouble("rating", 8.0),
                castMembers = castList,
                isFeatured = obj.optBoolean("is_featured", false),
                viewCount = obj.optLong("view_count", 0L),
                createdAt = obj.optString("created_at").takeIf { it.isNotBlank() },
                updatedAt = obj.optString("updated_at").takeIf { it.isNotBlank() }
            )
        }
    }
}
