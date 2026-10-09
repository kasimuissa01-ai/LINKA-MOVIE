package com.example.data.analytics

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.remote.SupabaseDatabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton manager for pseudonymous application usage and playback analytics.
 *
 * Privacy & Architecture Principles:
 * - Completely account-free: ordinary viewers are not required to log in or create an account.
 * - Anonymous Installation Identifier: UUID generated once per installation and persisted in SharedPreferences.
 * - Never collects or transmits phone numbers, passwords, IMEIs, Advertising IDs, or device hardware IDs.
 * - Fully Non-blocking: All queueing, network uploads, and retry loops run asynchronously on IO coroutines.
 * - Offline-Resilient: Events occurring while offline are saved to a local SQLite queue and synced when connectivity restores.
 */
class AppAnalyticsManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "AppAnalyticsManager"
        private const val PREFS_NAME = "movieroom_analytics_prefs"
        private const val KEY_INSTALLATION_ID = "key_installation_id"
        private const val KEY_FIRST_OPEN_RECORDED = "key_first_open_recorded"
        private const val KEY_LAST_APP_OPENED_TIMESTAMP = "key_last_app_opened_ts"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var INSTANCE: AppAnalyticsManager? = null

        fun getInstance(context: Context): AppAnalyticsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppAnalyticsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dbHelper = AnalyticsQueueDbHelper(appContext)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // Pseudonymous installation identifier (stable across normal app restarts)
    val installationId: String by lazy {
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, newId).apply()
            newId
        }
    }

    // App Session identifier (regenerated per app process lifecycle)
    var currentAppSessionId: String = UUID.randomUUID().toString()
        private set

    private val isSyncing = AtomicBoolean(false)
    private var hasRecordedAppOpenedThisProcess = false

    init {
        // Trigger initial queue flush in background
        scope.launch {
            checkAndRecordFirstOpen()
            flushPendingEvents()
        }
    }

    /**
     * Records the 'first_open' event on the very first time the app is launched.
     */
    private fun checkAndRecordFirstOpen() {
        val firstOpenRecorded = prefs.getBoolean(KEY_FIRST_OPEN_RECORDED, false)
        if (!firstOpenRecorded) {
            prefs.edit().putBoolean(KEY_FIRST_OPEN_RECORDED, true).apply()
            trackEvent(
                eventName = "first_open",
                properties = mapOf("install_id" to installationId)
            )
        }
    }

    /**
     * Called when the application is brought to foreground or launched.
     * Guards against duplicate events caused by activity rotation or recreation.
     */
    fun onAppForegrounded() {
        if (!hasRecordedAppOpenedThisProcess) {
            hasRecordedAppOpenedThisProcess = true
            currentAppSessionId = UUID.randomUUID().toString()

            val lastOpened = prefs.getLong(KEY_LAST_APP_OPENED_TIMESTAMP, 0L)
            val now = System.currentTimeMillis()
            // Only emit app_opened if more than 30 seconds have passed since last launch
            if (now - lastOpened > 30_000L) {
                prefs.edit().putLong(KEY_LAST_APP_OPENED_TIMESTAMP, now).apply()
                trackEvent(
                    eventName = "app_opened",
                    properties = mapOf("session_id" to currentAppSessionId)
                )
                trackEvent(
                    eventName = "session_started",
                    properties = mapOf("session_id" to currentAppSessionId)
                )
            }
        }
    }

    /**
     * Called when application enters background or process shuts down.
     */
    fun onAppBackgrounded() {
        trackEvent(
            eventName = "session_ended",
            properties = mapOf("session_id" to currentAppSessionId)
        )
    }

    /**
     * Submits or enqueues a generic analytics event.
     * Non-blocking: always delegates to background IO.
     */
    fun trackEvent(
        eventName: String,
        movieId: String? = null,
        playbackPositionSeconds: Int? = null,
        properties: Map<String, Any> = emptyMap(),
        customSessionId: String? = null
    ) {
        val sid = customSessionId ?: currentAppSessionId
        val event = AnalyticsEvent(
            eventName = eventName,
            installationId = installationId,
            sessionId = sid,
            movieId = movieId,
            playbackPositionSeconds = playbackPositionSeconds,
            properties = properties
        )

        scope.launch {
            enqueueEvent(event)
            flushPendingEvents()
        }
    }

    /**
     * Records or updates a video watch session.
     * Ensures playback watch time and completion are accurately persisted.
     */
    fun recordWatchSession(
        sessionId: String,
        movieId: String,
        lastPositionSeconds: Int,
        watchedSeconds: Int,
        isCompleted: Boolean = false
    ) {
        val payload = WatchSessionPayload(
            sessionId = sessionId,
            installationId = installationId,
            movieId = movieId,
            lastPositionSeconds = lastPositionSeconds,
            watchedSeconds = watchedSeconds,
            isCompleted = isCompleted
        )

        scope.launch {
            enqueueWatchSession(payload)
            flushPendingEvents()
        }
    }

    /**
     * Fetches aggregated analytics summary for the Admin Dashboard.
     * Uses the Supabase RPC function `get_admin_analytics_summary`.
     */
    suspend fun fetchAdminAnalyticsSummary(days: Int = 30): Result<AdminAnalyticsSummary> = withContext(Dispatchers.IO) {
        val endpoint = "${SupabaseDatabaseClient.SUPABASE_URL}/rest/v1/rpc/get_admin_analytics_summary"
        val payloadJson = JSONObject().apply {
            put("p_days", days)
        }

        val requestBody = payloadJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", SupabaseDatabaseClient.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseDatabaseClient.SUPABASE_ANON_KEY}")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch analytics summary: HTTP ${response.code} - $bodyStr")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val summary = parseSummaryJson(json)
                return@withContext Result.success(summary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching admin analytics summary: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    private fun parseSummaryJson(json: JSONObject): AdminAnalyticsSummary {
        val movieStatsList = mutableListOf<MovieAnalyticsItem>()
        val movieStatsArray = json.optJSONArray("movie_stats") ?: JSONArray()
        for (i in 0 until movieStatsArray.length()) {
            val obj = movieStatsArray.optJSONObject(i) ?: continue
            movieStatsList.add(
                MovieAnalyticsItem(
                    movieId = obj.optString("movie_id", ""),
                    title = obj.optString("title", "Untitled Movie"),
                    coverUrl = obj.optString("cover_url", ""),
                    uniqueViewers = obj.optLong("unique_viewers", 0L),
                    totalPlays = obj.optLong("total_plays", 0L),
                    totalWatchSeconds = obj.optLong("total_watch_seconds", 0L),
                    avgWatchSeconds = obj.optLong("avg_watch_seconds", 0L),
                    completionRate = obj.optDouble("completion_rate", 0.0)
                )
            )
        }

        val recentEventsList = mutableListOf<RecentActivityItem>()
        val recentEventsArray = json.optJSONArray("recent_events") ?: JSONArray()
        for (i in 0 until recentEventsArray.length()) {
            val obj = recentEventsArray.optJSONObject(i) ?: continue
            recentEventsList.add(
                RecentActivityItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    eventName = obj.optString("event_name", ""),
                    installationId = obj.optString("installation_id", "").take(8) + "...",
                    sessionId = obj.optString("session_id", "").take(8) + "...",
                    movieId = obj.optString("movie_id").takeIf { it.isNotBlank() },
                    movieTitle = obj.optString("movie_title", ""),
                    playbackPositionSeconds = if (obj.has("playback_position_seconds")) obj.optInt("playback_position_seconds") else null,
                    createdAt = obj.optString("created_at", "")
                )
            )
        }

        return AdminAnalyticsSummary(
            periodDays = json.optInt("period_days", 30),
            totalInstallations = json.optLong("total_installations", 0L),
            firstOpens = json.optLong("first_opens", 0L),
            activeToday = json.optLong("active_today", 0L),
            active7d = json.optLong("active_7d", 0L),
            active30d = json.optLong("active_30d", 0L),
            returningInstallations = json.optLong("returning_installations", 0L),
            totalPlays = json.optLong("total_plays", 0L),
            totalWatchSeconds = json.optLong("total_watch_seconds", 0L),
            totalWatchHours = json.optDouble("total_watch_hours", 0.0),
            avgWatchSecondsPerPlay = json.optLong("avg_watch_seconds_per_play", 0L),
            playbackErrors = json.optLong("playback_errors", 0L),
            movieStats = movieStatsList,
            recentEvents = recentEventsList,
            serverTimestamp = json.optString("server_timestamp", "")
        )
    }

    // --------------------------------------------------------------------------
    // LOCAL OFFLINE QUEUE & SYNC ENGINE
    // --------------------------------------------------------------------------

    private fun enqueueEvent(event: AnalyticsEvent) {
        try {
            val json = JSONObject().apply {
                put("type", "event")
                put("event_name", event.eventName)
                put("installation_id", event.installationId)
                put("session_id", event.sessionId)
                if (event.movieId != null) put("movie_id", event.movieId)
                if (event.playbackPositionSeconds != null) put("playback_position", event.playbackPositionSeconds)
                put("properties", JSONObject(event.properties))
            }
            dbHelper.insertQueueItem("event", json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enqueue event locally: ${e.message}")
        }
    }

    private fun enqueueWatchSession(payload: WatchSessionPayload) {
        try {
            val json = JSONObject().apply {
                put("type", "watch_session")
                put("installation_id", payload.installationId)
                put("session_id", payload.sessionId)
                put("movie_id", payload.movieId)
                put("last_position", payload.lastPositionSeconds)
                put("watched_seconds", payload.watchedSeconds)
                put("is_completed", payload.isCompleted)
            }
            dbHelper.insertQueueItem("watch_session", json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enqueue watch session locally: ${e.message}")
        }
    }

    suspend fun flushPendingEvents() = withContext(Dispatchers.IO) {
        if (!isNetworkConnected()) return@withContext
        if (!isSyncing.compareAndSet(false, true)) return@withContext

        try {
            val pendingItems = dbHelper.getPendingItems(limit = 25)
            if (pendingItems.isEmpty()) return@withContext

            for (item in pendingItems) {
                val success = uploadQueueItem(item.type, item.payloadJson)
                if (success) {
                    dbHelper.deleteItem(item.id)
                } else {
                    dbHelper.incrementRetry(item.id)
                    // If server error or table missing, pause flushing temporarily to avoid hammering
                    break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Queue flush pass encountered: ${e.message}")
        } finally {
            isSyncing.set(false)
        }
    }

    private fun uploadQueueItem(type: String, jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            if (type == "event") {
                // Call RPC submit_analytics_event
                val endpoint = "${SupabaseDatabaseClient.SUPABASE_URL}/rest/v1/rpc/submit_analytics_event"
                val bodyJson = JSONObject().apply {
                    put("p_event_name", json.optString("event_name"))
                    put("p_installation_id", json.optString("installation_id"))
                    put("p_session_id", json.optString("session_id"))
                    if (json.has("movie_id")) put("p_movie_id", json.optString("movie_id"))
                    if (json.has("playback_position")) put("p_playback_position", json.optInt("playback_position"))
                    put("p_properties", json.optJSONObject("properties") ?: JSONObject())
                }

                val req = Request.Builder()
                    .url(endpoint)
                    .header("apikey", SupabaseDatabaseClient.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${SupabaseDatabaseClient.SUPABASE_ANON_KEY}")
                    .header("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    resp.isSuccessful
                }
            } else {
                // Call RPC record_watch_session
                val endpoint = "${SupabaseDatabaseClient.SUPABASE_URL}/rest/v1/rpc/record_watch_session"
                val bodyJson = JSONObject().apply {
                    put("p_installation_id", json.optString("installation_id"))
                    put("p_session_id", json.optString("session_id"))
                    put("p_movie_id", json.optString("movie_id"))
                    put("p_last_position", json.optInt("last_position"))
                    put("p_watched_seconds", json.optInt("watched_seconds"))
                    put("p_is_completed", json.optBoolean("is_completed"))
                }

                val req = Request.Builder()
                    .url(endpoint)
                    .header("apikey", SupabaseDatabaseClient.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${SupabaseDatabaseClient.SUPABASE_ANON_KEY}")
                    .header("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    resp.isSuccessful
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Queue item upload exception: ${e.message}")
            false
        }
    }

    private fun isNetworkConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    // --------------------------------------------------------------------------
    // INTERNAL SQLITE QUEUE HELPER
    // --------------------------------------------------------------------------
    private data class QueueItem(val id: Long, val type: String, val payloadJson: String, val retryCount: Int)

    private class AnalyticsQueueDbHelper(context: Context) : SQLiteOpenHelper(context, "analytics_offline_queue.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_type TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    retry_count INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS queue")
            onCreate(db)
        }

        fun insertQueueItem(type: String, payload: String) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("item_type", type)
                put("payload", payload)
                put("retry_count", 0)
                put("created_at", System.currentTimeMillis())
            }
            db.insert("queue", null, values)
        }

        fun getPendingItems(limit: Int = 25): List<QueueItem> {
            val db = readableDatabase
            val list = mutableListOf<QueueItem>()
            val cursor = db.rawQuery(
                "SELECT id, item_type, payload, retry_count FROM queue WHERE retry_count < 10 ORDER BY id ASC LIMIT $limit",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        QueueItem(
                            id = it.getLong(0),
                            type = it.getString(1),
                            payloadJson = it.getString(2),
                            retryCount = it.getInt(3)
                        )
                    )
                }
            }
            return list
        }

        fun deleteItem(id: Long) {
            val db = writableDatabase
            db.delete("queue", "id = ?", arrayOf(id.toString()))
        }

        fun incrementRetry(id: Long) {
            val db = writableDatabase
            db.execSQL("UPDATE queue SET retry_count = retry_count + 1 WHERE id = ?", arrayOf(id.toString()))
        }
    }
}
