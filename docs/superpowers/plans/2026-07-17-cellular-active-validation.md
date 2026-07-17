# Cellular Active Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MobiSentinel actively validate cellular connectivity independently of Wi-Fi, while keeping airplane mode as a trigger rather than connectivity evidence.

**Architecture:** A suspendable `CellularValidationProbe` requests a temporary Android cellular network and returns one of four platform-neutral results. A single-flight coordinator runs that probe at startup, 60 seconds after each completion, and after coalesced events; `AndroidNetworkObserver` keeps Wi-Fi passive, routes cellular events only to the probe, and publishes only probe-derived cellular states.

**Tech Stack:** Kotlin 2.3.21, Android SDK 26–36, `ConnectivityManager`, `TelephonyManager`, Kotlin Coroutines 1.10.2, JUnit 4.13.2, AndroidX Test, Gradle 8.13.

## Global Constraints

- Application ID and namespace remain `com.mobisentinel.app`.
- Minimum Android version remains API 26; compile and target SDK remain 36.
- Request cellular with `TRANSPORT_CELLULAR` plus `NET_CAPABILITY_INTERNET`; observe `NET_CAPABILITY_VALIDATED` in callback capabilities and never require it in the request.
- Each probe times out after exactly 15,000 milliseconds.
- Run one probe immediately at observer start and the next periodic probe 60,000 milliseconds after the preceding probe completes.
- Permit at most one active probe and one pending rerun; coalesce additional triggers.
- `Validated` maps to `CONNECTED`, `Unvalidated` to `CONNECTED_NO_INTERNET`, `Unavailable` to `DISCONNECTED`, and `Failure` publishes no state.
- Cellular passive callbacks trigger validation but never publish cellular state.
- Airplane mode never determines either transport state; it independently refreshes Wi-Fi and triggers a cellular probe.
- Preserve the last confirmed state during later probes. Keep `Verificando` only until the first non-failure result.
- Preserve the existing `TransitionCoordinator`, debounce intervals, messages, notification copy, and narration policy.
- Add `CHANGE_NETWORK_STATE`; do not add `INTERNET`, location, phone, or other permissions.
- Do not send ICMP, HTTPS, TCP, DNS, or other application traffic to external servers.
- Use `TelephonyCallback.UserMobileDataStateListener` on API 31+, `PhoneStateListener.LISTEN_USER_MOBILE_DATA_STATE` on API 28–30, and periodic/connectivity/airplane triggers on API 26–27.
- Every new domain behavior follows red-green-refactor TDD and each task ends in an independently reviewable commit.
- Do not edit `appVersion`, `.release-please-manifest.json`, or `CHANGELOG.md`; the `fix:` implementation commits feed the existing Release Please automation and therefore produce a patch release.

## File Map

- Create `app/src/main/java/com/mobisentinel/app/monitoring/network/CellularValidationProbe.kt`: result and probe contracts.
- Create `app/src/main/java/com/mobisentinel/app/monitoring/network/CellularObservationPolicy.kt`: result mapping, deduplicated publishing, airplane routing, and suppression of temporary probe-network callbacks.
- Create `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbe.kt`: cancellable timeout logic and Android `requestNetwork` adapter.
- Create `app/src/main/java/com/mobisentinel/app/monitoring/network/CellularProbeCoordinator.kt`: startup, periodic, event, coalescing, and stop lifecycle.
- Modify `app/src/main/java/com/mobisentinel/app/monitoring/network/TransportNetworkTracker.kt`: atomic replacement used by explicit Wi-Fi refresh.
- Modify `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkObserver.kt`: Wi-Fi observation, cellular triggers, airplane receiver, mobile-data listeners, and probe integration.
- Modify `app/src/main/java/com/mobisentinel/app/MobiSentinelApplication.kt`: supply context, telephony service, coroutine scope, and active probe.
- Modify `app/src/main/AndroidManifest.xml`: declare `CHANGE_NETWORK_STATE` only.
- Create `app/src/test/java/com/mobisentinel/app/monitoring/network/CellularObservationPolicyTest.kt`: mapping and independent-event policy.
- Create `app/src/test/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbeTest.kt`: validated, unvalidated, unavailable, error, timeout, cancellation, and single cleanup.
- Create `app/src/test/java/com/mobisentinel/app/monitoring/network/CellularProbeCoordinatorTest.kt`: startup, timing, coalescing, single-flight, and stop.
- Modify `app/src/test/java/com/mobisentinel/app/monitoring/network/TransportNetworkTrackerTest.kt`: explicit refresh replacement behavior.
- Modify `docs/superpowers/specs/2026-07-16-mobisentinel-design.md`: replace the old airplane-mode inference.
- Modify `docs/testing/manual-test-matrix.md`: record the new emulator and physical gates without claiming unexecuted evidence.
- Modify `README.md`: document active Android validation, permission, privacy, interval, and physical limitation.

