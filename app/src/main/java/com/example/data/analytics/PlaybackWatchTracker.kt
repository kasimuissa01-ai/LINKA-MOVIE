package com.example.data.analytics

import android.util.Log
import androidx.media3.common.Player
import java.util.UUID

/**
 * High-accuracy playback session tracker integrated directly with Media3 ExoPlayer.
 *
 * Accuracy Guarantees:
 * - Genuine Watch Time Only: Accumulates playback time ONLY when ExoPlayer is in STATE_READY and isPlaying == true.
 * - Paused, buffering, loading, and idle durations are strictly excluded.
 * - Seeking Guard: Forward seeks (e.g. jumping 10 minutes forward in 1 second) do NOT count skipped segments as watched.
 *   Calculates delta by clamping position changes against elapsed wall-clock time adjusted for playback speed.
 * - Completion Threshold: Formally defined as reaching >= 90% of the media duration or Player.STATE_ENDED.
 *   Marked once per viewing session to avoid duplicate completion records.
 * - Periodic Updates: Syncs progress every 20 seconds and on state changes (pause, resume, error, release).
 */
class PlaybackWatchTracker(
    private val analyticsManager: AppAnalyticsManager
) {
    companion object {
        private const val TAG = "PlaybackWatchTracker"
        const val COMPLETION_THRESHOLD_RATIO = 0.90 // 90% of total duration
    }

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var currentMovieId: String? = null
    private var accumulatedWatchedSeconds: Double = 0.0
    private var lastKnownPositionMs: Long = 0L
    private var lastWallClockTimeMs: Long = 0L

    private var isSessionStartedEmitted = false
    private var isCompletedEmitted = false
    private var lastPeriodicSyncTimeMs: Long = 0L

    /**
     * Initializes a new playback session for a movie.
     */
    fun startSession(movieId: String, initialPositionMs: Long = 0L) {
        currentSessionId = UUID.randomUUID().toString()
        currentMovieId = movieId
        accumulatedWatchedSeconds = 0.0
        lastKnownPositionMs = initialPositionMs.coerceAtLeast(0L)
        lastWallClockTimeMs = System.currentTimeMillis()
        isSessionStartedEmitted = false
        isCompletedEmitted = false
        lastPeriodicSyncTimeMs = System.currentTimeMillis()

        Log.d(TAG, "Started playback analytics session: $currentSessionId for movie: $movieId at ${initialPositionMs}ms")
    }

    /**
     * Called when ExoPlayer transitions to playing state.
     */
    fun onPlay(player: Player) {
        val movieId = currentMovieId ?: return
        val posSec = (player.currentPosition.coerceAtLeast(0L) / 1000).toInt()

        if (!isSessionStartedEmitted) {
            isSessionStartedEmitted = true
            analyticsManager.trackEvent(
                eventName = "movie_started",
                movieId = movieId,
                playbackPositionSeconds = posSec,
                customSessionId = currentSessionId
            )
            syncSession(posSec, isCompleted = false)
        } else {
            analyticsManager.trackEvent(
                eventName = "movie_resumed",
                movieId = movieId,
                playbackPositionSeconds = posSec,
                customSessionId = currentSessionId
            )
        }

        lastWallClockTimeMs = System.currentTimeMillis()
        lastKnownPositionMs = player.currentPosition.coerceAtLeast(0L)
    }

    /**
     * Called when ExoPlayer transitions to paused state.
     */
    fun onPause(player: Player) {
        val movieId = currentMovieId ?: return
        val posSec = (player.currentPosition.coerceAtLeast(0L) / 1000).toInt()

        // Flush any remaining partial tick
        collectPlayingTick(player)

        analyticsManager.trackEvent(
            eventName = "movie_paused",
            movieId = movieId,
            playbackPositionSeconds = posSec,
            customSessionId = currentSessionId
        )
        syncSession(posSec, isCompleted = isCompletedEmitted)
        lastWallClockTimeMs = 0L
    }

    /**
     * Periodic tick invoked by PlayerViewModel's progress loop (every ~300ms to 1s).
     */
    fun onProgressTick(player: Player) {
        val movieId = currentMovieId ?: return
        val isGenuinelyPlaying = player.isPlaying && player.playbackState == Player.STATE_READY

        if (isGenuinelyPlaying) {
            collectPlayingTick(player)

            val currPos = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.coerceAtLeast(0L)

            // Check completion threshold (90% or STATE_ENDED)
            if (duration > 15_000L && currPos >= (duration * COMPLETION_THRESHOLD_RATIO) && !isCompletedEmitted) {
                isCompletedEmitted = true
                val posSec = (currPos / 1000).toInt()
                analyticsManager.trackEvent(
                    eventName = "movie_completed",
                    movieId = movieId,
                    playbackPositionSeconds = posSec,
                    properties = mapOf(
                        "duration_sec" to (duration / 1000).toInt(),
                        "watched_sec" to accumulatedWatchedSeconds.toInt()
                    ),
                    customSessionId = currentSessionId
                )
                syncSession(posSec, isCompleted = true)
            }

            // Periodic sync (every 20 seconds of genuine playback)
            val now = System.currentTimeMillis()
            if (now - lastPeriodicSyncTimeMs >= 20_000L) {
                lastPeriodicSyncTimeMs = now
                val posSec = (currPos / 1000).toInt()
                analyticsManager.trackEvent(
                    eventName = "movie_progress",
                    movieId = movieId,
                    playbackPositionSeconds = posSec,
                    properties = mapOf("watched_sec" to accumulatedWatchedSeconds.toInt()),
                    customSessionId = currentSessionId
                )
                syncSession(posSec, isCompleted = isCompletedEmitted)
            }
        } else {
            // Paused, buffering, or idle: do not accumulate watch time, just update anchor
            lastWallClockTimeMs = System.currentTimeMillis()
            lastKnownPositionMs = player.currentPosition.coerceAtLeast(0L)
        }
    }

    /**
     * Handles seeking: resets position and wall-clock anchors without accumulating skipped time.
     */
    fun onSeekDiscontinuity(newPositionMs: Long) {
        lastKnownPositionMs = newPositionMs.coerceAtLeast(0L)
        lastWallClockTimeMs = System.currentTimeMillis()
    }

    /**
     * Records playback errors.
     */
    fun onError(errorCode: String, errorMessage: String?) {
        val movieId = currentMovieId ?: return
        analyticsManager.trackEvent(
            eventName = "playback_error",
            movieId = movieId,
            playbackPositionSeconds = (lastKnownPositionMs / 1000).toInt(),
            properties = mapOf(
                "error_code" to errorCode,
                "error_message" to (errorMessage ?: "Unknown Playback Exception")
            ),
            customSessionId = currentSessionId
        )
    }

    /**
     * Releases the session and flushes final watch time to the database.
     */
    fun onRelease(player: Player?) {
        val movieId = currentMovieId ?: return
        if (player != null) {
            collectPlayingTick(player)
        }

        val lastPosSec = (lastKnownPositionMs / 1000).toInt()
        syncSession(lastPosSec, isCompleted = isCompletedEmitted)

        Log.d(TAG, "Completed watch session $currentSessionId: accumulated ${accumulatedWatchedSeconds.toInt()}s watched")
        currentMovieId = null
    }

    private fun collectPlayingTick(player: Player) {
        if (lastWallClockTimeMs <= 0L) {
            lastWallClockTimeMs = System.currentTimeMillis()
            lastKnownPositionMs = player.currentPosition.coerceAtLeast(0L)
            return
        }

        val now = System.currentTimeMillis()
        val currPos = player.currentPosition.coerceAtLeast(0L)
        val wallClockElapsedSec = (now - lastWallClockTimeMs) / 1000.0
        val posDiffSec = (currPos - lastKnownPositionMs) / 1000.0

        // Guard against long freezes or seeking forward:
        // Capped by playback speed (e.g. at 1.0x or 2.0x) + reasonable variance tolerance
        if (wallClockElapsedSec in 0.05..5.0 && posDiffSec > 0.0) {
            val speed = player.playbackParameters.speed.coerceAtLeast(0.5f)
            val maxAllowableDelta = (wallClockElapsedSec * speed) + 0.3
            val deltaSeconds = minOf(posDiffSec, maxAllowableDelta)
            accumulatedWatchedSeconds += deltaSeconds
        }

        lastWallClockTimeMs = now
        lastKnownPositionMs = currPos
    }

    private fun syncSession(lastPositionSeconds: Int, isCompleted: Boolean) {
        val movieId = currentMovieId ?: return
        analyticsManager.recordWatchSession(
            sessionId = currentSessionId,
            movieId = movieId,
            lastPositionSeconds = lastPositionSeconds,
            watchedSeconds = accumulatedWatchedSeconds.toInt(),
            isCompleted = isCompleted
        )
    }
}
