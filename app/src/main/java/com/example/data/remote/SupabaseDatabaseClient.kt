package com.example.data.remote

import android.util.Log
import com.example.data.remote.model.SupabaseMovieEntity
import com.example.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct REST client for Supabase PostgreSQL table `movies`.
 *
 * Storing movie metadata and verified Cloudflare R2 streaming URLs here provides:
 * - Instant catalog & verified streaming URL delivery via Supabase's global edge (<50ms)
 * - Zero egress bandwidth costs (the video bytes remain on Cloudflare R2; only URLs/text are stored in Supabase)
 * - Complete consistency across all devices: no phantom or unverified URLs
 */
class SupabaseDatabaseClient(
    private val supabaseUrl: String = SUPABASE_URL,
    private val anonKey: String = SUPABASE_ANON_KEY,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "SupabaseDbClient"
        const val SUPABASE_URL = "https://vqgnxqabvmmpfoiceass.supabase.co"
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZxZ254cWFidm1tcGZvaWNlYXNzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTkzODE5NDMsImV4cCI6MjA3NDk1Nzk0M30.ZkOlMsqmfv4gCl3YG5CLe7te5DoIbZad8Y2mIpKTleA"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Fetches all published movies from the Supabase `movies` table as domain [Movie] models.
     */
    suspend fun getMovies(): List<Movie> = withContext(Dispatchers.IO) {
        val endpoint = "$supabaseUrl/rest/v1/movies?select=*&order=created_at.desc"
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch movies from Supabase: HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val bodyString = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(bodyString)
                val resultList = mutableListOf<Movie>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val entity = SupabaseMovieEntity.fromJsonObject(obj)
                    resultList.add(entity.toDomain())
                }

                Log.d(TAG, "Successfully fetched ${resultList.size} movies from Supabase database")
                return@withContext resultList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Supabase movies table: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Fetches all published movies directly as [SupabaseMovieEntity] instances.
     */
    suspend fun getSupabaseMovieEntities(): List<SupabaseMovieEntity> = withContext(Dispatchers.IO) {
        val endpoint = "$supabaseUrl/rest/v1/movies?select=*&order=created_at.desc"
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch movies from Supabase: HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val bodyString = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(bodyString)
                val resultList = mutableListOf<SupabaseMovieEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    resultList.add(SupabaseMovieEntity.fromJsonObject(obj))
                }

                return@withContext resultList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Supabase movies table: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Upserts a movie record into the Supabase `movies` table using [SupabaseMovieEntity].
     * Uses `Prefer: resolution=merge-duplicates` to update if already existing.
     */
    suspend fun upsertMovieEntity(entity: SupabaseMovieEntity): Boolean = withContext(Dispatchers.IO) {
        val endpoint = "$supabaseUrl/rest/v1/movies"
        val requestBody = entity.toJsonObject().toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=representation")
            .post(requestBody)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (success) {
                    Log.i(TAG, "Successfully upserted movie '${entity.title}' to Supabase table (URL: ${entity.videoStreamUrl})")
                } else {
                    val errBody = response.body?.string()
                    Log.e(TAG, "Failed to upsert movie '${entity.title}' to Supabase: HTTP ${response.code} - $errBody")
                }
                return@withContext success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception upserting movie '${entity.title}' to Supabase: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Upserts a movie record into the Supabase `movies` table from a domain [Movie].
     */
    suspend fun upsertMovie(movie: Movie): Boolean {
        val entity = SupabaseMovieEntity.fromDomain(movie)
        return upsertMovieEntity(entity)
    }

    /**
     * Deletes a movie record by ID from the Supabase `movies` table.
     */
    suspend fun deleteMovie(movieId: String): Boolean = withContext(Dispatchers.IO) {
        val endpoint = "$supabaseUrl/rest/v1/movies?id=eq.$movieId"
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .delete()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (success) {
                    Log.i(TAG, "Successfully deleted movie ID '$movieId' from Supabase table")
                } else {
                    Log.w(TAG, "Failed to delete movie ID '$movieId' from Supabase: HTTP ${response.code}")
                }
                return@withContext success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting movie ID '$movieId' from Supabase: ${e.message}", e)
            return@withContext false
        }
    }
}
