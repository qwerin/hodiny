package cz.hodiny.worker

import android.content.Context
import androidx.work.*
import cz.hodiny.HodinyApp
import cz.hodiny.service.NotificationHelper
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class DepartureNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HodinyApp
        val today = LocalDate.now().toString()
        val record = app.repository.findByDate(today)

        if (record != null && !record.isLocked) {
            NotificationHelper.sendDepartureReminder(applicationContext, record.id, record.departureTime)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "departure_notification"

        fun schedule(context: Context, timeString: String) {
            val parts = timeString.split(":").mapNotNull { it.toIntOrNull() }
            val hour = parts.getOrElse(0) { 18 }
            val minute = parts.getOrElse(1) { 0 }

            val now = LocalDateTime.now()
            var target = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
            if (target.isBefore(now)) target = target.plusDays(1)

            val delay = Duration.between(now, target).toMinutes()

            val request = PeriodicWorkRequestBuilder<DepartureNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setConstraints(Constraints.NONE)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
