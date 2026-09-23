package com.example.data.remote.model

import com.example.domain.model.Episode
import com.example.domain.model.Movie
import com.example.util.R2UrlUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kotlin entity model mapping directly to the Supabase PostgreSQL `movies` table.
 *
 * Database Table: `public.movies`
 * Storage Rules:
 * - Supabase stores: ONLY the R2 object keys (video_key & cover_key) and upload_status
 * - NEVER stores full URLs in database columns
 * - The full URL is constructed at runtime: "{R2_PUBLIC_BASE_URL}/{key}"
 */
@JsonClass(generateAdapter = true)
data class SupabaseMovieEntity(
    @Json(name = "id")
    val id: String,

    @Json(name = "title")
    val title: String,

    @Json(name = "description")
    val description: String = "",

    @Json(name = "cover_key")
    val coverKey: String = "",

    @Json(name = "cover_url")
    val coverUrl: String = "",

    @Json(name = "video_key")
    val videoKey: String = "",

    @Json(name = "video_stream_url")
    val videoStreamUrl: String = "",

    @Json(name = "upload_status")
    val uploadStatus: String = "completed",

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

    @Json(name = "episodes")
    val episodes: List<Episode> = emptyList(),

    @Json(name = "created_at")
    val createdAt: String? = null,

    @Json(name = "updated_at")
    val updatedAt: String? = null
) {
    val thumbnailUrl: String
        get() = if (coverKey.isNotBlank()) R2UrlUtils.buildUrl(coverKey) else coverUrl

    val r2StreamingUrl: String
        get() = if (videoKey.isNotBlank()) R2UrlUtils.buildUrl(videoKey) else videoStreamUrl

    /**
     * Converts this Supabase table entity to domain [Movie] model.
     * URLs are constructed at runtime via R2UrlUtils.buildUrl.
     */
    fun toDomain(): Movie {
        val canonicalVideoKey = R2UrlUtils.extractCleanVideoKey(videoKey, videoStreamUrl)
        val canonicalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (coverKey.isNotBlank()) coverKey else coverUrl)
        val resolvedStream = if (canonicalVideoKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalVideoKey) else videoStreamUrl
        val resolvedCover = if (canonicalCoverKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalCoverKey) else coverUrl

        return Movie(
            id = id,
            title = title,
            description = description,
            genres = genres,
            coverKey = canonicalCoverKey,
            coverUrl = resolvedCover,
            videoKey = canonicalVideoKey,
            videoStreamUrl = resolvedStream,
            durationMinutes = durationMinutes,
            fileSizeMb = fileSizeMb,
            releaseYear = releaseYear,
            rating = rating,
            cast = castMembers,
            isFeatured = isFeatured,
            uploadStatus = uploadStatus,
            uploadDate = System.currentTimeMillis(),
            episodes = episodes
        )
    }

    /**
     * Serializes this entity into a [JSONObject] matching Supabase REST API schema.
     */
    fun toJsonObject(): JSONObject {
        val canonicalVideoKey = R2UrlUtils.extractCleanVideoKey(videoKey, videoStreamUrl)
        val canonicalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (coverKey.isNotBlank()) coverKey else coverUrl)
        val finalCoverUrl = if (canonicalCoverKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalCoverKey) else coverUrl
        val finalStreamUrl = if (canonicalVideoKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalVideoKey) else videoStreamUrl

        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("description", description)
            put("genres", JSONArray(genres))
            put("cover_url", finalCoverUrl)
            put("video_stream_url", finalStreamUrl)
            put("video_key", canonicalVideoKey)
            put("duration_minutes", durationMinutes)
            put("file_size_mb", fileSizeMb)
            put("release_year", releaseYear)
            put("rating", rating)
            put("cast_members", JSONArray(castMembers))
            put("is_featured", isFeatured)
            put("view_count", viewCount)
            if (episodes.isNotEmpty()) {
                val epArray = JSONArray()
                for (ep in episodes) {
                    val epObj = JSONObject().apply {
                        put("id", ep.id)
                        put("movieId", ep.movieId)
                        put("episodeNumber", ep.episodeNumber)
                        put("seasonNumber", ep.seasonNumber)
                        put("title", ep.title)
                        put("description", ep.description)
                        put("videoKey", ep.videoKey)
                        put("videoStreamUrl", ep.videoStreamUrl)
                        put("durationMinutes", ep.durationMinutes)
                        put("fileSizeMb", ep.fileSizeMb)
                    }
                    epArray.put(epObj)
                }
                put("episodes_json", epArray.toString())
            }
        }
    }

    companion object {
        fun fromDomain(movie: Movie): SupabaseMovieEntity {
            val canonicalVideoKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl)
            val canonicalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (movie.coverKey.isNotBlank()) movie.coverKey else movie.coverUrl)

            return SupabaseMovieEntity(
                id = movie.id,
                title = movie.title,
                description = movie.description,
                coverKey = canonicalCoverKey,
                coverUrl = movie.coverUrl,
                videoKey = canonicalVideoKey,
                videoStreamUrl = movie.videoStreamUrl,
                uploadStatus = movie.uploadStatus.ifBlank { "completed" },
                genres = movie.genres,
                durationMinutes = movie.durationMinutes,
                fileSizeMb = movie.fileSizeMb,
                releaseYear = movie.releaseYear,
                rating = movie.rating,
                castMembers = movie.cast,
                isFeatured = movie.isFeatured,
                episodes = movie.episodes
            )
        }

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

            val rawVideoKey = obj.optString("video_key", "")
            val rawVideoStreamUrl = obj.optString("video_stream_url", "")
            val cleanVideoKey = R2UrlUtils.extractCleanVideoKey(rawVideoKey, rawVideoStreamUrl)

            val rawCoverKey = obj.optString("cover_key", "")
            val rawCoverUrl = obj.optString("cover_url", "")
            val cleanCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (rawCoverKey.isNotBlank()) rawCoverKey else rawCoverUrl)

            val uploadStatus = obj.optString("upload_status", "completed").ifBlank { "completed" }

            val parsedEpisodes = mutableListOf<Episode>()
            val epJson = obj.optString("episodes_json", "").ifBlank {
                obj.optJSONArray("episodes")?.toString() ?: ""
            }
            if (epJson.isNotBlank()) {
                try {
                    val array = JSONArray(epJson)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val epKey = R2UrlUtils.extractCleanVideoKey(item.optString("videoKey", ""), item.optString("videoStreamUrl", ""))
                        val epStream = if (epKey.isNotBlank()) R2UrlUtils.buildUrl(epKey) else item.optString("videoStreamUrl", "")
                        parsedEpisodes.add(
                            Episode(
                                id = item.optString("id", "${obj.optString("id")}_ep_${i + 1}"),
                                movieId = item.optString("movieId", obj.optString("id")),
                                episodeNumber = item.optInt("episodeNumber", i + 1),
                                seasonNumber = item.optInt("seasonNumber", 1),
                                title = item.optString("title", "Episode ${i + 1}"),
                                description = item.optString("description", ""),
                                videoKey = epKey,
                                videoStreamUrl = epStream,
                                durationMinutes = item.optInt("durationMinutes", 45),
                                fileSizeMb = item.optLong("fileSizeMb", 250L)
                            )
                        )
                    }
                } catch (e: Exception) { }
            }

            return SupabaseMovieEntity(
                id = obj.optString("id"),
                title = obj.optString("title"),
                description = obj.optString("description", ""),
                coverKey = cleanCoverKey,
                coverUrl = rawCoverUrl,
                videoKey = cleanVideoKey,
                videoStreamUrl = rawVideoStreamUrl,
                uploadStatus = uploadStatus,
                genres = genresList,
                durationMinutes = obj.optInt("duration_minutes", 120),
                fileSizeMb = obj.optLong("file_size_mb", 500L),
                releaseYear = obj.optInt("release_year", 2026),
                rating = obj.optDouble("rating", 8.0),
                castMembers = castList,
                isFeatured = obj.optBoolean("is_featured", false),
                viewCount = obj.optLong("view_count", 0L),
                episodes = parsedEpisodes,
                createdAt = obj.optString("created_at").takeIf { it.isNotBlank() },
                updatedAt = obj.optString("updated_at").takeIf { it.isNotBlank() }
            )
        }
    }
}
