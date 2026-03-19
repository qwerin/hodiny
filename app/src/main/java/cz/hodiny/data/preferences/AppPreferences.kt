package cz.hodiny.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("hodiny_settings")

data class AppSettings(
    val workLat: Double = 0.0,
    val workLng: Double = 0.0,
    val workRadius: Int = 150,
    val workSsid: String = "",
    val detectionMode: String = "both",   // gps | wifi | both
    val notificationTime: String = "18:00",
    val userName: String = "",
    val hourlyRate: Double = 0.0,
    val isOnboarded: Boolean = false
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val WORK_LAT = doublePreferencesKey("work_lat")
        val WORK_LNG = doublePreferencesKey("work_lng")
        val WORK_RADIUS = intPreferencesKey("work_radius")
        val WORK_SSID = stringPreferencesKey("work_ssid")
        val DETECTION_MODE = stringPreferencesKey("detection_mode")
        val NOTIFICATION_TIME = stringPreferencesKey("notification_time")
        val USER_NAME = stringPreferencesKey("user_name")
        val HOURLY_RATE = doublePreferencesKey("hourly_rate")
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            workLat = prefs[Keys.WORK_LAT] ?: 0.0,
            workLng = prefs[Keys.WORK_LNG] ?: 0.0,
            workRadius = prefs[Keys.WORK_RADIUS] ?: 150,
            workSsid = prefs[Keys.WORK_SSID] ?: "",
            detectionMode = prefs[Keys.DETECTION_MODE] ?: "both",
            notificationTime = prefs[Keys.NOTIFICATION_TIME] ?: "18:00",
            userName = prefs[Keys.USER_NAME] ?: "",
            hourlyRate = prefs[Keys.HOURLY_RATE] ?: 0.0,
            isOnboarded = prefs[Keys.IS_ONBOARDED] ?: false
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WORK_LAT] = settings.workLat
            prefs[Keys.WORK_LNG] = settings.workLng
            prefs[Keys.WORK_RADIUS] = settings.workRadius
            prefs[Keys.WORK_SSID] = settings.workSsid
            prefs[Keys.DETECTION_MODE] = settings.detectionMode
            prefs[Keys.NOTIFICATION_TIME] = settings.notificationTime
            prefs[Keys.USER_NAME] = settings.userName
            prefs[Keys.HOURLY_RATE] = settings.hourlyRate
            prefs[Keys.IS_ONBOARDED] = settings.isOnboarded
        }
    }
}