---

### Task 1: Cellular Observation Policy and Independent Event Routing

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/CellularValidationProbe.kt`
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/CellularObservationPolicy.kt`
- Test: `app/src/test/java/com/mobisentinel/app/monitoring/network/CellularObservationPolicyTest.kt`

**Interfaces:**
- Consumes: existing `ConnectivityState`, `Transport`, and `TransportSnapshot`.
- Produces: `CellularValidationResult`, `CellularValidationProbe.validate()`, `CellularObservationPolicy`, and `ProbeNetworkEventGate<K>`.

- [ ] **Step 1: Write the failing policy tests**

Create `CellularObservationPolicyTest.kt` with these cases:

```kotlin
package com.mobisentinel.app.monitoring.network

import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.Transport
import com.mobisentinel.app.monitoring.model.TransportSnapshot
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
```

- [ ] **Step 2: Run the policy test and verify the missing-type failure**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Marco\AppData\Local\Android\Sdk'
.\gradlew.bat testDebugUnitTest --tests '*.CellularObservationPolicyTest'
```

Expected: `FAILURE` with unresolved references to `CellularObservationPolicy`, `CellularValidationResult`, and `ProbeNetworkEventGate`.

- [ ] **Step 3: Implement the probe contract and observation policy**

Create `CellularValidationProbe.kt`:

```kotlin
package com.mobisentinel.app.monitoring.network

sealed interface CellularValidationResult {
    data object Validated : CellularValidationResult
    data object Unvalidated : CellularValidationResult
    data object Unavailable : CellularValidationResult
    data class Failure(val cause: Throwable) : CellularValidationResult
}

fun interface CellularValidationProbe {
    suspend fun validate(): CellularValidationResult
}
```

Create `CellularObservationPolicy.kt`:

```kotlin
package com.mobisentinel.app.monitoring.network

import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.Transport
import com.mobisentinel.app.monitoring.model.TransportSnapshot

class CellularObservationPolicy(
    private val refreshWifiState: () -> ConnectivityState,
    private val triggerCellularProbe: () -> Unit,
    private val emit: (TransportSnapshot) -> Unit,
) {
    private var lastWifiState: ConnectivityState? = null
    private var lastCellularState: ConnectivityState? = null

    fun reset() {
        lastWifiState = null
        lastCellularState = null
    }

    fun emitInitialWifi() {
        publishWifi(refreshWifiState(), force = true)
    }

    fun onWifiStateChanged(state: ConnectivityState) {
        publishWifi(state, force = false)
    }

    fun onPassiveCellularEvent() {
        triggerCellularProbe()
    }

    fun onAirplaneModeChanged() {
        publishWifi(refreshWifiState(), force = false)
        triggerCellularProbe()
    }

    fun onProbeResult(result: CellularValidationResult) {
        val state = when (result) {
            CellularValidationResult.Validated -> ConnectivityState.CONNECTED
            CellularValidationResult.Unvalidated -> ConnectivityState.CONNECTED_NO_INTERNET
            CellularValidationResult.Unavailable -> ConnectivityState.DISCONNECTED
            is CellularValidationResult.Failure -> null
        } ?: return
        if (state == lastCellularState) return
        lastCellularState = state
        emit(TransportSnapshot(Transport.CELLULAR, state))
    }

    private fun publishWifi(state: ConnectivityState, force: Boolean) {
        if (!force && state == lastWifiState) return
        lastWifiState = state
        emit(TransportSnapshot(Transport.WIFI, state))
    }
}

class ProbeNetworkEventGate<K> {
    private val knownNetworks = linkedSetOf<K>()
    private val networksCreatedDuringProbe = linkedSetOf<K>()
    private var probeRunning = false

    fun seed(id: K) {
        knownNetworks += id
    }

    fun onProbeStarted() {
        probeRunning = true
    }

    fun onProbeFinished() {
        probeRunning = false
    }

    fun onAvailable(id: K): Boolean = observe(id)

    fun onCapabilitiesChanged(id: K): Boolean = observe(id)

    fun onLost(id: K): Boolean {
        knownNetworks -= id
        return !networksCreatedDuringProbe.remove(id)
    }

    fun clear() {
        knownNetworks.clear()
        networksCreatedDuringProbe.clear()
        probeRunning = false
    }

