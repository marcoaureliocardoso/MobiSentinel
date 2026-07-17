# Cellular Validation Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the three PR #5 review findings while preserving independent Wi-Fi and cellular diagnosis without phone-state permission.

**Architecture:** A small platform policy object owns Android-version and receiver-export decisions used by `AndroidNetworkObserver`. `AndroidCellularValidationProbe` coordinates registration and release with a synchronized state so cancellation cannot race past registration. Existing policy, coordinator, and monitoring state flows remain unchanged.

**Tech Stack:** Kotlin, Android SDK 26–36, coroutines, JUnit 4, kotlinx-coroutines-test, Gradle, adb.

## Global Constraints

- Do not add `READ_PHONE_STATE`, `INTERNET`, location, microphone, contacts, SMS, or storage permission.
- Use `TelephonyCallback.UserMobileDataStateListener` only on API 31 or newer.
- On API 26–30, retain initial, periodic, passive-network, and airplane-mode triggers.
- Airplane mode is a trigger only; it never directly establishes a transport state.
- Release every active cellular request exactly once after success, unavailability, timeout, failure, or cancellation.
- Restore airplane mode off, Wi-Fi on, and mobile data on after physical testing.

---

### Task 1: Encode Android trigger compatibility

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkTriggerPolicy.kt`
- Create: `app/src/test/java/com/mobisentinel/app/monitoring/network/AndroidNetworkTriggerPolicyTest.kt`
- Modify: `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkObserver.kt`

**Interfaces:**
- Produces: `AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(sdkInt: Int): Boolean`
- Produces: `AndroidNetworkTriggerPolicy.AIRPLANE_RECEIVER_FLAGS: Int`
- Consumes: `Build.VERSION_CODES.S` and `ContextCompat.RECEIVER_EXPORTED`

- [ ] **Step 1: Write the failing platform-policy tests**

```kotlin
package com.mobisentinel.app.monitoring.network

