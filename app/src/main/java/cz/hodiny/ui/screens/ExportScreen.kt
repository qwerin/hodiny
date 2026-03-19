package cz.hodiny.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import cz.hodiny.export.ExportHelper
import cz.hodiny.google.DriveHelper
import cz.hodiny.google.GoogleManager
import cz.hodiny.google.SheetsHelper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ExportScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val scope = rememberCoroutineScope()
    val settings by app.preferences.settings.collectAsState(initial = null)
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val isGoogleSignedIn = remember { GoogleManager.isSignedIn(context) }

    val now = LocalDate.now()
    val months = (0..5).map {
        val d = now.minusMonths(it.toLong())
        Pair(d.year, d.monthValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        Text("Export pro fakturaci", style = MaterialTheme.typography.headlineSmall)
        Text("Vyberte měsíc a formát", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

        if (!isGoogleSignedIn) {
            Text("Pro export do Sheets se přihlaste v Nastavení.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(months) { (year, month) ->
                val label = LocalDate.of(year, month, 1).month
                    .getDisplayName(TextStyle.FULL_STANDALONE, Locale("cs"))
                    .replaceFirstChar { it.uppercase() } + " $year"

                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        loading = true; error = ""
                                        try { ExportHelper.exportPDF(context, year, month, settings ?: return@launch) }
                                        catch (e: Exception) { error = e.message ?: "Chyba exportu" }
                                        finally { loading = false }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("PDF", fontSize = 13.sp) }

                            Button(
                                onClick = {
                                    scope.launch {
                                        loading = true; error = ""
                                        try { ExportHelper.exportCSV(context, year, month, settings ?: return@launch) }
                                        catch (e: Exception) { error = e.message ?: "Chyba exportu" }
                                        finally { loading = false }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("CSV", fontSize = 13.sp) }

                            Button(
                                onClick = {
                                    scope.launch {
                                        loading = true; error = ""
                                        try {
                                            val url = SheetsHelper.exportToSheets(context, year, month, settings ?: return@launch)
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (e: Exception) { error = e.message ?: "Chyba exportu" }
                                        finally { loading = false }
                                    }
                                },
                                enabled = isGoogleSignedIn,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("Sheets", fontSize = 13.sp) }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        loading = true; error = ""
                                        try {
                                            val url = DriveHelper.backupCSV(context, year, month, settings ?: return@launch)
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (e: Exception) { error = e.message ?: "Chyba zálohy" }
                                        finally { loading = false }
                                    }
                                },
                                enabled = isGoogleSignedIn,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("Drive", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}