    private fun observe(id: K): Boolean {
        val isNew = knownNetworks.add(id)
        if (probeRunning && isNew) {
            networksCreatedDuringProbe += id
            return false
        }
        return id !in networksCreatedDuringProbe
    }
}
```

- [ ] **Step 4: Run the policy tests and the existing state tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*.CellularObservationPolicyTest' --tests '*.TransitionCoordinatorTest'
```

Expected: `BUILD SUCCESSFUL`; the new mapping/event tests and existing debounce tests pass.

- [ ] **Step 5: Commit the policy increment**

```powershell
git add app/src/main/java/com/mobisentinel/app/monitoring/network/CellularValidationProbe.kt app/src/main/java/com/mobisentinel/app/monitoring/network/CellularObservationPolicy.kt app/src/test/java/com/mobisentinel/app/monitoring/network/CellularObservationPolicyTest.kt
git commit -m "fix: define cellular validation policy"
```

### Task 2: Cancellable Android Cellular Validation Probe

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbe.kt`
- Test: `app/src/test/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbeTest.kt`

**Interfaces:**
- Consumes: `CellularValidationProbe`, `CellularValidationResult`, `ConnectivityManager`, and coroutine cancellation.
- Produces: `CellularNetworkRequester`, `AndroidCellularNetworkRequester`, and `AndroidCellularValidationProbe(timeoutMillis = 15_000L)`.

- [ ] **Step 1: Write failing tests for every terminal path and cleanup**

Create `AndroidCellularValidationProbeTest.kt` with a fake requester that stores one callback and counts `request` and `unregister` calls. Cover these exact cases:

```kotlin
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
```

- [ ] **Step 2: Run the probe test and verify missing implementations**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*.AndroidCellularValidationProbeTest'
```

Expected: `FAILURE` with unresolved references to `CellularNetworkRequester` and `AndroidCellularValidationProbe`.

- [ ] **Step 3: Implement the platform-neutral requester bridge and timeout logic**

Create `AndroidCellularValidationProbe.kt` with these exact public contracts and behavior:

```kotlin
package com.mobisentinel.app.monitoring.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface CellularNetworkRequester {
    interface Callback {
        fun onAvailable()
        fun onCapabilitiesChanged(validated: Boolean)
        fun onUnavailable()
    }

    fun request(callback: Callback)
    fun unregister(callback: Callback)
}

class AndroidCellularNetworkRequester(
    private val connectivityManager: ConnectivityManager,
) : CellularNetworkRequester {
    private val callbacks = ConcurrentHashMap<CellularNetworkRequester.Callback, ConnectivityManager.NetworkCallback>()
    private val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    override fun request(callback: CellularNetworkRequester.Callback) {
        val platformCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = callback.onAvailable()

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                callback.onCapabilitiesChanged(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }

            override fun onUnavailable() = callback.onUnavailable()
        }
        check(callbacks.putIfAbsent(callback, platformCallback) == null)
        try {
            connectivityManager.requestNetwork(request, platformCallback)
        } catch (failure: Throwable) {
            callbacks.remove(callback, platformCallback)
            throw failure
        }
    }

    override fun unregister(callback: CellularNetworkRequester.Callback) {
        val platformCallback = callbacks.remove(callback) ?: return
        try {
            connectivityManager.unregisterNetworkCallback(platformCallback)
        } catch (_: IllegalArgumentException) {
            // Registration may have failed or Android may already have removed it.
        }
    }
}

class AndroidCellularValidationProbe(
    private val requester: CellularNetworkRequester,
    private val timeoutMillis: Long = 15_000L,
) : CellularValidationProbe {
    init {
        require(timeoutMillis > 0)
    }

    override suspend fun validate(): CellularValidationResult {
        val networkSeen = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        var callback: CellularNetworkRequester.Callback? = null

        fun releaseOnce() {
            if (released.compareAndSet(false, true)) {
                val registeredCallback = callback ?: return
                try {
                    requester.unregister(registeredCallback)
                } catch (_: RuntimeException) {
                    // Cleanup is idempotent and must not replace the connectivity result.
                }
            }
        }

        return try {
            val completed = withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<CellularValidationResult> { continuation ->
                    callback = object : CellularNetworkRequester.Callback {
                        override fun onAvailable() {
                            networkSeen.set(true)
                        }

                        override fun onCapabilitiesChanged(validated: Boolean) {
                            networkSeen.set(true)
                            if (validated && continuation.isActive) {
                                continuation.resume(CellularValidationResult.Validated)
                            }
                        }

                        override fun onUnavailable() {
                            if (continuation.isActive) {
                                continuation.resume(CellularValidationResult.Unavailable)
                            }
                        }
                    }
                    continuation.invokeOnCancellation { releaseOnce() }
                    try {
                        requester.request(checkNotNull(callback))
                    } catch (failure: Throwable) {
                        if (continuation.isActive) {
                            continuation.resume(CellularValidationResult.Failure(failure))
                        }
                    }
                }
            }
            completed ?: if (networkSeen.get()) {
                CellularValidationResult.Unvalidated
            } else {
                CellularValidationResult.Unavailable
            }
        } finally {
            releaseOnce()
        }
    }
}
```

