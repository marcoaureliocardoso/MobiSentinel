package com.mobisentinel.app.preferences

import com.mobisentinel.app.monitoring.model.MonitoringSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<MonitoringSettings>

    suspend fun setMonitoringEnabled(enabled: Boolean)

    suspend fun setNarrateWifi(enabled: Boolean)

    suspend fun setNarrateCellular(enabled: Boolean)

    suspend fun setLossDelaySeconds(seconds: Int)

    suspend fun setRecoveryDelaySeconds(seconds: Int)
}
