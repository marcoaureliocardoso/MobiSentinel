package br.com.marcocardoso.mobisentinel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import androidx.datastore.preferences.preferencesDataStore
import br.com.marcocardoso.mobisentinel.monitoring.MonitoringEngine
import br.com.marcocardoso.mobisentinel.monitoring.MonitoringStateStore
import br.com.marcocardoso.mobisentinel.monitoring.network.AndroidCellularNetworkRequester
import br.com.marcocardoso.mobisentinel.monitoring.network.AndroidCellularValidationProbe
import br.com.marcocardoso.mobisentinel.monitoring.network.AndroidNetworkObserver
import br.com.marcocardoso.mobisentinel.preferences.DataStoreSettingsRepository
import br.com.marcocardoso.mobisentinel.preferences.SettingsRepository
import br.com.marcocardoso.mobisentinel.speech.AndroidSpeechController
import br.com.marcocardoso.mobisentinel.speech.SpeechAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val Context.settingsDataStore by preferencesDataStore("monitoring_settings")

class MobiSentinelApplication : Application() {
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(settingsDataStore)
    }
    val monitoringStateStore = MonitoringStateStore()

    private val mutableSpeechAvailability = MutableStateFlow(SpeechAvailability.UNAVAILABLE)
    val speechAvailability: StateFlow<SpeechAvailability> = mutableSpeechAvailability.asStateFlow()

    fun createMonitoringEngine(scope: CoroutineScope): MonitoringEngine {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        val cellularProbe = AndroidCellularValidationProbe(
            AndroidCellularNetworkRequester(connectivityManager),
        )
        val speechController = AndroidSpeechController(this, mutableSpeechAvailability)
        return MonitoringEngine(
            parentScope = scope,
            networkObserver = AndroidNetworkObserver(
                context = this,
                connectivityManager = connectivityManager,
                telephonyManager = telephonyManager,
                scope = scope,
                cellularProbe = cellularProbe,
            ),
            settingsRepository = settingsRepository,
            speechController = speechController,
            stateStore = monitoringStateStore,
        )
    }
}
