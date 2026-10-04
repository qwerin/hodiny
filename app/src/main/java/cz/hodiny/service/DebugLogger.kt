package cz.hodiny.service

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DebugLogger {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private const val LOG_RETENTION_DAYS = 3L

    private var logFile: File? = null
    private var resetDateFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, "log.txt")
        resetDateFile = File(context.filesDir, "log_reset_date.txt")

        val today = LocalDate.now()
        val storedDate = resetDateFile?.takeIf { it.exists() }?.readText()?.trim()
            ?.let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() }

        if (storedDate == null || today >= storedDate.plusDays(LOG_RETENTION_DAYS)) {
            logFile?.writeText("")
            resetDateFile?.writeText(today.format(dateFmt))
        }
    }

    fun log(tag: String, msg: String) {
        val line = "${LocalDate.now().format(dateFmt)} ${LocalTime.now().format(timeFmt)} [$tag] $msg"
        logFile?.appendText("$line\n")
    }

    fun readLines(): List<String> =
        logFile?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.reversed()
            ?: emptyList()

    fun getShareUri(context: Context): Uri? {
        val file = logFile ?: return null
        if (!file.exists() || file.length() == 0L) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun clear() {
        logFile?.writeText("")
    }
}
