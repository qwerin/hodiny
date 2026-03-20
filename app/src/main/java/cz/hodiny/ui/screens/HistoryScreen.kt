package cz.hodiny.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.AttendanceRecord
import cz.hodiny.ui.components.AddRecordDialog
import cz.hodiny.ui.components.EditEntryDialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Generujeme posledních 24 měsíců (index 0 = nejaktuálnější)
private fun generateMonths(count: Int = 24): List<Pair<Int, Int>> {
    val now = LocalDate.now()
    return (0 until count).map {
        val d = now.minusMonths(it.toLong())
        Pair(d.year, d.monthValue)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val allRecords by app.repository.observeAll().collectAsState(initial = emptyList())
    val settings by app.preferences.settings.collectAsState(initial = null)
    val hourlyRate = settings?.hourlyRate ?: 0.0
    var editRecord by remember { mutableStateOf<AttendanceRecord?>(null) }
    var deleteRecord by remember { mutableStateOf<AttendanceRecord?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val months = remember { generateMonths() }
    val pagerState = rememberPagerState(initialPage = 0) { months.size }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Hlavička s názvem měsíce a šipkami
            val (year, month) = months[pagerState.currentPage]
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = pagerState.currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Novější měsíc")
                }
                Text(
                    text = monthLabel(year, month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    enabled = pagerState.currentPage < months.size - 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Starší měsíc")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val (y, m) = months[page]
                val prefix = "%04d-%02d".format(y, m)
                val monthRecords = allRecords.filter { it.date.startsWith(prefix) }
                val totalMinutes = monthRecords.sumOf { durationMinutes(it) }
                val totalAmount = if (hourlyRate > 0) totalMinutes / 60.0 * hourlyRate else 0.0

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Součet měsíce
                    item {
                        Surface(color = Color(0xFFE3F2FD), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${monthRecords.size} dní", color = Color(0xFF1565C0))
                                if (hourlyRate > 0) {
                                    Text(
                                        "${"%.0f".format(totalAmount)} Kč",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0)
                                    )
                                }
                                Text(formatMinutes(totalMinutes), fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                            }
                        }
                    }

                    if (monthRecords.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                                Text("Žádné záznamy", color = Color(0xFF999999))
                            }
                        }
                    }

                    items(monthRecords.sortedByDescending { it.date }, key = { it.id }) { record ->
                        val dayMinutes = durationMinutes(record)
                        val dayAmount = if (hourlyRate > 0 && dayMinutes > 0) dayMinutes / 60.0 * hourlyRate else 0.0
                        ListItem(
                            modifier = Modifier.clickable { editRecord = record },
                            headlineContent = { Text(formatDateShort(record.date)) },
                            supportingContent = {
                                if (record.entrySource == "manual") Text("ručně", color = Color(0xFFFF9800))
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${formatTime(record.arrivalTime)} – ${formatTime(record.departureTime)}")
                                        Text(
                                            formatMinutes(dayMinutes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF666666)
                                        )
                                        if (dayAmount > 0) {
                                            Text(
                                                "${"%.0f".format(dayAmount)} Kč",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF388E3C)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { deleteRecord = record }) {
                                        Icon(Icons.Default.Delete, null, tint = Color(0xFFBDBDBD))
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        // FAB pro přidání záznamu
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(padding).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Přidat záznam")
        }
    }

    editRecord?.let { rec ->
        EditEntryDialog(record = rec, onDismiss = { editRecord = null }, onSave = { editRecord = null })
    }

    deleteRecord?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteRecord = null },
            title = { Text("Smazat záznam") },
            text = { Text("Opravdu smazat záznam za ${formatDateShort(rec.date)}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.repository.delete(rec.id) }
                    deleteRecord = null
                }) { Text("Smazat", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteRecord = null }) { Text("Zrušit") } }
        )
    }

    if (showAddDialog) {
        AddRecordDialog(onDismiss = { showAddDialog = false }, onSave = { showAddDialog = false })
    }
}

private fun durationMinutes(record: AttendanceRecord): Long {
    val a = record.arrivalTime ?: return 0
    val d = record.departureTime ?: return 0
    return try {
        val start = LocalDateTime.parse(a, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val end = LocalDateTime.parse(d, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0)
    } catch (_: Exception) { 0 }
}

private fun formatTime(iso: String?): String {
    if (iso == null) return "--:--"
    return try {
        val dt = LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        "%02d:%02d".format(dt.hour, dt.minute)
    } catch (_: Exception) { "--:--" }
}

private fun formatMinutes(minutes: Long): String {
    if (minutes == 0L) return "–"
    val h = minutes / 60; val m = minutes % 60
    return if (h == 0L) "${m} min" else if (m == 0L) "${h} h" else "${h} h ${m} min"
}

private fun formatDateShort(date: String): String = try {
    val d = LocalDate.parse(date)
    val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("cs"))
    "${dow.replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}.${d.monthValue}."
} catch (_: Exception) { date }

private fun monthLabel(year: Int, month: Int): String {
    val d = LocalDate.of(year, month, 1)
    val name = d.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("cs"))
    return "${name.replaceFirstChar { it.uppercase() }} $year"
}
