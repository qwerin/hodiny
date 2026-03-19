package cz.hodiny

import android.app.Application
import androidx.work.Configuration
import cz.hodiny.data.db.HodinyDatabase
import cz.hodiny.data.preferences.AppPreferences
import cz.hodiny.data.repository.AttendanceRepository
import cz.hodiny.service.NotificationHelper

class HodinyApp : Application(), Configuration.Provider {

    val database by lazy { HodinyDatabase.getInstance(this) }
    val preferences by lazy { AppPreferences(this) }
    val repository by lazy { AttendanceRepository(database.attendanceDao()) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
