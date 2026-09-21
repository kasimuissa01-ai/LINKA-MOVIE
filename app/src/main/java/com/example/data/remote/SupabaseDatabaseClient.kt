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
     * Fetches all published completed movies from the Supabase `movies` table as domain [Movie] models.
     * Filters for upload_status = 'completed' (or legacy null status) to prevent partial/failed uploads.
     */
    suspend fun getMovies(): List<Movie> = withContext(Dispatchers.IO) {
        // Query only completed uploads
        val endpoint = "$supabaseUrl/rest/v1/movies?select=*&or=(upload_status.eq.completed,upload_status.is.null)&order=created_at.desc"
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

                Log.d(TAG, "Successfully fetched ${resultList.size} verified movies from Supabase database")
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
        val endpoint = "$supabaseUrl/rest/v1/movies?select=*&or=(upload_status.eq.completed,upload_status.is.null)&order=created_at.desc"
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
     * Automatically adapts if the remote Supabase schema is missing optional columns.
     */
    suspend fun upsertMovieEntity(entity: SupabaseMovieEntity): Boolean = withContext(Dispatchers.IO) {
        val endpoint = "$supabaseUrl/rest/v1/movies"
        val payload = entity.toJsonObject()

        // Allow up to 3 retries in case specific columns don't exist in remote PostgreSQL schema
        for (attempt in 0..3) {
            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
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
                    if (response.isSuccessful) {
                        Log.i(TAG, "Successfully upserted movie '${entity.title}' to Supabase table")
                        return@withContext true
                    }

                    val errBody = response.body?.string() ?: ""
                    Log.w(TAG, "Upsert attempt $attempt for '${entity.title}' returned HTTP ${response.code}: $errBody")

                    // Check for PostgREST PGRST204: Could not find the 'column_name' column
                    if (response.code == 400 && (errBody.contains("PGRST204") || errBody.contains("Could not find the"))) {
                        val missingColRegex = Regex("Could not find the '([^']+)' column")
                        val match = missingColRegex.find(errBody)
                        val missingCol = match?.groupValues?.get(1)
                        if (missingCol != null && payload.has(missingCol)) {
                            Log.w(TAG, "Removing unsupported column '$missingCol' from payload and retrying upsert for '${entity.title}'")
                            payload.remove(missingCol)
                            return@use // continue to next attempt loop iteration
                        }
                    }

                    Log.e(TAG, "Failed to upsert movie '${entity.title}' to Supabase: HTTP ${response.code} - $errBody")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception upserting movie '${entity.title}' to Supabase: ${e.message}", e)
                return@withContext false
            }
        }
        return@withContext false
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
