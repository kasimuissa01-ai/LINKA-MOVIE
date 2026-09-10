package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Movie
import com.example.domain.model.UploadPart
import com.example.domain.model.UploadSession

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val genres: String, // comma separated or JSON
    val coverUrl: String,
    val videoKey: String,
    val videoStreamUrl: String,
    val durationMinutes: Int,
    val fileSizeMb: Long,
    val releaseYear: Int,
    val rating: Double,
    val cast: String,
    val isFeatured: Boolean,
    val uploadDate: Long
) {
    fun toDomain(): Movie = Movie(
        id = id,
        title = title,
        description = description,
        genres = if (genres.isBlank()) emptyList() else genres.split(",").map { it.trim() },
        coverUrl = coverUrl,
        videoKey = videoKey,
        videoStreamUrl = videoStreamUrl,
        durationMinutes = durationMinutes,
        fileSizeMb = fileSizeMb,
        releaseYear = releaseYear,
        rating = rating,
        cast = if (cast.isBlank()) emptyList() else cast.split(",").map { it.trim() },
        isFeatured = isFeatured,
        uploadDate = uploadDate
    )

    companion object {
        fun fromDomain(movie: Movie): MovieEntity = MovieEntity(
            id = movie.id,
            title = movie.title,
            description = movie.description,
            genres = movie.genres.joinToString(","),
            coverUrl = movie.coverUrl,
            videoKey = movie.videoKey,
            videoStreamUrl = movie.videoStreamUrl,
            durationMinutes = movie.durationMinutes,
            fileSizeMb = movie.fileSizeMb,
            releaseYear = movie.releaseYear,
            rating = movie.rating,
            cast = movie.cast.joinToString(","),
            isFeatured = movie.isFeatured,
            uploadDate = movie.uploadDate
        )
    }
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val movieId: String,
    val movieTitle: String,
    val coverUrl: String,
    val localFilePath: String,
    val progress: Float,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    fun toDomain(): DownloadItem = DownloadItem(
        id = id,
        movieId = movieId,
        movieTitle = movieTitle,
        coverUrl = coverUrl,
        localFilePath = localFilePath,
        progress = progress,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes
    )

    companion object {
        fun fromDomain(item: DownloadItem): DownloadEntity = DownloadEntity(
            id = item.id,
            movieId = item.movieId,
            movieTitle = item.movieTitle,
            coverUrl = item.coverUrl,
            localFilePath = item.localFilePath,
            progress = item.progress,
            status = item.status.name,
            downloadedBytes = item.downloadedBytes,
            totalBytes = item.totalBytes
        )
    }
}

@Entity(tableName = "upload_sessions")
data class UploadStateEntity(
    @PrimaryKey val uploadId: String,
    val movieId: String,
    val movieTitle: String,
    val videoKey: String,
    val totalBytes: Long,
    val chunkSize: Long,
    val partsSummary: String, // format: "partNum:etag:isUploaded:progress|..."
    val isCompleted: Boolean,
    val isPaused: Boolean
) {
    fun toDomain(): UploadSession {
        val parsedParts = if (partsSummary.isBlank()) {
            emptyList()
        } else {
            partsSummary.split("|").mapNotNull { entry ->
                val tokens = entry.split(":")
                if (tokens.size >= 5) {
                    UploadPart(
                        partNumber = tokens[0].toIntOrNull() ?: 1,
                        etag = tokens[1],
                        startByte = tokens[2].toLongOrNull() ?: 0L,
                        endByte = tokens[3].toLongOrNull() ?: 0L,
                        isUploaded = tokens[4].toBooleanStrictOrNull() ?: false,
                        progress = tokens.getOrNull(5)?.toFloatOrNull() ?: 0f,
                        presignedUrl = tokens.getOrNull(6)?.let {
                            runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
                        } ?: ""
                    )
                } else null
            }
        }
        return UploadSession(
            uploadId = uploadId,
            movieId = movieId,
            movieTitle = movieTitle,
            videoKey = videoKey,
            totalBytes = totalBytes,
            chunkSize = chunkSize,
            parts = parsedParts,
            isCompleted = isCompleted,
            isPaused = isPaused
        )
    }

    companion object {
        fun fromDomain(session: UploadSession): UploadStateEntity {
            val partsStr = session.parts.joinToString("|") { part ->
                val encodedUrl = if (part.presignedUrl.isNotBlank()) {
                    runCatching { java.net.URLEncoder.encode(part.presignedUrl, "UTF-8") }.getOrDefault(part.presignedUrl)
                } else ""
                "${part.partNumber}:${part.etag}:${part.startByte}:${part.endByte}:${part.isUploaded}:${part.progress}:$encodedUrl"
            }
            return UploadStateEntity(
                uploadId = session.uploadId,
                movieId = session.movieId,
                movieTitle = session.movieTitle,
                videoKey = session.videoKey,
                totalBytes = session.totalBytes,
                chunkSize = session.chunkSize,
                partsSummary = partsStr,
                isCompleted = session.isCompleted,
                isPaused = session.isPaused
            )
        }
    }
}
