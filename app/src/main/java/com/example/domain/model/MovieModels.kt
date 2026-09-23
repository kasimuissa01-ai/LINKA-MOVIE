package com.example.domain.model

data class Episode(
    val id: String,
    val movieId: String,
    val episodeNumber: Int,
    val seasonNumber: Int = 1,
    val title: String,
    val description: String = "",
    val videoKey: String = "",
    val videoStreamUrl: String = "",
    val durationMinutes: Int = 45,
    val fileSizeMb: Long = 250L
)

data class Movie(
    val id: String,
    val title: String,
    val description: String,
    val genres: List<String>,
    val coverUrl: String = "",
    val coverKey: String = "",
    val videoKey: String = "",
    val videoStreamUrl: String = "",
    val durationMinutes: Int = 120,
    val fileSizeMb: Long = 500L,
    val releaseYear: Int = 2026,
    val rating: Double = 8.0,
    val cast: List<String> = emptyList(),
    val isFeatured: Boolean = false,
    val uploadStatus: String = "completed",
    val uploadDate: Long = System.currentTimeMillis(),
    val episodes: List<Episode> = emptyList()
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String,
    val movieId: String,
    val movieTitle: String,
    val coverUrl: String,
    val localFilePath: String,
    val progress: Float, // 0.0 to 1.0
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val episodeId: String? = null,
    val episodeTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

data class UploadPart(
    val partNumber: Int,
    val etag: String = "",
    val startByte: Long,
    val endByte: Long,
    val isUploaded: Boolean = false,
    val progress: Float = 0f,
    val presignedUrl: String = ""
)

data class UploadSession(
    val uploadId: String,
    val movieId: String,
    val movieTitle: String,
    val videoKey: String,
    val totalBytes: Long,
    val chunkSize: Long,
    val parts: List<UploadPart>,
    val isCompleted: Boolean = false,
    val isPaused: Boolean = false
)

enum class UserRole {
    USER,
    ADMIN
}

data class UserSession(
    val uid: String,
    val email: String = "",
    val displayName: String = "Guest User",
    val phoneNumber: String = "",
    val role: UserRole = UserRole.USER,
    val token: String = "",
    val watchedCount: Int = 0,
    val favoriteCount: Int = 0
)
