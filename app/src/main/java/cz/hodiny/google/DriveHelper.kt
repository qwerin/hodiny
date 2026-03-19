package cz.hodiny.google

import android.content.Context
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.AttendanceRecord
import cz.hodiny.data.preferences.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DriveHelper {

    suspend fun backupCSV(context: Context, year: Int, month: Int, settings: AppSettings): String {
        val credential = GoogleManager.getCredential(context)
            ?: throw Exception("Nejste přihlášeni ke Google účtu")
        val app = context.applicationContext as HodinyApp
        val records = app.repository.findByMonth(year, month)
        if (records.isEmpty()) throw Exception("Za vybraný měsíc nejsou žádné záznamy.")

        val monthName = LocalDate.of(year, month, 1).month
            .getDisplayName(TextStyle.FULL_STANDALONE, Locale("cs"))
            .replaceFirstChar { it.uppercase() }

        val csvFile = buildCsvFile(context, year, month, records, settings)

        return withContext(Dispatchers.IO) {
            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val service = Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("Hodiny")
                .build()

            val metadata = DriveFile().apply {
                name = "Hodiny_${monthName}_${year}.csv"
                mimeType = "text/csv"
            }
            val mediaContent = FileContent("text/csv", csvFile)
            val uploaded = service.files().create(metadata, mediaContent)
                .setFields("id, webViewLink")
                .execute()

            uploaded.webViewLink ?: "https://drive.google.com/drive/my-drive"
        }
    }

    private fun buildCsvFile(
        context: Context, year: Int, month: Int,
        records: List<AttendanceRecord>, settings: AppSettings
    ): File {
        val sb = StringBuilder()
        sb.appendLine("Datum;Příchod;Odchod;Délka (min);Délka;Poznámka")
        var totalMinutes = 0L
        records.forEach { r ->
            val dur = durationMinutes(r)
            totalMinutes += dur
            sb.appendLine("${r.date};${formatTime(r.arrivalTime)};${formatTime(r.departureTime)};$dur;${formatMins(dur)};${r.note ?: ""}")
        }
        sb.appendLine()
        sb.appendLine("Celkem hodin;${"%.2f".format(totalMinutes / 60.0)}")
        if (settings.hourlyRate > 0) {
            sb.appendLine("Sazba;${settings.hourlyRate} Kč/hod")
            sb.appendLine("K fakturaci;${"%.2f".format(totalMinutes / 60.0 * settings.hourlyRate)} Kč")
        }
        val file = File(context.cacheDir, "dochazka_${year}_${"%02d".format(month)}.csv")
        file.writeText("\uFEFF" + sb.toString(), Charsets.UTF_8)
        return file
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

    private fun formatMins(m: Long): String {
        if (m == 0L) return "–"
        val h = m / 60; val min = m % 60
        return if (h == 0L) "${min} min" else if (min == 0L) "${h} h" else "${h} h ${min} min"
    }
}
