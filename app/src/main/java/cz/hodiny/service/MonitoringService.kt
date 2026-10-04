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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MonitoringService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private var lastSsid: String? = null
    private var lastLoggedSsid: String? = null
    // Callbacky chodí souběžně – kontroly WiFi běží postupně, aby se nepraly o lastSsid
    private val checkMutex = Mutex()
    // Registrace callbacku probíhá v korutině, onDestroy na hlavním vlákně
    private val callbackLock = Any()
    private var callbackRegistered = false
    private var destroyed = false

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
        startForegroundSilent()
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // Výchozí stav bereme z uloženého příznaku (ne z aktuální WiFi), aby se po bootu
        // nebo restartu service zaznamenal příchod/odchod, který mezitím nastal.
        scope.launch {
            val app = applicationContext as HodinyApp
            val settings = app.preferences.settings.first()
            lastSsid = if (app.preferences.isOnWorkWifi()) settings.workSsid else ""
            DebugLogger.log("MonitoringService", "onCreate – init lastSsid='$lastSsid' (z uloženého stavu)")

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            synchronized(callbackLock) {
                if (destroyed) return@launch
                connectivityManager.registerNetworkCallback(request, networkCallback)
                callbackRegistered = true
            }
            checkWifi()

            while (true) {
                delay(5 * 60 * 1000L)
                checkWifi(periodic = true)
            }
        }
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

    private fun checkWifi(periodic: Boolean = false) {
        scope.launch { checkMutex.withLock {
            val app = applicationContext as HodinyApp
            val settings = app.preferences.settings.first()
            if (settings.workSsid.isBlank() || settings.detectionMode == "gps") {
                if (!periodic) DebugLogger.log("MonitoringService", "WiFi kontrola přeskočena (mode=${settings.detectionMode}, ssid=${settings.workSsid.ifBlank { "prázdné" }})")
                return@withLock
            }

            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val rawSsid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
            val currentSsid = if (rawSsid == "<unknown ssid>") "" else rawSsid
            val isOnWorkWifi = currentSsid.isNotBlank() && currentSsid == settings.workSsid
            // onCapabilitiesChanged chodí při každé změně síly signálu – logujeme jen změnu SSID
            if (currentSsid != lastLoggedSsid) {
                lastLoggedSsid = currentSsid
                DebugLogger.log("MonitoringService", "SSID='$currentSsid' vs '${settings.workSsid}' → shoda=$isOnWorkWifi, lastSsid='$lastSsid'")
            }

            when {
                isOnWorkWifi && lastSsid != settings.workSsid -> {
                    DebugLogger.log("MonitoringService", "→ enter")
                    lastSsid = settings.workSsid
                    app.preferences.setOnWorkWifi(true)
                    handleZoneEnter(applicationContext, "wifi")
                }
                !isOnWorkWifi && lastSsid == settings.workSsid -> {
                    DebugLogger.log("MonitoringService", "→ exit")
                    lastSsid = ""
                    app.preferences.setOnWorkWifi(false)
                    handleZoneExit(applicationContext, "wifi")
                }
                else -> {}
            }
        } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY // Systém ho restartuje pokud ho zabije

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.log("MonitoringService", "onDestroy – service zastaven!")
        scope.cancel()
        synchronized(callbackLock) {
            destroyed = true
            if (callbackRegistered) {
                runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
                callbackRegistered = false
            }
        }
    }
}
