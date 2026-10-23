package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil.Coil
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.R
import com.example.data.local.MovieRoomDatabase
import com.example.domain.model.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Universal Movie Notification Helper.
 * Delivers clean notifications for REAL catalog movies featuring high-quality cover art.
 */
object MovieNotificationHelper {

    private const val TAG = "MovieNotificationHelper"
    const val CHANNEL_ID = "movie_room_catalog_notifications"
    const val CHANNEL_NAME = "Movie Releases & Recommendations"
    const val EXTRA_MOVIE_ID = "extra_movie_id"

    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority cinema alerts with movie poster art"
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Sends an immediate rich notification when a real movie or TV series is published.
     */
    fun sendNewMovieNotification(context: Context, movie: Movie) {
        notificationScope.launch {
            val genreSnippet = if (movie.genres.isNotEmpty()) {
                movie.genres.take(2).joinToString(" • ")
            } else "Now Streaming"

            val title = "🎬 New Release: ${movie.title}"
            val summary = "$genreSnippet • ${movie.durationMinutes} min • Watch now in HD"

            postRichMovieNotification(
                context = context,
                notificationId = ("new_" + movie.id).hashCode(),
                movie = movie,
                contentTitle = title,
                contentText = summary
            )
        }
    }

    /**
     * Picks a real movie from the app's Room database and sends a daily pick notification.
     */
    fun sendDailyPickNotification(context: Context, slotType: String) {
        notificationScope.launch {
            try {
                val db = MovieRoomDatabase.getInstance(context)
                val entities = db.movieDao().getAllMoviesList()
                val realMovies = entities.map { it.toDomain() }.filter { it.title.isNotBlank() }

                if (realMovies.isEmpty()) {
                    Log.d(TAG, "No movies currently in catalog for $slotType notification.")
                    return@launch
                }

                // Deterministically cycle or pick from real catalog movies
                val pickedMovie = realMovies.random()

                val slotEmoji = when (slotType) {
                    "Morning Pick" -> "☀️"
                    "Afternoon Cinema" -> "🍿"
                    "Night Movie Special" -> "🌙"
                    else -> "🎬"
                }

                val title = "$slotEmoji $slotType: ${pickedMovie.title}"
                val genreStr = if (pickedMovie.genres.isNotEmpty()) pickedMovie.genres.take(2).joinToString(" • ") else "Featured"
                val summary = "$genreStr • Tap to stream now on MovieRoom"

                postRichMovieNotification(
                    context = context,
                    notificationId = ("daily_" + pickedMovie.id + "_" + slotType).hashCode(),
                    movie = pickedMovie,
                    contentTitle = title,
                    contentText = summary
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error generating daily movie pick notification: ${e.message}")
            }
        }
    }

    /**
     * Asynchronously loads the movie's cover art into a software Bitmap,
     * builds a BigPictureStyle notification, and dispatches it.
     */
    private suspend fun postRichMovieNotification(
        context: Context,
        notificationId: Int,
        movie: Movie,
        contentTitle: String,
        contentText: String
    ) = withContext(Dispatchers.IO) {
        createNotificationChannel(context)

        // Android 13+ Notification permission guard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Skipping notification.")
                return@withContext
            }
        }

        // 1. Asynchronously load cover poster as software Bitmap (allowHardware=false)
        val coverBitmap = loadCoverBitmap(context, movie.coverUrl)

        // 2. Build Intent to open the movie in MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_MOVIE_ID, movie.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Construct NotificationCompat with BigPictureStyle
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(coverBitmap)
                    .bigLargeIcon(null as Bitmap?) // Collapses thumbnail when expanded for clean look
                    .setBigContentTitle(contentTitle)
                    .setSummaryText(contentText)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.i(TAG, "Posted real movie notification: $contentTitle")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting notification: ${e.message}")
        }
    }

    private suspend fun loadCoverBitmap(context: Context, coverUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val trimmed = coverUrl.trim()
        if (trimmed.isBlank()) return@withContext null

        try {
            val request = ImageRequest.Builder(context)
                .data(trimmed)
                .allowHardware(false) // Mandatory for Notification BigPictureStyle Bitmaps
                .size(1024, 576)
                .build()

            val result = Coil.imageLoader(context).execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Could not load cover bitmap for notification from '$coverUrl': ${e.message}")
            null
        }
    }
}