import android.os.Build
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNetworkTriggerPolicyTest {
    @Test
    fun userMobileDataCallbackIsRegisteredOnlyOnApi31AndNewer() {
        assertFalse(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(26))
        assertFalse(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(28))
        assertFalse(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(30))
        assertTrue(
            AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(
                Build.VERSION_CODES.S,
            ),
        )
        assertTrue(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(36))
    }

    @Test
    fun airplaneReceiverAcceptsSystemBroadcasts() {
        assertEquals(
            ContextCompat.RECEIVER_EXPORTED,
            AndroidNetworkTriggerPolicy.AIRPLANE_RECEIVER_FLAGS,
        )
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mobisentinel.app.monitoring.network.AndroidNetworkTriggerPolicyTest" --console=plain
```

Expected: compilation fails because `AndroidNetworkTriggerPolicy` does not exist.

- [ ] **Step 3: Add the minimal platform policy**

```kotlin
package com.mobisentinel.app.monitoring.network

import android.os.Build
import androidx.core.content.ContextCompat

internal object AndroidNetworkTriggerPolicy {
    const val AIRPLANE_RECEIVER_FLAGS = ContextCompat.RECEIVER_EXPORTED

    fun shouldRegisterUserMobileDataCallback(sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.S
}
```

- [ ] **Step 4: Wire the policy and remove the protected legacy listener**

In `AndroidNetworkObserver.kt`:

```kotlin
ContextCompat.registerReceiver(
    context,
    airplaneReceiver,
    IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
    AndroidNetworkTriggerPolicy.AIRPLANE_RECEIVER_FLAGS,
)
```

Replace listener selection with:

```kotlin
private fun registerMobileDataListener(): Boolean {
    if (!AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(Build.VERSION.SDK_INT)) {
        return false
    }
    registerModernMobileDataListener()
    return true
}
```

Remove `PhoneStateListener`, `Handler`, `Looper`, `CountDownLatch`, `AtomicReference`, `legacyMobileDataListener`, `registerLegacyMobileDataListener`, `runOnMainThread`, and the legacy unregister branch. Keep modern registration and unregistration behind API 31 checks.

- [ ] **Step 5: Run the focused tests and verify GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mobisentinel.app.monitoring.network.AndroidNetworkTriggerPolicyTest" --console=plain
```

Expected: two tests pass.

- [ ] **Step 6: Commit the compatibility fix**

```powershell
git add app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkTriggerPolicy.kt app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkObserver.kt app/src/test/java/com/mobisentinel/app/monitoring/network/AndroidNetworkTriggerPolicyTest.kt
git commit -m "fix: preserve network monitoring on legacy Android"
```

---

### Task 2: Make probe cancellation release registration atomically

**Files:**
- Modify: `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbe.kt`
- Modify: `app/src/test/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbeTest.kt`

**Interfaces:**
- Preserves: `CellularValidationProbe.validate(): CellularValidationResult`
- Preserves: `CellularNetworkRequester.request(callback)` and `unregister(callback)`
- Adds no test-only production API.

- [ ] **Step 1: Add a failing cancellation-during-registration test**

Add imports for `CoroutineStart` and `Deferred`, then add:

```kotlin
@Test
fun cancellationInsideRequestReleasesAfterRegistrationCompletes() = runTest {
    lateinit var result: Deferred<CellularValidationResult>
    val requester = CancellationDuringRegistrationRequester { result.cancel() }
    result = async(start = CoroutineStart.LAZY) {
        AndroidCellularValidationProbe(requester).validate()
    }

    result.start()
    runCurrent()

    assertTrue(result.isCancelled)
    assertEquals(0, requester.unregisterBeforeRegistrationCount)
    assertEquals(1, requester.unregisterCount)
}

private class CancellationDuringRegistrationRequester(
    private val cancel: () -> Unit,
) : CellularNetworkRequester {
    private lateinit var callback: CellularNetworkRequester.Callback
    private var registered = false
    var unregisterBeforeRegistrationCount = 0
    var unregisterCount = 0

    override fun request(callback: CellularNetworkRequester.Callback) {
        this.callback = callback
        cancel()
        registered = true
    }

    override fun unregister(callback: CellularNetworkRequester.Callback) {
        assertEquals(this.callback, callback)
        if (registered) {
            unregisterCount++
        } else {
            unregisterBeforeRegistrationCount++
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mobisentinel.app.monitoring.network.AndroidCellularValidationProbeTest.cancellationInsideRequestReleasesAfterRegistrationCompletes" --console=plain
```

Expected: failure reports an unregister before registration and no unregister after registration.

- [ ] **Step 3: Implement synchronized registration/release state**

Inside `validate`, replace the single `released` flag with:

```kotlin
val registrationLock = Any()
var registrationCompleted = false
var releaseRequested = false
var released = false
```

Use one helper that performs `requester.unregister` outside the monitor:

```kotlin
fun unregisterSafely(registeredCallback: CellularNetworkRequester.Callback) {
    try {
        requester.unregister(registeredCallback)
    } catch (_: RuntimeException) {
        // Cleanup must not replace the connectivity result.
    }
}

fun releaseOnce() {
    val registeredCallback = synchronized(registrationLock) {
        releaseRequested = true
        if (!registrationCompleted || released) {
            null
        } else {
            released = true
            callback
        }
    }
    registeredCallback?.let(::unregisterSafely)
}

fun markRegistrationFinished() {
    val registeredCallback = synchronized(registrationLock) {
        registrationCompleted = true
        if (!releaseRequested || released) {
            null
        } else {
            released = true
            callback
        }
    }
    registeredCallback?.let(::unregisterSafely)
}
```

Wrap `requester.request` so `markRegistrationFinished()` always runs after it returns or throws:

```kotlin
try {
    requester.request(checkNotNull(callback))
} catch (failure: Throwable) {
    completeOnce(CellularValidationResult.Failure(failure))
} finally {
    markRegistrationFinished()
}
```

- [ ] **Step 4: Run all probe tests and verify GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mobisentinel.app.monitoring.network.AndroidCellularValidationProbeTest" --console=plain
```

Expected: seven probe tests pass, including the new race regression.

- [ ] **Step 5: Commit the cancellation fix**

```powershell
git add app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbe.kt app/src/test/java/com/mobisentinel/app/monitoring/network/AndroidCellularValidationProbeTest.kt
git commit -m "fix: release cancelled cellular probes safely"
```

---

### Task 3: Run the complete automated gate on the Moto G54 5G

**Files:**
- Verify: all production and test sources
- Verify: `scripts/tests/verify-release-apk-test.ps1`

**Interfaces:**
- Consumes device serial `ZF524J2LFN` reported by `adb devices -l`.
- Produces test, lint, APK, and instrumented-test evidence.

- [ ] **Step 1: Confirm the device and initial network settings**

```powershell
& 'C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
& 'C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s ZF524J2LFN shell settings get global airplane_mode_on
& 'C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s ZF524J2LFN shell settings get global wifi_on
& 'C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s ZF524J2LFN shell settings get global mobile_data
```

Expected: one authorized Moto G54 5G; record all three settings for restoration.

- [ ] **Step 2: Run the clean JVM, lint, build, and instrumented gate**

```powershell
.\gradlew.bat -p buildSrc test --console=plain
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero JVM failures, lint success, debug APK generated, and 13 instrumented tests pass on the Moto G54 5G.

- [ ] **Step 3: Verify the release APK guard script**

```powershell
.\scripts\tests\verify-release-apk-test.ps1
```

Expected: valid, mismatch, syntax, and component-range cases pass.

---

### Task 4: Execute the physical connectivity matrix

**Files:**
- Modify after evidence: `docs/testing/manual-test-matrix.md`
- Modify after evidence: `README.md`
- Modify after evidence: `docs/superpowers/specs/2026-07-16-mobisentinel-design.md`

**Interfaces:**
- Consumes the installed debug APK and Android UI-tree states.
- Produces timestamped pass/fail evidence without phone number, ICCID, IMSI, BSSID, MAC address, or IP address.

- [ ] **Step 1: Fresh-install and activate monitoring**

Run `installDebug`, clear only `com.mobisentinel.app` QA data, launch `.MainActivity`, derive tap coordinates from `uiautomator dump`, tap `Ativar monitoramento`, and allow notifications. Expect foreground service active and both cards to settle from `verificando`.

- [ ] **Step 2: Validate cellular loss/recovery with Wi-Fi on**

Use `svc data disable`, poll UI-tree content descriptions until only cellular is disconnected, then use `svc data enable` and poll until cellular is validated again. Expect Wi-Fi to remain connected throughout and crash buffer to remain empty.

- [ ] **Step 3: Validate cellular loss/recovery with Wi-Fi off**

Use `svc wifi disable`, confirm Wi-Fi loss, repeat mobile-data disable/enable, then restore Wi-Fi. Expect each card to follow only its own transport evidence.

- [ ] **Step 4: Validate real airplane-mode routing**

Open Quick Settings, find `qs_pager` from the UI tree, swipe to the page containing `Modo avião`, and tap its tree-derived center. Expect no immediate inferred state, then cellular disconnected from the probe. Re-enable Wi-Fi while airplane mode remains on and expect Wi-Fi validated with cellular still disconnected.

- [ ] **Step 5: Validate three unchanged periodic cycles**

With stable Wi-Fi and cellular, record UI/notification state, wait for three completed 60-second intervals, and inspect UI, notification, active network requests, TTS/logcat, and crash buffer after each cycle. Expect unchanged states, no duplicate transition, and no accumulating app cellular requests.

- [ ] **Step 6: Stop during an active probe**

Trigger a cellular probe, immediately use the notification action `Parar` located by UI tree, wait longer than 15 seconds, and inspect service, notification, UI state, app network requests, and logs. Expect no late state, callback, or narration and no outstanding app `REQUEST` for cellular.

- [ ] **Step 7: Validate screen-off/background behavior**

Reactivate monitoring, turn the screen off with key event 26, toggle mobile data off and on, wait for each debounce, wake the device, and inspect UI/notification. Expect confirmed loss/recovery while the screen was off and no crash.

- [ ] **Step 8: Validate reboot and explicit stop persistence**

With monitoring enabled, reboot through adb, wait for `sys.boot_completed=1`, and confirm the foreground service and notification return. Then use `Parar`, reboot again, and confirm neither service nor notification returns.

- [ ] **Step 9: Restore the device**

Disable airplane mode, enable Wi-Fi and mobile data, wake/unlock if needed, and confirm settings `0/1/1`. Leave the MobiSentinel installed; preserve the final explicit monitoring state recorded in the matrix.

- [ ] **Step 10: Document executable and unavailable scenarios**

Record model, Android/API version, carrier label shown by System UI, dual-SIM presence, date, timings, and results. Mark captive portal/no-internet Wi-Fi, missing TTS engine, prolonged OEM battery restrictions, and physical Android 8–11 as not executed when no safe reliable environment exists.

---

### Task 5: Final documentation, verification, and PR update

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-07-16-mobisentinel-design.md`
- Modify: `docs/testing/manual-test-matrix.md`

- [ ] **Step 1: Update compatibility and evidence text**

Document API 31+ telephony events, API 26–30 fallback triggers, exported system receiver, atomic cancellation, automated counts, Moto evidence, timings, limitations, and restored device state.

- [ ] **Step 2: Verify documentation and permissions**

```powershell
rg -n "READ_PHONE_STATE|android.permission.INTERNET|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION" app/src/main/AndroidManifest.xml
rg -n "API 31|API 26|Moto G54|modo avião|cancel" README.md docs/superpowers/specs/2026-07-16-mobisentinel-design.md docs/testing/manual-test-matrix.md
git diff --check
```

Expected: no forbidden permission declaration; updated compatibility and physical evidence present; no whitespace errors.

- [ ] **Step 3: Commit documentation**

```powershell
git add README.md docs/superpowers/specs/2026-07-16-mobisentinel-design.md docs/testing/manual-test-matrix.md
git commit -m "docs: record cellular validation retest"
```

- [ ] **Step 4: Run final verification and inspect scope**

```powershell
.\gradlew.bat -p buildSrc test --console=plain
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --console=plain
.\scripts\tests\verify-release-apk-test.ps1
git diff --check
git status -sb
git log --oneline origin/master..HEAD
```

Expected: every executable gate passes and the worktree is clean.

- [ ] **Step 5: Push and verify PR #5**

```powershell
git push
gh pr checks 5
gh pr view 5 --json url,state,isDraft,mergeable,mergeStateStatus,headRefOid
```

Expected: branch pushed; PR remains open and mergeable; new CI may be pending immediately after push.
