# MobiSentinel MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app that continuously monitors Wi-Fi and cellular connectivity, shows both states, and speaks confirmed changes according to user settings.

**Architecture:** A single Compose activity edits DataStore preferences and observes an in-process state store. A foreground service owns a transport-specific `ConnectivityManager` observer, one debounced state coordinator per transport, and an Android Text-to-Speech adapter. Domain logic is isolated behind small interfaces so timing, queueing, lifecycle, and message selection are unit tested without Android framework objects.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 8.13.2, Gradle 8.13, compile/target SDK 36, min SDK 26, Jetpack Compose BOM 2026.04.01, Material 3, Activity Compose 1.13.0, Lifecycle 2.10.0, DataStore 1.2.1, Kotlin Coroutines 1.10.2, JUnit 4.13.2, AndroidX Test 1.7.0/1.3.0, Espresso 3.7.0.

## Global Constraints

- Application ID and namespace: `com.mobisentinel.app`.
- UI language and spoken messages: Brazilian Portuguese.
- Monitor Wi-Fi and cellular independently; do not model VPN as a third transport.
- States: disconnected, connected without validated internet, connected with validated internet.
- Default loss confirmation: 5 seconds; default recovery confirmation: 2 seconds.
- User-configurable confirmation range: 0 through 60 seconds.
- Never speak the initial state after service startup.
- Do not perform periodic pings or contact an external server.
- Store all configuration locally; no account, backend, analytics, or event history.
- Use a user-started `specialUse` foreground service with an ongoing notification and an explicit stop action.
- Request no location, microphone, contacts, phone, or storage permission.
- Use TDD for domain behavior and run the narrowest test after every implementation step.
- Do not publish to Google Play in this plan; record the later Play Console foreground-service declaration requirement.

## File Map

### Build and application shell