- [ ] **Step 4: Run probe tests and static checks**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*.AndroidCellularValidationProbeTest'
.\gradlew.bat lintDebug
```

Expected: both commands end with `BUILD SUCCESSFUL`; all six probe tests pass and lint reports no error.

- [ ] **Step 5: Commit the probe increment**

```powershell
git add app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbe.kt app/src/test/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbeTest.kt
git commit -m "fix: add temporary cellular validation probe"
```

### Task 3: Single-Flight Probe Scheduling and Trigger Coalescing

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/CellularProbeCoordinator.kt`
- Test: `app/src/test/java/com/mobisentinel/app/monitoring/network/CellularProbeCoordinatorTest.kt`

**Interfaces:**
- Consumes: `CoroutineScope`, `CellularValidationProbe.validate()`, and `CellularValidationResult`.
- Produces: `CellularProbeCoordinator.start()`, `trigger()`, and `stop()`, with `intervalMillis = 60_000L`.

- [ ] **Step 1: Write failing scheduling tests**

Create `CellularProbeCoordinatorTest.kt`. Use a controlled probe whose `validate()` receives a `CompletableDeferred<CellularValidationResult>` from a channel, and assert:

```kotlin
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
        private val completions = Channel<CompletableDeferred<CellularValidationResult>>(Channel.UNLIMITED)
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
```

- [ ] **Step 2: Run the scheduling tests and verify the missing coordinator**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*.CellularProbeCoordinatorTest'
```

Expected: `FAILURE` because `CellularProbeCoordinator` does not exist.

- [ ] **Step 3: Implement idempotent startup, single-flight execution, rerun, period, and stop**

Create `CellularProbeCoordinator.kt` using this API and state machine:

```kotlin
package com.mobisentinel.app.monitoring.network

