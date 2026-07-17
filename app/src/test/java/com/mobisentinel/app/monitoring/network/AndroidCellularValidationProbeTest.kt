package com.mobisentinel.app.monitoring.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidCellularValidationProbeTest {
    @Test
    fun validatedCapabilityCompletesAndUnregistersExactlyOnce() = runTest {
        val requester = FakeRequester()
        val result = async { AndroidCellularValidationProbe(requester).validate() }
        runCurrent()

        requester.callback.onAvailable()
        requester.callback.onCapabilitiesChanged(validated = true)

        assertEquals(CellularValidationResult.Validated, result.await())
        assertEquals(1, requester.unregisterCount)
    }

    @Test
    fun timeoutAfterAvailableUnvalidatedNetworkReturnsUnvalidated() = runTest {
        val requester = FakeRequester()
        val result = async { AndroidCellularValidationProbe(requester).validate() }
        runCurrent()
        requester.callback.onAvailable()
        requester.callback.onCapabilitiesChanged(validated = false)

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(CellularValidationResult.Unvalidated, result.await())
        assertEquals(1, requester.unregisterCount)
    }

    @Test
    fun timeoutWithoutNetworkReturnsUnavailable() = runTest {
        val requester = FakeRequester()
        val result = async { AndroidCellularValidationProbe(requester).validate() }
        runCurrent()

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(CellularValidationResult.Unavailable, result.await())
        assertEquals(1, requester.unregisterCount)
    }

    @Test
    fun platformUnavailableCompletesImmediately() = runTest {
        val requester = FakeRequester()
        val result = async { AndroidCellularValidationProbe(requester).validate() }
        runCurrent()

        requester.callback.onUnavailable()

        assertEquals(CellularValidationResult.Unavailable, result.await())
        assertEquals(1, requester.unregisterCount)
    }

    @Test
    fun requestFailureIsMechanismFailureNotDisconnection() = runTest {
        val failure = SecurityException("denied")
        val requester = FakeRequester(requestFailure = failure)

        val result = AndroidCellularValidationProbe(requester).validate()

        assertEquals(failure, (result as CellularValidationResult.Failure).cause)
        assertEquals(1, requester.unregisterCount)
    }

    @Test
    fun cancellationUnregistersOnceAndPublishesNoLateResult() = runTest {
        val requester = FakeRequester()
        val result = async { AndroidCellularValidationProbe(requester).validate() }
        runCurrent()

        result.cancel()
        runCurrent()
        requester.callback.onCapabilitiesChanged(validated = true)

        assertTrue(result.isCancelled)
        assertEquals(1, requester.unregisterCount)
        assertFalse(result.isCompleted && !result.isCancelled)
    }

    private class FakeRequester(
        private val requestFailure: Throwable? = null,
    ) : CellularNetworkRequester {
        lateinit var callback: CellularNetworkRequester.Callback
        var unregisterCount = 0

        override fun request(callback: CellularNetworkRequester.Callback) {
            this.callback = callback
            requestFailure?.let { throw it }
        }

        override fun unregister(callback: CellularNetworkRequester.Callback) {
            assertEquals(this.callback, callback)
            unregisterCount++
        }
    }
}
