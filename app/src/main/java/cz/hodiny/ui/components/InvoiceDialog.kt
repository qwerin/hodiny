package cz.hodiny.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.ExtraItem
import cz.hodiny.data.preferences.NanoFakturaConfig
import cz.hodiny.export.NanoFakturaClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Vystavení faktury za měsíc v NanoFaktuře
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDialog(
    year: Int,
    month: Int,
    totalMinutes: Long,
    hourlyRate: Double,
    items: List<ExtraItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<NanoFakturaConfig?>(null) }
    var accounts by remember { mutableStateOf(emptyList<NanoFakturaClient.Account>()) }
    var subjects by remember { mutableStateOf(emptyList<NanoFakturaClient.Subject>()) }
    var selectedSlug by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf(0L) }
    var accountsExpanded by remember { mutableStateOf(false) }
    var subjectsExpanded by remember { mutableStateOf(false) }

    val monthName = LocalDate.of(year, month, 1).month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("cs"))
    var lineName by remember { mutableStateOf("Práce za období $monthName $year") }
    var hours by remember { mutableStateOf("%.2f".format(Locale.US, totalMinutes / 60.0)) }
    var rate by remember { mutableStateOf(if (hourlyRate > 0) "%.2f".format(Locale.US, hourlyRate).removeSuffix(".00") else "") }
    var draft by remember { mutableStateOf(true) }
    // Položky navíc, které jdou na fakturu (výchozí všechny)
    var includedItems by remember { mutableStateOf(items.map { it.id }.toSet()) }

    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var created by remember { mutableStateOf<NanoFakturaClient.Invoice?>(null) }

    val client = config?.let { NanoFakturaClient(it.url, it.token) }

    // Načtení konfigurace a účtů
    LaunchedEffect(Unit) {
        val cfg = app.preferences.nanoFaktura.first()
        config = cfg
        draft = cfg.draft
        if (cfg.token.isBlank()) {
            error = "Nejdřív zadejte API token NanoFaktury v Nastavení."
            loading = false
            return@LaunchedEffect
        }
        try {
            accounts = NanoFakturaClient(cfg.url, cfg.token).accounts()
            selectedSlug = accounts.firstOrNull { it.slug == cfg.accountSlug }?.slug ?: accounts.firstOrNull()?.slug ?: ""
            if (accounts.isEmpty()) {
                error = "Uživatel tokenu nemá v NanoFaktuře žádný účet."
                loading = false
            }
        } catch (e: Exception) {
            error = e.message ?: "Nepodařilo se spojit s NanoFakturou"
            loading = false
        }
    }

    // Odběratelé vybraného účtu
    LaunchedEffect(selectedSlug) {
        val cfg = config ?: return@LaunchedEffect
        if (selectedSlug.isEmpty()) return@LaunchedEffect
        loading = true
        try {
            subjects = NanoFakturaClient(cfg.url, cfg.token).subjects(selectedSlug)
            selectedSubjectId = subjects.firstOrNull { it.id == cfg.subjectId }?.id ?: subjects.firstOrNull()?.id ?: 0
            error = if (subjects.isEmpty()) "V účtu nejsou žádní odběratelé – založte je v NanoFaktuře." else ""
        } catch (e: Exception) {
            error = e.message ?: "Nepodařilo se načíst odběratele"
        }
        loading = false
    }

    val result = created
    if (result != null && client != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (result.status == "draft") "Koncept vytvořen" else "Faktura vystavena") },
            text = {
                Text(buildString {
                    if (result.number.isNotBlank()) append("Číslo: ${result.number}\n")
                    append("Celkem: ${"%.2f".format(result.total / 100.0)} ${result.currency}")
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(client.invoiceWebUrl(selectedSlug, result.id))))
                    onDismiss()
                }) { Text("Otevřít v NanoFaktuře") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Zavřít") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Faktura – $monthName $year") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.size > 1) {
                    ExposedDropdownMenuBox(expanded = accountsExpanded, onExpandedChange = { accountsExpanded = it }) {
                        OutlinedTextField(
                            value = accounts.firstOrNull { it.slug == selectedSlug }?.name ?: "",
                            onValueChange = {}, readOnly = true, label = { Text("Účet (dodavatel)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountsExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = accountsExpanded, onDismissRequest = { accountsExpanded = false }) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedSlug = acc.slug; accountsExpanded = false })
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = subjectsExpanded, onExpandedChange = { subjectsExpanded = it && subjects.isNotEmpty() }) {
                    OutlinedTextField(
                        value = subjects.firstOrNull { it.id == selectedSubjectId }?.name ?: "",
                        onValueChange = {}, readOnly = true, label = { Text("Odběratel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectsExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = subjectsExpanded, onDismissRequest = { subjectsExpanded = false }) {
                        subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(if (s.registrationNo.isNotBlank()) "${s.name} (IČO ${s.registrationNo})" else s.name) },
                                onClick = { selectedSubjectId = s.id; subjectsExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(value = lineName, onValueChange = { lineName = it }, label = { Text("Text položky") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Hodin") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Kč/hod") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                if (items.isNotEmpty()) {
                    Text("Položky navíc", style = MaterialTheme.typography.labelLarge)
                    items.forEach { extra ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = extra.id in includedItems,
                                onCheckedChange = { includedItems = if (it) includedItems + extra.id else includedItems - extra.id }
                            )
                            Text(extra.description, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${"%.0f".format(extra.amount)} Kč", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                val itemsTotal = items.filter { it.id in includedItems }.sumOf { it.amount }
                val total = (hours.replace(',', '.').toDoubleOrNull() ?: 0.0) * (rate.replace(',', '.').toDoubleOrNull() ?: 0.0) + itemsTotal
                Text("Celkem bez DPH: ${"%.2f".format(total)} Kč", style = MaterialTheme.typography.bodyMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft, onCheckedChange = { draft = it })
                    Text("Uložit jen jako koncept")
                }

                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotEmpty()) Text(error, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && !sending && client != null && selectedSubjectId > 0,
                onClick = {
                    // Prázdné / nulové hodiny = faktura jen z položek navíc
                    val h = hours.replace(',', '.').trim().ifEmpty { "0" }.toBigDecimalOrNull()
                    val r = rate.replace(',', '.').trim().ifEmpty { "0" }.toBigDecimalOrNull()
                    val withHours = h != null && h.signum() > 0
                    when {
                        h == null || h.signum() < 0 -> { error = "Neplatný počet hodin"; return@TextButton }
                        withHours && lineName.isBlank() -> { error = "Vyplňte text položky"; return@TextButton }
                        withHours && (r == null || r.signum() <= 0) -> { error = "Neplatná hodinová sazba"; return@TextButton }
                    }
                    val lines = buildList {
                        if (withHours) add(NanoFakturaClient.Line(
                            name = lineName.trim(),
                            quantity = h!!.setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
                            unitName = "hod",
                            unitPriceMinor = r!!.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
                        ))
                        items.filter { it.id in includedItems }.forEach { extra ->
                            val day = extra.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            add(NanoFakturaClient.Line(
                                name = if (day != null) "${extra.description} (${day.dayOfMonth}. ${day.monthValue}. ${day.year})" else extra.description,
                                quantity = "1",
                                unitName = "",
                                unitPriceMinor = Math.round(extra.amount * 100)
                            ))
                        }
                    }
                    if (lines.isEmpty()) { error = "Faktura nemá žádnou položku"; return@TextButton }
                    val cfg = config ?: return@TextButton
                    sending = true
                    error = ""
                    scope.launch {
                        try {
                            val invoice = client!!.createInvoice(
                                slug = selectedSlug,
                                subjectId = selectedSubjectId,
                                lines = lines,
                                draft = draft
                            )
                            created = invoice
                        } catch (e: Exception) {
                            error = e.message ?: "Vystavení selhalo"
                            sending = false
                            return@launch
                        }
                        // Faktura už existuje – selhání uložení voleb nesmí vést k opakovanému vystavení
                        runCatching {
                            app.preferences.saveNanoFaktura(cfg.copy(accountSlug = selectedSlug, subjectId = selectedSubjectId, draft = draft))
                        }
                        sending = false
                    }
                }
            ) { Text(if (sending) "Odesílám…" else if (draft) "Vytvořit koncept" else "Vystavit") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Zrušit") } }
    )
}
