package cz.hodiny.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Query("SELECT * FROM attendance_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<AttendanceRecord?>

    @Query("SELECT * FROM attendance_records WHERE date LIKE :prefix || '%' ORDER BY date ASC")
    suspend fun findByMonth(prefix: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    fun observeAll(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    suspend fun findAll(): List<AttendanceRecord>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: AttendanceRecord): Long

    @Update
    suspend fun update(record: AttendanceRecord)

    @Query("""
        UPDATE attendance_records
        SET arrival_time = :time, entry_source = :source, updated_at = :now
        WHERE id = :id
    """)
    suspend fun updateArrival(id: Long, time: String, source: String, now: String)

    @Query("""
        UPDATE attendance_records
        SET departure_time = :time, updated_at = :now
        WHERE id = :id AND is_locked = 0
    """)
    suspend fun updateDeparture(id: Long, time: String, now: String)

    @Query("UPDATE attendance_records SET is_locked = 1, updated_at = :now WHERE id = :id")
    suspend fun lock(id: Long, now: String)

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertZoneEvent(event: ZoneEvent)

    @Query("SELECT * FROM extra_items ORDER BY COALESCE(date, month || '-00') ASC, id ASC")
    fun observeExtraItems(): Flow<List<ExtraItem>>

    @Query("SELECT * FROM extra_items WHERE month = :month ORDER BY COALESCE(date, month || '-00') ASC, id ASC")
    suspend fun findExtraItemsByMonth(month: String): List<ExtraItem>

    @Insert
    suspend fun insertExtraItem(item: ExtraItem): Long

    @Update
    suspend fun updateExtraItem(item: ExtraItem)

    @Query("DELETE FROM extra_items WHERE id = :id")
    suspend fun deleteExtraItem(id: Long)
}
