package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules and dispatches daily movie recommendation notifications
 * for REAL movies saved in the user's catalog with cover art.
 */
class DailyMovieNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_SLOT_TYPE = "extra_slot_type"

        private const val REQUEST_CODE_MORNING = 1001
        private const val REQUEST_CODE_AFTERNOON = 1002
        private const val REQUEST_CODE_NIGHT = 1003

        fun scheduleDailyPicks(context: Context) {
            MovieNotificationHelper.createNotificationChannel(context)

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
        // Dispatches notification with real movie from database and high-res cover art
        MovieNotificationHelper.sendDailyPickNotification(context, slotType)
    }
}