import java.util.concurrent.CancellationException
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
            } catch (cancelled: CancellationException) {
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
                if (rerunAfterResult) launchProbeLocked() else scheduleNextLocked()
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
```

During implementation, keep `onProbeRunningChanged(false)` in a `finally` path so cancellation always closes the `ProbeNetworkEventGate`; the narrow stop test must observe exactly `[true, false]`.

Keep `onResult(result)` outside the coordinator lock as shown. The Android observer's `onResult` lambda rechecks `started` under its own lock, so a result either publishes before `stop()` acquires that lock or is discarded; it can never publish after `stop()` returns, and cellular callbacks cannot deadlock by acquiring the two locks in reverse order.

- [ ] **Step 4: Run scheduler and probe tests together**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*.CellularProbeCoordinatorTest' --tests '*.AndroidCellularValidationProbeTest'
```

Expected: `BUILD SUCCESSFUL`; the coordinator never exceeds one concurrent call and stop produces no result.

- [ ] **Step 5: Commit the scheduling increment**

```powershell
git add app/src/main/java/com/mobisentinel/app/monitoring/network/CellularProbeCoordinator.kt app/src/test/java/com/mobisentinel/app/monitoring/network/CellularProbeCoordinatorTest.kt
git commit -m "fix: schedule cellular validation probes"
```

### Task 4: Android Observer Integration, Explicit Wi-Fi Refresh, and Event Sources

**Files:**
- Modify: `app/src/main/java/com/mobisentinel/app/monitoring/network/TransportNetworkTracker.kt`
- Modify: `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkObserver.kt`
- Modify: `app/src/main/java/com/mobisentinel/app/MobiSentinelApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/java/com/mobisentinel/app/monitoring/network/TransportNetworkTrackerTest.kt`

**Interfaces:**
- Consumes: Task 1 policy/gate, Task 2 Android probe, Task 3 coordinator, `ConnectivityManager`, `TelephonyManager`, and the service scope.
- Produces: active cellular states through the unchanged `NetworkObserver.states` flow and independently refreshed Wi-Fi states.

- [ ] **Step 1: Add failing tests for atomic Wi-Fi replacement**

Add to `TransportNetworkTrackerTest.kt`:

```kotlin
@Test
fun replacingNetworksRebuildsAggregateWithoutPublishingIntermediateClear() {
    val tracker = TransportNetworkTracker<String>()
    tracker.onCapabilitiesChanged("old", validated = true)

    val state = tracker.replace(
        listOf(
            "new-unvalidated" to false,
            "new-validated" to true,
        ),
    )

    assertEquals(ConnectivityState.CONNECTED, state)
}

@Test
fun replacingWithNoNetworksDisconnects() {
    val tracker = TransportNetworkTracker<String>()
    tracker.onCapabilitiesChanged("old", validated = true)

    assertEquals(ConnectivityState.DISCONNECTED, tracker.replace(emptyList()))
}
```

- [ ] **Step 2: Run the narrow tests and verify `replace` is missing**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*.TransportNetworkTrackerTest'
```

Expected: `FAILURE` because `TransportNetworkTracker.replace()` is unresolved.

- [ ] **Step 3: Add atomic tracker replacement**

Add this method to `TransportNetworkTracker`:

```kotlin
fun replace(networks: Iterable<Pair<K, Boolean>>): ConnectivityState {
    validationByNetwork.clear()
    networks.forEach { (id, validated) -> validationByNetwork[id] = validated }
    return aggregate()
}
```

Run the two tracker tests again and expect `BUILD SUCCESSFUL`.

- [ ] **Step 4: Refactor `AndroidNetworkObserver` around Wi-Fi state and cellular triggers**

Change its constructor to:

```kotlin
class AndroidNetworkObserver(
    private val context: Context,
    private val connectivityManager: ConnectivityManager,
    private val telephonyManager: TelephonyManager,
    scope: CoroutineScope,
    cellularProbe: CellularValidationProbe,
) : NetworkObserver
```

Implement these exact ownership rules:

1. Keep `wifiRequest` and a Wi-Fi `NetworkCallback` backed by `TransportNetworkTracker<Network>`.
2. Replace the old cellular tracker with a passive `cellularRequest`, a `ProbeNetworkEventGate<Network>`, and a callback whose `onAvailable`, `onCapabilitiesChanged`, and `onLost` call `policy.onPassiveCellularEvent()` only when the corresponding gate method returns `true`.
3. Create `CellularProbeCoordinator(scope, cellularProbe, onProbeRunningChanged = { running -> if (running) gate.onProbeStarted() else gate.onProbeFinished() }, onResult = policy::onProbeResult)`.
4. Implement `refreshWifiState()` under the existing observer lock by enumerating `connectivityManager.allNetworks`, retaining only `TRANSPORT_WIFI`, pairing each network with `NET_CAPABILITY_VALIDATED`, and passing the complete list to `wifiTracker.replace()`.
5. On `start()`, reset policy and gate, register Wi-Fi and passive cellular callbacks, seed the gate with currently visible cellular networks, register airplane/mobile-data events, set `started = true`, call `policy.emitInitialWifi()`, and call `probeCoordinator.start()`.
6. On Wi-Fi callbacks, update the tracker and call `policy.onWifiStateChanged(state)` only after startup seeding completes.
7. On `Intent.ACTION_AIRPLANE_MODE_CHANGED`, call only `policy.onAirplaneModeChanged()`. Do not read the broadcast's boolean extra and do not submit `DISCONNECTED` directly.
8. On API 31+, register a `TelephonyCallback` implementing `UserMobileDataStateListener`; on API 28–30, register a `PhoneStateListener` with `LISTEN_USER_MOBILE_DATA_STATE`; both callbacks call `policy.onPassiveCellularEvent()` regardless of the boolean value because the probe supplies evidence.
9. On API 26–27, register no telephony listener. Initial, periodic, passive connectivity, and airplane triggers remain active.
10. On `stop()`, mark stopped before cleanup, stop the probe coordinator, unregister telephony and broadcast listeners, unregister both network callbacks with isolated `IllegalArgumentException` handling, clear policy/trackers/gate, and ensure late callbacks cannot emit.
11. If a partial registration in `start()` throws, undo every registration already completed and leave `started = false`; do not publish a cellular disconnection.

Use `ContextCompat.registerReceiver(context, airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)` for the runtime receiver. Use `context.mainExecutor` for API 31+ telephony callbacks. Keep API-specific classes behind `Build.VERSION.SDK_INT` branches and annotate only the API 28–30 legacy registration helper with `@Suppress("DEPRECATION")`.

Use these concrete callback and refresh shapes inside the observer; all three event entry points take the observer lock before touching policy or gate state:

```kotlin
private fun refreshWifiState(): ConnectivityState = synchronized(lock) {
    val networks = connectivityManager.allNetworks.mapNotNull { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return@mapNotNull null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return@mapNotNull null
        }
        network to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    wifiTracker.replace(networks)
}

private fun updateWifi(block: () -> ConnectivityState) = synchronized(lock) {
    val state = block()
    if (started && !seeding) policy.onWifiStateChanged(state)
}

private fun routePassiveCellular(block: () -> Boolean) = synchronized(lock) {
    if (started && block()) policy.onPassiveCellularEvent()
}

private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) = updateWifi {
        wifiTracker.onAvailable(network)
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
        updateWifi {
            wifiTracker.onCapabilitiesChanged(
                network,
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }

    override fun onLost(network: Network) = updateWifi { wifiTracker.onLost(network) }
}

private val cellularCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) = routePassiveCellular {
        cellularGate.onAvailable(network)
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
        routePassiveCellular { cellularGate.onCapabilitiesChanged(network) }

    override fun onLost(network: Network) = routePassiveCellular {
        cellularGate.onLost(network)
    }
}

