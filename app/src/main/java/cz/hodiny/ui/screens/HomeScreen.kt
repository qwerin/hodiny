package cz.hodiny.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.AttendanceRecord
import cz.hodiny.ui.components.EditEntryDialog
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val record by app.repository.observeToday().collectAsState(initial = null)
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var showEdit by remember { mutableStateOf(false) }

    // Živý timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // Datum
        Text(
            text = LocalDate.now().let {
                val day = it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("cs"))
                val date = it.format(DateTimeFormatter.ofPattern("d. M. yyyy"))
                "${day.replaceFirstChar { c -> c.uppercase() }}, $date"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // Hlavní karta
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val inZone = record?.arrivalTime != null && record?.isLocked == false
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (inZone) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (inZone) "Pracovní den probíhá" else "Mimo pracoviště",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()

                AttendanceRow("Příchod", formatTime(record?.arrivalTime))
                HorizontalDivider()
                AttendanceRow("Odpracováno", computeDuration(record, now))
                HorizontalDivider()
                AttendanceRow("Poslední odchod", formatTime(record?.departureTime))

                if (record?.isLocked == true) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Uzamčeno ✓",
                            modifier = Modifier.padding(8.dp),
                            color = Color(0xFF388E3C),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        record?.note?.let { note ->
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
            ) {
                Text(note, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showEdit = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text("Upravit záznam")
        }
    }

    if (showEdit && record != null) {
        EditEntryDialog(
            record = record!!,
            onDismiss = { showEdit = false },
            onSave = { showEdit = false }
        )
    }
}

@Composable
private fun AttendanceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF666666))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTime(iso: String?): String {
    if (iso == null) return "--:--"
    return try {
        val dt = LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        "%02d:%02d".format(dt.hour, dt.minute)
    } catch (_: Exception) { "--:--" }
}

private fun computeDuration(record: AttendanceRecord?, now: LocalDateTime): String {
    val arrival = record?.arrivalTime ?: return "--"
    return try {
        val start = LocalDateTime.parse(arrival, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val end = record.departureTime?.let {
            LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } ?: now
        val minutes = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        if (h == 0L) "${m} min" else if (m == 0L) "${h} h" else "${h} h ${m} min"
    } catch (_: Exception) { "--" }
}
