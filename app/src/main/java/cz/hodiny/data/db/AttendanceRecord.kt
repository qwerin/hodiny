package cz.hodiny.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records", indices = [Index(value = ["date"], unique = true)])
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,           // "YYYY-MM-DD"
    @ColumnInfo(name = "arrival_time") val arrivalTime: String? = null,    // ISO datetime
    @ColumnInfo(name = "departure_time") val departureTime: String? = null, // ISO datetime, vždy poslední odchod
    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,
    @ColumnInfo(name = "entry_source") val entrySource: String = "manual",  // gps|wifi|manual
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: String = ""
)

@Entity(tableName = "zone_events")
data class ZoneEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "record_id") val recordId: Long,
    @ColumnInfo(name = "event_type") val eventType: String,   // enter|exit
    @ColumnInfo(name = "event_source") val eventSource: String, // gps|wifi
    @ColumnInfo(name = "timestamp") val timestamp: String,
    @ColumnInfo(name = "raw_data") val rawData: String? = null
)
