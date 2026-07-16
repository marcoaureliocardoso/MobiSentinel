package com.mobisentinel.app.monitoring.network

import com.mobisentinel.app.monitoring.model.ConnectivityState
import org.junit.Assert.assertEquals
import org.junit.Test

class TransportNetworkTrackerTest {
    @Test
    fun emptyTrackerIsDisconnected() {
        val tracker = TransportNetworkTracker<String>()

        assertEquals(ConnectivityState.DISCONNECTED, tracker.clear())
    }

    @Test
    fun availableUnvalidatedNetworkHasNoValidatedInternet() {
        val tracker = TransportNetworkTracker<String>()

        val state = tracker.onAvailable("network-1")

        assertEquals(ConnectivityState.CONNECTED_NO_INTERNET, state)
    }

    @Test
    fun validatedCapabilityBecomesConnected() {
        val tracker = TransportNetworkTracker<String>()
        tracker.onAvailable("network-1")

        val state = tracker.onCapabilitiesChanged("network-1", validated = true)

        assertEquals(ConnectivityState.CONNECTED, state)
    }

    @Test
    fun losingOnlyNetworkDisconnects() {
        val tracker = TransportNetworkTracker<String>()
        tracker.onAvailable("network-1")

        val state = tracker.onLost("network-1")

        assertEquals(ConnectivityState.DISCONNECTED, state)
    }

    @Test
    fun validatedNetworkWinsOverUnvalidatedNetwork() {
        val tracker = TransportNetworkTracker<String>()
        tracker.onAvailable("unvalidated")

        val state = tracker.onCapabilitiesChanged("validated", validated = true)

        assertEquals(ConnectivityState.CONNECTED, state)
    }

    @Test
    fun losingOneOfTwoNetworksRetainsRemainingAggregateState() {
        val tracker = TransportNetworkTracker<String>()
        tracker.onCapabilitiesChanged("validated", validated = true)
        tracker.onAvailable("unvalidated")

        val state = tracker.onLost("validated")

        assertEquals(ConnectivityState.CONNECTED_NO_INTERNET, state)
    }
}
