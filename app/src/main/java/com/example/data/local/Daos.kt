package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY uploadDate DESC")
    fun getAllMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE isFeatured = 1 ORDER BY uploadDate DESC LIMIT 5")
    fun getFeaturedMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieById(id: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    fun observeMovieById(id: String): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR genres LIKE '%' || :query || '%'")
    fun searchMovies(query: String): Flow<List<MovieEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: MovieEntity)

    @Update
    suspend fun updateMovie(movie: MovieEntity)

    @Query("DELETE FROM movies WHERE id = :id")
    suspend fun deleteMovieById(id: String)

    @Query("DELETE FROM movies WHERE id IN (:ids)")
    suspend fun deleteMoviesByIds(ids: List<String>)

    @Query("DELETE FROM movies")
    suspend fun clearAllMovies()

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getMovieCount(): Int

    @Query("SELECT * FROM movies")
    suspend fun getAllMoviesList(): List<MovieEntity>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE userId = :userId AND :userId != '' ORDER BY id DESC")
    fun getAllDownloads(userId: String = ""): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE userId = :userId AND :userId != ''")
    suspend fun getAllDownloadsList(userId: String = ""): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY id DESC")
    suspend fun getAllDownloadsRaw(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE movieId = :movieId AND episodeId IS NULL AND userId = :userId AND :userId != '' ORDER BY CASE WHEN status = 'COMPLETED' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    suspend fun getDownloadByMovieId(movieId: String, userId: String = ""): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE movieId = :movieId AND episodeId IS NULL AND userId = :userId AND :userId != '' ORDER BY CASE WHEN status = 'COMPLETED' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    fun observeDownloadByMovieId(movieId: String, userId: String = ""): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE movieId = :movieId AND episodeId = :episodeId AND userId = :userId AND :userId != '' ORDER BY CASE WHEN status = 'COMPLETED' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    suspend fun getDownloadByEpisode(movieId: String, episodeId: String, userId: String = ""): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE movieId = :movieId AND episodeId = :episodeId AND userId = :userId AND :userId != '' ORDER BY CASE WHEN status = 'COMPLETED' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    fun observeDownloadByEpisode(movieId: String, episodeId: String, userId: String = ""): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE movieId = :movieId AND userId = :userId AND :userId != ''")
    fun observeDownloadsForMovie(movieId: String, userId: String = ""): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET progress = :progress, status = :status, downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, status: String, bytes: Long)

    @Query("UPDATE downloads SET progress = :progress, status = :status, downloadedBytes = :bytes, localFilePath = :localPath, isFileVerified = CASE WHEN :status = 'COMPLETED' THEN 1 ELSE 0 END, lastVerifiedAt = :verifiedAt WHERE id = :id")
    suspend fun updateDownloadProgressAndPath(
        id: String,
        progress: Float,
        status: String,
        bytes: Long,
        localPath: String,
        verifiedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET localFilePath = :localPath, downloadedBytes = :bytes, totalBytes = CASE WHEN totalBytes < :bytes THEN :bytes ELSE totalBytes END, progress = 1.0, status = 'COMPLETED', isFileVerified = 1, lastVerifiedAt = :verifiedAt WHERE id = :id")
    suspend fun markVerifiedComplete(
        id: String,
        localPath: String,
        bytes: Long,
        verifiedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET isFileVerified = :verified, lastVerifiedAt = :verifiedAt WHERE id = :id")
    suspend fun updateVerificationState(
        id: String,
        verified: Boolean,
        verifiedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET isFileVerified = 0, status = :status, lastVerifiedAt = :verifiedAt WHERE id = :id")
    suspend fun markFileMissing(
        id: String,
        status: String = "FAILED",
        verifiedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND userId = :userId AND :userId != ''")
    fun getCompletedDownloads(userId: String = ""): Flow<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE movieId = :movieId AND userId = :userId AND :userId != ''")
    suspend fun deleteByMovieId(movieId: String, userId: String = "")

    @Query("DELETE FROM downloads WHERE userId = :userId AND :userId != ''")
    suspend fun deleteAllDownloads(userId: String = "")

    @Query("UPDATE downloads SET userId = :targetUserId WHERE userId = '' OR userId IS NULL")
    suspend fun claimLegacyDownloads(targetUserId: String)
}

@Dao
interface UploadStateDao {
    @Query("SELECT * FROM upload_sessions WHERE uploadId = :uploadId LIMIT 1")
    suspend fun getSession(uploadId: String): UploadStateEntity?

    @Query("SELECT * FROM upload_sessions WHERE isCompleted = 0 ORDER BY uploadId DESC")
    fun getActiveSessions(): Flow<List<UploadStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: UploadStateEntity)

    @Query("DELETE FROM upload_sessions WHERE uploadId = :uploadId")
    suspend fun deleteSession(uploadId: String)
}
