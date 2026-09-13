package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service that handles background downloads for files > 2GB.
 * Features:
 * 1. Holds a partial WakeLock to prevent CPU throttling or network cutoff during sleep.
 * 2. Displays a persistent ongoing notification with live progress, chunk metrics, and action controls (Pause/Cancel).
 * 3. Dispatches user actions directly to [OfflineDownloadManager] listeners.
 */
class OfflineDownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_FOREGROUND
        val movieId = intent?.getStringExtra(EXTRA_MOVIE_ID)

        when (action) {
            ACTION_START_FOREGROUND -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Movie Asset"
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                val progressText = intent?.getStringExtra(EXTRA_TEXT) ?: "Preparing chunked download..."
                val notification = buildDownloadNotification(movieId, title, progress, progressText)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    startForeground(NOTIFICATION_ID, notification, foregroundType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

            ACTION_UPDATE_PROGRESS -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Movie Asset"
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                val progressText = intent?.getStringExtra(EXTRA_TEXT) ?: "Downloading..."
                val isComplete = intent?.getBooleanExtra(EXTRA_IS_COMPLETE, false) ?: false

                val notification = if (isComplete) {
                    buildCompleteNotification(title)
                } else {
                    buildDownloadNotification(movieId, title, progress, progressText)
                }

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)

                if (isComplete) {
                    // Detach from foreground once download is complete
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
            }

            ACTION_PAUSE -> {
                Log.d(TAG, "Received ACTION_PAUSE from notification for movie $movieId")
                actionListener?.invoke(ACTION_PAUSE, movieId)
            }

            ACTION_CANCEL -> {
                Log.d(TAG, "Received ACTION_CANCEL from notification for movie $movieId")
                actionListener?.invoke(ACTION_CANCEL, movieId)
            }

            ACTION_STOP_FOREGROUND -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MovieRoom:OfflineDownloadWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // 6 hours max for large >2GB files
            }
            Log.d(TAG, "Acquired WakeLock for background download")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Released WakeLock")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Movie Downloads (>2GB)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time background progress and controls for chunked movie downloads"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildDownloadNotification(
        movieId: String?,
        title: String,
        progress: Int,
        progressText: String
    ): android.app.Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $title")
            .setContentText(progressText)
            .setSubText("Chunked Download (>2GB)")
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        // Pause action button
        val pauseIntent = Intent(this, OfflineDownloadService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_MOVIE_ID, movieId)
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1001,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)

        // Cancel action button
        val cancelIntent = Intent(this, OfflineDownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_MOVIE_ID, movieId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1002,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        return builder.build()
    }

    private fun buildCompleteNotification(title: String): android.app.Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("$title is ready for offline playback")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val TAG = "OfflineDownloadService"
        const val CHANNEL_ID = "movieroom_downloads_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START_FOREGROUND = "com.example.action.START_DOWNLOAD_FOREGROUND"
        const val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_DOWNLOAD_PROGRESS"
        const val ACTION_STOP_FOREGROUND = "com.example.action.STOP_DOWNLOAD_FOREGROUND"
        const val ACTION_PAUSE = "com.example.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.action.CANCEL_DOWNLOAD"

        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_IS_COMPLETE = "extra_is_complete"

        private var actionListener: ((action: String, movieId: String?) -> Unit)? = null

        fun registerActionListener(listener: (action: String, movieId: String?) -> Unit) {
            actionListener = listener
        }

        fun unregisterActionListener() {
            actionListener = null
        }

        fun start(context: Context, movieId: String, title: String, progress: Int = 0, text: String = "Starting download...") {
            val intent = Intent(context, OfflineDownloadService::class.java).apply {
                action = ACTION_START_FOREGROUND
                putExtra(EXTRA_MOVIE_ID, movieId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_TEXT, text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(
            context: Context,
            movieId: String,
            title: String,
            progress: Int,
            text: String,
            isComplete: Boolean = false
        ) {
            val intent = Intent(context, OfflineDownloadService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_MOVIE_ID, movieId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_IS_COMPLETE, isComplete)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OfflineDownloadService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
            }
            context.startService(intent)
        }
    }
}

