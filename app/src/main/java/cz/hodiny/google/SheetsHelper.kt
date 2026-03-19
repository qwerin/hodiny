package cz.hodiny.google

import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AutoResizeDimensionsRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.CellData
import com.google.api.services.sheets.v4.model.CellFormat
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.GridRange
import com.google.api.services.sheets.v4.model.RepeatCellRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.TextFormat
import com.google.api.services.sheets.v4.model.ValueRange
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.AttendanceRecord
import cz.hodiny.data.preferences.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.google.api.services.sheets.v4.model.Color as SheetColor

object SheetsHelper {

    suspend fun exportToSheets(context: Context, year: Int, month: Int, settings: AppSettings): String {
        val credential = GoogleManager.getCredential(context)
            ?: throw Exception("Nejste přihlášeni ke Google účtu")
        val app = context.applicationContext as HodinyApp
        val records = app.repository.findByMonth(year, month)
        if (records.isEmpty()) throw Exception("Za vybraný měsíc nejsou žádné záznamy.")

        val monthName = LocalDate.of(year, month, 1).month
            .getDisplayName(TextStyle.FULL_STANDALONE, Locale("cs"))
            .replaceFirstChar { it.uppercase() }

        return withContext(Dispatchers.IO) {
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val service = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("Hodiny")
                .build()

            // Vytvoř spreadsheet
            val spreadsheet = Spreadsheet().apply {
                properties = SpreadsheetProperties().apply { title = "Hodiny – $monthName $year" }
            }
            val created = service.spreadsheets().create(spreadsheet).execute()
            val sheetId = created.spreadsheetId

            // Sestavení dat
            val rows = mutableListOf<List<Any>>()
            rows.add(listOf("Datum", "Den", "Příchod", "Odchod", "Délka (h)", "Poznámka"))
            var totalMinutes = 0L
            records.forEach { r ->
                val dur = durationMinutes(r)
                totalMinutes += dur
                rows.add(listOf(
                    r.date,
                    dayOfWeek(r.date),
                    formatTime(r.arrivalTime),
                    formatTime(r.departureTime),
                    "%.2f".format(dur / 60.0),
                    r.note ?: ""
                ))
            }
            rows.add(listOf())
            rows.add(listOf("Celkem dní", records.size))
            rows.add(listOf("Celkem hodin", "%.2f".format(totalMinutes / 60.0)))
            if (settings.hourlyRate > 0) {
                rows.add(listOf("Sazba (Kč/h)", settings.hourlyRate))
                rows.add(listOf("K fakturaci (Kč)", "%.2f".format(totalMinutes / 60.0 * settings.hourlyRate)))
            }

            // Zápis dat
            service.spreadsheets().values()
                .update(sheetId, "A1", ValueRange().apply { setValues(rows) })
                .setValueInputOption("RAW")
                .execute()

            // Formátování hlavičky
            val darkBlue = SheetColor().apply { red = 0.1f; green = 0.1f; blue = 0.18f }
            val white = SheetColor().apply { red = 1f; green = 1f; blue = 1f }
            val requests = listOf(
                Request().apply {
                    repeatCell = RepeatCellRequest().apply {
                        range = GridRange().apply { sheetId = 0; startRowIndex = 0; endRowIndex = 1 }
                        cell = CellData().apply {
                            userEnteredFormat = CellFormat().apply {
                                backgroundColor = darkBlue
                                textFormat = TextFormat().apply { bold = true; foregroundColor = white }
                            }
                        }
                        fields = "userEnteredFormat(backgroundColor,textFormat)"
                    }
                },
                Request().apply {
                    autoResizeDimensions = AutoResizeDimensionsRequest().apply {
                        dimensions = DimensionRange().apply {
                            sheetId = 0; dimension = "COLUMNS"; startIndex = 0; endIndex = 6
                        }
                    }
                }
            )
            service.spreadsheets().batchUpdate(
                sheetId,
                BatchUpdateSpreadsheetRequest().apply { this.requests = requests }
            ).execute()

            "https://docs.google.com/spreadsheets/d/$sheetId"
        }
    }

    private fun durationMinutes(r: AttendanceRecord): Long {
        val a = r.arrivalTime ?: return 0
        val d = r.departureTime ?: return 0
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

    private fun dayOfWeek(date: String): String = try {
        LocalDate.parse(date).dayOfWeek
            .getDisplayName(TextStyle.SHORT, Locale("cs"))
            .replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { "" }
}
