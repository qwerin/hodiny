package cz.hodiny.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import cz.hodiny.HodinyApp
import cz.hodiny.MainActivity
import cz.hodiny.data.db.HodinyDatabase
import cz.hodiny.data.preferences.AppSettings
import cz.hodiny.ui.components.SectionTitle
import cz.hodiny.ui.components.TimePickerField
import cz.hodiny.service.DebugLogger
import cz.hodiny.service.GeofenceManager
import cz.hodiny.worker.DepartureNotificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()
    val currentSettings by app.preferences.settings.collectAsState(initial = null)

    var userName by remember(currentSettings) { mutableStateOf(currentSettings?.userName ?: "") }
    var ssid by remember(currentSettings) { mutableStateOf(currentSettings?.workSsid ?: "") }
    var radius by remember(currentSettings) { mutableStateOf(currentSettings?.workRadius?.toString() ?: "150") }
    var notifHour by remember(currentSettings) { mutableStateOf(currentSettings?.notificationTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 18) }
    var notifMinute by remember(currentSettings) { mutableStateOf(currentSettings?.notificationTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0) }
    var hourlyRate by remember(currentSettings) { mutableStateOf(currentSettings?.hourlyRate?.let { if (it > 0) it.toString() else "" } ?: "") }
    var gpsLat by remember(currentSettings) { mutableStateOf(currentSettings?.workLat ?: 0.0) }
    var gpsLng by remember(currentSettings) { mutableStateOf(currentSettings?.workLng ?: 0.0) }
    var detectionMode by remember(currentSettings) { mutableStateOf(currentSettings?.detectionMode ?: "both") }
    var roundingMinutes by remember(currentSettings) { mutableStateOf(currentSettings?.roundingMinutes ?: 0) }
    var isLocating by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var ssidError by remember { mutableStateOf("") }
    var dbMessage by remember { mutableStateOf("") }

    // Stav detekce
    var refreshKey by remember { mutableStateOf(0) }
    var statusSsid by remember { mutableStateOf("") }
    var statusDistance by remember { mutableStateOf<Float?>(null) }
    val debugEntries by DebugLogger.entries.collectAsState()

    LaunchedEffect(currentSettings?.workLat, currentSettings?.workLng, refreshKey) {
        statusSsid = getCurrentSsid(context)
        val settings = currentSettings ?: return@LaunchedEffect
        if (settings.workLat == 0.0 && settings.workLng == 0.0) { statusDistance = null; return@LaunchedEffect }
        try {
            val loc = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
            if (loc != null) {
                val result = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, settings.workLat, settings.workLng, result)
                statusDistance = result[0]
                DebugLogger.log("GPS", "poloha: ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)} | vzdálenost od práce: ${"%.0f".format(result[0])} m (radius ${settings.workRadius} m)")
            } else {
                DebugLogger.log("GPS", "lastLocation=null (GPS fix nedostupný)")
            }
        } catch (e: Exception) {
            DebugLogger.log("GPS", "chyba načítání polohy: ${e.message}")
        }
    }

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

    val exportDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dbFile = context.getDatabasePath("hodiny.db")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        dbFile.inputStream().use { it.copyTo(out) }
                    }
                    dbMessage = "Export dokončen"
                } catch (e: Exception) { dbMessage = "Chyba exportu: ${e.message}" }
            }
        }
    }

    val importDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dbFile = context.getDatabasePath("hodiny.db")
                    HodinyDatabase.closeAndReset()
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        dbFile.outputStream().use { inp.copyTo(it) }
                    }
                    // smaž WAL a SHM soubory aby se DB otevřela čistě
                    context.getDatabasePath("hodiny.db-wal").delete()
                    context.getDatabasePath("hodiny.db-shm").delete()
                } catch (e: Exception) {
                    dbMessage = "Chyba importu: ${e.message}"
                    return@withContext
                }
            }
            // Restart aplikace
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ssid, onValueChange = { ssid = it },
                label = { Text("SSID pracovní WiFi") },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    ssidError = ""
                    val detected = getCurrentSsid(context)
                    if (detected.isNotBlank()) {
                        ssid = detected
                    } else {
                        ssidError = "SSID nelze načíst – povolte přístup k poloze"
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Aktuální") }
        }
        if (ssidError.isNotBlank()) {
            Text(ssidError, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        SectionTitle("Režim detekce")
        listOf("both" to "GPS + WiFi", "gps" to "Pouze GPS", "wifi" to "Pouze WiFi").forEach { (mode, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = detectionMode == mode, onClick = { detectionMode = mode })
                Text(label, modifier = Modifier.padding(start = 4.dp))
            }
        }

        SectionTitle("Notifikace")
        TimePickerField(
            label = "Čas připomenutí",
            hour = notifHour,
            minute = notifMinute,
            onTimeSelected = { h, m -> notifHour = h; notifMinute = m },
            modifier = Modifier.fillMaxWidth()
        )

        SectionTitle("Zaokrouhlení času")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Žádné", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min").forEach { (value, label) ->
                FilterChip(
                    selected = roundingMinutes == value,
                    onClick = { roundingMinutes = value },
                    label = { Text(label) }
                )
            }
        }

        SectionTitle("Záloha databáze")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { exportDbLauncher.launch("hodiny_backup.db") },
                modifier = Modifier.weight(1f)
            ) { Text("Exportovat") }
            OutlinedButton(
                onClick = { importDbLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("Importovat") }
        }
        if (dbMessage.isNotBlank()) {
            Text(dbMessage, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(28.dp))

        Button(onClick = {
            scope.launch {
                val settings = AppSettings(
                    workLat = gpsLat, workLng = gpsLng,
                    workRadius = radius.toIntOrNull() ?: 150,
                    workSsid = ssid.trim(),
                    detectionMode = detectionMode,
                    notificationTime = "%02d:%02d".format(notifHour, notifMinute),
                    userName = userName.trim(),
                    hourlyRate = hourlyRate.toDoubleOrNull() ?: 0.0,
                    roundingMinutes = roundingMinutes,
                    isOnboarded = true
                )
                app.preferences.save(settings)
                GeofenceManager.stop(context)
                GeofenceManager.start(context, settings)
                DepartureNotificationWorker.schedule(context, "%02d:%02d".format(notifHour, notifMinute))
                context.startForegroundService(
                    android.content.Intent(context, cz.hodiny.service.MonitoringService::class.java)
                )
                saved = true
                refreshKey++
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Uložit nastavení") }

        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("Nastavení uloženo ✓", color = MaterialTheme.colorScheme.secondary)
        }

        // --- Stav detekce ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stav detekce", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { refreshKey++ }) { Text("Obnovit") }
        }

        val workSsid = currentSettings?.workSsid ?: ""
        val ssidMatch = statusSsid.isNotBlank() && workSsid.isNotBlank() && statusSsid == workSsid
        val ssidColor = if (ssidMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("WiFi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Aktuální SSID: ${statusSsid.ifBlank { "—" }}")
                Text("Pracovní SSID: ${workSsid.ifBlank { "—" }}")
                Text(
                    if (workSsid.isBlank()) "⚠ SSID není nastaveno"
                    else if (statusSsid.isBlank()) "⚠ Nelze načíst aktuální SSID"
                    else if (ssidMatch) "✓ Shoda – v pracovní síti"
                    else "✗ Neshoda – mimo pracovní síť",
                    color = ssidColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val workRadius = currentSettings?.workRadius ?: 150
        val workLat = currentSettings?.workLat ?: 0.0
        val workLng = currentSettings?.workLng ?: 0.0
        val isInZone = statusDistance?.let { it <= workRadius }
        val gpsColor = if (isInZone == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    workLat == 0.0 && workLng == 0.0 -> Text("⚠ GPS poloha pracoviště není nastavena")
                    statusDistance == null -> Text("Zjišťuji polohu...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        Text("Vzdálenost: ${"%.0f".format(statusDistance)} m (radius: $workRadius m)")
                        Text(
                            if (isInZone == true) "✓ V zóně" else "✗ Mimo zónu (${"%.0f".format(statusDistance!! - workRadius)} m navíc)",
                            color = gpsColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // --- Debug log ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Debug log", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val text = debugEntries.joinToString("\n")
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Sdílet log"))
                }) { Text("Sdílet") }
                OutlinedButton(onClick = { DebugLogger.clear() }) { Text("Smazat") }
            }
        }

        if (debugEntries.isEmpty()) {
            Text(
                "Žádné záznamy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(Modifier.padding(8.dp)) {
                    debugEntries.take(100).forEach { entry ->
                        Text(
                            entry,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@SuppressLint("MissingPermission")
private fun getCurrentSsid(context: Context): String {
    return try {
        @Suppress("DEPRECATION")
        val wm = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
        val raw = wm?.connectionInfo?.ssid?.removeSurrounding("\"") ?: ""
        if (raw == "<unknown ssid>" || raw.isBlank()) "" else raw
    } catch (_: Exception) { "" }
}