private val airplaneReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_AIRPLANE_MODE_CHANGED) return
        synchronized(lock) {
            if (started) policy.onAirplaneModeChanged()
        }
    }
}
```

Initialize policy and coordinator without exposing a partially built object:

```kotlin
init {
    lateinit var coordinator: CellularProbeCoordinator
    policy = CellularObservationPolicy(
        refreshWifiState = ::refreshWifiState,
        triggerCellularProbe = { coordinator.trigger() },
        emit = { snapshot -> mutableStates.tryEmit(snapshot) },
    )
    coordinator = CellularProbeCoordinator(
        scope = scope,
        probe = cellularProbe,
        onProbeRunningChanged = { running ->
            synchronized(lock) {
                if (running) cellularGate.onProbeStarted() else cellularGate.onProbeFinished()
            }
        },
        onResult = { result ->
            synchronized(lock) {
                if (started) policy.onProbeResult(result)
            }
        },
    )
    probeCoordinator = coordinator
}
```

Declare `policy` and `probeCoordinator` as uninitialized `val` properties assigned by that `init` block. Register the API-specific mobile-data listeners with these helpers:

```kotlin
private var modernMobileDataCallback: Any? = null
private var legacyMobileDataListener: PhoneStateListener? = null

private fun triggerFromMobileDataSetting() = synchronized(lock) {
    if (started) policy.onPassiveCellularEvent()
}

private fun registerMobileDataListener() {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> registerModernMobileDataListener()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> registerLegacyMobileDataListener()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun registerModernMobileDataListener() {
    val callback = object : TelephonyCallback(),
        TelephonyCallback.UserMobileDataStateListener {
        override fun onUserMobileDataStateChanged(enabled: Boolean) {
            triggerFromMobileDataSetting()
        }
    }
    modernMobileDataCallback = callback
    telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
}

@Suppress("DEPRECATION")
private fun registerLegacyMobileDataListener() {
    val listener = object : PhoneStateListener() {
        override fun onUserMobileDataStateChanged(enabled: Boolean) {
            triggerFromMobileDataSetting()
        }
    }
    legacyMobileDataListener = listener
    telephonyManager.listen(listener, PhoneStateListener.LISTEN_USER_MOBILE_DATA_STATE)
}

@Suppress("DEPRECATION")
private fun unregisterMobileDataListener() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        unregisterModernMobileDataListener()
    } else {
        legacyMobileDataListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        legacyMobileDataListener = null
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun unregisterModernMobileDataListener() {
    (modernMobileDataCallback as? TelephonyCallback)?.let(
        telephonyManager::unregisterTelephonyCallback,
    )
    modernMobileDataCallback = null
}
```

Import `androidx.annotation.RequiresApi`. Use these registration flags and lifecycle methods so partial startup and normal shutdown share one idempotent teardown:

```kotlin
private var wifiRegistered = false
private var cellularRegistered = false
private var airplaneRegistered = false
private var mobileDataRegistered = false

override fun start() = synchronized(lock) {
    if (started) return@synchronized
    seeding = true
    policy.reset()
    cellularGate.clear()
    wifiTracker.clear()
    try {
        connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)
        wifiRegistered = true
        connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)
        cellularRegistered = true

        connectivityManager.allNetworks.forEach { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return@forEach
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                cellularGate.seed(network)
            }
        }

        ContextCompat.registerReceiver(
            context,
            airplaneReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        airplaneRegistered = true
        registerMobileDataListener()
        mobileDataRegistered = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        started = true
        seeding = false
        policy.emitInitialWifi()
        probeCoordinator.start()
    } catch (_: RuntimeException) {
        started = false
        cleanupRegistrationsLocked()
        policy.reset()
        cellularGate.clear()
        wifiTracker.clear()
        seeding = false
    }
}

