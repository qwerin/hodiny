package cz.hodiny.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.hodiny.HodinyApp
import cz.hodiny.worker.DepartureNotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as HodinyApp
            val settings = app.preferences.settings.first()
            if (!settings.isOnboarded) return@launch

            // Restartuj geofencing po restartu telefonu
            GeofenceManager.start(context, settings)
            DepartureNotificationWorker.schedule(context, settings.notificationTime)
            context.startForegroundService(
                android.content.Intent(context, MonitoringService::class.java)
            )
        }
    }
}
