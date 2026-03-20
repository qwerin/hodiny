package cz.hodiny.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AttendanceRecord::class, ZoneEvent::class],
    version = 1,
    exportSchema = false
)
abstract class HodinyDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile private var instance: HodinyDatabase? = null

        fun getInstance(context: Context): HodinyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HodinyDatabase::class.java,
                    "hodiny.db"
                ).build().also { instance = it }
            }

        fun closeAndReset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
