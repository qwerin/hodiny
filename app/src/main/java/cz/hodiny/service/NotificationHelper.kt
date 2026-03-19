package cz.hodiny.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import cz.hodiny.MainActivity
import cz.hodiny.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object NotificationHelper {

    const val CHANNEL_ATTENDANCE = "hodiny_attendance"
    const val CHANNEL_REMINDER = "hodiny_reminder"
    const val CHANNEL_SILENT = "hodiny_silent"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ATTENDANCE, "Docházka", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Potvrzení příchodu a odchodu" }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Připomenutí", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Denní připomenutí odchodu" }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, "Monitorování", NotificationManager.IMPORTANCE_MIN)
                .apply {
                    description = "Sledování pracovní WiFi na pozadí"
                    setShowBadge(false)
                }
        )
    }

    fun sendArrivalNotification(context: Context, recordId: Long, arrivalTime: String) {
        val time = formatTime(arrivalTime)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ATTENDANCE)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Příchod do práce")
            .setContentText("Zaznamenán příchod v $time. Klepněte pro potvrzení.")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(recordId.toInt(), notif)
    }

    fun sendDepartureReminder(context: Context, recordId: Long, departureTime: String?) {
        val timeText = if (departureTime != null) formatTime(departureTime) else "nezaznamenán"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Potvrzení odchodu")
            .setContentText("Poslední odchod: $timeText. Klepněte pro potvrzení.")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(10_000 + recordId.toInt(), notif)
    }

    private fun formatTime(iso: String): String = try {
        val dt = LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        "%02d:%02d".format(dt.hour, dt.minute)
    } catch (_: Exception) { iso }
}
