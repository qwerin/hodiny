package cz.hodiny.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import cz.hodiny.HodinyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WifiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return

        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO) ?: return
        val ssid = intent.getStringExtra(WifiManager.EXTRA_BSSID) // SSID v jiném klíči
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as HodinyApp
            val settings = app.preferences.settings.first()

            if (settings.workSsid.isBlank() || settings.detectionMode == "gps") return@launch

            val currentSsid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
            val isConnectedToWork = networkInfo.isConnected && currentSsid == settings.workSsid

            if (isConnectedToWork) {
                handleZoneEnter(context, "wifi")
            } else if (!networkInfo.isConnected) {
                handleZoneExit(context, "wifi")
            }
        }
    }
}
