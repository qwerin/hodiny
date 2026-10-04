package cz.hodiny.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.ExtraItem
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Přidání/úprava položky navíc (např. „Nákup těsnění“ 5000 Kč) k měsíci nebo dni
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraItemDialog(year: Int, month: Int, item: ExtraItem?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()

    var description by remember { mutableStateOf(item?.description ?: "") }
    var amount by remember { mutableStateOf(item?.amount?.let { "%.2f".format(Locale.US, it).removeSuffix(".00") } ?: "") }
    // null = celý měsíc
    var day by remember { mutableStateOf(item?.date?.let { runCatching { LocalDate.parse(it).dayOfMonth }.getOrNull() }) }
    var dayExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
    fun dayLabel(d: Int?): String {
        if (d == null) return "Celý měsíc"
        val date = LocalDate.of(year, month, d)
        val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("cs")).replaceFirstChar { it.uppercase() }
        return "$dow $d.$month."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Přidat položku" else "Upravit položku") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Popis (např. nákup těsnění)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Částka (Kč)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = it }) {
                    OutlinedTextField(
                        value = dayLabel(day), onValueChange = {}, readOnly = true,
                        label = { Text("Ke dni") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                        (listOf<Int?>(null) + (1..daysInMonth)).forEach { d ->
                            DropdownMenuItem(text = { Text(dayLabel(d)) }, onClick = { day = d; dayExpanded = false })
                        }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.replace(',', '.').replace(" ", "").toDoubleOrNull()
                when {
                    description.isBlank() -> { error = "Vyplňte popis"; return@TextButton }
                    value == null || value == 0.0 -> { error = "Neplatná částka"; return@TextButton }
                }
                val monthKey = "%04d-%02d".format(year, month)
                val saved = (item ?: ExtraItem(month = monthKey, description = "", amount = 0.0)).copy(
                    month = monthKey,
                    date = day?.let { LocalDate.of(year, month, it).toString() },
                    description = description.trim(),
                    amount = value!!
                )
                scope.launch {
                    app.repository.saveExtraItem(saved)
                    onDismiss()
                }
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )
}
