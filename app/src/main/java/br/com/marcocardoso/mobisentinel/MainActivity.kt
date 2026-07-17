package br.com.marcocardoso.mobisentinel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import br.com.marcocardoso.mobisentinel.service.MonitoringService
import br.com.marcocardoso.mobisentinel.ui.MainViewModel
import br.com.marcocardoso.mobisentinel.ui.MobiSentinelApp
import br.com.marcocardoso.mobisentinel.ui.theme.MobiSentinelTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application as MobiSentinelApplication)
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        MonitoringService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MobiSentinelTheme {
                MobiSentinelApp(
                    uiState = uiState,
                    onActivate = {
                        viewModel.activate()
                        startMonitoringWithPermission()
                    },
                    onTestVoice = { MonitoringService.testVoice(this) },
                    onOpenVoiceSettings = ::openVoiceSettings,
                    onStopMonitoring = { MonitoringService.stop(this) },
                    onNarrateWifiChange = viewModel::setNarrateWifi,
                    onNarrateCellularChange = viewModel::setNarrateCellular,
                    onLossDelayChange = viewModel::setLossDelaySeconds,
                    onRecoveryDelayChange = viewModel::setRecoveryDelaySeconds,
                )
            }
        }
    }

    private fun startMonitoringWithPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            MonitoringService.start(this)
        }
    }

    private fun openVoiceSettings() {
        val candidates = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
        candidates.firstOrNull { it.resolveActivity(packageManager) != null }?.let(::startActivity)
    }
}
