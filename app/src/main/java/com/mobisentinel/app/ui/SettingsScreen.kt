package com.mobisentinel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.speech.SpeechAvailability
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: MonitoringSettings,
    speechAvailability: SpeechAvailability,
    onNarrateWifiChange: (Boolean) -> Unit,
    onNarrateCellularChange: (Boolean) -> Unit,
    onLossDelayChange: (Int) -> Unit,
    onRecoveryDelayChange: (Int) -> Unit,
    onTestVoice: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onStopMonitoring: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Text("Voltar")
            }
            Text(
                text = "Configurações",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider()
        SettingSwitch(
            label = "Narrar eventos de Wi-Fi",
            checked = settings.narrateWifi,
            tag = "narrate_wifi",
            onCheckedChange = onNarrateWifiChange,
        )
        SettingSwitch(
            label = "Narrar eventos de dados móveis",
            checked = settings.narrateCellular,
            tag = "narrate_cellular",
            onCheckedChange = onNarrateCellularChange,
        )
        DelaySlider(
            label = "Confirmar perda após",
            seconds = settings.lossDelaySeconds,
            tag = "loss_delay",
            onValueChange = onLossDelayChange,
        )
        DelaySlider(
            label = "Confirmar retorno após",
            seconds = settings.recoveryDelaySeconds,
            tag = "recovery_delay",
            onValueChange = onRecoveryDelayChange,
        )
        Button(
            onClick = onTestVoice,
            enabled = settings.monitoringEnabled && speechAvailability == SpeechAvailability.READY,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("test_voice"),
        ) {
            Text("Testar narração")
        }
        if (speechAvailability == SpeechAvailability.UNAVAILABLE) {
            OutlinedButton(
                onClick = onOpenVoiceSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_voice_settings"),
            ) {
                Text("Abrir configurações de voz")
            }
        }
        if (settings.monitoringEnabled) {
            OutlinedButton(
                onClick = onStopMonitoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stop_monitoring"),
            ) {
                Text("Parar monitoramento")
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun DelaySlider(
    label: String,
    seconds: Int,
    tag: String,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text("$seconds segundos")
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 60)) },
            valueRange = 0f..60f,
            steps = 59,
            modifier = Modifier.testTag(tag),
        )
    }
}
