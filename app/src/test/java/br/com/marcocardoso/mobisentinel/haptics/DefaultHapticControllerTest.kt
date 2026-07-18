package br.com.marcocardoso.mobisentinel.haptics

import br.com.marcocardoso.mobisentinel.monitoring.alerts.HapticPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHapticControllerTest {
    @Test
    fun availabilityReflectsTheDevice() = runTest {
        val unavailable = FakeHapticDevice(available = false)
        val available = FakeHapticDevice(available = true)

        val unavailableController = DefaultHapticController(this, unavailable)
        val availableController = DefaultHapticController(this, available)

        assertFalse(unavailableController.isAvailable)
        assertTrue(availableController.isAvailable)
        unavailableController.close()
        availableController.close()
    }

    @Test
    fun manualTestPlaysLossImmediatelyAndRecoveryAfterTheFullGap() = runTest {
        val fixture = Fixture(this)

        fixture.controller.testPatterns()

        assertEquals(listOf(HapticPattern.LOSS), fixture.device.playedPatterns)
        runCurrent()
        advanceTimeBy(LOSS_DURATION_MS + TEST_GAP_MS - 1)
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS), fixture.device.playedPatterns)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(HapticPattern.LOSS, HapticPattern.RECOVERY),
            fixture.device.playedPatterns,
        )
        fixture.controller.close()
    }

    @Test
    fun automaticPlayCancelsManualRecoveryAndTheCurrentEffect() = runTest {
        val fixture = Fixture(this)
        fixture.controller.testPatterns()
        runCurrent()

        fixture.controller.play(HapticPattern.RECOVERY)
        advanceTimeBy(LOSS_DURATION_MS + TEST_GAP_MS)
        runCurrent()

        assertEquals(
            listOf(HapticPattern.LOSS, HapticPattern.RECOVERY),
            fixture.device.playedPatterns,
        )
        assertEquals(2, fixture.device.cancelAttempts)
        fixture.controller.close()
    }

    @Test
    fun newerManualTestReplacesTheEarlierTest() = runTest {
        val fixture = Fixture(this)
        fixture.controller.testPatterns()
        runCurrent()

        fixture.controller.testPatterns()
        runCurrent()
        advanceTimeBy(LOSS_DURATION_MS + TEST_GAP_MS - 1)
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS, HapticPattern.LOSS), fixture.device.playedPatterns)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(HapticPattern.LOSS, HapticPattern.LOSS, HapticPattern.RECOVERY),
            fixture.device.playedPatterns,
        )
        fixture.controller.close()
    }

    @Test
    fun unavailableDeviceReceivesNoPlayAttempts() = runTest {
        val fixture = Fixture(this, available = false)

        fixture.controller.play(HapticPattern.LOSS)
        fixture.controller.testPatterns()

        assertTrue(fixture.device.playAttempts.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun securityExceptionFromPlayDoesNotEscape() = runTest {
        val fixture = Fixture(this)
        fixture.device.playFailure = SecurityException("VIBRATE denied")

        fixture.controller.play(HapticPattern.LOSS)

        assertEquals(listOf(HapticPattern.LOSS), fixture.device.playAttempts)
        assertTrue(fixture.device.playedPatterns.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun runtimeFailuresFromAvailabilityAndCancelDoNotEscape() = runTest {
        val fixture = Fixture(this)
        fixture.device.availabilityFailure = IllegalStateException("vibrator unavailable")

        assertFalse(fixture.controller.isAvailable)

        fixture.device.availabilityFailure = null
        fixture.device.cancelFailure = IllegalStateException("vibrator unavailable")
        fixture.controller.play(HapticPattern.LOSS)

        assertEquals(listOf(HapticPattern.LOSS), fixture.device.playedPatterns)
        fixture.controller.close()
    }

    @Test
    fun closeCancelsPendingManualRecoveryAndTheDevice() = runTest {
        val fixture = Fixture(this)
        fixture.controller.testPatterns()
        runCurrent()

        fixture.controller.close()
        advanceTimeBy(LOSS_DURATION_MS + TEST_GAP_MS)
        runCurrent()

        assertEquals(listOf(HapticPattern.LOSS), fixture.device.playedPatterns)
        assertEquals(2, fixture.device.cancelAttempts)
    }

    private class Fixture(scope: CoroutineScope, available: Boolean = true) {
        val device = FakeHapticDevice(available)
        val controller = DefaultHapticController(scope, device)
    }

    private class FakeHapticDevice(
        private val available: Boolean,
    ) : HapticDevice {
        var availabilityFailure: RuntimeException? = null
        var playFailure: RuntimeException? = null
        var cancelFailure: RuntimeException? = null
        val playAttempts = mutableListOf<HapticPattern>()
        val playedPatterns = mutableListOf<HapticPattern>()
        var cancelAttempts = 0

        override val isAvailable: Boolean
            get() {
                availabilityFailure?.let { throw it }
                return available
            }

        override fun play(pattern: HapticPattern) {
            playAttempts += pattern
            playFailure?.let { throw it }
            playedPatterns += pattern
        }

        override fun cancel() {
            cancelAttempts += 1
            cancelFailure?.let { throw it }
        }
    }
}
