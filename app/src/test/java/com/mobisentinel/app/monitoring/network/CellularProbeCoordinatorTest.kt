package com.mobisentinel.app.monitoring.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CellularProbeCoordinatorTest {
    @Test
    fun startRunsImmediatelyAndPeriodBeginsAfterCompletion() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.start()
        runCurrent()
        assertEquals(1, fixture.probe.callCount)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, fixture.probe.callCount)

        fixture.probe.completeNext(CellularValidationResult.Validated)
        runCurrent()
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(1, fixture.probe.callCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fixture.probe.callCount)
        fixture.coordinator.stop()
    }

    @Test
    fun triggersDuringProbeCoalesceIntoOneRerun() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.start()
        runCurrent()

        repeat(5) { fixture.coordinator.trigger() }
        fixture.probe.completeNext(CellularValidationResult.Validated)
        runCurrent()

        assertEquals(2, fixture.probe.callCount)
        assertEquals(1, fixture.probe.maxConcurrent)
        fixture.coordinator.stop()
    }

    @Test
    fun idleEventCancelsPeriodicTimerAndRunsImmediately() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.start()
        runCurrent()
        fixture.probe.completeNext(CellularValidationResult.Validated)
        runCurrent()

        advanceTimeBy(10_000)
        fixture.coordinator.trigger()
        runCurrent()

        assertEquals(2, fixture.probe.callCount)
        fixture.coordinator.stop()
    }

    @Test
    fun stopCancelsTimerAndActiveProbeWithoutPublishingLateResult() = runTest {
        val fixture = Fixture(this)
        fixture.coordinator.start()
        runCurrent()

        fixture.coordinator.stop()
        fixture.probe.completeNext(CellularValidationResult.Validated)
        advanceTimeBy(120_000)
        runCurrent()

        assertTrue(fixture.results.isEmpty())
        assertEquals(listOf(true, false), fixture.runningChanges)
        assertEquals(1, fixture.probe.callCount)
    }

    @Test
    fun unexpectedProbeExceptionBecomesFailureAndSchedulerContinues() = runTest {
        val fixture = Fixture(this)
        fixture.probe.failNext = IllegalStateException("boom")

        fixture.coordinator.start()
        runCurrent()

        assertTrue(fixture.results.single() is CellularValidationResult.Failure)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, fixture.probe.callCount)
        fixture.coordinator.stop()
    }

    private class Fixture(scope: CoroutineScope) {
        val probe = ControlledProbe()
        val results = mutableListOf<CellularValidationResult>()
        val runningChanges = mutableListOf<Boolean>()
        val coordinator = CellularProbeCoordinator(
            scope = scope,
            probe = probe,
            onProbeRunningChanged = runningChanges::add,
            onResult = results::add,
        )
    }

    private class ControlledProbe : CellularValidationProbe {
        private val completions =
            Channel<CompletableDeferred<CellularValidationResult>>(Channel.UNLIMITED)
        var callCount = 0
        var concurrent = 0
        var maxConcurrent = 0
        var failNext: Throwable? = null

        override suspend fun validate(): CellularValidationResult {
            callCount++
            failNext?.let { failure ->
                failNext = null
                throw failure
            }
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            val completion = CompletableDeferred<CellularValidationResult>()
            completions.send(completion)
            return try {
                completion.await()
            } finally {
                concurrent--
            }
        }

        suspend fun completeNext(result: CellularValidationResult) {
            completions.receive().complete(result)
        }
    }
}
