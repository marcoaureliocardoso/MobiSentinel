package br.com.marcocardoso.mobisentinel.monitoring.network

import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport
import br.com.marcocardoso.mobisentinel.monitoring.model.TransportSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularObservationPolicyTest {
    @Test
    fun probeResultsMapToExistingCellularStatesAndDuplicatesAreSuppressed() {
        val fixture = Fixture()

        fixture.policy.onProbeResult(CellularValidationResult.Validated)
        fixture.policy.onProbeResult(CellularValidationResult.Validated)
        fixture.policy.onProbeResult(CellularValidationResult.Unvalidated)
        fixture.policy.onProbeResult(CellularValidationResult.Unavailable)

        assertEquals(
            listOf(
                snapshot(ConnectivityState.CONNECTED),
                snapshot(ConnectivityState.CONNECTED_NO_INTERNET),
                snapshot(ConnectivityState.DISCONNECTED),
            ),
            fixture.emitted,
        )
    }

    @Test
    fun failurePreservesPriorStateAndFirstFailureKeepsCheckingState() {
        val fixture = Fixture()

        fixture.policy.onProbeResult(CellularValidationResult.Failure(IllegalStateException("first")))
        fixture.policy.onProbeResult(CellularValidationResult.Validated)
        fixture.policy.onProbeResult(CellularValidationResult.Failure(IllegalStateException("later")))

        assertEquals(listOf(snapshot(ConnectivityState.CONNECTED)), fixture.emitted)
    }

    @Test
    fun passiveCellularEventTriggersProbeWithoutPublishingState() {
        val fixture = Fixture()

        fixture.policy.onPassiveCellularEvent()

        assertEquals(1, fixture.probeTriggers)
        assertTrue(fixture.emitted.isEmpty())
    }

    @Test
    fun airplaneModeRefreshesWifiAndTriggersCellularIndependently() {
        val fixture = Fixture(initialWifiState = ConnectivityState.CONNECTED)
        fixture.policy.emitInitialWifi()
        fixture.emitted.clear()

        fixture.policy.onAirplaneModeChanged()

        assertEquals(1, fixture.probeTriggers)
        assertTrue(fixture.emitted.isEmpty())
    }

    @Test
    fun airplaneModePublishesOnlyARealWifiRefreshChange() {
        val fixture = Fixture(initialWifiState = ConnectivityState.CONNECTED)
        fixture.policy.emitInitialWifi()
        fixture.wifiState = ConnectivityState.DISCONNECTED

        fixture.policy.onAirplaneModeChanged()

        assertEquals(
            listOf(
                TransportSnapshot(Transport.WIFI, ConnectivityState.CONNECTED),
                TransportSnapshot(Transport.WIFI, ConnectivityState.DISCONNECTED),
            ),
            fixture.emitted,
        )
        assertEquals(1, fixture.probeTriggers)
    }

    @Test
    fun networkCreatedByActiveProbeDoesNotRetriggerProbeWhenReleased() {
        val gate = ProbeNetworkEventGate<String>()
        gate.onProbeStarted()

        assertFalse(gate.onAvailable("probe-network"))
        assertFalse(gate.onCapabilitiesChanged("probe-network"))
        gate.onProbeFinished()
        assertFalse(gate.onLost("probe-network"))
    }

    @Test
    fun independentPassiveNetworkEventsRemainTriggers() {
        val gate = ProbeNetworkEventGate<String>()
        gate.seed("existing")
        gate.onProbeStarted()

        assertTrue(gate.onCapabilitiesChanged("existing"))
        assertTrue(gate.onLost("existing"))
        gate.onProbeFinished()
        assertTrue(gate.onAvailable("later"))
    }

    private class Fixture(initialWifiState: ConnectivityState = ConnectivityState.DISCONNECTED) {
        var wifiState = initialWifiState
        var probeTriggers = 0
        val emitted = mutableListOf<TransportSnapshot>()
        val policy = CellularObservationPolicy(
            refreshWifiState = { wifiState },
            triggerCellularProbe = { probeTriggers++ },
            emit = emitted::add,
        )
    }

    private companion object {
        fun snapshot(state: ConnectivityState) = TransportSnapshot(Transport.CELLULAR, state)
    }
}
