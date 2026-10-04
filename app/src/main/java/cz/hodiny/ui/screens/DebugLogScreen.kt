package cz.hodiny.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cz.hodiny.service.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Debug log na samostatné obrazovce (v Nastavení by byl nepřehledný)
@Composable
fun DebugLogScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<String>?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(refreshKey) {
        entries = withContext(Dispatchers.IO) { DebugLogger.readLines() }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") }
            Text("Debug log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { shareLog(context) }) { Icon(Icons.Default.Share, "Sdílet log") }
            IconButton(onClick = { DebugLogger.clear(); refreshKey++ }) { Icon(Icons.Default.Delete, "Smazat log") }
        }

        OutlinedTextField(
            value = filter, onValueChange = { filter = it },
            label = { Text("Filtr (např. Geofence, WifiReceiver)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        val all = entries
        val shown = all?.filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }
        when {
            all == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            shown.isNullOrEmpty() -> Text(
                if (all.isEmpty()) "Žádné záznamy" else "Filtru nic neodpovídá",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            else -> {
                Text(
                    "${shown.size} z ${all.size} záznamů, nejnovější nahoře",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    itemsIndexed(shown) { _, entry ->
                        Text(
                            entry,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

fun shareLog(context: Context) {
    val uri = DebugLogger.getShareUri(context) ?: return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Sdílet log"))
}
