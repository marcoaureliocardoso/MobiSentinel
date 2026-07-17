package com.mobisentinel.app.monitoring.network

import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.Transport
import com.mobisentinel.app.monitoring.model.TransportSnapshot

class CellularObservationPolicy(
    private val refreshWifiState: () -> ConnectivityState,
    private val triggerCellularProbe: () -> Unit,
    private val emit: (TransportSnapshot) -> Unit,
) {
    private var lastWifiState: ConnectivityState? = null
    private var lastCellularState: ConnectivityState? = null

    fun reset() {
        lastWifiState = null
        lastCellularState = null
    }

    fun emitInitialWifi() {
        publishWifi(refreshWifiState(), force = true)
    }

    fun onWifiStateChanged(state: ConnectivityState) {
        publishWifi(state, force = false)
    }

    fun onPassiveCellularEvent() {
        triggerCellularProbe()
    }

    fun onAirplaneModeChanged() {
        publishWifi(refreshWifiState(), force = false)
        triggerCellularProbe()
    }

    fun onProbeResult(result: CellularValidationResult) {
        val state = when (result) {
            CellularValidationResult.Validated -> ConnectivityState.CONNECTED
            CellularValidationResult.Unvalidated -> ConnectivityState.CONNECTED_NO_INTERNET
            CellularValidationResult.Unavailable -> ConnectivityState.DISCONNECTED
            is CellularValidationResult.Failure -> null
        } ?: return
        if (state == lastCellularState) return
        lastCellularState = state
        emit(TransportSnapshot(Transport.CELLULAR, state))
    }

    private fun publishWifi(state: ConnectivityState, force: Boolean) {
        if (!force && state == lastWifiState) return
        lastWifiState = state
        emit(TransportSnapshot(Transport.WIFI, state))
    }
}

class ProbeNetworkEventGate<K> {
    private val knownNetworks = linkedSetOf<K>()
    private val networksCreatedDuringProbe = linkedSetOf<K>()
    private var probeRunning = false

    fun seed(id: K) {
        knownNetworks += id
    }

    fun onProbeStarted() {
        probeRunning = true
    }

    fun onProbeFinished() {
        probeRunning = false
    }

    fun onAvailable(id: K): Boolean = observe(id)

    fun onCapabilitiesChanged(id: K): Boolean = observe(id)

    fun onLost(id: K): Boolean {
        knownNetworks -= id
        return !networksCreatedDuringProbe.remove(id)
    }

    fun clear() {
        knownNetworks.clear()
        networksCreatedDuringProbe.clear()
        probeRunning = false
    }

    private fun observe(id: K): Boolean {
        val isNew = knownNetworks.add(id)
        if (probeRunning && isNew) {
            networksCreatedDuringProbe += id
            return false
        }
        return id !in networksCreatedDuringProbe
    }
}
