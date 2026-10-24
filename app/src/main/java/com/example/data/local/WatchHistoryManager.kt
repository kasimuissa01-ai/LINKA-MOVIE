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
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
}

/**
 * Manages user watch history locally per authenticated/guest user.
 * Provides instant real-time updates to UI components via StateFlow.
 */
class WatchHistoryManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "movieroom_watch_history"
        private const val MAX_HISTORY_ITEMS = 20

        @Volatile
        private var instance: WatchHistoryManager? = null

        fun getInstance(context: Context): WatchHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: WatchHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _historyFlow = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<WatchHistoryItem>> = _historyFlow.asStateFlow()

    private var activeUserId: String = ""

    fun setUser(userId: String) {
        if (activeUserId != userId) {
            activeUserId = userId
            _historyFlow.value = loadHistoryForUser(userId)
        }
    }

    fun recordWatchProgress(
        userId: String,
        movie: Movie,
        episode: Episode?,
        positionMs: Long,
        durationMs: Long
    ) {
        if (positionMs <= 1000L && durationMs <= 0L) return
        val effectiveUserId = userId.ifBlank { activeUserId }
        val currentList = loadHistoryForUser(effectiveUserId).toMutableList()

        val itemKey = if (episode != null) "${movie.id}_ep_${episode.id}" else movie.id
        val displayTitle = if (episode != null) {
            val epNumber = episode.episodeNumber
            val sNumber = episode.seasonNumber
            "${movie.title} S${String.format(java.util.Locale.US, "%02d", sNumber)} EP${String.format(java.util.Locale.US, "%02d", epNumber)}"
        } else {
            movie.title
        }

        val newItem = WatchHistoryItem(
            movieId = movie.id,
            episodeId = episode?.id,
            movieTitle = movie.title,
            episodeTitle = displayTitle,
            coverUrl = movie.coverUrl,
            lastPositionMs = positionMs,
            durationMs = if (durationMs > 0L) durationMs else (movie.durationMinutes * 60L * 1000L),
            timestamp = System.currentTimeMillis()
        )

        // Remove previous entry for same movie / episode to move to top
        currentList.removeAll {
            if (episode != null) {
                it.movieId == movie.id && it.episodeId == episode.id
            } else {
                it.movieId == movie.id && it.episodeId.isNullOrBlank()
            }
        }
        currentList.add(0, newItem)

        val trimmed = currentList.take(MAX_HISTORY_ITEMS)
        saveHistoryForUser(effectiveUserId, trimmed)
        if (effectiveUserId == activeUserId) {
            _historyFlow.value = trimmed
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
                        episodeId = if (obj.has("episodeId") && !obj.isNull("episodeId")) obj.getString("episodeId") else null,
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

    private fun saveHistoryForUser(userId: String, items: List<WatchHistoryItem>) {
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
        prefs.edit().putString("history_$userId", array.toString()).apply()
    }
}
