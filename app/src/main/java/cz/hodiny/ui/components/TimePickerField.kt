package cz.hodiny.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    label: String,
    hour: Int?,
    minute: Int?,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayValue = if (hour != null && minute != null) "%02d:%02d".format(hour, minute) else "--:--"

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier
    ) {
        Icon(Icons.Default.AccessTime, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("$label: $displayValue")
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = hour ?: 8,
            initialMinute = minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(state.hour, state.minute)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Zrušit") }
            }
        )
    }
}
