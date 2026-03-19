package cz.hodiny.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import cz.hodiny.data.preferences.AppSettings

object GeofenceManager {

    private const val GEOFENCE_ID = "WORK_ZONE"
    private lateinit var client: GeofencingClient

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context, settings: AppSettings) {
        if (settings.workLat == 0.0 && settings.workLng == 0.0) return
        if (settings.detectionMode == "wifi") return

        client = LocationServices.getGeofencingClient(context)

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(settings.workLat, settings.workLng, settings.workRadius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        client.addGeofences(request, pendingIntent(context))
    }

    fun stop(context: Context) {
        if (!::client.isInitialized) {
            client = LocationServices.getGeofencingClient(context)
        }
        client.removeGeofences(pendingIntent(context))
    }
}
