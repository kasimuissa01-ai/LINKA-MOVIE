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
    @Query("SELECT * FROM downloads ORDER BY id DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE movieId = :movieId ORDER BY CASE WHEN status = 'COMPLETED' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    suspend fun getDownloadByMovieId(movieId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE movieId = :movieId ORDER BY CASE WHEN status = 'COMPLETED' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    fun observeDownloadByMovieId(movieId: String): Flow<DownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET progress = :progress, status = :status, downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, status: String, bytes: Long)

    @Query("UPDATE downloads SET progress = :progress, status = :status, downloadedBytes = :bytes, localFilePath = :localPath WHERE id = :id")
    suspend fun updateDownloadProgressAndPath(id: String, progress: Float, status: String, bytes: Long, localPath: String)

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE movieId = :movieId")
    suspend fun deleteByMovieId(movieId: String)
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
