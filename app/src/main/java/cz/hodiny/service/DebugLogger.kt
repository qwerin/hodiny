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

    private const val MAX_LOG_BYTES = 512 * 1024L

    // Zapisuje se z více vláken najednou – serializujeme a hlídáme velikost souboru
    @Synchronized
    fun log(tag: String, msg: String) {
        val file = logFile ?: return
        val line = "${LocalDate.now().format(dateFmt)} ${LocalTime.now().format(timeFmt)} [$tag] $msg"
        runCatching {
            if (file.length() > MAX_LOG_BYTES) {
                val lines = file.readLines()
                file.writeText(lines.takeLast(lines.size / 2).joinToString("\n", postfix = "\n"))
            }
            file.appendText("$line\n")
        }
    }

    @Synchronized
    fun readLines(): List<String> =
        logFile?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.reversed()
            ?: emptyList()

    fun getShareUri(context: Context): Uri? {
        val file = logFile ?: return null
        if (!file.exists() || file.length() == 0L) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    @Synchronized
    fun clear() {
        logFile?.writeText("")
    }
}
