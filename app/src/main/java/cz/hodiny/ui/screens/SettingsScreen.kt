package cz.hodiny.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import cz.hodiny.HodinyApp
import cz.hodiny.data.preferences.AppSettings
import cz.hodiny.ui.components.SectionTitle
import cz.hodiny.service.GeofenceManager
import cz.hodiny.worker.DepartureNotificationWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()
    val currentSettings by app.preferences.settings.collectAsState(initial = null)

    var userName by remember(currentSettings) { mutableStateOf(currentSettings?.userName ?: "") }
    var ssid by remember(currentSettings) { mutableStateOf(currentSettings?.workSsid ?: "") }
    var radius by remember(currentSettings) { mutableStateOf(currentSettings?.workRadius?.toString() ?: "150") }
    var notifTime by remember(currentSettings) { mutableStateOf(currentSettings?.notificationTime ?: "18:00") }
    var hourlyRate by remember(currentSettings) { mutableStateOf(currentSettings?.hourlyRate?.let { if (it > 0) it.toString() else "" } ?: "") }
    var gpsLat by remember(currentSettings) { mutableStateOf(currentSettings?.workLat ?: 0.0) }
    var gpsLng by remember(currentSettings) { mutableStateOf(currentSettings?.workLng ?: 0.0) }
    var detectionMode by remember(currentSettings) { mutableStateOf(currentSettings?.detectionMode ?: "both") }
    var isLocating by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            isLocating = true
            scope.launch {
                try {
                    val loc = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                    if (loc != null) { gpsLat = loc.latitude; gpsLng = loc.longitude }
                } finally { isLocating = false }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        SectionTitle("Profil")
        OutlinedTextField(value = userName, onValueChange = { userName = it }, label = { Text("Jméno (pro export)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = hourlyRate, onValueChange = { hourlyRate = it }, label = { Text("Hodinová sazba (Kč)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

        SectionTitle("GPS detekce")
        Button(onClick = { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            modifier = Modifier.fillMaxWidth()) {
            Text(if (isLocating) "Zjišťuji..." else if (gpsLat != 0.0) "GPS: ${"%.4f".format(gpsLat)}, ${"%.4f".format(gpsLng)}" else "Nastavit GPS polohu")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("Radius (metry)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

        SectionTitle("WiFi detekce")
        OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("SSID pracovní WiFi") }, modifier = Modifier.fillMaxWidth())

        SectionTitle("Režim detekce")
        listOf("both" to "GPS + WiFi", "gps" to "Pouze GPS", "wifi" to "Pouze WiFi").forEach { (mode, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = detectionMode == mode, onClick = { detectionMode = mode })
                Text(label, modifier = Modifier.padding(start = 4.dp))
            }
        }

        SectionTitle("Notifikace")
        OutlinedTextField(value = notifTime, onValueChange = { notifTime = it }, label = { Text("Čas připomenutí (HH:MM)") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(28.dp))

        Button(onClick = {
            scope.launch {
                val settings = AppSettings(
                    workLat = gpsLat, workLng = gpsLng,
                    workRadius = radius.toIntOrNull() ?: 150,
                    workSsid = ssid.trim(),
                    detectionMode = detectionMode,
                    notificationTime = notifTime,
                    userName = userName.trim(),
                    hourlyRate = hourlyRate.toDoubleOrNull() ?: 0.0,
                    isOnboarded = true
                )
                app.preferences.save(settings)
                GeofenceManager.stop(context)
                GeofenceManager.start(context, settings)
                DepartureNotificationWorker.schedule(context, notifTime)
                saved = true
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Uložit nastavení") }

        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("Nastavení uloženo ✓", color = MaterialTheme.colorScheme.secondary)
        }
    }
}
