package br.com.marcocardoso.mobisentinel.monitoring.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CellularProbeCoordinator(
    private val scope: CoroutineScope,
    private val probe: CellularValidationProbe,
    private val intervalMillis: Long = 60_000L,
    private val onProbeRunningChanged: (Boolean) -> Unit = {},
    private val onResult: (CellularValidationResult) -> Unit,
) {
    private val lock = Any()
    private var started = false
    private var rerunPending = false
    private var probeJob: Job? = null
    private var timerJob: Job? = null

    init {
        require(intervalMillis > 0)
    }

    fun start() = synchronized(lock) {
        if (started) return@synchronized
        started = true
        launchProbeLocked()
    }

    fun trigger() = synchronized(lock) {
        if (!started) return@synchronized
        timerJob?.cancel()
        timerJob = null
        if (probeJob?.isActive == true) {
            rerunPending = true
        } else {
            launchProbeLocked()
        }
    }

    fun stop() = synchronized(lock) {
        if (!started) return@synchronized
        started = false
        rerunPending = false
        timerJob?.cancel()
        timerJob = null
        probeJob?.cancel()
        probeJob = null
    }

    private fun launchProbeLocked() {
        timerJob?.cancel()
        timerJob = null
        probeJob = scope.launch {
            onProbeRunningChanged(true)
            val result = try {
                probe.validate()
            } catch (_: CancellationException) {
                return@launch
            } catch (failure: Throwable) {
                CellularValidationResult.Failure(failure)
            } finally {
                onProbeRunningChanged(false)
            }

            val rerunAfterResult = synchronized(lock) {
                probeJob = null
                if (!started) return@launch
                rerunPending.also { rerunPending = false }
            }
            onResult(result)

            synchronized(lock) {
                if (!started || probeJob?.isActive == true) return@synchronized
                if (rerunAfterResult) {
                    launchProbeLocked()
                } else {
                    scheduleNextLocked()
                }
            }
        }
    }

    private fun scheduleNextLocked() {
        timerJob = scope.launch {
            delay(intervalMillis)
            synchronized(lock) {
                timerJob = null
                if (started && probeJob?.isActive != true) {
                    launchProbeLocked()
                }
            }
        }
    }
}
