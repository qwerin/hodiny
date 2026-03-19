package cz.hodiny.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import cz.hodiny.HodinyApp
import cz.hodiny.data.db.AttendanceRecord
import cz.hodiny.data.preferences.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object ExportHelper {

    suspend fun exportPDF(context: Context, year: Int, month: Int, settings: AppSettings) {
        val app = context.applicationContext as HodinyApp
        val records = app.repository.findByMonth(year, month)
        if (records.isEmpty()) throw Exception("Za vybraný měsíc nejsou žádné záznamy.")

        val monthLabel = monthLabel(year, month)
        val totalMinutes = records.sumOf { durationMinutes(it) }
        val totalHours = totalMinutes / 60.0

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        drawPDF(canvas, records, monthLabel, settings, totalHours)
        doc.finishPage(page)

        val file = File(context.cacheDir, "dochazka_${year}_${"%02d".format(month)}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        shareFile(context, file, "application/pdf")
    }

    suspend fun exportCSV(context: Context, year: Int, month: Int, settings: AppSettings) {
        val app = context.applicationContext as HodinyApp
        val records = app.repository.findByMonth(year, month)
        if (records.isEmpty()) throw Exception("Za vybraný měsíc nejsou žádné záznamy.")

        val sb = StringBuilder()
        sb.appendLine("Datum;Příchod;Odchod;Délka (min);Délka;Poznámka")
        records.forEach { r ->
            val dur = durationMinutes(r)
            sb.appendLine("${r.date};${formatTime(r.arrivalTime)};${formatTime(r.departureTime)};$dur;${formatMinutes(dur)};${r.note ?: ""}")
        }
        val total = records.sumOf { durationMinutes(it) }
        sb.appendLine()
        sb.appendLine("Celkem hodin;${"%.2f".format(total / 60.0)}")
        if (settings.hourlyRate > 0) {
            sb.appendLine("Sazba;${settings.hourlyRate} Kč/hod")
            sb.appendLine("K fakturaci;${"%.2f".format(total / 60.0 * settings.hourlyRate)} Kč")
        }

        val file = File(context.cacheDir, "dochazka_${year}_${"%02d".format(month)}.csv")
        file.writeText("\uFEFF" + sb.toString(), Charsets.UTF_8)
        shareFile(context, file, "text/csv")
    }

    private fun drawPDF(canvas: Canvas, records: List<AttendanceRecord>, monthLabel: String, settings: AppSettings, totalHours: Double) {
        val titlePaint = Paint().apply { color = Color.rgb(26, 26, 46); textSize = 22f; isFakeBoldText = true }
        val subtitlePaint = Paint().apply { color = Color.GRAY; textSize = 13f }
        val headerPaint = Paint().apply { color = Color.WHITE; textSize = 12f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { color = Color.rgb(50, 50, 50); textSize = 11f }
        val bgPaint = Paint().apply { color = Color.rgb(26, 26, 46) }
        val altPaint = Paint().apply { color = Color.rgb(249, 249, 249) }
        val summaryPaint = Paint().apply { color = Color.rgb(227, 242, 253) }

        var y = 50f
        canvas.drawText("Přehled docházky – $monthLabel", 40f, y, titlePaint)
        y += 20f
        canvas.drawText(settings.userName.ifBlank { "Jméno nezadáno" }, 40f, y, subtitlePaint)
        y += 30f

        // Hlavička tabulky
        canvas.drawRect(40f, y, 555f, y + 24f, bgPaint)
        canvas.drawText("Datum", 45f, y + 16f, headerPaint)
        canvas.drawText("Příchod", 145f, y + 16f, headerPaint)
        canvas.drawText("Odchod", 225f, y + 16f, headerPaint)
        canvas.drawText("Délka", 310f, y + 16f, headerPaint)
        canvas.drawText("Poznámka", 380f, y + 16f, headerPaint)
        y += 24f

        records.forEachIndexed { i, r ->
            if (i % 2 == 1) canvas.drawRect(40f, y, 555f, y + 20f, altPaint)
            val dur = durationMinutes(r)
            canvas.drawText(formatDateShort(r.date), 45f, y + 14f, bodyPaint)
            canvas.drawText(formatTime(r.arrivalTime), 145f, y + 14f, bodyPaint)
            canvas.drawText(formatTime(r.departureTime), 225f, y + 14f, bodyPaint)
            canvas.drawText(formatMinutes(dur), 310f, y + 14f, bodyPaint)
            canvas.drawText(r.note ?: "", 380f, y + 14f, bodyPaint)
            y += 20f
        }

        y += 16f
        canvas.drawRect(40f, y, 555f, y + (if (settings.hourlyRate > 0) 70f else 50f), summaryPaint)
        y += 18f
        canvas.drawText("Počet dní: ${records.size}", 50f, y, bodyPaint)
        y += 16f
        canvas.drawText("Celkem hodin: ${"%.2f".format(totalHours)} h", 50f, y, bodyPaint)
        if (settings.hourlyRate > 0) {
            y += 16f
            canvas.drawText("Sazba: ${settings.hourlyRate} Kč/hod", 50f, y, bodyPaint)
            y += 16f
            val amount = "%.2f".format(totalHours * settings.hourlyRate)
            canvas.drawText("K fakturaci: $amount Kč", 50f, y, Paint().apply { color = Color.rgb(21, 101, 192); textSize = 13f; isFakeBoldText = true })
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Sdílet").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
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

    private fun formatMinutes(m: Long): String {
        if (m == 0L) return "–"
        val h = m / 60; val min = m % 60
        return if (h == 0L) "${min} min" else if (min == 0L) "${h} h" else "${h} h ${min} min"
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
}
