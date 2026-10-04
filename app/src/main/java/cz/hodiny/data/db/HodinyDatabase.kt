package cz.hodiny.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AttendanceRecord::class, ZoneEvent::class, ExtraItem::class],
    version = 2,
    exportSchema = false
)
abstract class HodinyDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `extra_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `month` TEXT NOT NULL,
                        `date` TEXT,
                        `description` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `created_at` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_extra_items_month` ON `extra_items` (`month`)")
            }
        }

        @Volatile private var instance: HodinyDatabase? = null

        fun getInstance(context: Context): HodinyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HodinyDatabase::class.java,
                    "hodiny.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        fun closeAndReset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
