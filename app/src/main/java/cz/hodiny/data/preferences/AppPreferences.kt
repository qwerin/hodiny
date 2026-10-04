package cz.hodiny.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val roundingMinutes: Int = 0,
    val isOnboarded: Boolean = false
)

// Napojení na NanoFakturu – ukládá se zvlášť, aby ho neresetovalo uložení AppSettings
data class NanoFakturaConfig(
    val url: String = DEFAULT_NANOFAKTURA_URL,
    val token: String = "",
    val accountSlug: String = "",
    val subjectId: Long = 0,
    val draft: Boolean = true
)

const val DEFAULT_NANOFAKTURA_URL = "https://nanofaktura.cz/"

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
        val ROUNDING_MINUTES = intPreferencesKey("rounding_minutes")
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        val IS_INSIDE_ZONE = booleanPreferencesKey("is_inside_zone")
        val IS_ON_WORK_WIFI = booleanPreferencesKey("is_on_work_wifi")
        val LAST_ENTER_MS = longPreferencesKey("last_enter_ms")
        val LAST_ENTER_SOURCE = stringPreferencesKey("last_enter_source")
        val LAST_EXIT_MS = longPreferencesKey("last_exit_ms")
        val LAST_EXIT_SOURCE = stringPreferencesKey("last_exit_source")
        val NF_URL = stringPreferencesKey("nf_url")
        val NF_TOKEN = stringPreferencesKey("nf_token")
        val NF_ACCOUNT_SLUG = stringPreferencesKey("nf_account_slug")
        val NF_SUBJECT_ID = longPreferencesKey("nf_subject_id")
        val NF_DRAFT = booleanPreferencesKey("nf_draft")
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
            roundingMinutes = prefs[Keys.ROUNDING_MINUTES] ?: 0,
            isOnboarded = prefs[Keys.IS_ONBOARDED] ?: false
        )
    }

    val nanoFaktura: Flow<NanoFakturaConfig> = context.dataStore.data.map { prefs ->
        NanoFakturaConfig(
            url = prefs[Keys.NF_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_NANOFAKTURA_URL,
            token = prefs[Keys.NF_TOKEN] ?: "",
            accountSlug = prefs[Keys.NF_ACCOUNT_SLUG] ?: "",
            subjectId = prefs[Keys.NF_SUBJECT_ID] ?: 0,
            draft = prefs[Keys.NF_DRAFT] ?: true
        )
    }

    suspend fun saveNanoFaktura(config: NanoFakturaConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NF_URL] = config.url
            prefs[Keys.NF_TOKEN] = config.token
            prefs[Keys.NF_ACCOUNT_SLUG] = config.accountSlug
            prefs[Keys.NF_SUBJECT_ID] = config.subjectId
            prefs[Keys.NF_DRAFT] = config.draft
        }
    }

    suspend fun isInsideZone(): Boolean =
        context.dataStore.data.map { it[Keys.IS_INSIDE_ZONE] ?: false }.first()

    suspend fun setInsideZone(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_INSIDE_ZONE] = value }
    }

    suspend fun isOnWorkWifi(): Boolean =
        context.dataStore.data.map { it[Keys.IS_ON_WORK_WIFI] ?: false }.first()

    suspend fun setOnWorkWifi(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_ON_WORK_WIFI] = value }
    }

    suspend fun getDebounceEnter(): Pair<Long, String> =
        context.dataStore.data.map {
            Pair(it[Keys.LAST_ENTER_MS] ?: 0L, it[Keys.LAST_ENTER_SOURCE] ?: "")
        }.first()

    suspend fun setDebounceEnter(ms: Long, source: String) {
        context.dataStore.edit {
            it[Keys.LAST_ENTER_MS] = ms
            it[Keys.LAST_ENTER_SOURCE] = source
        }
    }

    suspend fun getDebounceExit(): Pair<Long, String> =
        context.dataStore.data.map {
            Pair(it[Keys.LAST_EXIT_MS] ?: 0L, it[Keys.LAST_EXIT_SOURCE] ?: "")
        }.first()

    suspend fun setDebounceExit(ms: Long, source: String) {
        context.dataStore.edit {
            it[Keys.LAST_EXIT_MS] = ms
            it[Keys.LAST_EXIT_SOURCE] = source
        }
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
            prefs[Keys.ROUNDING_MINUTES] = settings.roundingMinutes
            prefs[Keys.IS_ONBOARDED] = settings.isOnboarded
        }
    }
}
