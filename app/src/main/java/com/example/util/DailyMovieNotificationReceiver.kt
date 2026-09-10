package com.example.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.util.Calendar

class DailyMovieNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "daily_movie_recommendations"
        const val EXTRA_SLOT_TYPE = "extra_slot_type"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
        const val EXTRA_MOVIE_ID = "extra_movie_id"

        private const val REQUEST_CODE_MORNING = 1001
        private const val REQUEST_CODE_AFTERNOON = 1002
        private const val REQUEST_CODE_NIGHT = 1003

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Daily Movie Recommendations",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Personalized morning, afternoon, and night movie picks"
                    enableVibration(true)
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun scheduleDailyPicks(context: Context) {
            createNotificationChannel(context)

            // Morning: 09:00 AM
            scheduleAlarm(context, 9, 0, REQUEST_CODE_MORNING, "Morning Pick")
            // Afternoon: 02:00 PM (14:00)
            scheduleAlarm(context, 14, 0, REQUEST_CODE_AFTERNOON, "Afternoon Cinema")
            // Night: 08:30 PM (20:30)
            scheduleAlarm(context, 20, 30, REQUEST_CODE_NIGHT, "Night Movie Special")
        }

        private fun scheduleAlarm(
            context: Context,
            hourOfDay: Int,
            minute: Int,
            requestCode: Int,
            slotType: String
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DailyMovieNotificationReceiver::class.java).apply {
                putExtra(EXTRA_SLOT_TYPE, slotType)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val slotType = intent.getStringExtra(EXTRA_SLOT_TYPE) ?: "Cinema Pick"

        val sampleTitles = when (slotType) {
            "Morning Pick" -> listOf(
                "Dune: Part Two" to "Kickstart your morning with thrilling Sci-Fi adventures.",
                "Spider-Man: Across the Spider-Verse" to "Start your day with high-flying energy!",
                "Interstellar" to "Expand your horizon this morning."
            )
            "Afternoon Cinema" -> listOf(
                "Oppenheimer" to "Perfect afternoon masterclass drama on MovieRoom.",
                "Inception" to "Take a lunch break inside a dream within a dream.",
                "The Batman" to "Unravel the Gotham mystery this afternoon."
            )
            else -> listOf(
                "Avengers: Endgame" to "Relax and enjoy an epic movie night with MovieRoom!",
                "Blade Runner 2049" to "Immerse in stunning neon cyberpunk visuals tonight.",
                "Deadpool & Wolverine" to "Unwind tonight with laugh-out-loud action!"
            )
        }

        val pick = sampleTitles.random()

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎬 $slotType: ${pick.first}")
            .setContentText(pick.second)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${pick.second} Stream now in 4K Ultra HD."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
}
