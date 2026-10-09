package com.example.data.analytics

/**
 * Data models for application usage tracking and video playback analytics.
 */

data class AnalyticsEvent(
    val eventName: String,
    val installationId: String,
    val sessionId: String,
    val movieId: String? = null,
    val playbackPositionSeconds: Int? = null,
    val properties: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

data class WatchSessionPayload(
    val sessionId: String,
    val installationId: String,
    val movieId: String,
    val lastPositionSeconds: Int,
    val watchedSeconds: Int,
    val isCompleted: Boolean = false,
    val startedAt: Long = System.currentTimeMillis()
)

data class MovieAnalyticsItem(
    val movieId: String,
    val title: String,
    val coverUrl: String,
    val uniqueViewers: Long,
    val totalPlays: Long,
    val totalWatchSeconds: Long,
    val avgWatchSeconds: Long,
    val completionRate: Double
) {
    val totalWatchHoursFormatted: String
        get() {
            val hours = totalWatchSeconds / 3600
            val minutes = (totalWatchSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }

    val avgWatchMinutesFormatted: String
        get() {
            val minutes = avgWatchSeconds / 60
            val seconds = avgWatchSeconds % 60
            return "${minutes}m ${seconds}s"
        }
}

data class RecentActivityItem(
    val id: String,
    val eventName: String,
    val installationId: String,
    val sessionId: String,
    val movieId: String?,
    val movieTitle: String,
    val playbackPositionSeconds: Int?,
    val createdAt: String
)

data class AdminAnalyticsSummary(
    val periodDays: Int = 30,
    val totalInstallations: Long = 0,
    val firstOpens: Long = 0,
    val activeToday: Long = 0,
    val active7d: Long = 0,
    val active30d: Long = 0,
    val returningInstallations: Long = 0,
    val totalPlays: Long = 0,
    val totalWatchSeconds: Long = 0,
    val totalWatchHours: Double = 0.0,
    val avgWatchSecondsPerPlay: Long = 0,
    val playbackErrors: Long = 0,
    val movieStats: List<MovieAnalyticsItem> = emptyList(),
    val recentEvents: List<RecentActivityItem> = emptyList(),
    val serverTimestamp: String = ""
) {
    val avgWatchTimeFormatted: String
        get() {
            val minutes = avgWatchSecondsPerPlay / 60
            val seconds = avgWatchSecondsPerPlay % 60
            return "${minutes}m ${seconds}s"
        }
}

enum class DateFilterRange(val days: Int, val label: String) {
    TODAY(1, "Today"),
    LAST_7_DAYS(7, "7 Days"),
    LAST_30_DAYS(30, "30 Days"),
    ALL_TIME(0, "All Time")
}

enum class MovieSortOption(val label: String) {
    WATCH_TIME("Watch Time"),
    PLAYS("Total Plays"),
    UNIQUE_VIEWERS("Viewers"),
    COMPLETION_RATE("Completion %")
}
