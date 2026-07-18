package br.com.marcocardoso.mobisentinel.monitoring

import br.com.marcocardoso.mobisentinel.haptics.HapticController
import br.com.marcocardoso.mobisentinel.monitoring.alerts.AlertPolicy
import br.com.marcocardoso.mobisentinel.monitoring.alerts.LocalMinuteProvider
import br.com.marcocardoso.mobisentinel.monitoring.alerts.SystemLocalMinuteProvider
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport
import br.com.marcocardoso.mobisentinel.monitoring.network.NetworkObserver
import br.com.marcocardoso.mobisentinel.monitoring.state.TransitionCoordinator
import br.com.marcocardoso.mobisentinel.preferences.SettingsRepository
import br.com.marcocardoso.mobisentinel.speech.PortugueseMessageFactory
import br.com.marcocardoso.mobisentinel.speech.SpeechController
import kotlinx.coroutines.CancellationException
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
    private val hapticController: HapticController,
    private val stateStore: MonitoringStateStore,
    private val alertPolicy: AlertPolicy = AlertPolicy(),
    private val localMinuteProvider: LocalMinuteProvider = SystemLocalMinuteProvider,
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
            bestEffort { speechController.close() }
            bestEffort { hapticController.close() }
            networkObserver.stop()
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

    fun testHaptics() {
        synchronized(lock) {
            if (started) {
                hapticController.testPatterns()
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
            val settings = currentSettings
            val decision = alertPolicy.decide(
                transition = transition,
                settings = settings,
                minuteOfDay = localMinuteProvider.currentMinuteOfDay(),
            )
            if (decision.narrate) {
                bestEffort { speechController.announce(PortugueseMessageFactory.from(transition)) }
            }
            decision.hapticPattern?.let { pattern ->
                bestEffort { hapticController.play(pattern) }
            }
        },
    )

    private inline fun bestEffort(action: () -> Unit) {
        try {
            action()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            // Alerts and controller teardown must not block independent monitoring work.
        }
    }
}
