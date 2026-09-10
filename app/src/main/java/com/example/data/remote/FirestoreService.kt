package com.example.data.remote

import android.util.Log
import com.example.domain.model.DownloadItem
import com.example.domain.model.Movie
import com.example.domain.model.UserRole
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Service for Firestore operations:
 * - users/{uid}: role ('admin' | 'user')
 * - movies/{movieId}: title, description, genre, coverUrl, r2ObjectKey, durationSec, sizeBytes, uploadedAt
 * - downloads/{uid}/{movieId}: status, localPath, progress
 */
class FirestoreService {

    companion object {
        private const val TAG = "FirestoreService"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_MOVIES = "movies"
        private const val COLLECTION_DOWNLOADS = "downloads"
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore initialization fallback: ${e.message}")
            null
        }
    }

    /**
     * Reads user profile and role from Firestore `users/{uid}`
     */
    suspend fun getUserRole(uid: String): UserRole = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext UserRole.USER
            val snapshot = db.collection(COLLECTION_USERS).document(uid).get().await()
            val roleStr = snapshot.getString("role")
            if (roleStr?.equals("admin", ignoreCase = true) == true) {
                return@withContext UserRole.ADMIN
            }
        } catch (e: Exception) {
            Log.w(TAG, "getUserRole failed for $uid: ${e.message}")
        }
        return@withContext UserRole.USER
    }

    /**
     * Saves or updates user document in `users/{uid}`
     */
    suspend fun saveUserProfile(
        uid: String,
        email: String,
        displayName: String,
        role: UserRole,
        phoneNumber: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext false
            val data = hashMapOf(
                "uid" to uid,
                "email" to email,
                "displayName" to displayName,
                "phoneNumber" to phoneNumber,
                "role" to if (role == UserRole.ADMIN) "admin" else "user",
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_USERS).document(uid).set(data, SetOptions.merge()).await()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfile failed: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Reads all movies from Firestore `movies/{movieId}`
     */
    suspend fun fetchMovies(): List<Movie> = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext emptyList()
            val querySnapshot = db.collection(COLLECTION_MOVIES).get().await()
            return@withContext querySnapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val title = doc.getString("title") ?: return@mapNotNull null
                val description = doc.getString("description") ?: ""
                @Suppress("UNCHECKED_CAST")
                val genres = (doc.get("genre") as? List<String>) ?: listOf("General")
                val coverUrl = doc.getString("coverUrl") ?: ""
                val r2ObjectKey = doc.getString("r2ObjectKey") ?: "movies/$id.mp4"
                val durationSec = doc.getLong("durationSec")?.toInt() ?: 7200
                val sizeBytes = doc.getLong("sizeBytes") ?: (1024L * 1024L * 800L)
                val uploadedAt = doc.getLong("uploadedAt") ?: System.currentTimeMillis()
                val rating = doc.getDouble("rating") ?: 4.5
                val releaseYear = doc.getLong("releaseYear")?.toInt() ?: 2026

                Movie(
                    id = id,
                    title = title,
                    description = description,
                    genres = genres,
                    coverUrl = coverUrl,
                    videoKey = r2ObjectKey,
                    videoStreamUrl = "", // Stream URL obtained via Supabase Edge Function get-download-url
                    durationMinutes = durationSec / 60,
                    fileSizeMb = sizeBytes / (1024 * 1024),
                    releaseYear = releaseYear,
                    rating = rating,
                    uploadDate = uploadedAt
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchMovies failed: ${e.message}")
            return@withContext emptyList()
        }
    }

    /**
     * Saves a movie document to Firestore `movies/{movieId}` (admin-only)
     */
    suspend fun saveMovie(movie: Movie): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext false
            val data = hashMapOf(
                "title" to movie.title,
                "description" to movie.description,
                "genre" to movie.genres,
                "coverUrl" to movie.coverUrl,
                "r2ObjectKey" to movie.videoKey,
                "durationSec" to movie.durationMinutes * 60,
                "sizeBytes" to movie.fileSizeMb * 1024 * 1024,
                "uploadedAt" to movie.uploadDate,
                "rating" to movie.rating,
                "releaseYear" to movie.releaseYear
            )
            db.collection(COLLECTION_MOVIES).document(movie.id).set(data, SetOptions.merge()).await()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "saveMovie failed: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Deletes movie document from Firestore `movies/{movieId}`
     */
    suspend fun deleteMovie(movieId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext false
            db.collection(COLLECTION_MOVIES).document(movieId).delete().await()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "deleteMovie failed: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Syncs download state to `downloads/{uid}/{movieId}`
     */
    suspend fun syncDownload(uid: String, item: DownloadItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext false
            val data = hashMapOf(
                "movieId" to item.movieId,
                "status" to item.status.name,
                "localPath" to item.localFilePath,
                "progress" to item.progress,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_DOWNLOADS)
                .document(uid)
                .collection("user_downloads")
                .document(item.movieId)
                .set(data, SetOptions.merge())
                .await()
            return@withContext true
        } catch (e: Exception) {
            Log.w(TAG, "syncDownload failed: ${e.message}")
            return@withContext false
        }
    }
}
