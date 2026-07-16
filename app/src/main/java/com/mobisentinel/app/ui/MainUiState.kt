package com.mobisentinel.app.ui

import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.monitoring.model.MonitoringSnapshot
import com.mobisentinel.app.speech.SpeechAvailability

data class MainUiState(
    val snapshot: MonitoringSnapshot = MonitoringSnapshot(),
    val settings: MonitoringSettings = MonitoringSettings(),
    val speechAvailability: SpeechAvailability = SpeechAvailability.INITIALIZING,
)