override fun stop() = synchronized(lock) {
    if (!started && !wifiRegistered && !cellularRegistered) return@synchronized
    started = false
    cleanupRegistrationsLocked()
    policy.reset()
    cellularGate.clear()
    wifiTracker.clear()
    seeding = false
}

private fun cleanupRegistrationsLocked() {
    probeCoordinator.stop()
    if (mobileDataRegistered) {
        try {
            unregisterMobileDataListener()
        } catch (_: RuntimeException) {
            // The platform may already have discarded the listener.
        }
        mobileDataRegistered = false
    }
    if (airplaneRegistered) {
        try {
            context.unregisterReceiver(airplaneReceiver)
        } catch (_: IllegalArgumentException) {
            // The receiver may already have been removed during teardown.
        }
        airplaneRegistered = false
    }
    if (cellularRegistered) {
        unregisterNetworkCallbackSafely(cellularCallback)
        cellularRegistered = false
    }
    if (wifiRegistered) {
        unregisterNetworkCallbackSafely(wifiCallback)
        wifiRegistered = false
    }
}

private fun unregisterNetworkCallbackSafely(callback: ConnectivityManager.NetworkCallback) {
    try {
        connectivityManager.unregisterNetworkCallback(callback)
    } catch (_: IllegalArgumentException) {
        // The callback may already have been removed during teardown.
    }
}
```

- [ ] **Step 5: Compose the Android dependencies explicitly**

In `MobiSentinelApplication.createMonitoringEngine(scope)`, obtain both services and construct the active probe:

```kotlin
val connectivityManager = getSystemService(ConnectivityManager::class.java)
val telephonyManager = getSystemService(TelephonyManager::class.java)
val cellularProbe = AndroidCellularValidationProbe(
    AndroidCellularNetworkRequester(connectivityManager),
)
```

Pass them to:

```kotlin
AndroidNetworkObserver(
    context = this,
    connectivityManager = connectivityManager,
    telephonyManager = telephonyManager,
    scope = scope,
    cellularProbe = cellularProbe,
)
```

Do not change `NetworkObserver`, `MonitoringEngine`, service lifecycle, state store, UI, notification, or speech contracts.

- [ ] **Step 6: Add only the required manifest permission**

Immediately after `ACCESS_NETWORK_STATE`, add:

```xml
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```

Verify the manifest still has no `android.permission.INTERNET`, location, phone-state, or storage permission.

- [ ] **Step 7: Run integration regression, manifest, lint, and build checks**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
rg -n "CHANGE_NETWORK_STATE|INTERNET|READ_PHONE_STATE|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION" app/src/main/AndroidManifest.xml
```

Expected:

- both Gradle invocations end with `BUILD SUCCESSFUL`;
- the manifest search finds `CHANGE_NETWORK_STATE` and the foreground-service permission containing the word `INTERNET` nowhere;
- it finds no `android.permission.INTERNET`, `READ_PHONE_STATE`, or location permission;
- existing Wi-Fi, debounce, notification, and narration tests remain green.

