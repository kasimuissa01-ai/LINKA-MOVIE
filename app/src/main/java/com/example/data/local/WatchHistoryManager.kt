package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryItem(
    val movieId: String,
    val episodeId: String? = null,
    val movieTitle: String,
    val episodeTitle: String? = null,
    val coverUrl: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val progressPercent: Int
        get() = if (durationMs > 0L) {
            ((lastPositionMs.toDouble() / durationMs.toDouble()) * 100).toInt().coerceIn(1, 100)
        } else {
            0
        }

    val formattedDuration: String
        get() = WatchHistoryManager.formatTimestamp(durationMs)

    val formattedPosition: String
        get() = WatchHistoryManager.formatTimestamp(lastPositionMs)
}

/**
 * Manages user watch history and offline-safe playback resume positions locally on device
 * per authenticated/guest user using private SharedPreferences.
 *
 * Works completely offline without network or Supabase dependency and provides instant updates
 * to Compose UI components via [historyFlow].
 */
class WatchHistoryManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "movieroom_watch_history"
        private const val MAX_HISTORY_ITEMS = 30
        private const val MAX_POSITION_MAP_ENTRIES = 200
        const val MIN_RESUME_POSITION_MS = 3_000L

        @Volatile
        private var instance: WatchHistoryManager? = null

        fun getInstance(context: Context): WatchHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: WatchHistoryManager(context.applicationContext).also { instance = it }
            }
        }

        fun formatTimestamp(millis: Long): String {
            val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        fun buildPositionKey(movieId: String, episodeId: String? = null): String {
            return if (!episodeId.isNullOrBlank() && !episodeId.startsWith("synth_s1_e1_")) {
                "${movieId}_ep_${episodeId}"
            } else {
                movieId
            }
        }

        fun isPlaybackCompleted(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0L) return false
            if (durationMs >= 60_000L && positionMs >= (durationMs * 0.96).toLong()) return true
            if (durationMs >= 30_000L && (durationMs - positionMs) <= 5_000L) return true
            return false
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _historyFlow = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<WatchHistoryItem>> = _historyFlow.asStateFlow()

    private var activeUserId: String = ""

    @Synchronized
    fun setUser(userId: String) {
        if (activeUserId != userId) {
            activeUserId = userId
            _historyFlow.value = loadHistoryForUser(userId)
        } else if (_historyFlow.value.isEmpty()) {
            _historyFlow.value = loadHistoryForUser(userId)
        }
    }

    @Synchronized
    fun recordWatchProgress(
        userId: String,
        movie: Movie,
        episode: Episode?,
        positionMs: Long,
        durationMs: Long,
        commitNow: Boolean = false
    ) {
        if (positionMs < 1000L) return
        val effectiveUserId = userId.ifBlank { activeUserId }
        val effectiveDuration = if (durationMs > 0L) durationMs else (movie.durationMinutes * 60L * 1000L)
        val effectiveEpId = episode?.id?.takeIf { !it.startsWith("synth_s1_e1_") }
        val itemKey = buildPositionKey(movie.id, effectiveEpId)
        val now = System.currentTimeMillis()

        val currentList = loadHistoryForUser(effectiveUserId).toMutableList()

        val displayTitle = if (episode != null) {
            val epNumber = episode.episodeNumber
            val sNumber = episode.seasonNumber
            "${movie.title} S${String.format(java.util.Locale.US, "%02d", sNumber)} EP${String.format(java.util.Locale.US, "%02d", epNumber)}"
        } else {
            movie.title
        }

        val newItem = WatchHistoryItem(
            movieId = movie.id,
            episodeId = effectiveEpId,
            movieTitle = movie.title,
            episodeTitle = displayTitle,
            coverUrl = movie.coverUrl,
            lastPositionMs = positionMs,
            durationMs = effectiveDuration,
            timestamp = now
        )

        // Remove previous entry for same movie / episode to move to top
        currentList.removeAll {
            if (effectiveEpId != null) {
                it.movieId == movie.id && it.episodeId == effectiveEpId
            } else {
                it.movieId == movie.id && it.episodeId.isNullOrBlank()
            }
        }
        currentList.add(0, newItem)

        val trimmed = currentList.take(MAX_HISTORY_ITEMS)
        saveHistoryAndPositionForUser(
            userId = effectiveUserId,
            items = trimmed,
            positionKey = itemKey,
            positionItem = newItem,
            commitNow = commitNow
        )
        if (effectiveUserId == activeUserId) {
            _historyFlow.value = trimmed
        }
    }

    @Synchronized
    fun getSavedWatchItem(
        userId: String,
        movieId: String,
        episodeId: String? = null
    ): WatchHistoryItem? {
        val normalizedEpId = episodeId?.takeIf { it.isNotBlank() && !it.startsWith("synth_s1_e1_") }
        val positionKey = buildPositionKey(movieId, normalizedEpId)

        val candidateUids = listOf(
            userId.ifBlank { activeUserId },
            activeUserId,
            "guest",
            ""
        ).distinct()

        for (uid in candidateUids) {
            // 1. Check ordered recent watch history
            val historyMatch = loadHistoryForUser(uid).firstOrNull { item ->
                if (normalizedEpId != null) {
                    item.movieId == movieId && item.episodeId == normalizedEpId
                } else {
                    item.movieId == movieId && item.episodeId.isNullOrBlank()
                }
            }
            if (historyMatch != null) return historyMatch

            // 2. Check full local position map (retains entries beyond MAX_HISTORY_ITEMS)
            val mapMatch = loadPositionFromMap(uid, positionKey, movieId, normalizedEpId)
            if (mapMatch != null) return mapMatch
        }
        return null
    }

    fun getResumablePositionMs(
        userId: String,
        movieId: String,
        episodeId: String? = null
    ): Long {
        val saved = getSavedWatchItem(userId, movieId, episodeId) ?: return 0L
        if (saved.lastPositionMs < MIN_RESUME_POSITION_MS) return 0L
        if (isPlaybackCompleted(saved.lastPositionMs, saved.durationMs)) return 0L
        return saved.lastPositionMs
    }

    @Synchronized
    fun clearPlaybackPosition(
        userId: String,
        movieId: String,
        episodeId: String? = null,
        commitNow: Boolean = true
    ) {
        val normalizedEpId = episodeId?.takeIf { it.isNotBlank() && !it.startsWith("synth_s1_e1_") }
        val effectiveUserId = userId.ifBlank { activeUserId }
        val positionKey = buildPositionKey(movieId, normalizedEpId)

        val currentList = loadHistoryForUser(effectiveUserId).toMutableList()
        val updated = currentList.map { item ->
            val matches = if (normalizedEpId != null) {
                item.movieId == movieId && item.episodeId == normalizedEpId
            } else {
                item.movieId == movieId && item.episodeId.isNullOrBlank()
            }
            if (matches) item.copy(lastPositionMs = 0L) else item
        }

        val posMap = loadPositionMapJson(effectiveUserId)
        posMap.remove(positionKey)

        val editor = prefs.edit()
            .putString("history_$effectiveUserId", serializeHistoryArray(updated))
            .putString("positions_$effectiveUserId", posMap.toString())
        if (commitNow) editor.commit() else editor.apply()

        if (effectiveUserId == activeUserId) {
            _historyFlow.value = updated
        }
    }

    private fun loadHistoryForUser(userId: String): List<WatchHistoryItem> {
        val jsonStr = prefs.getString("history_$userId", null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val items = mutableListOf<WatchHistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    WatchHistoryItem(
                        movieId = obj.getString("movieId"),
                        episodeId = if (obj.has("episodeId") && !obj.isNull("episodeId")) {
                            obj.getString("episodeId").takeIf { it.isNotBlank() }
                        } else null,
                        movieTitle = obj.getString("movieTitle"),
                        episodeTitle = if (obj.has("episodeTitle") && !obj.isNull("episodeTitle")) obj.getString("episodeTitle") else null,
                        coverUrl = obj.optString("coverUrl", ""),
                        lastPositionMs = obj.optLong("lastPositionMs", 0L),
                        durationMs = obj.optLong("durationMs", 0L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadPositionMapJson(userId: String): JSONObject {
        val raw = prefs.getString("positions_$userId", null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun loadPositionFromMap(
        userId: String,
        positionKey: String,
        movieId: String,
        episodeId: String?
    ): WatchHistoryItem? {
        val map = loadPositionMapJson(userId)
        val entry = map.optJSONObject(positionKey) ?: return null
        val pos = entry.optLong("pos", 0L)
        val dur = entry.optLong("dur", 0L)
        if (pos <= 0L) return null
        return WatchHistoryItem(
            movieId = movieId,
            episodeId = episodeId,
            movieTitle = entry.optString("title", ""),
            episodeTitle = entry.optString("epTitle", "").takeIf { it.isNotBlank() },
            coverUrl = entry.optString("cover", ""),
            lastPositionMs = pos,
            durationMs = dur,
            timestamp = entry.optLong("ts", 0L)
        )
    }

    private fun saveHistoryAndPositionForUser(
        userId: String,
        items: List<WatchHistoryItem>,
        positionKey: String,
        positionItem: WatchHistoryItem,
        commitNow: Boolean
    ) {
        val posMap = loadPositionMapJson(userId)
        posMap.put(
            positionKey,
            JSONObject().apply {
                put("pos", positionItem.lastPositionMs)
                put("dur", positionItem.durationMs)
                put("ts", positionItem.timestamp)
                put("title", positionItem.movieTitle)
                put("epTitle", positionItem.episodeTitle ?: "")
                put("cover", positionItem.coverUrl)
            }
        )
        // Bound position map size while retaining most recent entries
        if (posMap.length() > MAX_POSITION_MAP_ENTRIES) {
            val entries = mutableListOf<Pair<String, Long>>()
            val keys = posMap.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val ts = posMap.optJSONObject(k)?.optLong("ts", 0L) ?: 0L
                entries.add(k to ts)
            }
            entries.sortedBy { it.second }.take(posMap.length() - MAX_POSITION_MAP_ENTRIES).forEach { (oldKey, _) ->
                posMap.remove(oldKey)
            }
        }

        val editor = prefs.edit()
            .putString("history_$userId", serializeHistoryArray(items))
            .putString("positions_$userId", posMap.toString())
        if (commitNow) editor.commit() else editor.apply()
    }

    private fun serializeHistoryArray(items: List<WatchHistoryItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("movieId", item.movieId)
                put("episodeId", item.episodeId)
                put("movieTitle", item.movieTitle)
                put("episodeTitle", item.episodeTitle)
                put("coverUrl", item.coverUrl)
                put("lastPositionMs", item.lastPositionMs)
                put("durationMs", item.durationMs)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
