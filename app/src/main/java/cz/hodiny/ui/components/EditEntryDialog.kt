package cz.hodiny.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.AttendanceRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun EditEntryDialog(record: AttendanceRecord, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()

    var arrivalHour by remember { mutableStateOf(parseHour(record.arrivalTime)) }
    var arrivalMinute by remember { mutableStateOf(parseMinute(record.arrivalTime)) }
    var departureHour by remember { mutableStateOf(parseHour(record.departureTime)) }
    var departureMinute by remember { mutableStateOf(parseMinute(record.departureTime)) }
    var note by remember { mutableStateOf(record.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upravit záznam – ${record.date}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePickerField(
                    label = "Příchod",
                    hour = arrivalHour, minute = arrivalMinute,
                    onTimeSelected = { h, m -> arrivalHour = h; arrivalMinute = m },
                    modifier = Modifier.fillMaxWidth()
                )
                TimePickerField(
                    label = "Odchod",
                    hour = departureHour, minute = departureMinute,
                    onTimeSelected = { h, m -> departureHour = h; departureMinute = m },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Poznámka") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            var error by remember { mutableStateOf("") }
            Column {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = androidx.compose.ui.Modifier.padding(horizontal = 24.dp))
                TextButton(onClick = {
                    val arrMins = (arrivalHour ?: 0) * 60 + (arrivalMinute ?: 0)
                    val depMins = (departureHour ?: 0) * 60 + (departureMinute ?: 0)
                    if (arrivalHour != null && departureHour != null && depMins <= arrMins) {
                        error = "Odchod musí být po příchodu"; return@TextButton
                    }
                    error = ""
                    scope.launch {
                        app.repository.updateRecord(
                            id = record.id,
                            arrivalTime = toISO(record.date, arrivalHour, arrivalMinute),
                            departureTime = toISO(record.date, departureHour, departureMinute),
                            note = note.ifBlank { null }
                        )
                        onSave()
                    }
                }) { Text("Uložit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )
}

private fun parseHour(iso: String?): Int? = iso?.let {
    runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME).hour }.getOrNull()
}

private fun parseMinute(iso: String?): Int? = iso?.let {
    runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME).minute }.getOrNull()
}

private fun toISO(date: String, hour: Int?, minute: Int?): String? {
    if (hour == null || minute == null) return null
    return LocalDateTime.of(LocalDate.parse(date), LocalTime.of(hour, minute))
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
