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
    onTestHaptics: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onStopMonitoring: () -> Unit,
    onNarrateWifiChange: (Boolean) -> Unit,
    onNarrateCellularChange: (Boolean) -> Unit,
    onVibrateWifiChange: (Boolean) -> Unit,
    onVibrateCellularChange: (Boolean) -> Unit,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursChange: (Int, Int) -> Unit,
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
            hapticAvailable = uiState.hapticAvailable,
            onNarrateWifiChange = onNarrateWifiChange,
            onNarrateCellularChange = onNarrateCellularChange,
            onVibrateWifiChange = onVibrateWifiChange,
            onVibrateCellularChange = onVibrateCellularChange,
            onQuietHoursEnabledChange = onQuietHoursEnabledChange,
            onQuietHoursChange = onQuietHoursChange,
            onLossDelayChange = onLossDelayChange,
            onRecoveryDelayChange = onRecoveryDelayChange,
            onTestVoice = onTestVoice,
            onTestHaptics = onTestHaptics,
            onOpenVoiceSettings = onOpenVoiceSettings,
            onStopMonitoring = onStopMonitoring,
            onBack = { destination = Destination.MAIN },
        )
    }
}
