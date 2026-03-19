package cz.hodiny.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var arrivalHour by remember { mutableStateOf<Int?>(8) }
    var arrivalMinute by remember { mutableStateOf<Int?>(0) }
    var departureHour by remember { mutableStateOf<Int?>(17) }
    var departureMinute by remember { mutableStateOf<Int?>(0) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toEpochDay() * 86_400_000L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Přidat záznam") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Výběr data
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Datum: ${selectedDate}")
                }

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
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val arrMins = (arrivalHour ?: 0) * 60 + (arrivalMinute ?: 0)
                val depMins = (departureHour ?: 0) * 60 + (departureMinute ?: 0)
                if (arrivalHour != null && departureHour != null && depMins <= arrMins) {
                    error = "Odchod musí být po příchodu"; return@TextButton
                }
                error = ""
                scope.launch {
                    val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val record = AttendanceRecord(
                        date = selectedDate.toString(),
                        arrivalTime = toISO(selectedDate, arrivalHour, arrivalMinute),
                        departureTime = toISO(selectedDate, departureHour, departureMinute),
                        isLocked = true,
                        entrySource = "manual",
                        note = note.ifBlank { null },
                        createdAt = now,
                        updatedAt = now
                    )
                    app.repository.updateRecordByRecord(record)
                    onSave()
                }
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )

    // DatePicker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = LocalDate.ofEpochDay(it / 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Zrušit") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun toISO(date: LocalDate, hour: Int?, minute: Int?): String? {
    if (hour == null || minute == null) return null
    return LocalDateTime.of(date, LocalTime.of(hour, minute))
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
