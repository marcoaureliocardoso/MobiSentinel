package com.mobisentinel.app.monitoring

import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.monitoring.model.Transport
import com.mobisentinel.app.monitoring.network.NetworkObserver
import com.mobisentinel.app.monitoring.state.TransitionCoordinator
import com.mobisentinel.app.preferences.SettingsRepository
import com.mobisentinel.app.speech.PortugueseMessageFactory
import com.mobisentinel.app.speech.SpeechController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MonitoringEngine(
    private val parentScope: CoroutineScope,
    private val networkObserver: NetworkObserver,
    private val settingsRepository: SettingsRepository,
    private val speechController: SpeechController,
    private val stateStore: MonitoringStateStore,
) {
    private val lock = Any()

    @Volatile
    private var currentSettings = MonitoringSettings()

    private var started = false
    private var engineJob: Job? = null
    private var wifiCoordinator: TransitionCoordinator? = null
    private var cellularCoordinator: TransitionCoordinator? = null

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            stateStore.setServiceActive(true)

            val supervisor = SupervisorJob(parentScope.coroutineContext[Job])
            val scope = CoroutineScope(parentScope.coroutineContext + supervisor)
            engineJob = supervisor
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                currentSettings = settingsRepository.settings.first()

                val wifi = createCoordinator(Transport.WIFI, scope)
                val cellular = createCoordinator(Transport.CELLULAR, scope)
                synchronized(lock) {
                    if (!started) {
                        wifi.close()
                        cellular.close()
                        return@launch
                    }
                    wifiCoordinator = wifi
                    cellularCoordinator = cellular
                }

                launch(start = CoroutineStart.UNDISPATCHED) {
                    settingsRepository.settings.collect { currentSettings = it }
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    networkObserver.states.collect { snapshot ->
                        when (snapshot.transport) {
                            Transport.WIFI -> wifi.submit(snapshot.state)
                            Transport.CELLULAR -> cellular.submit(snapshot.state)
                        }
                    }
                }
                networkObserver.start()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
            engineJob?.cancel()
            engineJob = null
            wifiCoordinator?.close()
            cellularCoordinator?.close()
            wifiCoordinator = null
            cellularCoordinator = null
            networkObserver.stop()
            speechController.close()
            stateStore.setServiceActive(false)
        }
    }

    fun testVoice() {
        synchronized(lock) {
            if (started) {
                speechController.testVoice()
            }
        }
    }

    private fun createCoordinator(
        transport: Transport,
        scope: CoroutineScope,
    ): TransitionCoordinator = TransitionCoordinator(
        transport = transport,
        scope = scope,
        settings = { currentSettings },
        onConfirmedState = { stateStore.setState(transport, it) },
        onTransition = { transition ->
            if (currentSettings.narrationEnabled(transport)) {
                speechController.announce(PortugueseMessageFactory.from(transition))
            }
        },
    )
}
