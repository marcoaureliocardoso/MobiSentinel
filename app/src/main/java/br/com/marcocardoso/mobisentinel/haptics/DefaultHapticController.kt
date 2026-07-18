package br.com.marcocardoso.mobisentinel.haptics

import br.com.marcocardoso.mobisentinel.monitoring.alerts.HapticPattern
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val LOSS_DURATION_MS = 360L
const val TEST_GAP_MS = 800L

internal class DefaultHapticController(
    parentScope: CoroutineScope,
    private val device: HapticDevice,
) : HapticController {
    private val lock = Any()
    private val controllerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val controllerScope = CoroutineScope(parentScope.coroutineContext + controllerJob)
    private var manualSequence: Job? = null
    private var generation = 0L
    private var closed = false

    override val isAvailable: Boolean
        get() = synchronized(lock) { isAvailableLocked() }

    override fun play(pattern: HapticPattern) {
        synchronized(lock) {
            if (closed) return

            generation += 1
            manualSequence?.cancel()
            manualSequence = null
            cancelDeviceLocked()
            playIfAvailableLocked(pattern)
        }
    }

    override fun testPatterns() {
        synchronized(lock) {
            if (closed) return

            val sequenceGeneration = generation + 1
            generation = sequenceGeneration
            manualSequence?.cancel()
            cancelDeviceLocked()
            playIfAvailableLocked(HapticPattern.LOSS)
            manualSequence = controllerScope.launch {
                delay(LOSS_DURATION_MS + TEST_GAP_MS)
                recoverIfCurrent(sequenceGeneration)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return

            closed = true
            generation += 1
            manualSequence?.cancel()
            manualSequence = null
            controllerJob.cancel()
            cancelDeviceLocked()
        }
    }

    private fun recoverIfCurrent(sequenceGeneration: Long) {
        synchronized(lock) {
            if (closed || sequenceGeneration != generation) return

            playIfAvailableLocked(HapticPattern.RECOVERY)
            manualSequence = null
        }
    }

    private fun playIfAvailableLocked(pattern: HapticPattern) {
        if (!isAvailableLocked()) return
        bestEffort { device.play(pattern) }
    }

    private fun cancelDeviceLocked() {
        bestEffort { device.cancel() }
    }

    private fun isAvailableLocked(): Boolean = bestEffort { device.isAvailable } ?: false

    private inline fun <T> bestEffort(block: () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: RuntimeException) {
        null
    }
}
