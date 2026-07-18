package br.com.marcocardoso.mobisentinel.haptics

import br.com.marcocardoso.mobisentinel.monitoring.alerts.HapticPattern
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        advanceTimeBy(400)

        fixture.controller.testPatterns()
        runCurrent()
        advanceTimeBy(LOSS_DURATION_MS + TEST_GAP_MS - 400)
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS, HapticPattern.LOSS), fixture.device.playedPatterns)

        advanceTimeBy(399)
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
    fun concurrentManualTestsLeaveOnlyTheLaterSequenceToRecover() = runTest {
        val firstLossEntered = CountDownLatch(1)
        val secondLossEntered = CountDownLatch(1)
        val releaseFirstLoss = CountDownLatch(1)
        val lossCount = AtomicInteger()
        val workerFailure = AtomicReference<Throwable?>()
        val device = FakeHapticDevice(
            available = true,
            onPlay = { pattern ->
                if (pattern == HapticPattern.LOSS && lossCount.incrementAndGet() == 1) {
                    firstLossEntered.countDown()
                    assertTrue(releaseFirstLoss.await(2, TimeUnit.SECONDS))
                } else if (pattern == HapticPattern.LOSS) {
                    secondLossEntered.countDown()
                }
            },
        )
        val controller = DefaultHapticController(this, device)

        val first = startWorker("first-manual-test", workerFailure) { controller.testPatterns() }
        assertTrue(firstLossEntered.await(2, TimeUnit.SECONDS))
        val second = startWorker("second-manual-test", workerFailure) { controller.testPatterns() }

        assertFalse(secondLossEntered.await(150, TimeUnit.MILLISECONDS))
        releaseFirstLoss.countDown()
        assertTrue(secondLossEntered.await(2, TimeUnit.SECONDS))
        first.join(2_000)
        second.join(2_000)
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        throwWorkerFailure(workerFailure)

        runCurrent()
        advanceTimeBy(LOSS_DURATION_MS + TEST_GAP_MS)
        runCurrent()

        assertEquals(
            listOf(HapticPattern.LOSS, HapticPattern.LOSS, HapticPattern.RECOVERY),
            device.playedPatterns,
        )
        controller.close()
    }

    @Test
    fun closeWaitsForConcurrentManualTestAndPreventsItsRecovery() = runTest {
        val playEntered = CountDownLatch(1)
        val releasePlay = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val workerFailure = AtomicReference<Throwable?>()
        val device = FakeHapticDevice(
            available = true,
            onPlay = { pattern ->
                if (pattern == HapticPattern.LOSS) {
                    playEntered.countDown()
                    assertTrue(releasePlay.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val controller = DefaultHapticController(this, device)

        val manualTest = startWorker("manual-test", workerFailure) { controller.testPatterns() }
        assertTrue(playEntered.await(2, TimeUnit.SECONDS))
        val close = startWorker("close", workerFailure) {
            controller.close()
            closeReturned.countDown()
        }

        assertFalse(closeReturned.await(150, TimeUnit.MILLISECONDS))
        releasePlay.countDown()
        manualTest.join(2_000)
        close.join(2_000)
        assertFalse(manualTest.isAlive)
        assertFalse(close.isAlive)
        throwWorkerFailure(workerFailure)

        runCurrent()
        advanceTimeBy(LOSS_DURATION_MS + TEST_GAP_MS)
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS), device.playedPatterns)
    }

    @Test
    fun closeWaitsForConcurrentAutomaticPlay() = runTest {
        val playEntered = CountDownLatch(1)
        val releasePlay = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val workerFailure = AtomicReference<Throwable?>()
        val device = FakeHapticDevice(
            available = true,
            onPlay = { pattern ->
                if (pattern == HapticPattern.RECOVERY) {
                    playEntered.countDown()
                    assertTrue(releasePlay.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val controller = DefaultHapticController(this, device)

        val automaticPlay = startWorker("automatic-play", workerFailure) {
            controller.play(HapticPattern.RECOVERY)
        }
        assertTrue(playEntered.await(2, TimeUnit.SECONDS))
        val close = startWorker("close", workerFailure) {
            controller.close()
            closeReturned.countDown()
        }

        assertFalse(closeReturned.await(150, TimeUnit.MILLISECONDS))
        releasePlay.countDown()
        automaticPlay.join(2_000)
        close.join(2_000)
        assertFalse(automaticPlay.isAlive)
        assertFalse(close.isAlive)
        throwWorkerFailure(workerFailure)
        assertEquals(listOf(HapticPattern.RECOVERY), device.playedPatterns)
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
    fun cancellationExceptionFromPlayEscapes() = runTest {
        val fixture = Fixture(this)
        fixture.device.playFailure = CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            fixture.controller.play(HapticPattern.LOSS)
        }

        fixture.controller.close()
    }

    @Test
    fun errorFromPlayEscapes() = runTest {
        val fixture = Fixture(this)
        fixture.device.playFailure = AssertionError("programming error")

        assertThrows(AssertionError::class.java) {
            fixture.controller.play(HapticPattern.LOSS)
        }

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

    private fun startWorker(
        name: String,
        failure: AtomicReference<Throwable?>,
        action: () -> Unit,
    ): Thread = thread(name = name) {
        try {
            action()
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
        }
    }

    private fun throwWorkerFailure(failure: AtomicReference<Throwable?>) {
        failure.get()?.let { throw it }
    }

    private class FakeHapticDevice(
        private val available: Boolean,
        private val onPlay: ((HapticPattern) -> Unit)? = null,
    ) : HapticDevice {
        var availabilityFailure: Throwable? = null
        var playFailure: Throwable? = null
        var cancelFailure: Throwable? = null
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
            onPlay?.invoke(pattern)
            playFailure?.let { throw it }
            playedPatterns += pattern
        }

        override fun cancel() {
            cancelAttempts += 1
            cancelFailure?.let { throw it }
        }
    }
}
