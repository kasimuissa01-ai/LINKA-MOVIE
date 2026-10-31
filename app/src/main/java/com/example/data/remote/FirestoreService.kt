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
        const val COLLECTION_ADMINS = "admins"
        const val ROLE_MAIN_ADMIN = "main_admin"
        const val ROLE_SUB_ADMIN = "sub_admin"
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
     * Fetches all registered admins from Firestore `admins` collection.
     * Maps normalized phone numbers to their role ("main_admin" or "sub_admin").
     */
    suspend fun fetchAdmins(): Map<String, String> = withContext(Dispatchers.IO) {
        val adminMap = mutableMapOf<String, String>()
        // Seed persistent main admins
        adminMap["255696102700"] = ROLE_MAIN_ADMIN
        adminMap["255716123283"] = ROLE_MAIN_ADMIN

        try {
            val db = firestore ?: return@withContext adminMap
            val snapshot = db.collection(COLLECTION_ADMINS).get().await()
            for (doc in snapshot.documents) {
                val phone = doc.getString("phone") ?: doc.id
                val role = doc.getString("role") ?: ROLE_SUB_ADMIN
                val digits = phone.filter { it.isDigit() }
                if (digits.isNotBlank()) {
                    adminMap[digits] = role
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAdmins error: ${e.message}")
        }
        adminMap
    }

    /**
     * Adds or updates a sub-admin in Firestore `admins/{phone}`
     */
    suspend fun saveAdmin(phone: String, role: String, addedBy: String): Boolean = withContext(Dispatchers.IO) {
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) return@withContext false
        try {
            val db = firestore ?: return@withContext false
            val data = hashMapOf(
                "phone" to digits,
                "role" to role,
                "addedBy" to addedBy,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_ADMINS).document(digits).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveAdmin failed for $digits: ${e.message}", e)
            false
        }
    }

    /**
     * Removes an admin from Firestore `admins/{phone}`
     */
    suspend fun deleteAdmin(phone: String): Boolean = withContext(Dispatchers.IO) {
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) return@withContext false
        // Prevent deleting permanent main admins
        if (digits == "255696102700" || digits == "255716123283") {
            Log.w(TAG, "Cannot delete primary main admin $digits")
            return@withContext false
        }
        try {
            val db = firestore ?: return@withContext false
            db.collection(COLLECTION_ADMINS).document(digits).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAdmin failed for $digits: ${e.message}", e)
            false
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

                val coverKey = doc.getString("coverKey") ?: ""
                val videoStreamUrl = doc.getString("videoStreamUrl") ?: ""
                
                // Read episodes from native Firestore Map list or fallback to episodesJson
                val episodesList: List<com.example.domain.model.Episode> = run {
                    val nativeList = doc.get("episodes") as? List<Map<String, Any>>
                    if (!nativeList.isNullOrEmpty()) {
                        nativeList.mapIndexed { i, map ->
                            com.example.domain.model.Episode(
                                id = (map["id"] as? String) ?: "${id}_ep_${i + 1}",
                                movieId = (map["movieId"] as? String) ?: id,
                                episodeNumber = (map["episodeNumber"] as? Number)?.toInt() ?: (i + 1),
                                seasonNumber = (map["seasonNumber"] as? Number)?.toInt() ?: 1,
                                title = (map["title"] as? String) ?: "Episode ${i + 1}",
                                description = (map["description"] as? String) ?: "",
                                videoKey = (map["videoKey"] as? String) ?: "",
                                videoStreamUrl = (map["videoStreamUrl"] as? String) ?: "",
                                durationMinutes = (map["durationMinutes"] as? Number)?.toInt() ?: 45,
                                fileSizeMb = (map["fileSizeMb"] as? Number)?.toLong() ?: 250L
                            )
                        }
                    } else {
                        val episodesJson = doc.getString("episodesJson") ?: ""
                        if (episodesJson.isNotBlank()) {
                            try {
                                val arr = org.json.JSONArray(episodesJson)
                                val eps = mutableListOf<com.example.domain.model.Episode>()
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    eps.add(
                                        com.example.domain.model.Episode(
                                            id = obj.optString("id", "${id}_ep_${i + 1}"),
                                            movieId = obj.optString("movieId", id),
                                            episodeNumber = obj.optInt("episodeNumber", i + 1),
                                            seasonNumber = obj.optInt("seasonNumber", 1),
                                            title = obj.optString("title", "Episode ${i + 1}"),
                                            description = obj.optString("description", ""),
                                            videoKey = obj.optString("videoKey", ""),
                                            videoStreamUrl = obj.optString("videoStreamUrl", ""),
                                            durationMinutes = obj.optInt("durationMinutes", 45),
                                            fileSizeMb = obj.optLong("fileSizeMb", 250L)
                                        )
                                    )
                                }
                                eps
                            } catch (e: Exception) {
                                emptyList()
                            }
                        } else emptyList()
                    }
                }

                Movie(
                    id = id,
                    title = title,
                    description = description,
                    genres = genres,
                    coverUrl = coverUrl,
                    coverKey = coverKey,
                    videoKey = r2ObjectKey,
                    videoStreamUrl = videoStreamUrl,
                    durationMinutes = durationSec / 60,
                    fileSizeMb = sizeBytes / (1024 * 1024),
                    releaseYear = releaseYear,
                    rating = rating,
                    uploadDate = uploadedAt,
                    episodes = episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })),
                    isSwahiliTranslated = doc.getBoolean("isSwahiliTranslated") ?: true,
                    phoneNumber = doc.getString("phoneNumber") ?: ""
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
            val sortedEpisodes = movie.episodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            val epJson = if (sortedEpisodes.isNotEmpty()) {
                val arr = org.json.JSONArray()
                for (ep in sortedEpisodes) {
                    val obj = org.json.JSONObject().apply {
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
                    arr.put(obj)
                }
                arr.toString()
            } else ""

            val epMapList = sortedEpisodes.map { ep ->
                hashMapOf(
                    "id" to ep.id,
                    "movieId" to ep.movieId,
                    "episodeNumber" to ep.episodeNumber,
                    "seasonNumber" to ep.seasonNumber,
                    "title" to ep.title,
                    "description" to ep.description,
                    "videoKey" to ep.videoKey,
                    "videoStreamUrl" to ep.videoStreamUrl,
                    "durationMinutes" to ep.durationMinutes,
                    "fileSizeMb" to ep.fileSizeMb
                )
            }

            val data = hashMapOf(
                "title" to movie.title,
                "description" to movie.description,
                "genre" to movie.genres,
                "genres" to movie.genres,
                "coverUrl" to movie.coverUrl,
                "coverKey" to movie.coverKey,
                "r2ObjectKey" to movie.videoKey,
                "videoStreamUrl" to movie.videoStreamUrl,
                "durationSec" to movie.durationMinutes * 60,
                "sizeBytes" to movie.fileSizeMb * 1024 * 1024,
                "uploadedAt" to movie.uploadDate,
                "rating" to movie.rating,
                "releaseYear" to movie.releaseYear,
                "episodes" to epMapList,
                "episodesJson" to epJson,
                "isSwahiliTranslated" to movie.isSwahiliTranslated,
                "phoneNumber" to movie.phoneNumber
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