- [ ] **Step 8: Commit the Android integration as the release-driving fix**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mobisentinel/app/MobiSentinelApplication.kt app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkObserver.kt app/src/main/java/com/mobisentinel/app/monitoring/network/TransportNetworkTracker.kt app/src/test/java/com/mobisentinel/app/monitoring/network/TransportNetworkTrackerTest.kt
git commit -m "fix: validate cellular connectivity independently"
```

### Task 5: Documentation, Physical Gate, and Final Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-07-16-mobisentinel-design.md`
- Modify: `docs/testing/manual-test-matrix.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: verified behavior from Tasks 1–4 and the existing Release Please configuration.
- Produces: accurate user/developer documentation and an evidence-ready physical test checklist.

- [ ] **Step 1: Correct the original specification without rewriting history**

Replace the sentence asserting that airplane mode disconnects both transports with:

```markdown
- Modo avião não determina diretamente o estado de nenhum transporte. O evento dispara uma reavaliação independente do Wi‑Fi e uma sonda celular; somente os resultados dessas verificações entram no debounce e podem produzir avisos.
```

Extend the connectivity section to state that cellular validation uses a temporary `requestNetwork` every 60 seconds and on relevant events, with a 15-second timeout and Android `NET_CAPABILITY_VALIDATED`, while Wi-Fi remains passively observed.

- [ ] **Step 2: Update README architecture, permissions, and privacy**

Make these exact semantic changes:

- replace the claim that two passive callbacks observe both transports with a paragraph distinguishing passive Wi-Fi from temporary active cellular requests;
- clarify that “não faz pings periódicos” still means no application traffic to external servers;
- add `CHANGE_NETWORK_STATE` to the permission table with the reason “Solicitar temporariamente uma rede celular para validação independente do Wi‑Fi”;
- state that no `INTERNET` permission or external host is used in this version;
- retain the physical SIM/eSIM gate and add the 60-second no-duplicate observation.

- [ ] **Step 3: Replace the obsolete airplane evidence and expand the physical matrix**

In `docs/testing/manual-test-matrix.md`:

1. Change the old airplane row from `PASSOU` to `REVALIDAÇÃO NECESSÁRIA` because its evidence used the obsolete assumption that both transports must disconnect.
2. Record that the new expected result is independent evidence: airplane broadcast alone changes nothing; Wi-Fi can remain/recover connected; cellular follows only the active probe.
3. Replace the physical cellular checklist with the eight approved steps: Wi-Fi-on mobile-data loss, recovery, airplane non-inference, Wi-Fi re-enable in airplane mode, cellular probe result, multiple 60-second cycles without duplicate narration, and stop during an active probe without late output.
4. Add fields for device model, Android version, carrier, SIM/eSIM presence, execution date, and result; leave them explicitly `NÃO EXECUTADO` until a real run supplies evidence and do not record phone numbers, SIM identifiers, subscriber IDs, or other personal identifiers.
5. Update the automated-test totals only from the fresh Gradle output produced in Step 5; do not retain stale counts.

- [ ] **Step 4: Verify documentation, versioning boundaries, and diff hygiene**

Run:

```powershell
rg -n "Modo avião leva|modo avião leva|android.permission.INTERNET|8\.8\.8\.8|1\.1\.1\.1" README.md docs/superpowers/specs docs/testing app/src/main/AndroidManifest.xml
git diff --check
git diff -- app/build.gradle.kts .release-please-manifest.json CHANGELOG.md
```

Expected:

- no obsolete airplane assertion;
- no manifest `android.permission.INTERNET`;
- external IP addresses occur only in the approved design's future-evolution explanation, not production code or current README behavior;
- `git diff --check` is silent;
- version file, manifest version, and changelog diff is empty.

- [ ] **Step 5: Run the complete automated gate and capture fresh counts**

With an emulator or device available for instrumentation, run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Marco\AppData\Local\Android\Sdk'
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`; all JVM and connected Android tests pass, lint has no errors, and `app/build/outputs/apk/debug/app-debug.apk` exists. Update only the automated-count paragraph in the manual matrix from this output.

If no connected target is available, run the non-device gate:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Record `connectedDebugAndroidTest` as `NÃO EXECUTADO`, keep the emulator/physical rows open, and do not claim the complete gate passed.

- [ ] **Step 6: Execute the physical release gate before publishing a release**

On a phone with SIM/eSIM and Portuguese TTS:

1. Start with validated Wi-Fi and validated mobile data; confirm both cards and notification states.
2. Disable mobile data while Wi-Fi stays connected; after the probe result plus configured loss debounce, confirm only cellular becomes disconnected and exactly one “Dados móveis desconectados.” announcement occurs.
3. Re-enable mobile data while Wi-Fi stays connected; after validation plus recovery debounce, confirm cellular recovery and exactly one recovery announcement.
4. Enable airplane mode and confirm the broadcast itself causes no immediate state inference or airplane-specific speech.
5. Re-enable Wi-Fi while airplane mode remains active; confirm Wi-Fi validates independently and is not reported disconnected because of airplane mode.
6. Confirm the cellular card follows the cellular probe result during airplane mode.
7. Wait through at least three unchanged 60-second cycles and confirm no duplicate state update or narration.
8. Stop monitoring during an active probe and confirm no late notification update or speech.

Record device model, Android version, carrier, SIM/eSIM presence, timestamps, visible state, notification text, and heard messages in the matrix. A failed or unexecuted physical gate keeps the next GitHub Release in prerelease status.

- [ ] **Step 7: Commit documentation without touching generated release files**

```powershell
git add README.md docs/superpowers/specs/2026-07-16-mobisentinel-design.md docs/testing/manual-test-matrix.md
git commit -m "docs: describe active cellular validation"
```

- [ ] **Step 8: Final branch verification**

Run:

```powershell
git status --short
git log --oneline --decorate -6
git diff --check HEAD~4..HEAD
```

Expected: clean worktree; the design commit plus five task commits are visible; diff check is silent. Before claiming completion or publishing, use `superpowers:verification-before-completion` and report the exact Gradle gates and physical rows that passed or remain open.
