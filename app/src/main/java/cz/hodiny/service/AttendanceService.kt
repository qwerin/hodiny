package cz.hodiny.service

import android.content.Context
import cz.hodiny.HodinyApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Debounce okno 2 minuty – zabraňuje duplicitním eventům z GPS + WiFi současně
private const val DEBOUNCE_MS = 2 * 60 * 1000L

// Události z GPS a WiFi se zpracovávají postupně – kontrola a zápis debounce musí být atomické
private val zoneMutex = Mutex()

// Pro příchod má prioritu GPS – pokud GPS přijde po WiFi, přepíše ho
// Pro odchod má prioritu WiFi/SSID – pokud WiFi přijde po GPS, přepíše ho
suspend fun handleZoneEnter(context: Context, source: String) = zoneMutex.withLock {
    val app = context.applicationContext as HodinyApp
    val now = System.currentTimeMillis()
    val (lastEnterMs, lastEnterSource) = app.preferences.getDebounceEnter()
    val elapsed = now - lastEnterMs
    if (elapsed < DEBOUNCE_MS) {
        if (source == "gps" && lastEnterSource == "wifi") {
            DebugLogger.log(source, "GPS přebíjí WiFi enter (priorita GPS pro příchod)")
            // Pokračujeme – GPS přepíše WiFi záznam
        } else {
            DebugLogger.log(source, "enter ignorován (debounce, zbývá ${(DEBOUNCE_MS - elapsed) / 1000}s, zdroj=$lastEnterSource)")
            return@withLock
        }
    }
    app.preferences.setDebounceEnter(now, source)
    DebugLogger.log(source, "enter zaznamenán")

    app.preferences.setInsideZone(true)
    val rounding = app.preferences.settings.first().roundingMinutes
    val timestamp = roundedNow(rounding).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    val (record, isNew) = app.repository.recordArrival(source, timestamp)

    if (isNew && record.arrivalTime != null) {
        DebugLogger.log(source, "příchod uložen: ${record.arrivalTime}")
        NotificationHelper.sendArrivalNotification(context, record.id, record.arrivalTime)
    } else {
        DebugLogger.log(source, "příchod dnes již existuje, přeskočen (isNew=$isNew)")
    }
}

suspend fun handleZoneExit(context: Context, source: String) = zoneMutex.withLock {
    val app = context.applicationContext as HodinyApp
    val now = System.currentTimeMillis()
    val (lastExitMs, lastExitSource) = app.preferences.getDebounceExit()
    val elapsed = now - lastExitMs
    if (elapsed < DEBOUNCE_MS) {
        if (source == "wifi" && lastExitSource == "gps") {
            DebugLogger.log(source, "WiFi přebíjí GPS exit (priorita WiFi pro odchod)")
            // Pokračujeme – WiFi přepíše GPS záznam
        } else {
            DebugLogger.log(source, "exit ignorován (debounce, zbývá ${(DEBOUNCE_MS - elapsed) / 1000}s, zdroj=$lastExitSource)")
            return@withLock
        }
    }
    if (!app.preferences.isInsideZone()) {
        DebugLogger.log(source, "exit ignorován – nebyl předchozí enter (restart mimo zónu?)")
        return@withLock
    }

    app.preferences.setDebounceExit(now, source)
    DebugLogger.log(source, "exit zaznamenán")

    app.preferences.setInsideZone(false)
    val rounding = app.preferences.settings.first().roundingMinutes
    val timestamp = roundedNow(rounding).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    app.repository.recordDeparture(source, timestamp)
    DebugLogger.log(source, "odchod uložen: $timestamp")
}

private fun roundedNow(minutes: Int): LocalDateTime {
    val now = LocalDateTime.now()
    if (minutes == 0) return now
    val total = now.hour * 60 + now.minute
    val rounded = ((total + minutes / 2) / minutes) * minutes
    return now.withHour(rounded / 60 % 24).withMinute(rounded % 60).withSecond(0).withNano(0)
}
