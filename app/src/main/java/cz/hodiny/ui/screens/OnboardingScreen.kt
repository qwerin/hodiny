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
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import cz.hodiny.HodinyApp
import cz.hodiny.data.preferences.AppSettings
import cz.hodiny.ui.components.SectionTitle
import cz.hodiny.service.GeofenceManager
import cz.hodiny.worker.DepartureNotificationWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()

    var userName by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("150") }
    var notifTime by remember { mutableStateOf("18:00") }
    var gpsLat by remember { mutableStateOf(0.0) }
    var gpsLng by remember { mutableStateOf(0.0) }
    var gpsSet by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            isLocating = true
            scope.launch {
                try {
                    val client = LocationServices.getFusedLocationProviderClient(context)
                    val loc = client.lastLocation.await()
                    if (loc != null) {
                        gpsLat = loc.latitude; gpsLng = loc.longitude; gpsSet = true
                    } else { error = "Nepodařilo se zjistit polohu. Zkuste to znovu." }
                } catch (e: Exception) {
                    error = "Chyba GPS: ${e.message}"
                } finally { isLocating = false }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Nastavení pracoviště", style = MaterialTheme.typography.headlineMedium)
        Text("Jednorázové nastavení pro automatické sledování docházky",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

        SectionTitle("Profil")
        OutlinedTextField(value = userName, onValueChange = { userName = it },
            label = { Text("Vaše jméno (pro export)") }, modifier = Modifier.fillMaxWidth())

        SectionTitle("GPS detekce")
        Button(
            onClick = {
                locationLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLocating) "Zjišťuji polohu..." else if (gpsSet) "GPS nastavena ✓ (${"%.4f".format(gpsLat)}, ${"%.4f".format(gpsLng)})" else "Nastavit GPS polohu pracoviště")
        }
        OutlinedTextField(value = radius, onValueChange = { radius = it },
            label = { Text("Radius zóny (metry)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        SectionTitle("WiFi detekce")
        OutlinedTextField(value = ssid, onValueChange = { ssid = it },
            label = { Text("SSID pracovní WiFi") }, modifier = Modifier.fillMaxWidth())

        SectionTitle("Notifikace")
        OutlinedTextField(value = notifTime, onValueChange = { notifTime = it },
            label = { Text("Čas večerního připomenutí (HH:MM)") },
            modifier = Modifier.fillMaxWidth())

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (!gpsSet && ssid.isBlank()) {
                    error = "Zadejte alespoň GPS nebo WiFi SSID."; return@Button
                }
                scope.launch {
                    val settings = AppSettings(
                        workLat = gpsLat, workLng = gpsLng,
                        workRadius = radius.toIntOrNull() ?: 150,
                        workSsid = ssid.trim(),
                        detectionMode = if (gpsSet && ssid.isNotBlank()) "both" else if (gpsSet) "gps" else "wifi",
                        notificationTime = notifTime,
                        userName = userName.trim(),
                        isOnboarded = true
                    )
                    app.preferences.save(settings)
                    GeofenceManager.start(context, settings)
                    DepartureNotificationWorker.schedule(context, notifTime)
                    context.startForegroundService(
                        android.content.Intent(context, cz.hodiny.service.MonitoringService::class.java)
                    )
                    onFinished()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Uložit a spustit") }
    }
}

