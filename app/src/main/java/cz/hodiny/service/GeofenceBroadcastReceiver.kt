package cz.hodiny.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            DebugLogger.log("Geofence", "event je null")
            return
        }
        if (event.hasError()) {
            DebugLogger.log("Geofence", "chyba eventu: ${event.errorCode}")
            return
        }

        val transition = event.geofenceTransition
        val transitionName = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            else -> "NEZNÁMÝ($transition)"
        }
        DebugLogger.log("Geofence", "přechod: $transitionName")

        CoroutineScope(Dispatchers.IO).launch {
            when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> handleZoneEnter(context, "gps")
                Geofence.GEOFENCE_TRANSITION_EXIT -> handleZoneExit(context, "gps")
            }
        }
    }
}
