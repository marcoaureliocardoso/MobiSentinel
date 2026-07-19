package br.com.marcocardoso.mobisentinel.ui

import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSnapshot
import br.com.marcocardoso.mobisentinel.speech.SpeechAvailability

data class MainUiState(
    val snapshot: MonitoringSnapshot = MonitoringSnapshot(),
    val settings: MonitoringSettings = MonitoringSettings(),
    val speechAvailability: SpeechAvailability = SpeechAvailability.INITIALIZING,
    val hapticAvailable: Boolean = false,
)
