package br.com.marcocardoso.mobisentinel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.marcocardoso.mobisentinel.MobiSentinelApplication
import br.com.marcocardoso.mobisentinel.monitoring.MonitoringStateStore
import br.com.marcocardoso.mobisentinel.preferences.SettingsRepository
import br.com.marcocardoso.mobisentinel.speech.SpeechAvailability
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    stateStore: MonitoringStateStore,
    private val settingsRepository: SettingsRepository,
    speechAvailability: StateFlow<SpeechAvailability>,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = combine(
        stateStore.snapshot,
        settingsRepository.settings,
        speechAvailability,
    ) { snapshot, settings, availability ->
        MainUiState(snapshot, settings, availability)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun activate() {
        viewModelScope.launch { settingsRepository.setMonitoringEnabled(true) }
    }

    fun setNarrateWifi(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNarrateWifi(enabled) }
    }

    fun setNarrateCellular(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNarrateCellular(enabled) }
    }

    fun setLossDelaySeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setLossDelaySeconds(seconds) }
    }

    fun setRecoveryDelaySeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setRecoveryDelaySeconds(seconds) }
    }

    class Factory(
        private val application: MobiSentinelApplication,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(
                    application.monitoringStateStore,
                    application.settingsRepository,
                    application.speechAvailability,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