- `settings.gradle.kts`: repositories and `:app` inclusion.
- `build.gradle.kts`: root Android/Kotlin/Compose plugins.
- `gradle/libs.versions.toml`: pinned dependency catalog.
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat`: reproducible Gradle 8.13 wrapper.
- `app/build.gradle.kts`: Android, Compose, and test configuration.
- `app/src/main/AndroidManifest.xml`: permissions, activity, service, and boot receiver.
- `app/src/main/java/com/mobisentinel/app/MobiSentinelApplication.kt`: process-level dependency graph.

### Monitoring domain

- `monitoring/model/ConnectivityModels.kt`: transports, states, settings, snapshots, and transitions.
- `monitoring/state/TransitionCoordinator.kt`: initial baseline and debounced confirmation.
- `monitoring/network/NetworkObserver.kt`: platform-neutral network observer contract.
- `monitoring/network/TransportNetworkTracker.kt`: aggregate multiple Android `Network` instances for one transport.
- `monitoring/network/AndroidNetworkObserver.kt`: two transport-specific `NetworkCallback` registrations.
- `monitoring/MonitoringStateStore.kt`: current confirmed state and service-active flag.
- `monitoring/MonitoringEngine.kt`: lifecycle orchestration and narration policy.

### Preferences and speech

- `preferences/SettingsRepository.kt`: settings contract.
- `preferences/DataStoreSettingsRepository.kt`: Preferences DataStore adapter.
- `speech/SpeechController.kt`: speech contract and availability.
- `speech/AnnouncementQueue.kt`: deterministic replacement/ordering policy.
- `speech/PortugueseMessageFactory.kt`: exact spoken copy.
- `speech/AndroidSpeechController.kt`: Text-to-Speech lifecycle and queue bridge.

### Android service and UI

- `service/MonitoringService.kt`: foreground-service lifecycle and actions.
- `service/MonitoringNotification.kt`: notification channel and state summary.
- `service/BootReceiver.kt`: conditional restart after boot/package replacement.
- `ui/MainViewModel.kt`: combines state and settings; exposes user actions.
- `ui/MobiSentinelApp.kt`: two-screen Compose host.
- `ui/MainScreen.kt`: connection cards and activation state.
- `ui/SettingsScreen.kt`: narration switches, delays, stop, and voice test.
- `ui/theme/*`: Material 3 theme.
- `MainActivity.kt`: permission request, ViewModel creation, and Compose content.

---

### Task 1: Buildable Android/Compose Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `local.properties` (untracked)
- Create: `.gitignore`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/mobisentinel/app/MainActivity.kt`
- Create: `app/src/main/java/com/mobisentinel/app/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/mobisentinel/app/ProjectSmokeTest.kt`

**Interfaces:**
- Consumes: Android SDK at `C:\Users\Marco\AppData\Local\Android\Sdk`, JDK 21, and Android Studio Quail 1 already installed.
- Produces: a single `:app` module whose debug APK and JVM test task build from the Gradle wrapper.

- [x] **Step 1: Create Gradle settings, catalog, and root build**

Use these exact version pins in `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.13.2"
kotlin = "2.3.21"
composeBom = "2026.04.01"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
coreKtx = "1.18.0"
datastore = "1.2.1"
coroutines = "1.10.2"
junit = "4.13.2"
androidxTestCore = "1.7.0"
androidxTestJunit = "1.3.0"
espresso = "3.7.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-core = { module = "androidx.test:core-ktx", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit-ktx", version.ref = "androidxTestJunit" }
androidx-test-espresso = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`settings.gradle.kts` must use `google()`, `mavenCentral()`, and `gradlePluginPortal()` only. Root `build.gradle.kts` applies the three catalog plugins with `apply false`.

- [x] **Step 2: Generate and pin the wrapper**

Run the cached Gradle executable once to generate wrapper scripts, then pin Gradle 8.13:

```powershell
& 'C:\Users\Marco\.gradle\wrapper\dists\gradle-9.1.0-all\7wzd0jkjit61aq2p43wpjgij9\gradle-9.1.0\bin\gradle.bat' wrapper --gradle-version 8.13 --distribution-type bin
```

Expected: `BUILD SUCCESSFUL`, with `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, and `gradle-wrapper.properties` created. Confirm the properties file contains:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
```

- [x] **Step 3: Configure the app module**

Create `app/build.gradle.kts` with namespace/application ID `com.mobisentinel.app`, `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`, Java/Kotlin target 17, `buildFeatures.compose = true`, `buildFeatures.buildConfig = true`, the BOM applied to production and Android tests, and all catalog dependencies above. Configure `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and `unitTests.isIncludeAndroidResources = true`.

Create `local.properties` with:

```properties
sdk.dir=C\:\\Users\\Marco\\AppData\\Local\\Android\\Sdk
```

Add `local.properties`, `.gradle/`, `.idea/`, `build/`, and `app/build/` to `.gitignore`.

- [x] **Step 4: Write the failing smoke test**

```kotlin
package com.mobisentinel.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test fun packageNameIsStable() {
        assertEquals("com.mobisentinel.app", BuildConfig.APPLICATION_ID)
    }
}
```

- [x] **Step 5: Run the test and observe the missing shell failure**

Run: `./gradlew testDebugUnitTest --tests '*.ProjectSmokeTest'`

Expected before `MainActivity`, theme, manifest, and resources exist: compilation or manifest processing fails because the application shell is incomplete.

- [x] **Step 6: Create the minimal Compose shell**

`MainActivity.kt` must render `MobiSentinelTheme { Text("MobiSentinel") }`. Create a no-action-bar Material theme, strings for `app_name`, and a launcher activity manifest entry. Keep `MainActivity` focused on `setContent`; no monitoring logic enters this file in this task.

- [x] **Step 7: Verify tests and APK**

Run:

```powershell
./gradlew testDebugUnitTest assembleDebug
```

Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [x] **Step 8: Commit**

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "build: scaffold native Android app"
```

### Task 2: Connectivity Models and Debounced Transitions

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/model/ConnectivityModels.kt`
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/state/TransitionCoordinator.kt`
- Test: `app/src/test/java/com/mobisentinel/app/monitoring/state/TransitionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `CoroutineScope` and `kotlinx.coroutines.delay`.
- Produces: `Transport`, `ConnectivityState`, `MonitoringSettings`, `TransportSnapshot`, `MonitoringSnapshot`, `ConfirmedTransition`, and `TransitionCoordinator.submit(ConnectivityState)`.

- [x] **Step 1: Define the model types**

```kotlin
package com.mobisentinel.app.monitoring.model

enum class Transport { WIFI, CELLULAR }
enum class ConnectivityState { DISCONNECTED, CONNECTED_NO_INTERNET, CONNECTED }

data class MonitoringSettings(
    val monitoringEnabled: Boolean = false,
    val narrateWifi: Boolean = true,
    val narrateCellular: Boolean = true,
    val lossDelaySeconds: Int = 5,
    val recoveryDelaySeconds: Int = 2,
) {
    init {
        require(lossDelaySeconds in 0..60)
        require(recoveryDelaySeconds in 0..60)
    }

    fun narrationEnabled(transport: Transport): Boolean = when (transport) {
        Transport.WIFI -> narrateWifi
        Transport.CELLULAR -> narrateCellular
    }
}

data class TransportSnapshot(val transport: Transport, val state: ConnectivityState)

data class MonitoringSnapshot(
    val wifi: ConnectivityState? = null,
    val cellular: ConnectivityState? = null,
    val serviceActive: Boolean = false,
)

data class ConfirmedTransition(
    val transport: Transport,
    val previous: ConnectivityState,
    val current: ConnectivityState,
)
```

- [x] **Step 2: Write coordinator tests first**

Use `StandardTestDispatcher` and `runTest` to prove all of these cases in separate tests:

```kotlin
@Test fun firstStateBecomesBaselineWithoutTransition()
@Test fun lossIsConfirmedOnlyAfterConfiguredDelay()
@Test fun recoveryUsesRecoveryDelay()
@Test fun returningToConfirmedStateCancelsPendingChange()
@Test fun newerCandidateReplacesOlderCandidate()
@Test fun zeroDelayConfirmsOnTheNextSchedulerTurn()
```

The fixture records `ConfirmedTransition` values in a mutable list and reads delay seconds from a mutable `MonitoringSettings` so later preference changes affect new submissions.

- [x] **Step 3: Run the new test class and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*.TransitionCoordinatorTest'`

Expected: FAIL because `TransitionCoordinator` does not exist.

- [x] **Step 4: Implement the minimal coordinator**

```kotlin
package com.mobisentinel.app.monitoring.state

import com.mobisentinel.app.monitoring.model.*
import kotlinx.coroutines.*

class TransitionCoordinator(
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val settings: () -> MonitoringSettings,
    private val onConfirmedState: (ConnectivityState) -> Unit,
    private val onTransition: (ConfirmedTransition) -> Unit,
) {
    private var confirmed: ConnectivityState? = null
    private var pending: Job? = null

    fun submit(candidate: ConnectivityState) {
        val current = confirmed
        if (current == null) {
            confirmed = candidate
            onConfirmedState(candidate)
            return
        }
        if (candidate == current) {
            pending?.cancel()
            pending = null
            return
        }
        pending?.cancel()
        val delaySeconds = if (candidate == ConnectivityState.CONNECTED) {
            settings().recoveryDelaySeconds
        } else {
            settings().lossDelaySeconds
        }
        pending = scope.launch {
            delay(delaySeconds * 1_000L)
            val previous = confirmed ?: return@launch
            confirmed = candidate
            onConfirmedState(candidate)
            onTransition(ConfirmedTransition(transport, previous, candidate))
            pending = null
        }
    }

    fun close() {
        pending?.cancel()
        pending = null
    }
}
```

- [x] **Step 5: Run focused and full unit tests**

Run:

```powershell
./gradlew testDebugUnitTest --tests '*.TransitionCoordinatorTest'
./gradlew testDebugUnitTest
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/mobisentinel/app/monitoring app/src/test/java/com/mobisentinel/app/monitoring
git commit -m "feat: debounce confirmed connectivity states"
```

### Task 3: Per-Transport Android Network Observation

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/NetworkObserver.kt`
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/TransportNetworkTracker.kt`
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/network/AndroidNetworkObserver.kt`
- Test: `app/src/test/java/com/mobisentinel/app/monitoring/network/TransportNetworkTrackerTest.kt`

**Interfaces:**
- Consumes: `TransportSnapshot`, Android `ConnectivityManager`, `NetworkRequest`, and `NetworkCapabilities`.
- Produces: `NetworkObserver.states: Flow<TransportSnapshot>`, `start()`, `stop()`, and transport aggregation independent of Android object identity.

- [x] **Step 1: Define the observer contract and tracker tests**

```kotlin
interface NetworkObserver {
    val states: kotlinx.coroutines.flow.Flow<TransportSnapshot>
    fun start()
    fun stop()
}
```

Write tracker tests for: empty is disconnected; available unvalidated network is connected without internet; validated capability becomes connected; losing the only network disconnects; one validated network wins over another unvalidated network; losing one of two networks retains the remaining aggregate state.

- [x] **Step 2: Run tracker tests and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*.TransportNetworkTrackerTest'`

Expected: FAIL because `TransportNetworkTracker` is missing.

- [x] **Step 3: Implement the pure tracker**

```kotlin
class TransportNetworkTracker<K> {
    private val validationByNetwork = linkedMapOf<K, Boolean>()

    fun onAvailable(id: K): ConnectivityState {
        validationByNetwork.putIfAbsent(id, false)
        return aggregate()
    }

    fun onCapabilitiesChanged(id: K, validated: Boolean): ConnectivityState {
        validationByNetwork[id] = validated
        return aggregate()
    }

    fun onLost(id: K): ConnectivityState {
        validationByNetwork.remove(id)
        return aggregate()
    }

    fun clear(): ConnectivityState {
        validationByNetwork.clear()
        return aggregate()
    }

    private fun aggregate(): ConnectivityState = when {
        validationByNetwork.values.any { it } -> ConnectivityState.CONNECTED
        validationByNetwork.isNotEmpty() -> ConnectivityState.CONNECTED_NO_INTERNET
        else -> ConnectivityState.DISCONNECTED
    }
}
```

- [x] **Step 4: Implement Android callbacks**

Create one `NetworkRequest` for `TRANSPORT_WIFI` and one for `TRANSPORT_CELLULAR`. Do not add `NET_CAPABILITY_VALIDATED` to either request; observe `NET_CAPABILITY_VALIDATED` in `onCapabilitiesChanged`. Each callback owns a `TransportNetworkTracker<Network>` and emits only when its aggregate state changes. Register both callbacks once in `start()`, unregister both in `stop()`, catch `IllegalArgumentException` only around unregister, and clear both trackers on stop.

Seed the initial state atomically: under a shared lock set `seeding=true`, register both callbacks, enumerate `connectivityManager.allNetworks`, inspect each network's transport and validation capabilities, update the matching tracker, set `seeding=false`, then emit exactly one Wi-Fi and one cellular snapshot. Callback methods use the same lock; while `seeding` is true they update trackers without emitting. This guarantees both initial baselines exist and prevents a currently connected network from being narrated as a recovery during startup.

The adapter emits through `MutableSharedFlow<TransportSnapshot>(extraBufferCapacity = 8, onBufferOverflow = DROP_OLDEST)` and guards start/stop with a synchronized boolean.

- [x] **Step 5: Add manifest network permission and compile**

Add before `<application>`:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Run:

```powershell
./gradlew testDebugUnitTest --tests '*.TransportNetworkTrackerTest'
./gradlew assembleDebug
```

Expected: tracker tests pass and the debug APK builds.

- [x] **Step 6: Commit**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mobisentinel/app/monitoring/network app/src/test/java/com/mobisentinel/app/monitoring/network
git commit -m "feat: observe Wi-Fi and cellular networks separately"
```

### Task 4: Persistent Monitoring Settings

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/preferences/SettingsRepository.kt`
- Create: `app/src/main/java/com/mobisentinel/app/preferences/DataStoreSettingsRepository.kt`
- Test: `app/src/androidTest/java/com/mobisentinel/app/preferences/DataStoreSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `MonitoringSettings` and Android `DataStore<Preferences>`.
- Produces: `SettingsRepository.settings: Flow<MonitoringSettings>` and explicit setters for activation, narration selection, and confirmation delays.

- [x] **Step 1: Define the settings contract**

```kotlin
interface SettingsRepository {
    val settings: kotlinx.coroutines.flow.Flow<MonitoringSettings>
    suspend fun setMonitoringEnabled(enabled: Boolean)
    suspend fun setNarrateWifi(enabled: Boolean)
    suspend fun setNarrateCellular(enabled: Boolean)
    suspend fun setLossDelaySeconds(seconds: Int)
    suspend fun setRecoveryDelaySeconds(seconds: Int)
}
```

Every delay setter must call `require(seconds in 0..60)` before writing.

- [x] **Step 2: Write the instrumented persistence tests**

Use a unique temporary preferences file created with `PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { context.preferencesDataStoreFile(fileName) })`. Verify default values, each setter, flow updates, and rejection of `-1` and `61`.

Representative assertion:

```kotlin
repository.setLossDelaySeconds(12)
assertEquals(12, repository.settings.first().lossDelaySeconds)
```

- [x] **Step 3: Run the test and verify failure**

Run on the active emulator:

```powershell
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mobisentinel.app.preferences.DataStoreSettingsRepositoryTest
```

Expected: compilation fails because `DataStoreSettingsRepository` is missing.

- [x] **Step 4: Implement the DataStore adapter**

Use these exact keys and defaults:

```kotlin
private object Keys {
    val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
    val narrateWifi = booleanPreferencesKey("narrate_wifi")
    val narrateCellular = booleanPreferencesKey("narrate_cellular")
    val lossDelaySeconds = intPreferencesKey("loss_delay_seconds")
    val recoveryDelaySeconds = intPreferencesKey("recovery_delay_seconds")
}
```

Map `IOException` while reading to `emptyPreferences()` and rethrow every other exception. Clamp persisted delay values with `coerceIn(0, 60)` so a damaged or manually modified store cannot crash model construction. Each setter uses `dataStore.edit` and changes exactly one key.

- [x] **Step 5: Run persistence and unit tests**

Run:

```powershell
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mobisentinel.app.preferences.DataStoreSettingsRepositoryTest
./gradlew testDebugUnitTest
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/mobisentinel/app/preferences app/src/androidTest/java/com/mobisentinel/app/preferences
git commit -m "feat: persist monitoring preferences"
```

### Task 5: Portuguese Messages and Replaceable Speech Queue

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/speech/SpeechController.kt`
- Create: `app/src/main/java/com/mobisentinel/app/speech/AnnouncementQueue.kt`
- Create: `app/src/main/java/com/mobisentinel/app/speech/PortugueseMessageFactory.kt`
- Create: `app/src/main/java/com/mobisentinel/app/speech/AndroidSpeechController.kt`
- Test: `app/src/test/java/com/mobisentinel/app/speech/AnnouncementQueueTest.kt`
- Test: `app/src/test/java/com/mobisentinel/app/speech/PortugueseMessageFactoryTest.kt`

**Interfaces:**
- Consumes: `ConfirmedTransition` and Android `TextToSpeech`.
- Produces: `SpeechController.announce`, `testVoice`, `availability`, and `close`; exact Portuguese event copy; deterministic cross-transport ordering.

- [x] **Step 1: Define speech types and contracts**

```kotlin
enum class SpeechAvailability { INITIALIZING, READY, UNAVAILABLE }

data class Announcement(val transport: Transport, val text: String)

interface SpeechController {
    val availability: kotlinx.coroutines.flow.StateFlow<SpeechAvailability>
    fun announce(announcement: Announcement)
    fun testVoice()
    fun close()
}
```

- [x] **Step 2: Write exact-copy message tests**

Assert the current state maps to these strings:

```kotlin
Transport.WIFI + DISCONNECTED -> "Wi-Fi desconectado."
Transport.WIFI + CONNECTED_NO_INTERNET -> "Wi-Fi conectado, mas sem acesso à internet."
Transport.WIFI + CONNECTED -> "Acesso à internet por Wi-Fi restabelecido."
Transport.CELLULAR + DISCONNECTED -> "Dados móveis desconectados."
Transport.CELLULAR + CONNECTED_NO_INTERNET -> "Dados móveis conectados, mas sem acesso à internet."
Transport.CELLULAR + CONNECTED -> "Acesso à internet por dados móveis restabelecido."
```

Implement `PortugueseMessageFactory.from(ConfirmedTransition): Announcement` with an exhaustive `when` and no Android dependency.

- [x] **Step 3: Write queue tests first**

Cover: first offer starts immediately; second transport waits; a newer pending Wi-Fi event replaces the older pending Wi-Fi event in place; completing current returns the next item; completing the last item returns null; clearing drops every pending item.

The public API is:

```kotlin
class AnnouncementQueue {
    fun offer(item: Announcement): Announcement?
    fun completeCurrent(): Announcement?
    fun clear()
}
```

`offer` returns the item only when the queue was idle and the caller must start speech. While an item is speaking, new items enter a pending deque; pending items with the same transport are replaced.

- [x] **Step 4: Run both test classes and verify failure**

Run:

```powershell
./gradlew testDebugUnitTest --tests '*.PortugueseMessageFactoryTest' --tests '*.AnnouncementQueueTest'
```

Expected: FAIL because factory and queue implementations do not exist.

- [x] **Step 5: Implement the pure queue**

```kotlin
class AnnouncementQueue {
    private var current: Announcement? = null
    private val pending = ArrayDeque<Announcement>()

    fun offer(item: Announcement): Announcement? {
        if (current == null) {
            current = item
            return item
        }
        val retained = pending.filterNot { it.transport == item.transport }
        pending.clear()
        pending.addAll(retained)
        pending.addLast(item)
        return null
    }

    fun completeCurrent(): Announcement? {
        current = pending.removeFirstOrNull()
        return current
    }

    fun clear() {
        current = null
        pending.clear()
    }
}
```

- [x] **Step 6: Implement Android Text-to-Speech lifecycle**

Initialize `TextToSpeech` with application context, set `Locale("pt", "BR")`, and expose `READY` only when initialization succeeds and `isLanguageAvailable` is not `LANG_MISSING_DATA` or `LANG_NOT_SUPPORTED`. Drop announcements received before `READY`; do not replay them later. Use `QUEUE_FLUSH` only for `testVoice`; normal announcements use one queue item at a time and `UtteranceProgressListener.onDone/onError` advances `AnnouncementQueue` on the main handler. `close()` clears the queue, stops speech, calls `shutdown()`, and marks `UNAVAILABLE`.

Add the Android 11 package-visibility query:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

- [x] **Step 7: Verify focused tests and compile**

Run:

```powershell
./gradlew testDebugUnitTest --tests '*.PortugueseMessageFactoryTest' --tests '*.AnnouncementQueueTest'
./gradlew assembleDebug
```

Expected: all focused tests pass and the debug APK builds.

- [x] **Step 8: Commit**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mobisentinel/app/speech app/src/test/java/com/mobisentinel/app/speech
git commit -m "feat: narrate confirmed events in Portuguese"
```

### Task 6: Monitoring Engine and In-Process State Store

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/MonitoringStateStore.kt`
- Create: `app/src/main/java/com/mobisentinel/app/monitoring/MonitoringEngine.kt`
- Test: `app/src/test/java/com/mobisentinel/app/monitoring/MonitoringEngineTest.kt`

**Interfaces:**
- Consumes: `NetworkObserver`, `SettingsRepository`, `SpeechController`, `TransitionCoordinator`, and `CoroutineScope`.
- Produces: `MonitoringStateStore.snapshot: StateFlow<MonitoringSnapshot>`, idempotent `MonitoringEngine.start()/stop()`, and `MonitoringEngine.testVoice()`.

- [x] **Step 1: Create state-store API**

```kotlin
class MonitoringStateStore {
    private val mutable = MutableStateFlow(MonitoringSnapshot())
    val snapshot: StateFlow<MonitoringSnapshot> = mutable.asStateFlow()

    fun setServiceActive(active: Boolean) = mutable.update { it.copy(serviceActive = active) }

    fun setState(transport: Transport, state: ConnectivityState) = mutable.update {
        when (transport) {
            Transport.WIFI -> it.copy(wifi = state)
            Transport.CELLULAR -> it.copy(cellular = state)
        }
    }
}
```

- [x] **Step 2: Write engine lifecycle and policy tests**

Build fakes backed by `MutableSharedFlow<TransportSnapshot>`, `MutableStateFlow<MonitoringSettings>`, and a recording speech controller. Verify separately:

- start is idempotent and calls `NetworkObserver.start()` once;
- stop is idempotent, stops observation, closes both coordinators and speech, and marks service inactive;
- initial Wi-Fi and cellular snapshots update the store without speech;
- confirmed loss and recovery update the store and announce exact copy;
- Wi-Fi narration disabled suppresses only Wi-Fi;
- cellular narration disabled suppresses only cellular;
- settings changes are used by subsequent transitions;
- `testVoice()` delegates once to the active speech controller;
- no events are handled after stop.

- [x] **Step 3: Run engine tests and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*.MonitoringEngineTest'`

Expected: FAIL because `MonitoringEngine` is missing.

- [x] **Step 4: Implement orchestration**

On `start()`, obtain the latest settings with `settings.first()`, keep them current in a private volatile field via a collection job, create one `TransitionCoordinator` per transport, collect `NetworkObserver.states`, and route snapshots by transport. The coordinator's state callback updates `MonitoringStateStore`; the transition callback checks `currentSettings.narrationEnabled(transport)` before using `PortugueseMessageFactory` and `SpeechController.announce`.

Use a child `SupervisorJob` added to the injected parent scope. `testVoice()` delegates to `SpeechController.testVoice()` only while started. `stop()` cancels that job, closes coordinators, stops the observer, closes speech, and marks the store inactive. Synchronize start/stop with a private lock and boolean.

- [x] **Step 5: Run engine and all unit tests**

Run:

```powershell
./gradlew testDebugUnitTest --tests '*.MonitoringEngineTest'
./gradlew testDebugUnitTest
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/mobisentinel/app/monitoring app/src/test/java/com/mobisentinel/app/monitoring
git commit -m "feat: orchestrate continuous connection monitoring"
```

### Task 7: Foreground Service, Notification, Boot Restart, and Dependency Graph

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/MobiSentinelApplication.kt`
- Create: `app/src/main/java/com/mobisentinel/app/service/MonitoringNotification.kt`
- Create: `app/src/main/java/com/mobisentinel/app/service/MonitoringService.kt`
- Create: `app/src/main/java/com/mobisentinel/app/service/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/mobisentinel/app/service/MonitoringNotificationTextTest.kt`

**Interfaces:**
- Consumes: the monitoring engine and settings repository.
- Produces: `MonitoringService.start(context)`, `MonitoringService.stop(context)`, an ongoing notification, and conditional boot/package-replacement restart.

- [x] **Step 1: Add service and boot declarations**

Add these permissions:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Set `android:name=".MobiSentinelApplication"` on `<application>`. Declare a non-exported service with `android:foregroundServiceType="specialUse"` and:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="Continuous Wi-Fi and cellular availability monitoring for immediate spoken user alerts" />
```

Declare an exported `BootReceiver` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Do not add location or phone-state permissions.

- [x] **Step 2: Create the application graph**

Define one process-level `DataStore` with `val Context.settingsDataStore by preferencesDataStore("monitoring_settings")`. `MobiSentinelApplication` exposes:

```kotlin
val settingsRepository: SettingsRepository
val monitoringStateStore: MonitoringStateStore
val speechAvailability: StateFlow<SpeechAvailability>
fun createMonitoringEngine(scope: CoroutineScope): MonitoringEngine
```

The application owns a private `MutableStateFlow<SpeechAvailability>` exposed as `speechAvailability`. The factory creates a fresh `AndroidNetworkObserver`, `AndroidSpeechController`, and `MonitoringEngine`; pass the shared speech-availability flow into each new speech controller so the UI follows the active controller across service recreation. Shared objects are the repository, state store, and availability flow. No DI framework is added.

- [x] **Step 3: Write notification-summary tests**

Extract `MonitoringNotification.summary(snapshot)` as a pure function. Assert these examples:

```kotlin
MonitoringSnapshot(null, null, true) -> "Wi-Fi: verificando • Dados móveis: verificando"
MonitoringSnapshot(CONNECTED, DISCONNECTED, true) -> "Wi-Fi: com internet • Dados móveis: desconectados"
MonitoringSnapshot(CONNECTED_NO_INTERNET, CONNECTED, true) -> "Wi-Fi: sem internet • Dados móveis: com internet"
```

- [x] **Step 4: Run the summary test and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*.MonitoringNotificationTextTest'`

Expected: FAIL because `MonitoringNotification` does not exist.

- [x] **Step 5: Implement the notification**

Create channel ID `mobisentinel_monitoring`, channel name `Monitoramento de conexões`, importance `LOW`, no sound, and no vibration. Build an ongoing `CATEGORY_SERVICE` notification titled `MobiSentinel monitorando conexões`, containing `summary(snapshot)`, opening `MainActivity`, and exposing an action labeled `Parar` that targets `MonitoringService.ACTION_STOP` through an immutable service `PendingIntent`.

Use a stable notification ID of `1001`. On Android 34+, start it with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` through `ServiceCompat.startForeground`.

- [x] **Step 6: Implement service lifecycle**

```kotlin
class MonitoringService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private lateinit var engine: MonitoringEngine
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MobiSentinelApplication
        engine = app.createMonitoringEngine(scope)
        MonitoringNotification.createChannel(this)
        MonitoringNotification.startForeground(this, app.monitoringStateStore.snapshot.value)
        engine.start()
        notificationJob = scope.launch {
            app.monitoringStateStore.snapshot.collect {
                MonitoringNotification.update(this@MonitoringService, it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopFromUser()
        if (intent?.action == ACTION_TEST_VOICE) engine.testVoice()
        return START_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        engine.stop()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
```

`stopFromUser()` launches a final coroutine that sets `monitoringEnabled=false`, then switches to main dispatcher to remove the foreground notification and call `stopSelf()`. Companion functions use `ContextCompat.startForegroundService`; `stop(context)` sends `ACTION_STOP` rather than directly calling `stopService`. `testVoice(context)` sends `ACTION_TEST_VOICE` to the already active service and is ignored by the UI while monitoring is inactive.

- [x] **Step 7: Implement boot restart**

Use `goAsync()`, an IO `CoroutineScope`, and `settings.first()`. Start the service only when `monitoringEnabled` is true. Always call `PendingResult.finish()` in `finally`. Ignore unrelated actions.

- [x] **Step 8: Verify compile, tests, and manifest merge**

Run:

```powershell
./gradlew testDebugUnitTest --tests '*.MonitoringNotificationTextTest'
./gradlew processDebugMainManifest assembleDebug
```

Expected: tests pass, manifest merge succeeds, and APK builds without missing foreground-service-type errors.

- [x] **Step 9: Commit**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mobisentinel/app/MobiSentinelApplication.kt app/src/main/java/com/mobisentinel/app/service app/src/test/java/com/mobisentinel/app/service
git commit -m "feat: run monitoring as a restartable foreground service"
```

### Task 8: ViewModel, Main Screen, and Settings Screen

**Files:**
- Create: `app/src/main/java/com/mobisentinel/app/ui/MainUiState.kt`
- Create: `app/src/main/java/com/mobisentinel/app/ui/MainViewModel.kt`
- Create: `app/src/main/java/com/mobisentinel/app/ui/MobiSentinelApp.kt`
- Create: `app/src/main/java/com/mobisentinel/app/ui/MainScreen.kt`
- Create: `app/src/main/java/com/mobisentinel/app/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/mobisentinel/app/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/mobisentinel/app/ui/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `MonitoringStateStore`, `SettingsRepository`, the shared speech-availability flow, and service callbacks supplied by `MainActivity`.
- Produces: observable `MainUiState`, an accessible two-screen UI, explicit activation, stop, independent narration toggles, delay controls, and voice test.

- [ ] **Step 1: Define UI state**

```kotlin
data class MainUiState(
    val snapshot: MonitoringSnapshot = MonitoringSnapshot(),
    val settings: MonitoringSettings = MonitoringSettings(),
    val speechAvailability: SpeechAvailability = SpeechAvailability.INITIALIZING,
)
```

`MainViewModel` combines the state-store, settings, and shared speech-availability flows with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())`. Setter methods launch one repository update each. `activate()` persists `monitoringEnabled=true`; stopping and testing voice remain activity callbacks because they target service actions.

- [ ] **Step 2: Write ViewModel tests first**

Use fake state/settings flows. Verify initial combination, state changes, Wi-Fi toggle, cellular toggle, loss delay, recovery delay, and activation persistence. Each setter assertion uses `advanceUntilIdle()`.

- [ ] **Step 3: Run the ViewModel test and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*.MainViewModelTest'`

Expected: FAIL because `MainViewModel` is missing.

- [ ] **Step 4: Implement ViewModel and factory**

Implement `MainViewModel.Factory(application)` using `ViewModelProvider.Factory`. Do not hold an Activity or Service context. Expose only immutable state and methods that delegate to the repository.

- [ ] **Step 5: Build the accessible screens**

`MainScreen` contains:

- heading `MobiSentinel`;
- monitoring chip with text `Monitoramento ativo` or `Monitoramento inativo`;
- Wi-Fi card tagged `wifi_status_card`;
- cellular card tagged `cellular_status_card`;
- each card has icon, transport label, and one of `Verificando`, `Desconectado`, `Conectado sem internet`, `Conectado com internet`;
- activation button tagged `activate_monitoring` when monitoring is disabled;
- settings button tagged `open_settings`.

Each card sets a complete content description such as `Wi-Fi: conectado com internet`; color is never the only indicator.

`SettingsScreen` contains switches tagged `narrate_wifi` and `narrate_cellular`, sliders tagged `loss_delay` and `recovery_delay` with integer values 0–60, displayed values in seconds, a `Testar narração` button, an `Abrir configurações de voz` button when speech is unavailable, a `Parar monitoramento` button when active, and a back button. Use exact defaults from `MonitoringSettings`.

For `Abrir configurações de voz`, `MainActivity` first resolves `Intent("com.android.settings.TTS_SETTINGS")`; if unavailable it resolves `Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)`; if neither resolves it opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`. Check `resolveActivity(packageManager)` before every launch so the action never crashes on a vendor-specific Android build.

- [ ] **Step 6: Wire first-run activation and notification permission**

In `MainActivity`, register `ActivityResultContracts.RequestPermission`. When the user taps activate:

1. Persist activation through the ViewModel.
2. On API 33+, request `POST_NOTIFICATIONS` if not granted.
3. Start `MonitoringService` after the permission result, even when denied, because Android still exposes foreground-service activity in system UI.
4. Below API 33, start immediately.

`MobiSentinelApp` uses `rememberSaveable` for a local `MAIN`/`SETTINGS` destination; no Navigation dependency is added.

- [ ] **Step 7: Verify ViewModel tests and APK**

Run:

```powershell
./gradlew testDebugUnitTest --tests '*.MainViewModelTest'
./gradlew assembleDebug
```

Expected: ViewModel tests pass and Compose sources compile.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/mobisentinel/app/MainActivity.kt app/src/main/java/com/mobisentinel/app/ui app/src/main/res/values/strings.xml app/src/test/java/com/mobisentinel/app/ui
git commit -m "feat: add status and monitoring settings screens"
```

### Task 9: Compose and Lifecycle Integration Tests

**Files:**
- Create: `app/src/androidTest/java/com/mobisentinel/app/ui/MainScreenTest.kt`
- Create: `app/src/androidTest/java/com/mobisentinel/app/ui/SettingsScreenTest.kt`
- Create: `app/src/androidTest/java/com/mobisentinel/app/service/BootReceiverTest.kt`
- Modify: `app/src/main/java/com/mobisentinel/app/service/BootReceiver.kt` only if tests expose a lifecycle defect.

**Interfaces:**
- Consumes: public Compose screen parameters and `BootReceiver` decision logic.
- Produces: regression coverage for status semantics, setting controls, navigation, and conditional boot restart.

- [ ] **Step 1: Write main-screen Compose tests**

Use `createComposeRule()` and render `MainScreen` directly. Verify:

```kotlin
onNodeWithTag("wifi_status_card").assertContentDescriptionEquals("Wi-Fi: conectado com internet")
onNodeWithTag("cellular_status_card").assertContentDescriptionEquals("Dados móveis: conectado sem internet")
onNodeWithTag("activate_monitoring").assertIsDisplayed()
```

Also verify the activation and settings callbacks are each invoked exactly once on click.

- [ ] **Step 2: Write settings-screen Compose tests**

Render active settings with Wi-Fi narration on, cellular narration off, loss 5, recovery 2. Assert switch states and displayed delay labels; perform clicks and semantic slider changes; verify callbacks receive `false`, `true`, `12`, and `4`; verify voice-settings button appears only for `UNAVAILABLE`; verify test-voice and stop callbacks. Render inactive monitoring separately and verify the test-voice action is disabled.

- [ ] **Step 3: Extract and test boot decision**

Keep Android dispatch thin by extracting:

```kotlin
internal fun shouldRestartMonitoring(action: String?, enabled: Boolean): Boolean =
    enabled && (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED)
```

Test the two allowed actions, a null action, unrelated action, and disabled monitoring.

- [ ] **Step 4: Run all instrumented tests**

Run:

```powershell
./gradlew connectedDebugAndroidTest
```

Expected: all DataStore and Compose instrumentation tests pass on `emulator-5554`.

- [ ] **Step 5: Run all JVM tests**

Run:

```powershell
./gradlew testDebugUnitTest
```

Expected: all coordinator, tracker, message, queue, engine, notification, ViewModel, and boot-decision tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/androidTest app/src/main/java/com/mobisentinel/app/service/BootReceiver.kt
git commit -m "test: cover MobiSentinel UI and restart policy"
```

### Task 10: Emulator Validation and Developer Handoff

**Files:**
- Create: `README.md`
- Create: `docs/testing/manual-test-matrix.md`
- Modify: `docs/superpowers/specs/2026-07-16-mobisentinel-design.md` only to link the implementation plan and recorded verification evidence.

**Interfaces:**
- Consumes: debug APK, active API 35 emulator, and `adb` at the installed SDK path.
- Produces: repeatable build/run instructions, recorded emulator evidence, physical-device checklist, and a clean repository ready to relocate.

- [ ] **Step 1: Install and launch the debug build**

```powershell
$adb = 'C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe'
./gradlew installDebug
& $adb shell am start -n com.mobisentinel.app/.MainActivity
```

Expected: `Starting: Intent` and the MobiSentinel main screen on `emulator-5554`.

- [ ] **Step 2: Validate activation and foreground service**

Activate monitoring in the UI, grant notifications, then run:

```powershell
& $adb shell dumpsys activity services com.mobisentinel.app
& $adb shell dumpsys notification --noredact
```

Expected: `MonitoringService` is running and notification ID `1001` appears on channel `mobisentinel_monitoring`.

- [ ] **Step 3: Validate Wi-Fi transitions and debounce**

```powershell
& $adb shell svc wifi disable
Start-Sleep -Seconds 6
& $adb shell svc wifi enable
Start-Sleep -Seconds 3
```

Expected: UI/notification show Wi-Fi disconnected after about 5 seconds and restored after about 2 stable seconds; one spoken message occurs for each confirmed transition when Wi-Fi narration is enabled. Repeat with narration disabled and confirm visual updates remain but voice is silent.

- [ ] **Step 4: Validate mode changes and restart**

Toggle airplane mode through emulator quick settings and verify both transports settle to disconnected. Re-enable connectivity, then reboot:

```powershell
& $adb reboot
& $adb wait-for-device
& $adb shell am wait-for-broadcast-idle
& $adb shell dumpsys activity services com.mobisentinel.app
```

Expected: the service is present after boot when monitoring was enabled. Use the notification `Parar` action, reboot again, and confirm the service does not restart.

- [ ] **Step 5: Run the complete verification gate**

```powershell
./gradlew clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
git status --short
```

Expected: every Gradle task reports `BUILD SUCCESSFUL`; `git status --short` shows only the documentation files being prepared in this task.

- [ ] **Step 6: Write the handoff documentation**

`README.md` must include prerequisites, SDK path, build/test/install commands, architecture summary, permissions with reasons, how to activate/stop monitoring, and known limitations. `manual-test-matrix.md` records pass/fail and evidence for Wi-Fi, no-internet/captive portal, mode airplane, reboot, TTS unavailable, and physical cellular loss/recovery. Mark physical cellular validation as a required release gate, not as completed emulator coverage.

Record that Google Play publication later requires a foreground-service declaration and review for `specialUse`; publication remains outside this MVP.

- [ ] **Step 7: Commit documentation and evidence**

```powershell
git add README.md docs
git commit -m "docs: add build and device validation guide"
git status --short --branch
```

Expected: clean working tree on the implementation branch.

## Final Relocation

This Codex session can write only inside its authorized workspace. After all verification passes, relocate the complete `MobiSentinel` repository to `C:\projects\MobiSentinel` from a session that has that directory as an authorized workspace, or perform this non-destructive copy manually:

```powershell
Copy-Item -LiteralPath 'C:\Users\Marco\Documents\Codex\2026-07-16\estou-com-uma-ideia-de-um\MobiSentinel' -Destination 'C:\projects\MobiSentinel' -Recurse
```

Before copying, verify that `C:\projects\MobiSentinel` does not already exist. Do not overwrite an existing project.

## Authoritative Version and Platform References

- Android Studio Quail 1 supports AGP through 9.2: https://developer.android.com/build/releases/about-agp
- AGP 8.13 supports API 36.1, Gradle 8.13, and JDK 17+: https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Compose BOM 2026.04.01 is the stable April 2026 release: https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release
- DataStore 1.2.1 release notes: https://developer.android.com/jetpack/androidx/releases/datastore
- Foreground service `specialUse` requirements: https://developer.android.com/reference/android/content/pm/ServiceInfo#FOREGROUND_SERVICE_TYPE_SPECIAL_USE
- Google Play foreground-service policy for later publication: https://support.google.com/googleplay/android-developer/answer/17190352
