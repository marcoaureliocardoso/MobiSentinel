package com.mobisentinel.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.preferences.preferencesDataStore
import com.mobisentinel.app.monitoring.MonitoringEngine
import com.mobisentinel.app.monitoring.MonitoringStateStore
import com.mobisentinel.app.monitoring.network.AndroidNetworkObserver
import com.mobisentinel.app.preferences.DataStoreSettingsRepository
import com.mobisentinel.app.preferences.SettingsRepository
import com.mobisentinel.app.speech.AndroidSpeechController
import com.mobisentinel.app.speech.SpeechAvailability
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
        val speechController = AndroidSpeechController(this, mutableSpeechAvailability)
        return MonitoringEngine(
            parentScope = scope,
            networkObserver = AndroidNetworkObserver(connectivityManager),
            settingsRepository = settingsRepository,
            speechController = speechController,
            stateStore = monitoringStateStore,
        )
    }
}
