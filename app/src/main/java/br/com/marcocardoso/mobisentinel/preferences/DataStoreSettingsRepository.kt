package br.com.marcocardoso.mobisentinel.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<MonitoringSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val (quietStartMinuteOfDay, quietEndMinuteOfDay) = preferences.quietHours()
            MonitoringSettings(
                monitoringEnabled = preferences[Keys.monitoringEnabled] ?: false,
                narrateWifi = preferences[Keys.narrateWifi] ?: true,
                narrateCellular = preferences[Keys.narrateCellular] ?: true,
                vibrateWifi = preferences[Keys.vibrateWifi] ?: false,
                vibrateCellular = preferences[Keys.vibrateCellular] ?: false,
                quietHoursEnabled = preferences[Keys.quietHoursEnabled] ?: false,
                quietStartMinuteOfDay = quietStartMinuteOfDay,
                quietEndMinuteOfDay = quietEndMinuteOfDay,
                lossDelaySeconds = (preferences[Keys.lossDelaySeconds] ?: 5).coerceIn(0, 60),
                recoveryDelaySeconds = (preferences[Keys.recoveryDelaySeconds] ?: 2).coerceIn(0, 60),
            )
        }

    override suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.monitoringEnabled] = enabled }
    }

    override suspend fun setNarrateWifi(enabled: Boolean) {
        dataStore.edit { it[Keys.narrateWifi] = enabled }
    }

    override suspend fun setNarrateCellular(enabled: Boolean) {
        dataStore.edit { it[Keys.narrateCellular] = enabled }
    }

    override suspend fun setVibrateWifi(enabled: Boolean) {
        dataStore.edit { it[Keys.vibrateWifi] = enabled }
    }

    override suspend fun setVibrateCellular(enabled: Boolean) {
        dataStore.edit { it[Keys.vibrateCellular] = enabled }
    }

    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.quietHoursEnabled] = enabled }
    }

    override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        require(startMinuteOfDay in MonitoringSettings.MINUTE_OF_DAY_RANGE)
        require(endMinuteOfDay in MonitoringSettings.MINUTE_OF_DAY_RANGE)
        require(startMinuteOfDay != endMinuteOfDay)
        dataStore.edit { preferences ->
            preferences[Keys.quietStartMinuteOfDay] = startMinuteOfDay
            preferences[Keys.quietEndMinuteOfDay] = endMinuteOfDay
        }
    }

    override suspend fun setLossDelaySeconds(seconds: Int) {
        require(seconds in 0..60)
        dataStore.edit { it[Keys.lossDelaySeconds] = seconds }
    }

    override suspend fun setRecoveryDelaySeconds(seconds: Int) {
        require(seconds in 0..60)
        dataStore.edit { it[Keys.recoveryDelaySeconds] = seconds }
    }

    private fun Preferences.quietHours(): Pair<Int, Int> {
        val start = this[Keys.quietStartMinuteOfDay] ?: MonitoringSettings.DEFAULT_QUIET_START_MINUTE
        val end = this[Keys.quietEndMinuteOfDay] ?: MonitoringSettings.DEFAULT_QUIET_END_MINUTE
        return if (
            start in MonitoringSettings.MINUTE_OF_DAY_RANGE &&
            end in MonitoringSettings.MINUTE_OF_DAY_RANGE &&
            start != end
        ) {
            start to end
        } else {
            MonitoringSettings.DEFAULT_QUIET_START_MINUTE to MonitoringSettings.DEFAULT_QUIET_END_MINUTE
        }
    }

    private object Keys {
        val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
        val narrateWifi = booleanPreferencesKey("narrate_wifi")
        val narrateCellular = booleanPreferencesKey("narrate_cellular")
        val vibrateWifi = booleanPreferencesKey("vibrate_wifi")
        val vibrateCellular = booleanPreferencesKey("vibrate_cellular")
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietStartMinuteOfDay = intPreferencesKey("quiet_start_minute_of_day")
        val quietEndMinuteOfDay = intPreferencesKey("quiet_end_minute_of_day")
        val lossDelaySeconds = intPreferencesKey("loss_delay_seconds")
        val recoveryDelaySeconds = intPreferencesKey("recovery_delay_seconds")
    }
}
