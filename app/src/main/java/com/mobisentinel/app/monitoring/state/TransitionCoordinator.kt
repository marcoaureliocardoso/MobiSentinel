package com.mobisentinel.app.monitoring.state

import com.mobisentinel.app.monitoring.model.ConfirmedTransition
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.monitoring.model.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TransitionCoordinator(
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val settings: () -> MonitoringSettings,
    private val onConfirmedState: (ConnectivityState) -> Unit,
    private val onTransition: (ConfirmedTransition) -> Unit,
) {
    private var confirmed: ConnectivityState? = null
    private var pending: Job? = null

    fun submit(candidate: ConnectivityState) {
        val current = confirmed
        if (current == null) {
            confirmed = candidate
            onConfirmedState(candidate)
            return
        }
        if (candidate == current) {
            pending?.cancel()
            pending = null
            return
        }

        pending?.cancel()
        val delaySeconds = if (candidate == ConnectivityState.CONNECTED) {
            settings().recoveryDelaySeconds
        } else {
            settings().lossDelaySeconds
        }
        pending = scope.launch {
            delay(delaySeconds * 1_000L)
            val previous = confirmed ?: return@launch
            confirmed = candidate
            onConfirmedState(candidate)
            onTransition(ConfirmedTransition(transport, previous, candidate))
            pending = null
        }
    }

    fun close() {
        pending?.cancel()
        pending = null
    }
}
