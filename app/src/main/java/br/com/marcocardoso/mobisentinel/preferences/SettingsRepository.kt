package br.com.marcocardoso.mobisentinel.preferences

import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<MonitoringSettings>

    suspend fun setMonitoringEnabled(enabled: Boolean)

    suspend fun setNarrateWifi(enabled: Boolean)

    suspend fun setNarrateCellular(enabled: Boolean)

    suspend fun setVibrateWifi(enabled: Boolean)

    suspend fun setVibrateCellular(enabled: Boolean)

    suspend fun setQuietHoursEnabled(enabled: Boolean)

    suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int)

    suspend fun setLossDelaySeconds(seconds: Int)

    suspend fun setRecoveryDelaySeconds(seconds: Int)
}
