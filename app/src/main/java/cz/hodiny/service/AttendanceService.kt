package cz.hodiny.service

import android.content.Context
import cz.hodiny.HodinyApp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Debounce okno 2 minuty – zabraňuje duplicitním eventům z GPS + WiFi současně
private const val DEBOUNCE_MS = 2 * 60 * 1000L

private var lastEnterMs = 0L
private var lastExitMs = 0L

suspend fun handleZoneEnter(context: Context, source: String) {
    val now = System.currentTimeMillis()
    if (now - lastEnterMs < DEBOUNCE_MS) return
    lastEnterMs = now

    val app = context.applicationContext as HodinyApp
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    val record = app.repository.recordArrival(source, timestamp)

    // Pošli notifikaci o příchodu jen pokud jsme právě zapsali příchod
    if (record.arrivalTime != null) {
        NotificationHelper.sendArrivalNotification(context, record.id, record.arrivalTime)
    }
}

suspend fun handleZoneExit(context: Context, source: String) {
    val now = System.currentTimeMillis()
    if (now - lastExitMs < DEBOUNCE_MS) return
    lastExitMs = now

    val app = context.applicationContext as HodinyApp
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    app.repository.recordDeparture(source, timestamp)
}
