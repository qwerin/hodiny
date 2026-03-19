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
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        CoroutineScope(Dispatchers.IO).launch {
            when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> handleZoneEnter(context, "gps")
                Geofence.GEOFENCE_TRANSITION_EXIT -> handleZoneExit(context, "gps")
            }
        }
    }
}
