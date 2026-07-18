package br.com.marcocardoso.mobisentinel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private enum class Destination { MAIN, SETTINGS }

@Composable
fun MobiSentinelApp(
    uiState: MainUiState,
    onActivate: () -> Unit,
    onTestVoice: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onStopMonitoring: () -> Unit,
    onNarrateWifiChange: (Boolean) -> Unit,
    onNarrateCellularChange: (Boolean) -> Unit,
    onLossDelayChange: (Int) -> Unit,
    onRecoveryDelayChange: (Int) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.MAIN) }
    when (destination) {
        Destination.MAIN -> MainScreen(
            uiState = uiState,
            onActivate = onActivate,
            onOpenSettings = { destination = Destination.SETTINGS },
        )
        Destination.SETTINGS -> SettingsScreen(
            settings = uiState.settings,
            speechAvailability = uiState.speechAvailability,
            onNarrateWifiChange = onNarrateWifiChange,
            onNarrateCellularChange = onNarrateCellularChange,
            onLossDelayChange = onLossDelayChange,
            onRecoveryDelayChange = onRecoveryDelayChange,
            onTestVoice = onTestVoice,
            onOpenVoiceSettings = onOpenVoiceSettings,
            onStopMonitoring = onStopMonitoring,
            onBack = { destination = Destination.MAIN },
        )
    }
}
