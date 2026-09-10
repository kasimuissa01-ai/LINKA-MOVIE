package com.example.domain.model

data class Movie(
    val id: String,
    val title: String,
    val description: String,
    val genres: List<String>,
    val coverUrl: String,
    val videoKey: String,
    val videoStreamUrl: String,
    val durationMinutes: Int,
    val fileSizeMb: Long,
    val releaseYear: Int,
    val rating: Double,
    val cast: List<String> = emptyList(),
    val isFeatured: Boolean = false,
    val uploadDate: Long = System.currentTimeMillis()
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
    val totalBytes: Long
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
    val email: String,
    val displayName: String = "Alex Vance",
    val phoneNumber: String = "+255 696 102 700",
    val role: UserRole = UserRole.USER,
    val token: String = "",
    val watchedCount: Int = 14,
    val favoriteCount: Int = 8
)
