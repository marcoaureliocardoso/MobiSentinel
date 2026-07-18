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
    private val controllerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val controllerScope = CoroutineScope(parentScope.coroutineContext + controllerJob)
    private var manualSequence: Job? = null
    private var closed = false

    override val isAvailable: Boolean
        get() = bestEffort { device.isAvailable } ?: false

    override fun play(pattern: HapticPattern) {
        if (closed) return

        manualSequence?.cancel()
        manualSequence = null
        cancelDevice()
        playIfAvailable(pattern)
    }

    override fun testPatterns() {
        if (closed) return

        manualSequence?.cancel()
        cancelDevice()
        playIfAvailable(HapticPattern.LOSS)
        manualSequence = controllerScope.launch {
            delay(LOSS_DURATION_MS + TEST_GAP_MS)
            if (!closed) playIfAvailable(HapticPattern.RECOVERY)
        }
    }

    override fun close() {
        if (closed) return

        closed = true
        manualSequence?.cancel()
        manualSequence = null
        controllerJob.cancel()
        cancelDevice()
    }

    private fun playIfAvailable(pattern: HapticPattern) {
        if (!isAvailable) return
        bestEffort { device.play(pattern) }
    }

    private fun cancelDevice() {
        bestEffort { device.cancel() }
    }

    private inline fun <T> bestEffort(block: () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: RuntimeException) {
        null
    }
}
