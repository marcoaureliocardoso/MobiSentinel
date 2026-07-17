package br.com.marcocardoso.mobisentinel.preferences

import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<MonitoringSettings>

    suspend fun setMonitoringEnabled(enabled: Boolean)

    suspend fun setNarrateWifi(enabled: Boolean)

    suspend fun setNarrateCellular(enabled: Boolean)

    suspend fun setLossDelaySeconds(seconds: Int)

    suspend fun setRecoveryDelaySeconds(seconds: Int)
}
