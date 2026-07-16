package com.mobisentinel.app.monitoring.state

import com.mobisentinel.app.monitoring.model.ConfirmedTransition
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.monitoring.model.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransitionCoordinatorTest {
    @Test
    fun firstStateBecomesBaselineWithoutTransition() = runTest {
        val fixture = Fixture(this)

        fixture.coordinator.submit(ConnectivityState.CONNECTED)
        runCurrent()

        assertEquals(listOf(ConnectivityState.CONNECTED), fixture.confirmedStates)
        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun lossIsConfirmedOnlyAfterConfiguredDelay() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.submit(ConnectivityState.CONNECTED)

        fixture.coordinator.submit(ConnectivityState.DISCONNECTED)
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(fixture.transitions.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(
                ConfirmedTransition(
                    Transport.WIFI,
                    ConnectivityState.CONNECTED,
                    ConnectivityState.DISCONNECTED,
                ),
            ),
            fixture.transitions,
        )
    }

    @Test
    fun recoveryUsesRecoveryDelay() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.submit(ConnectivityState.DISCONNECTED)

        fixture.coordinator.submit(ConnectivityState.CONNECTED)
        advanceTimeBy(1_999)
        runCurrent()
        assertTrue(fixture.transitions.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(ConnectivityState.CONNECTED, fixture.transitions.single().current)
    }

    @Test
    fun returningToConfirmedStateCancelsPendingChange() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.submit(ConnectivityState.CONNECTED)
        fixture.coordinator.submit(ConnectivityState.DISCONNECTED)
        advanceTimeBy(4_000)

        fixture.coordinator.submit(ConnectivityState.CONNECTED)
        advanceUntilIdle()

        assertEquals(listOf(ConnectivityState.CONNECTED), fixture.confirmedStates)
        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun newerCandidateReplacesOlderCandidate() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.submit(ConnectivityState.CONNECTED)
        fixture.coordinator.submit(ConnectivityState.DISCONNECTED)
        advanceTimeBy(1_000)

        fixture.coordinator.submit(ConnectivityState.CONNECTED_NO_INTERNET)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(1, fixture.transitions.size)
        assertEquals(ConnectivityState.CONNECTED_NO_INTERNET, fixture.transitions.single().current)
    }

    @Test
    fun zeroDelayConfirmsOnTheNextSchedulerTurn() = runTest {
        val fixture = Fixture(this)
        fixture.settings = MonitoringSettings(lossDelaySeconds = 0)
        fixture.coordinator.submit(ConnectivityState.CONNECTED)

        fixture.coordinator.submit(ConnectivityState.DISCONNECTED)
        assertTrue(fixture.transitions.isEmpty())

        runCurrent()
        assertEquals(ConnectivityState.DISCONNECTED, fixture.transitions.single().current)
    }

    @Test
    fun closeCancelsPendingChange() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.submit(ConnectivityState.CONNECTED)
        fixture.coordinator.submit(ConnectivityState.DISCONNECTED)

        fixture.coordinator.close()
        advanceUntilIdle()

        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun updatedSettingsApplyToNewCandidates() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.submit(ConnectivityState.CONNECTED)
        fixture.settings = fixture.settings.copy(lossDelaySeconds = 1)

        fixture.coordinator.submit(ConnectivityState.DISCONNECTED)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(ConnectivityState.DISCONNECTED, fixture.transitions.single().current)
    }

    @Test
    fun narrationSettingsAreTransportSpecific() {
        val settings = MonitoringSettings(narrateWifi = false, narrateCellular = true)

        assertEquals(false, settings.narrationEnabled(Transport.WIFI))
        assertEquals(true, settings.narrationEnabled(Transport.CELLULAR))
    }

    @Test
    fun confirmationDelaysMustStayWithinSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(lossDelaySeconds = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(recoveryDelaySeconds = 61)
        }
    }

    private class Fixture(scope: CoroutineScope) {
        var settings = MonitoringSettings()
        val confirmedStates = mutableListOf<ConnectivityState>()
        val transitions = mutableListOf<ConfirmedTransition>()
        val coordinator = TransitionCoordinator(
            transport = Transport.WIFI,
            scope = scope,
            settings = { settings },
            onConfirmedState = confirmedStates::add,
            onTransition = transitions::add,
        )
    }
}
