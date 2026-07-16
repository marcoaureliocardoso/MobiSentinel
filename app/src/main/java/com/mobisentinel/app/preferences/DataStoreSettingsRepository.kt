package com.mobisentinel.app.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.mobisentinel.app.monitoring.model.MonitoringSettings
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
            MonitoringSettings(
                monitoringEnabled = preferences[Keys.monitoringEnabled] ?: false,
                narrateWifi = preferences[Keys.narrateWifi] ?: true,
                narrateCellular = preferences[Keys.narrateCellular] ?: true,
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

    override suspend fun setLossDelaySeconds(seconds: Int) {
        require(seconds in 0..60)
        dataStore.edit { it[Keys.lossDelaySeconds] = seconds }
    }

    override suspend fun setRecoveryDelaySeconds(seconds: Int) {
        require(seconds in 0..60)
        dataStore.edit { it[Keys.recoveryDelaySeconds] = seconds }
    }

    private object Keys {
        val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
        val narrateWifi = booleanPreferencesKey("narrate_wifi")
        val narrateCellular = booleanPreferencesKey("narrate_cellular")
        val lossDelaySeconds = intPreferencesKey("loss_delay_seconds")
        val recoveryDelaySeconds = intPreferencesKey("recovery_delay_seconds")
    }
}
