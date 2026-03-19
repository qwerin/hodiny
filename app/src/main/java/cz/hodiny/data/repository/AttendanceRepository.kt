package cz.hodiny.data.repository

import cz.hodiny.data.db.AttendanceDao
import cz.hodiny.data.db.AttendanceRecord
import cz.hodiny.data.db.ZoneEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AttendanceRepository(private val dao: AttendanceDao) {

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun now() = LocalDateTime.now().format(isoFormatter)
    fun today() = LocalDate.now().toString()

    fun observeToday(): Flow<AttendanceRecord?> = dao.observeByDate(today())
    fun observeAll(): Flow<List<AttendanceRecord>> = dao.observeAll()

    suspend fun findByDate(date: String) = dao.findByDate(date)
    suspend fun findAll() = dao.findAll()
    suspend fun findByMonth(year: Int, month: Int): List<AttendanceRecord> {
        val prefix = "%04d-%02d".format(year, month)
        return dao.findByMonth(prefix)
    }

    // Zajistí existenci dnešního záznamu, vrátí ho
    suspend fun getOrCreateToday(source: String): AttendanceRecord {
        val date = today()
        val existing = dao.findByDate(date)
        if (existing != null) return existing
        val now = now()
        val id = dao.insert(AttendanceRecord(date = date, entrySource = source, createdAt = now, updatedAt = now))
        return dao.findByDate(date)!!
    }

    // Příchod – zapisuje se pouze jednou za den (první vstup)
    suspend fun recordArrival(source: String, timestamp: String = now()): AttendanceRecord {
        val record = getOrCreateToday(source)
        if (record.arrivalTime == null) {
            dao.updateArrival(record.id, timestamp, source, now())
            logZoneEvent(record.id, "enter", source, timestamp)
        } else {
            // Duplicitní vstup, jen logujeme
            logZoneEvent(record.id, "enter", source, timestamp)
        }
        return dao.findByDate(today())!!
    }

    // Odchod – vždy přepisuje (poslední odchod dne)
    suspend fun recordDeparture(source: String, timestamp: String = now()) {
        val date = today()
        val record = dao.findByDate(date) ?: return
        if (record.isLocked) return
        dao.updateDeparture(record.id, timestamp, now())
        logZoneEvent(record.id, "exit", source, timestamp)
    }

    suspend fun confirmDeparture(id: Long, correctedTime: String? = null) {
        val now = now()
        if (correctedTime != null) {
            val record = dao.findByDate(today()) ?: return
            dao.updateDeparture(id, correctedTime, now)
        }
        dao.lock(id, now)
    }

    suspend fun updateRecord(id: Long, arrivalTime: String?, departureTime: String?, note: String?) {
        val record = findRecordById(id) ?: return
        dao.update(record.copy(
            arrivalTime = arrivalTime,
            departureTime = departureTime,
            note = note,
            entrySource = "manual",
            updatedAt = now()
        ))
    }

    suspend fun updateRecordByRecord(record: AttendanceRecord) {
        val existing = dao.findByDate(record.date)
        if (existing != null) {
            dao.update(existing.copy(
                arrivalTime = record.arrivalTime,
                departureTime = record.departureTime,
                note = record.note,
                entrySource = "manual",
                isLocked = true,
                updatedAt = now()
            ))
        } else {
            dao.insert(record)
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    private suspend fun findRecordById(id: Long): AttendanceRecord? = dao.findById(id)

    private suspend fun logZoneEvent(recordId: Long, type: String, source: String, timestamp: String) {
        dao.insertZoneEvent(ZoneEvent(recordId = recordId, eventType = type, eventSource = source, timestamp = timestamp))
    }
}
