package br.com.marcocardoso.mobisentinel.ui

import android.app.TimePickerDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.speech.SpeechAvailability
import java.util.Locale
import kotlin.math.roundToInt

fun interface TimePickerLauncher {
    fun show(initialMinuteOfDay: Int, onSelected: (Int) -> Unit)
}

@Composable
fun SettingsScreen(
    settings: MonitoringSettings,
    speechAvailability: SpeechAvailability,
    hapticAvailable: Boolean,
    onNarrateWifiChange: (Boolean) -> Unit,
    onNarrateCellularChange: (Boolean) -> Unit,
    onVibrateWifiChange: (Boolean) -> Unit,
    onVibrateCellularChange: (Boolean) -> Unit,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursChange: (Int, Int) -> Unit,
    onLossDelayChange: (Int) -> Unit,
    onRecoveryDelayChange: (Int) -> Unit,
    onTestVoice: () -> Unit,
    onTestHaptics: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onStopMonitoring: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timePickerLauncher: TimePickerLauncher? = null,
) {
    val productionTimePickerLauncher = rememberTimePickerLauncher()
    val launcher = timePickerLauncher ?: productionTimePickerLauncher
    var quietHoursError by rememberSaveable { mutableStateOf<String?>(null) }

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

        SectionTitle("Narração")
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

        SectionTitle("Vibração")
        SettingSwitch(
            label = "Vibrar em eventos de Wi-Fi",
            checked = settings.vibrateWifi,
            tag = "vibrate_wifi",
            onCheckedChange = onVibrateWifiChange,
        )
        SettingSwitch(
            label = "Vibrar em eventos de dados móveis",
            checked = settings.vibrateCellular,
            tag = "vibrate_cellular",
            onCheckedChange = onVibrateCellularChange,
        )
        Button(
            onClick = onTestHaptics,
            enabled = settings.monitoringEnabled && hapticAvailable,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("test_haptics"),
        ) {
            Text("Testar vibração")
        }
        if (!hapticAvailable) {
            Text("Este aparelho não possui vibrador disponível.")
        }

        SectionTitle("Não perturbe")
        SettingSwitch(
            label = "Ativar horário silencioso",
            checked = settings.quietHoursEnabled,
            tag = "quiet_hours_enabled",
            onCheckedChange = onQuietHoursEnabledChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TimeControl(
                label = "Início",
                minuteOfDay = settings.quietStartMinuteOfDay,
                tag = "quiet_start",
                modifier = Modifier.weight(1f),
                onClick = {
                    launcher.show(settings.quietStartMinuteOfDay) { selected ->
                        if (selected == settings.quietEndMinuteOfDay) {
                            quietHoursError = "Início e fim precisam ser diferentes."
                        } else {
                            quietHoursError = null
                            onQuietHoursChange(selected, settings.quietEndMinuteOfDay)
                        }
                    }
                },
            )
            TimeControl(
                label = "Fim",
                minuteOfDay = settings.quietEndMinuteOfDay,
                tag = "quiet_end",
                modifier = Modifier.weight(1f),
                onClick = {
                    launcher.show(settings.quietEndMinuteOfDay) { selected ->
                        if (selected == settings.quietStartMinuteOfDay) {
                            quietHoursError = "Início e fim precisam ser diferentes."
                        } else {
                            quietHoursError = null
                            onQuietHoursChange(settings.quietStartMinuteOfDay, selected)
                        }
                    }
                },
            )
        }
        quietHoursError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("quiet_hours_error"),
            )
        }
        Text(
            "Durante este horário, narração e vibração são silenciadas. " +
                "O monitoramento continua ativo.",
        )

        SectionTitle("Confirmação")
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
private fun rememberTimePickerLauncher(): TimePickerLauncher {
    val context = LocalContext.current
    return remember(context) {
        TimePickerLauncher { initialMinuteOfDay, onSelected ->
            TimePickerDialog(
                context,
                { _, hour, minute -> onSelected(hour * 60 + minute) },
                initialMinuteOfDay / 60,
                initialMinuteOfDay % 60,
                true,
            ).show()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TimeControl(
    label: String,
    minuteOfDay: Int,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag),
        ) {
            Text(formatMinuteOfDay(minuteOfDay))
        }
    }
}

private fun formatMinuteOfDay(minuteOfDay: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minuteOfDay / 60,
    minuteOfDay % 60,
)

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
