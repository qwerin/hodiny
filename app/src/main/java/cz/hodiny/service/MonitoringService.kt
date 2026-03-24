package cz.hodiny.service

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cz.hodiny.HodinyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MonitoringService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private var lastSsid: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            checkWifi()
        }
        override fun onLost(network: Network) {
            checkWifi()
        }
    }

    override fun onCreate() {
        super.onCreate()
        DebugLogger.log("MonitoringService", "onCreate – service spuštěn")
        startForegroundSilent()
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun startForegroundSilent() {
        // IMPORTANCE_MIN = bez zvuku, bez ikony ve status baru, skryta
        val notif = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SILENT)
            .setContentTitle("Hodiny")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        startForeground(999, notif)
    }

    private fun checkWifi() {
        scope.launch {
            val app = applicationContext as HodinyApp
            val settings = app.preferences.settings.first()
            if (settings.workSsid.isBlank() || settings.detectionMode == "gps") {
                DebugLogger.log("MonitoringService", "WiFi kontrola přeskočena (mode=${settings.detectionMode}, ssid=${settings.workSsid.ifBlank { "prázdné" }})")
                return@launch
            }

            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val rawSsid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
            val currentSsid = if (rawSsid == "<unknown ssid>") "" else rawSsid
            val isOnWorkWifi = currentSsid.isNotBlank() && currentSsid == settings.workSsid
            DebugLogger.log("MonitoringService", "SSID='$currentSsid' vs '${settings.workSsid}' → shoda=$isOnWorkWifi, lastSsid='$lastSsid'")

            when {
                isOnWorkWifi && lastSsid != settings.workSsid -> {
                    DebugLogger.log("MonitoringService", "→ enter")
                    lastSsid = settings.workSsid
                    handleZoneEnter(applicationContext, "wifi")
                }
                !isOnWorkWifi && lastSsid == settings.workSsid -> {
                    DebugLogger.log("MonitoringService", "→ exit")
                    lastSsid = ""
                    handleZoneExit(applicationContext, "wifi")
                }
                else -> DebugLogger.log("MonitoringService", "→ žádná změna")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY // Systém ho restartuje pokud ho zabije

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.log("MonitoringService", "onDestroy – service zastaven!")
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
