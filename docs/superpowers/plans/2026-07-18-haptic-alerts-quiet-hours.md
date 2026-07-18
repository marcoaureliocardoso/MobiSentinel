# Haptic Alerts and Quiet Hours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar alertas hápticos opcionais por transporte e um horário silencioso diário que suprima voz e vibração sem interromper o diagnóstico de conectividade.

**Architecture:** Uma política Kotlin pura transforma cada `ConfirmedTransition`, o snapshot de `MonitoringSettings` e o minuto local em decisões independentes de narração e vibração. O `MonitoringEngine` aplica a decisão a controladores isolados; DataStore persiste as opções e a UI Compose edita os valores, enquanto um adaptador Android encapsula `VibratorManager`/`Vibrator` em modo best-effort.

**Tech Stack:** Kotlin 2.3.21, Android SDK 26–36, Coroutines 1.10.2, Preferences DataStore 1.2.1, Jetpack Compose Material 3, JUnit 4, AndroidX Test e GitHub Actions com emulador Android 35.

## Global Constraints

- Manter `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`, Java/Kotlin 17 e o pacote `br.com.marcocardoso.mobisentinel`.
- Não declarar `INTERNET`, não criar backend, telemetria, histórico, alarmes ou sondas externas.
- `vibrateWifi`, `vibrateCellular` e `quietHoursEnabled` começam em `false`.
- Horários persistidos começam em `22:00–07:00`; cada minuto deve estar entre `0` e `1439`, e início igual ao fim é inválido.
- A faixa silenciosa é diária, usa hora/fuso locais, inclui o início, exclui o fim, pode cruzar meia-noite e descarta eventos suprimidos.
- A faixa silenciosa suprime somente narração e vibração automáticas; diagnóstico, tela e notificação persistente continuam ativos.
- Perda háptica é `[120 ms, pausa 120 ms, 120 ms]`; recuperação é uma vibração de `350 ms`.
- Somente `CONNECTED -> {DISCONNECTED, CONNECTED_NO_INTERNET}` vibra como perda e somente `{DISCONNECTED, CONNECTED_NO_INTERNET} -> CONNECTED` vibra como recuperação.
- O teste manual executa perda e recuperação, ignora horário silencioso e seletores por transporte e espera `800 ms` após o término da perda.
- Não solicitar `ACCESS_NOTIFICATION_POLICY`, não prometer bypass do DND e tratar indisponibilidade/erro háptico como no-op.
- Teste físico é opcional; a suíte instrumentada obrigatória permanece no emulador Android 35 do GitHub Actions.

---

### Task 1: Modelar horário silencioso e política pura de alertas

**Files:**
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/monitoring/model/ConnectivityModels.kt`
- Create: `app/src/main/java/br/com/marcocardoso/mobisentinel/monitoring/alerts/AlertPolicy.kt`
- Create: `app/src/test/java/br/com/marcocardoso/mobisentinel/monitoring/alerts/AlertPolicyTest.kt`

**Interfaces:**
- Consumes: `ConfirmedTransition`, `ConnectivityState` e `Transport` existentes.
- Produces: `HapticPattern`, `AlertDecision`, `AlertPolicy.decide(ConfirmedTransition, MonitoringSettings, Int)`, `LocalMinuteProvider.currentMinuteOfDay()`, `MonitoringSettings.vibrationEnabled(Transport)` e `MonitoringSettings.isQuietAt(Int)`.

- [ ] **Step 1: Escrever os testes falhos da matriz de decisão**

Criar `AlertPolicyTest.kt` com testes parametrizados por loops simples que comprovem defaults, faixa diurna/noturna, limites, seletores independentes e transições:

```kotlin
package br.com.marcocardoso.mobisentinel.monitoring.alerts

import br.com.marcocardoso.mobisentinel.monitoring.model.ConfirmedTransition
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    private val policy = AlertPolicy()

    @Test
    fun newSettingsKeepHapticsAndQuietHoursDisabled() {
        val settings = MonitoringSettings()
        assertFalse(settings.vibrateWifi)
        assertFalse(settings.vibrateCellular)
        assertFalse(settings.quietHoursEnabled)
        assertEquals(22 * 60, settings.quietStartMinuteOfDay)
        assertEquals(7 * 60, settings.quietEndMinuteOfDay)
    }

    @Test
    fun quietHoursUseInclusiveStartExclusiveEndAcrossMidnight() {
        val settings = MonitoringSettings(quietHoursEnabled = true)
        assertTrue(settings.isQuietAt(22 * 60))
        assertTrue(settings.isQuietAt(23 * 60 + 59))
        assertTrue(settings.isQuietAt(6 * 60 + 59))
        assertFalse(settings.isQuietAt(7 * 60))
        assertFalse(settings.isQuietAt(12 * 60))
    }

    @Test
    fun daytimeQuietHoursAndDisabledScheduleAreHandled() {
        val daytime = MonitoringSettings(
            quietHoursEnabled = true,
            quietStartMinuteOfDay = 8 * 60,
            quietEndMinuteOfDay = 17 * 60,
        )
        assertTrue(daytime.isQuietAt(8 * 60))
        assertFalse(daytime.isQuietAt(17 * 60))
        assertFalse(daytime.copy(quietHoursEnabled = false).isQuietAt(12 * 60))
    }

    @Test
    fun invalidMinutesAndEqualEndpointsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(quietStartMinuteOfDay = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(quietEndMinuteOfDay = 1440)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(quietStartMinuteOfDay = 420, quietEndMinuteOfDay = 420)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings().isQuietAt(1440)
        }
    }

    @Test
    fun lossAndRecoveryOnlyCrossTheAvailableBoundary() {
        val settings = MonitoringSettings(vibrateWifi = true)
        assertEquals(
            HapticPattern.LOSS,
            decide(ConnectivityState.CONNECTED, ConnectivityState.DISCONNECTED, settings).hapticPattern,
        )
        assertEquals(
            HapticPattern.LOSS,
            decide(ConnectivityState.CONNECTED, ConnectivityState.CONNECTED_NO_INTERNET, settings).hapticPattern,
        )
        assertEquals(
            HapticPattern.RECOVERY,
            decide(ConnectivityState.DISCONNECTED, ConnectivityState.CONNECTED, settings).hapticPattern,
        )
        assertNull(
            decide(
                ConnectivityState.DISCONNECTED,
                ConnectivityState.CONNECTED_NO_INTERNET,
                settings,
            ).hapticPattern,
        )
        assertNull(
            decide(
                ConnectivityState.CONNECTED_NO_INTERNET,
                ConnectivityState.DISCONNECTED,
                settings,
            ).hapticPattern,
        )
    }

    @Test
    fun transportSelectorsAreIndependent() {
        val settings = MonitoringSettings(
            narrateWifi = false,
            narrateCellular = true,
            vibrateWifi = true,
            vibrateCellular = false,
        )
        val wifi = policy.decide(
            transition(Transport.WIFI, ConnectivityState.CONNECTED, ConnectivityState.DISCONNECTED),
            settings,
            12 * 60,
        )
        val cellular = policy.decide(
            transition(Transport.CELLULAR, ConnectivityState.CONNECTED, ConnectivityState.DISCONNECTED),
            settings,
            12 * 60,
        )
        assertEquals(AlertDecision(narrate = false, hapticPattern = HapticPattern.LOSS), wifi)
        assertEquals(AlertDecision(narrate = true, hapticPattern = null), cellular)
    }

    @Test
    fun quietHoursSuppressBothEffects() {
        val settings = MonitoringSettings(
            vibrateWifi = true,
            quietHoursEnabled = true,
        )
        assertEquals(
            AlertDecision(narrate = false, hapticPattern = null),
            decide(ConnectivityState.CONNECTED, ConnectivityState.DISCONNECTED, settings, 22 * 60),
        )
    }

    private fun decide(
        previous: ConnectivityState,
        current: ConnectivityState,
        settings: MonitoringSettings,
        minuteOfDay: Int = 12 * 60,
    ): AlertDecision = policy.decide(
        transition(Transport.WIFI, previous, current),
        settings,
        minuteOfDay,
    )

    private fun transition(
        transport: Transport,
        previous: ConnectivityState,
        current: ConnectivityState,
    ) = ConfirmedTransition(transport, previous, current)
}
```

- [ ] **Step 2: Executar o teste e confirmar a falha correta**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.AlertPolicyTest"
```

Expected: `FAILED` por ausência de `AlertPolicy`, `AlertDecision`, `HapticPattern` e dos novos campos/helpers.

- [ ] **Step 3: Implementar o modelo e a política mínima**

Ampliar `MonitoringSettings` com os cinco campos persistidos e estes invariantes/helpers:

```kotlin
data class MonitoringSettings(
    val monitoringEnabled: Boolean = false,
    val narrateWifi: Boolean = true,
    val narrateCellular: Boolean = true,
    val vibrateWifi: Boolean = false,
    val vibrateCellular: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinuteOfDay: Int = DEFAULT_QUIET_START_MINUTE,
    val quietEndMinuteOfDay: Int = DEFAULT_QUIET_END_MINUTE,
    val lossDelaySeconds: Int = 5,
    val recoveryDelaySeconds: Int = 2,
) {
    init {
        require(lossDelaySeconds in 0..60)
        require(recoveryDelaySeconds in 0..60)
        require(quietStartMinuteOfDay in MINUTE_OF_DAY_RANGE)
        require(quietEndMinuteOfDay in MINUTE_OF_DAY_RANGE)
        require(quietStartMinuteOfDay != quietEndMinuteOfDay)
    }

    fun narrationEnabled(transport: Transport): Boolean = when (transport) {
        Transport.WIFI -> narrateWifi
        Transport.CELLULAR -> narrateCellular
    }

    fun vibrationEnabled(transport: Transport): Boolean = when (transport) {
        Transport.WIFI -> vibrateWifi
        Transport.CELLULAR -> vibrateCellular
    }

    fun isQuietAt(minuteOfDay: Int): Boolean {
        require(minuteOfDay in MINUTE_OF_DAY_RANGE)
        if (!quietHoursEnabled) return false
        return if (quietStartMinuteOfDay < quietEndMinuteOfDay) {
            minuteOfDay >= quietStartMinuteOfDay && minuteOfDay < quietEndMinuteOfDay
        } else {
            minuteOfDay >= quietStartMinuteOfDay || minuteOfDay < quietEndMinuteOfDay
        }
    }

    companion object {
        val MINUTE_OF_DAY_RANGE = 0..1439
        const val DEFAULT_QUIET_START_MINUTE = 22 * 60
        const val DEFAULT_QUIET_END_MINUTE = 7 * 60
    }
}
```

Criar `AlertPolicy.kt`:

```kotlin
package br.com.marcocardoso.mobisentinel.monitoring.alerts

import br.com.marcocardoso.mobisentinel.monitoring.model.ConfirmedTransition
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import java.time.LocalTime

enum class HapticPattern { LOSS, RECOVERY }

data class AlertDecision(
    val narrate: Boolean,
    val hapticPattern: HapticPattern?,
)

fun interface LocalMinuteProvider {
    fun currentMinuteOfDay(): Int
}

object SystemLocalMinuteProvider : LocalMinuteProvider {
    override fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
}

class AlertPolicy {
    fun decide(
        transition: ConfirmedTransition,
        settings: MonitoringSettings,
        minuteOfDay: Int,
    ): AlertDecision {
        if (settings.isQuietAt(minuteOfDay)) {
            return AlertDecision(narrate = false, hapticPattern = null)
        }
        val hapticPattern = if (settings.vibrationEnabled(transition.transport)) {
            when {
                transition.previous == ConnectivityState.CONNECTED &&
                    transition.current != ConnectivityState.CONNECTED -> HapticPattern.LOSS
                transition.previous != ConnectivityState.CONNECTED &&
                    transition.current == ConnectivityState.CONNECTED -> HapticPattern.RECOVERY
                else -> null
            }
        } else {
            null
        }
        return AlertDecision(
            narrate = settings.narrationEnabled(transition.transport),
            hapticPattern = hapticPattern,
        )
    }
}
```

- [ ] **Step 4: Executar testes focados e a regressão do coordenador**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.AlertPolicyTest" --tests "*.TransitionCoordinatorTest"
```

Expected: `BUILD SUCCESSFUL`; os novos testes passam e os testes de delay/narração existentes continuam verdes.

- [ ] **Step 5: Registrar a entrega de domínio**

```powershell
git add app/src/main/java/br/com/marcocardoso/mobisentinel/monitoring/model/ConnectivityModels.kt app/src/main/java/br/com/marcocardoso/mobisentinel/monitoring/alerts/AlertPolicy.kt app/src/test/java/br/com/marcocardoso/mobisentinel/monitoring/alerts/AlertPolicyTest.kt
git commit -m "feat: define haptic alert policy"
```

---

### Task 2: Persistir vibração e horário silencioso de forma atômica

**Files:**
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/preferences/SettingsRepository.kt`
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/preferences/DataStoreSettingsRepository.kt`
- Modify: `app/src/androidTest/java/br/com/marcocardoso/mobisentinel/preferences/DataStoreSettingsRepositoryTest.kt`
- Modify: `app/src/test/java/br/com/marcocardoso/mobisentinel/monitoring/MonitoringEngineTest.kt`
- Modify: `app/src/test/java/br/com/marcocardoso/mobisentinel/ui/MainViewModelTest.kt`

**Interfaces:**
- Consumes: constantes e invariantes de `MonitoringSettings` da Task 1.
- Produces: `setVibrateWifi(Boolean)`, `setVibrateCellular(Boolean)`, `setQuietHoursEnabled(Boolean)` e `setQuietHours(Int, Int)` em `SettingsRepository`.

- [ ] **Step 1: Ampliar primeiro os testes de DataStore**

Em `DataStoreSettingsRepositoryTest`, manter referência ao `DataStore<Preferences>` no fixture e acrescentar:

```kotlin
@Test
fun hapticAndQuietHourSettersPersistEveryValue() = runTest {
    val fixture = createFixture(backgroundScope)
    fixture.repository.setVibrateWifi(true)
    fixture.repository.setVibrateCellular(true)
    fixture.repository.setQuietHoursEnabled(true)
    fixture.repository.setQuietHours(8 * 60, 17 * 60)

    assertEquals(
        MonitoringSettings(
            vibrateWifi = true,
            vibrateCellular = true,
            quietHoursEnabled = true,
            quietStartMinuteOfDay = 8 * 60,
            quietEndMinuteOfDay = 17 * 60,
        ),
        fixture.repository.settings.first(),
    )
}

@Test
fun invalidQuietHourPairIsRejectedWithoutPartialWrite() = runTest {
    val fixture = createFixture(backgroundScope)
    fixture.repository.setQuietHours(8 * 60, 17 * 60)

    val failure = runCatching { fixture.repository.setQuietHours(420, 420) }.exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals(8 * 60, fixture.repository.settings.first().quietStartMinuteOfDay)
    assertEquals(17 * 60, fixture.repository.settings.first().quietEndMinuteOfDay)
}

@Test
fun corruptPersistedQuietPairFallsBackToBothDefaults() = runTest {
    val fixture = createFixture(backgroundScope)
    fixture.dataStore.edit {
        it[intPreferencesKey("quiet_start_minute_of_day")] = 420
        it[intPreferencesKey("quiet_end_minute_of_day")] = 420
    }

    val settings = fixture.repository.settings.first()

    assertEquals(MonitoringSettings.DEFAULT_QUIET_START_MINUTE, settings.quietStartMinuteOfDay)
    assertEquals(MonitoringSettings.DEFAULT_QUIET_END_MINUTE, settings.quietEndMinuteOfDay)
}

@Test
fun outOfRangePersistedQuietPairFallsBackToBothDefaults() = runTest {
    val fixture = createFixture(backgroundScope)
    fixture.dataStore.edit {
        it[intPreferencesKey("quiet_start_minute_of_day")] = 1440
        it[intPreferencesKey("quiet_end_minute_of_day")] = 17 * 60
    }

    val settings = fixture.repository.settings.first()

    assertEquals(MonitoringSettings.DEFAULT_QUIET_START_MINUTE, settings.quietStartMinuteOfDay)
    assertEquals(MonitoringSettings.DEFAULT_QUIET_END_MINUTE, settings.quietEndMinuteOfDay)
}

private data class Fixture(
    val dataStore: DataStore<Preferences>,
    val repository: DataStoreSettingsRepository,
)
```

Atualizar `createRepository` para `createFixture` e retornar o mesmo DataStore usado pelo repositório.

- [ ] **Step 2: Executar o teste instrumentado e confirmar a falha**

Run em um alvo Android 35 disponível:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.marcocardoso.mobisentinel.preferences.DataStoreSettingsRepositoryTest
```

Expected: `FAILED` por métodos/chaves ausentes.

- [ ] **Step 3: Implementar contrato, chaves, normalização e escrita atômica**

Adicionar ao contrato:

```kotlin
suspend fun setVibrateWifi(enabled: Boolean)
suspend fun setVibrateCellular(enabled: Boolean)
suspend fun setQuietHoursEnabled(enabled: Boolean)
suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int)
```

No mapeamento do DataStore, normalizar sempre o par completo:

```kotlin
private fun Preferences.quietHours(): Pair<Int, Int> {
    val start = this[Keys.quietStartMinuteOfDay]
        ?: MonitoringSettings.DEFAULT_QUIET_START_MINUTE
    val end = this[Keys.quietEndMinuteOfDay]
        ?: MonitoringSettings.DEFAULT_QUIET_END_MINUTE
    return if (
        start in MonitoringSettings.MINUTE_OF_DAY_RANGE &&
        end in MonitoringSettings.MINUTE_OF_DAY_RANGE &&
        start != end
    ) {
        start to end
    } else {
        MonitoringSettings.DEFAULT_QUIET_START_MINUTE to
            MonitoringSettings.DEFAULT_QUIET_END_MINUTE
    }
}
```

Construir o modelo com:

```kotlin
val (quietStart, quietEnd) = preferences.quietHours()
MonitoringSettings(
    monitoringEnabled = preferences[Keys.monitoringEnabled] ?: false,
    narrateWifi = preferences[Keys.narrateWifi] ?: true,
    narrateCellular = preferences[Keys.narrateCellular] ?: true,
    vibrateWifi = preferences[Keys.vibrateWifi] ?: false,
    vibrateCellular = preferences[Keys.vibrateCellular] ?: false,
    quietHoursEnabled = preferences[Keys.quietHoursEnabled] ?: false,
    quietStartMinuteOfDay = quietStart,
    quietEndMinuteOfDay = quietEnd,
    lossDelaySeconds = (preferences[Keys.lossDelaySeconds] ?: 5).coerceIn(0, 60),
    recoveryDelaySeconds = (preferences[Keys.recoveryDelaySeconds] ?: 2).coerceIn(0, 60),
)
```

Implementar os setters, validando antes de `edit`:

```kotlin
override suspend fun setVibrateWifi(enabled: Boolean) {
    dataStore.edit { it[Keys.vibrateWifi] = enabled }
}

override suspend fun setVibrateCellular(enabled: Boolean) {
    dataStore.edit { it[Keys.vibrateCellular] = enabled }
}

override suspend fun setQuietHoursEnabled(enabled: Boolean) {
    dataStore.edit { it[Keys.quietHoursEnabled] = enabled }
}

override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
    require(startMinuteOfDay in MonitoringSettings.MINUTE_OF_DAY_RANGE)
    require(endMinuteOfDay in MonitoringSettings.MINUTE_OF_DAY_RANGE)
    require(startMinuteOfDay != endMinuteOfDay)
    dataStore.edit {
        it[Keys.quietStartMinuteOfDay] = startMinuteOfDay
        it[Keys.quietEndMinuteOfDay] = endMinuteOfDay
    }
}
```

Adicionar as chaves com nomes persistidos estáveis:

```kotlin
val vibrateWifi = booleanPreferencesKey("vibrate_wifi")
val vibrateCellular = booleanPreferencesKey("vibrate_cellular")
val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
val quietStartMinuteOfDay = intPreferencesKey("quiet_start_minute_of_day")
val quietEndMinuteOfDay = intPreferencesKey("quiet_end_minute_of_day")
```

Atualizar os fakes de `SettingsRepository` nos dois testes JVM com estes setters, além dos métodos já existentes:

```kotlin
override suspend fun setVibrateWifi(enabled: Boolean) {
    mutable.value = mutable.value.copy(vibrateWifi = enabled)
}

override suspend fun setVibrateCellular(enabled: Boolean) {
    mutable.value = mutable.value.copy(vibrateCellular = enabled)
}

override suspend fun setQuietHoursEnabled(enabled: Boolean) {
    mutable.value = mutable.value.copy(quietHoursEnabled = enabled)
}

override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
    mutable.value = mutable.value.copy(
        quietStartMinuteOfDay = startMinuteOfDay,
        quietEndMinuteOfDay = endMinuteOfDay,
    )
}
```

No fake de `MainViewModelTest`, substituir `mutable` pelo nome local `mutableSettings` usado naquela classe, sem alterar as assinaturas.

- [ ] **Step 4: Reexecutar persistência e compilação JVM**

Run:

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.marcocardoso.mobisentinel.preferences.DataStoreSettingsRepositoryTest
```

Expected: `BUILD SUCCESSFUL`; defaults, escrita, rejeição e normalização passam.

- [ ] **Step 5: Registrar a persistência**

```powershell
git add app/src/main/java/br/com/marcocardoso/mobisentinel/preferences app/src/androidTest/java/br/com/marcocardoso/mobisentinel/preferences/DataStoreSettingsRepositoryTest.kt app/src/test/java/br/com/marcocardoso/mobisentinel/monitoring/MonitoringEngineTest.kt app/src/test/java/br/com/marcocardoso/mobisentinel/ui/MainViewModelTest.kt
git commit -m "feat: persist haptic and quiet hour settings"
```

---

### Task 3: Encapsular vibração Android em controlador best-effort

**Files:**
- Create: `app/src/main/java/br/com/marcocardoso/mobisentinel/haptics/HapticController.kt`
- Create: `app/src/main/java/br/com/marcocardoso/mobisentinel/haptics/DefaultHapticController.kt`
- Create: `app/src/main/java/br/com/marcocardoso/mobisentinel/haptics/AndroidHapticController.kt`
- Create: `app/src/test/java/br/com/marcocardoso/mobisentinel/haptics/DefaultHapticControllerTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `scripts/tests/privacy-manifest-test.ps1`

**Interfaces:**
- Consumes: `HapticPattern` da Task 1 e `CoroutineScope` do serviço.
- Produces: `HapticController.isAvailable`, `play(HapticPattern)`, `testPatterns()` e `close()`; `AndroidHapticController.isAvailable(Context)`.

- [ ] **Step 1: Escrever testes falhos para padrão, cancelamento e tolerância a erro**

Criar um `FakeHapticDevice` e testar:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHapticControllerTest {
    @Test
    fun manualTestPlaysLossThenRecoveryAfterPatternAndGap() = runTest {
        val device = FakeHapticDevice()
        val controller = DefaultHapticController(backgroundScope, device)
        controller.testPatterns()
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS), device.played)

        advanceTimeBy(DefaultHapticController.LOSS_DURATION_MS + 799)
        runCurrent()
        assertEquals(1, device.played.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS, HapticPattern.RECOVERY), device.played)
    }

    @Test
    fun automaticPatternCancelsManualSequenceAndCurrentEffect() = runTest {
        val device = FakeHapticDevice()
        val controller = DefaultHapticController(backgroundScope, device)
        controller.testPatterns()
        runCurrent()
        controller.play(HapticPattern.RECOVERY)
        advanceUntilIdle()

        assertEquals(listOf(HapticPattern.LOSS, HapticPattern.RECOVERY), device.played)
        assertTrue(device.cancelCount >= 1)
    }

    @Test
    fun newerManualTestReplacesThePreviousSequence() = runTest {
        val device = FakeHapticDevice()
        val controller = DefaultHapticController(backgroundScope, device)
        controller.testPatterns()
        runCurrent()
        controller.testPatterns()
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf(HapticPattern.LOSS, HapticPattern.LOSS, HapticPattern.RECOVERY),
            device.played,
        )
    }

    @Test
    fun deviceFailureAndMissingHardwareBecomeSafeNoOps() = runTest {
        val missing = DefaultHapticController(
            backgroundScope,
            FakeHapticDevice(isAvailable = false),
        )
        missing.play(HapticPattern.LOSS)

        val failingDevice = FakeHapticDevice(failOnPlay = true)
        val failing = DefaultHapticController(backgroundScope, failingDevice)
        failing.play(HapticPattern.LOSS)

        assertFalse(missing.isAvailable)
        assertEquals(1, failingDevice.attemptCount)
    }

    @Test
    fun closeCancelsPendingManualRecovery() = runTest {
        val device = FakeHapticDevice()
        val controller = DefaultHapticController(backgroundScope, device)
        controller.testPatterns()
        runCurrent()
        controller.close()
        advanceUntilIdle()
        assertEquals(listOf(HapticPattern.LOSS), device.played)
    }

    private class FakeHapticDevice(
        override val isAvailable: Boolean = true,
        private val failOnPlay: Boolean = false,
    ) : HapticDevice {
        val played = mutableListOf<HapticPattern>()
        var attemptCount = 0
        var cancelCount = 0

        override fun play(pattern: HapticPattern) {
            attemptCount++
            if (failOnPlay) throw SecurityException("blocked by test device")
            played += pattern
        }

        override fun cancel() {
            cancelCount++
        }
    }
}
```

- [ ] **Step 2: Executar o teste e confirmar tipos ausentes**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.DefaultHapticControllerTest"
```

Expected: `FAILED` porque o controlador e o dispositivo ainda não existem.

- [ ] **Step 3: Criar controlador puro, adaptador Android e permissão**

Definir o contrato e a porta de dispositivo:

```kotlin
interface HapticController {
    val isAvailable: Boolean
    fun play(pattern: HapticPattern)
    fun testPatterns()
    fun close()
}

internal interface HapticDevice {
    val isAvailable: Boolean
    fun play(pattern: HapticPattern)
    fun cancel()
}
```

Implementar `DefaultHapticController` com `SupervisorJob`, sincronização e falhas isoladas:

```kotlin
internal class DefaultHapticController(
    parentScope: CoroutineScope,
    private val device: HapticDevice,
) : HapticController {
    private val lock = Any()
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private var manualJob: Job? = null

    override val isAvailable: Boolean
        get() = runCatching { device.isAvailable }.getOrDefault(false)

    override fun play(pattern: HapticPattern) = synchronized(lock) {
        manualJob?.cancel()
        manualJob = null
        safeCancel()
        safePlay(pattern)
    }

    override fun testPatterns() = synchronized(lock) {
        manualJob?.cancel()
        safeCancel()
        manualJob = scope.launch {
            safePlay(HapticPattern.LOSS)
            delay(LOSS_DURATION_MS + TEST_GAP_MS)
            safePlay(HapticPattern.RECOVERY)
        }
    }

    override fun close() = synchronized(lock) {
        manualJob?.cancel()
        manualJob = null
        job.cancel()
        safeCancel()
    }

    private fun safePlay(pattern: HapticPattern) {
        if (isAvailable) runCatching { device.play(pattern) }
    }

    private fun safeCancel() {
        runCatching { device.cancel() }
    }

    companion object {
        const val LOSS_DURATION_MS = 120L + 120L + 120L
        const val TEST_GAP_MS = 800L
    }
}
```

Implementar `AndroidHapticController.kt` sem acesso à política de DND:

```kotlin
package br.com.marcocardoso.mobisentinel.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import br.com.marcocardoso.mobisentinel.monitoring.alerts.HapticPattern
import kotlinx.coroutines.CoroutineScope

class AndroidHapticController(
    context: Context,
    scope: CoroutineScope,
) : HapticController {
    private val delegate = DefaultHapticController(scope, AndroidHapticDevice(context))

    override val isAvailable: Boolean
        get() = delegate.isAvailable

    override fun play(pattern: HapticPattern) = delegate.play(pattern)

    override fun testPatterns() = delegate.testPatterns()

    override fun close() = delegate.close()

    companion object {
        fun isAvailable(context: Context): Boolean = runCatching {
            AndroidHapticDevice(context).isAvailable
        }.getOrDefault(false)
    }
}

private class AndroidHapticDevice(context: Context) : HapticDevice {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override val isAvailable: Boolean
        get() = vibrator?.hasVibrator() == true

    override fun play(pattern: HapticPattern) {
        vibrator?.vibrate(effect(pattern), ATTRIBUTES)
    }

    override fun cancel() {
        vibrator?.cancel()
    }

    private fun effect(pattern: HapticPattern): VibrationEffect = when (pattern) {
        HapticPattern.LOSS -> VibrationEffect.createWaveform(
            longArrayOf(0, 120, 120, 120),
            -1,
        )
        HapticPattern.RECOVERY -> VibrationEffect.createOneShot(
            350,
            VibrationEffect.DEFAULT_AMPLITUDE,
        )
    }

    companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
```

Adicionar ao manifesto:

```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

Atualizar também a descrição de `FOREGROUND_SERVICE_SPECIAL_USE` para não restringi-la a voz:

```xml
android:value="Continuous Wi-Fi and cellular availability monitoring for immediate spoken and haptic user alerts"
```

Estender `privacy-manifest-test.ps1` com as duas asserções:

```powershell
if ($manifest -notmatch 'android\.permission\.VIBRATE') {
    throw 'Haptic alerts require the normal VIBRATE permission'
}
if ($manifest -match 'android\.permission\.ACCESS_NOTIFICATION_POLICY') {
    throw 'The app must not request DND policy access'
}
```

- [ ] **Step 4: Verificar controlador, manifesto e compilação das duas APIs**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.DefaultHapticControllerTest"
.\scripts\tests\privacy-manifest-test.ps1
.\gradlew.bat lintDebug assembleDebug
```

Expected: todos terminam com código `0`; lint não aponta chamada sem guarda de API e o manifesto contém somente a permissão normal `VIBRATE` adicional.

- [ ] **Step 5: Registrar o controlador háptico**

```powershell
git add app/src/main/java/br/com/marcocardoso/mobisentinel/haptics app/src/test/java/br/com/marcocardoso/mobisentinel/haptics app/src/main/AndroidManifest.xml scripts/tests/privacy-manifest-test.ps1
git commit -m "feat: add best effort Android haptics"
```

---

### Task 4: Orquestrar voz e vibração nas transições confirmadas

**Files:**
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/monitoring/MonitoringEngine.kt`
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/MobiSentinelApplication.kt`
- Modify: `app/src/test/java/br/com/marcocardoso/mobisentinel/monitoring/MonitoringEngineTest.kt`

**Interfaces:**
- Consumes: `AlertPolicy`, `LocalMinuteProvider`, `HapticController` e configurações persistidas.
- Produces: `MonitoringEngine.testHaptics()` e aplicação automática de `AlertDecision` após confirmação.

- [ ] **Step 1: Escrever testes falhos da integração e independência dos efeitos**

Ampliar o fixture com `RecordingHapticController`, `minuteProvider` mutável e `ThrowingSpeechController`. Adicionar casos que provem:

```kotlin
@Test
fun confirmedBoundaryTransitionsPlayExpectedHaptics() = runTest {
    val fixture = Fixture(
        backgroundScope,
        zeroDelaySettings.copy(vibrateWifi = true),
    )
    fixture.engine.start()
    runCurrent()
    fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
    fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
    runCurrent()
    fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED_NO_INTERNET)
    fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
    runCurrent()

    assertEquals(listOf(HapticPattern.LOSS, HapticPattern.RECOVERY), fixture.haptics.patterns)
}

@Test
fun quietHoursDiscardAutomaticVoiceAndHapticsButKeepConfirmedState() = runTest {
    val fixture = Fixture(
        backgroundScope,
        zeroDelaySettings.copy(vibrateWifi = true, quietHoursEnabled = true),
        minuteOfDay = 23 * 60,
    )
    fixture.engine.start()
    runCurrent()
    fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
    fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
    runCurrent()

    assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
    assertTrue(fixture.speech.announcements.isEmpty())
    assertTrue(fixture.haptics.patterns.isEmpty())
}

@Test
fun speechFailureDoesNotBlockHapticEffect() = runTest {
    val fixture = Fixture(
        backgroundScope,
        zeroDelaySettings.copy(vibrateWifi = true),
        speech = ThrowingSpeechController(),
    )
    fixture.engine.start()
    runCurrent()
    fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
    fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
    runCurrent()
    assertEquals(listOf(HapticPattern.LOSS), fixture.haptics.patterns)
}

@Test
fun hapticFailureDoesNotBlockSpeechOrConfirmedState() = runTest {
    val fixture = Fixture(
        backgroundScope,
        zeroDelaySettings.copy(vibrateWifi = true),
        haptics = ThrowingHapticController(),
    )
    fixture.engine.start()
    runCurrent()
    fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
    fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
    runCurrent()

    assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
    assertEquals(
        listOf(Announcement(Transport.WIFI, "Wi-Fi desconectado.")),
        fixture.speech.announcements,
    )
}

@Test
fun manualTestsBypassAutomaticPolicyOnlyWhileActive() = runTest {
    val fixture = Fixture(
        backgroundScope,
        zeroDelaySettings.copy(quietHoursEnabled = true),
        minuteOfDay = 23 * 60,
    )
    fixture.engine.start()
    runCurrent()
    fixture.engine.testVoice()
    fixture.engine.testHaptics()
    fixture.engine.stop()
    fixture.engine.testHaptics()

    assertEquals(1, fixture.speech.testVoiceCount)
    assertEquals(1, fixture.haptics.testCount)
    assertEquals(1, fixture.haptics.closeCount)
}
```

Alterar o fixture e os doubles do mesmo arquivo para assinaturas consistentes:

```kotlin
private class Fixture(
    scope: CoroutineScope,
    initialSettings: MonitoringSettings = zeroDelaySettings,
    minuteOfDay: Int = 12 * 60,
    val speech: RecordingSpeechController = RecordingSpeechController(),
    val haptics: RecordingHapticController = RecordingHapticController(),
) {
    val network = FakeNetworkObserver()
    val settings = FakeSettingsRepository(initialSettings)
    val store = MonitoringStateStore()
    val engine = MonitoringEngine(
        parentScope = scope,
        networkObserver = network,
        settingsRepository = settings,
        speechController = speech,
        hapticController = haptics,
        stateStore = store,
        localMinuteProvider = LocalMinuteProvider { minuteOfDay },
    )
}

private open class RecordingSpeechController : SpeechController {
    override val availability: StateFlow<SpeechAvailability> =
        MutableStateFlow(SpeechAvailability.READY)
    val announcements = mutableListOf<Announcement>()
    var testVoiceCount = 0
    var closeCount = 0

    override fun announce(announcement: Announcement) {
        announcements += announcement
    }

    override fun testVoice() {
        testVoiceCount++
    }

    override fun close() {
        closeCount++
    }
}

private class ThrowingSpeechController : RecordingSpeechController() {
    override fun announce(announcement: Announcement) {
        throw IllegalStateException("speech failure")
    }
}

private open class RecordingHapticController : HapticController {
    override val isAvailable = true
    val patterns = mutableListOf<HapticPattern>()
    var testCount = 0
    var closeCount = 0

    override fun play(pattern: HapticPattern) {
        patterns += pattern
    }

    override fun testPatterns() {
        testCount++
    }

    override fun close() {
        closeCount++
    }
}

private class ThrowingHapticController : RecordingHapticController() {
    override fun play(pattern: HapticPattern) {
        throw SecurityException("haptic failure")
    }
}
```

- [ ] **Step 2: Executar os testes e observar as falhas de integração**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.MonitoringEngineTest"
```

Expected: `FAILED` porque o engine ainda não recebe política, hora ou controlador háptico.

- [ ] **Step 3: Injetar dependências e aplicar decisões sem acoplar os efeitos**

Ampliar o construtor:

```kotlin
class MonitoringEngine(
    private val parentScope: CoroutineScope,
    private val networkObserver: NetworkObserver,
    private val settingsRepository: SettingsRepository,
    private val speechController: SpeechController,
    private val hapticController: HapticController,
    private val stateStore: MonitoringStateStore,
    private val alertPolicy: AlertPolicy = AlertPolicy(),
    private val localMinuteProvider: LocalMinuteProvider = SystemLocalMinuteProvider,
)
```

Substituir o corpo de `onTransition` por:

```kotlin
onTransition = { transition ->
    val decision = alertPolicy.decide(
        transition = transition,
        settings = currentSettings,
        minuteOfDay = localMinuteProvider.currentMinuteOfDay(),
    )
    if (decision.narrate) {
        runCatching {
            speechController.announce(PortugueseMessageFactory.from(transition))
        }
    }
    decision.hapticPattern?.let { pattern ->
        runCatching { hapticController.play(pattern) }
    }
},
```

Em `stop()`, fechar ambos os controladores; em `testHaptics()`, chamar `hapticController.testPatterns()` somente quando `started`. Em `MobiSentinelApplication.createMonitoringEngine`, construir `AndroidHapticController(this, scope)` e passá-lo ao engine. Expor:

```kotlin
val hapticAvailable: Boolean by lazy {
    AndroidHapticController.isAvailable(this)
}
```

- [ ] **Step 4: Executar integração e regressão completa JVM debug**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; estado confirmado permanece independente de voz/vibração e os dois transportes continuam separados.

- [ ] **Step 5: Registrar a orquestração**

```powershell
git add app/src/main/java/br/com/marcocardoso/mobisentinel/monitoring/MonitoringEngine.kt app/src/main/java/br/com/marcocardoso/mobisentinel/MobiSentinelApplication.kt app/src/test/java/br/com/marcocardoso/mobisentinel/monitoring/MonitoringEngineTest.kt
git commit -m "feat: orchestrate confirmed haptic alerts"
```

---

### Task 5: Expor configurações e teste háptico no ViewModel e serviço

**Files:**
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/ui/MainUiState.kt`
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/ui/MainViewModel.kt`
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/service/MonitoringService.kt`
- Modify: `app/src/test/java/br/com/marcocardoso/mobisentinel/ui/MainViewModelTest.kt`

**Interfaces:**
- Consumes: métodos do repositório, `MobiSentinelApplication.hapticAvailable` e `MonitoringEngine.testHaptics()`.
- Produces: callbacks do ViewModel para cada preferência, `MainUiState.hapticAvailable` e `MonitoringService.testHaptics(Context)`.

- [ ] **Step 1: Escrever testes falhos para cada ação do ViewModel**

Adicionar ao `MainViewModelTest` testes equivalentes a:

```kotlin
@Test
fun hapticAndQuietHourActionsPersistAndReachUi() = viewModelTest {
    val fixture = Fixture(hapticAvailable = true)
    collectState(fixture.viewModel)

    fixture.viewModel.setVibrateWifi(true)
    fixture.viewModel.setVibrateCellular(true)
    fixture.viewModel.setQuietHoursEnabled(true)
    fixture.viewModel.setQuietHours(8 * 60, 17 * 60)
    advanceUntilIdle()

    val state = fixture.viewModel.uiState.value
    assertEquals(true, state.hapticAvailable)
    assertEquals(true, state.settings.vibrateWifi)
    assertEquals(true, state.settings.vibrateCellular)
    assertEquals(true, state.settings.quietHoursEnabled)
    assertEquals(8 * 60, state.settings.quietStartMinuteOfDay)
    assertEquals(17 * 60, state.settings.quietEndMinuteOfDay)
}
```

- [ ] **Step 2: Executar o teste e confirmar métodos ausentes**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.MainViewModelTest"
```

Expected: `FAILED` pelos novos métodos/campo ainda ausentes.

- [ ] **Step 3: Implementar estado, delegações e ação do serviço**

Adicionar ao estado:

```kotlin
val hapticAvailable: Boolean = false,
```

O construtor do ViewModel recebe `hapticAvailable: Boolean`, inclui-o em cada `MainUiState` e expõe:

```kotlin
fun setVibrateWifi(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setVibrateWifi(enabled) }
}

fun setVibrateCellular(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setVibrateCellular(enabled) }
}

fun setQuietHoursEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setQuietHoursEnabled(enabled) }
}

fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
    viewModelScope.launch { settingsRepository.setQuietHours(startMinuteOfDay, endMinuteOfDay) }
}
```

O `Factory` passa `application.hapticAvailable`. Em `MonitoringService`, adicionar `ACTION_TEST_HAPTICS`, tratá-la em `onStartCommand` e expor:

```kotlin
fun testHaptics(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, MonitoringService::class.java).setAction(ACTION_TEST_HAPTICS),
    )
}
```

- [ ] **Step 4: Verificar ViewModel, serviço e compilação**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.MainViewModelTest" --tests "*.MonitoringEngineTest"
.\gradlew.bat assembleDebug
```

Expected: ambos terminam com `BUILD SUCCESSFUL`.

- [ ] **Step 5: Registrar o fluxo até a camada de apresentação**

```powershell
git add app/src/main/java/br/com/marcocardoso/mobisentinel/ui/MainUiState.kt app/src/main/java/br/com/marcocardoso/mobisentinel/ui/MainViewModel.kt app/src/main/java/br/com/marcocardoso/mobisentinel/service/MonitoringService.kt app/src/test/java/br/com/marcocardoso/mobisentinel/ui/MainViewModelTest.kt
git commit -m "feat: expose haptic and quiet hour controls"
```

---

### Task 6: Construir a interface Compose e validação dos horários

**Files:**
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/MainActivity.kt`
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/ui/MobiSentinelApp.kt`
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/ui/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/br/com/marcocardoso/mobisentinel/ui/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `MainUiState.hapticAvailable`, setters do ViewModel e ação `MonitoringService.testHaptics`.
- Produces: tags `vibrate_wifi`, `vibrate_cellular`, `test_haptics`, `quiet_hours_enabled`, `quiet_start`, `quiet_end` e `quiet_hours_error`; `TimePickerLauncher.show(Int, (Int) -> Unit)`.

- [ ] **Step 1: Escrever testes instrumentados falhos da nova tela**

Atualizar todas as chamadas de `SettingsScreen` com os novos callbacks e adicionar:

```kotlin
@Test
fun hapticAndQuietControlsExposeValuesAndForwardChanges() {
    val wifiValues = mutableListOf<Boolean>()
    val cellularValues = mutableListOf<Boolean>()
    val enabledValues = mutableListOf<Boolean>()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var testCount = 0
    var selection: ((Int) -> Unit)? = null
    val launcher = TimePickerLauncher { _, onSelected -> selection = onSelected }

    composeRule.setContent {
        SettingsScreen(
            settings = MonitoringSettings(
                monitoringEnabled = true,
                vibrateWifi = true,
                quietHoursEnabled = true,
            ),
            speechAvailability = SpeechAvailability.READY,
            hapticAvailable = true,
            onNarrateWifiChange = {},
            onNarrateCellularChange = {},
            onVibrateWifiChange = wifiValues::add,
            onVibrateCellularChange = cellularValues::add,
            onQuietHoursEnabledChange = enabledValues::add,
            onQuietHoursChange = { start, end -> ranges += start to end },
            onLossDelayChange = {},
            onRecoveryDelayChange = {},
            onTestVoice = {},
            onTestHaptics = { testCount++ },
            onOpenVoiceSettings = {},
            onStopMonitoring = {},
            onBack = {},
            timePickerLauncher = launcher,
        )
    }

    composeRule.onNodeWithTag("vibrate_wifi").assertIsOn().performClick()
    composeRule.onNodeWithTag("vibrate_cellular").assertIsOff().performClick()
    composeRule.onNodeWithTag("quiet_hours_enabled").assertIsOn().performClick()
    composeRule.onNodeWithTag("test_haptics").assertIsEnabled().performClick()
    composeRule.onNodeWithText("22:00").assertIsDisplayed()
    composeRule.onNodeWithText("07:00").assertIsDisplayed()
    composeRule.onNodeWithTag("quiet_end").performClick()
    composeRule.runOnIdle { requireNotNull(selection).invoke(22 * 60) }
    composeRule.onNodeWithTag("quiet_hours_error").assertIsDisplayed()

    assertEquals(listOf(false), wifiValues)
    assertEquals(listOf(true), cellularValues)
    assertEquals(listOf(false), enabledValues)
    assertEquals(1, testCount)
    assertTrue(ranges.isEmpty())
}

@Test
fun missingVibratorDisablesTestAndExplainsWhy() {
    composeRule.setContent {
        SettingsScreen(
            settings = MonitoringSettings(monitoringEnabled = true),
            speechAvailability = SpeechAvailability.READY,
            hapticAvailable = false,
            onNarrateWifiChange = {},
            onNarrateCellularChange = {},
            onVibrateWifiChange = {},
            onVibrateCellularChange = {},
            onQuietHoursEnabledChange = {},
            onQuietHoursChange = { _, _ -> },
            onLossDelayChange = {},
            onRecoveryDelayChange = {},
            onTestVoice = {},
            onTestHaptics = {},
            onOpenVoiceSettings = {},
            onStopMonitoring = {},
            onBack = {},
        )
    }
    composeRule.onNodeWithTag("test_haptics").assertIsNotEnabled()
    composeRule.onNodeWithText("Este aparelho não possui vibrador disponível.").assertIsDisplayed()
}
```

- [ ] **Step 2: Executar o teste de tela e confirmar parâmetros/tags ausentes**

Run no emulador Android 35:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.marcocardoso.mobisentinel.ui.SettingsScreenTest
```

Expected: `FAILED` porque a assinatura e os controles ainda não existem.

- [ ] **Step 3: Implementar seções, seletor injetável e validação local**

Adicionar à assinatura de `SettingsScreen`:

```kotlin
hapticAvailable: Boolean,
onVibrateWifiChange: (Boolean) -> Unit,
onVibrateCellularChange: (Boolean) -> Unit,
onQuietHoursEnabledChange: (Boolean) -> Unit,
onQuietHoursChange: (Int, Int) -> Unit,
onTestHaptics: () -> Unit,
timePickerLauncher: TimePickerLauncher? = null,
```

Definir o launcher real:

```kotlin
fun interface TimePickerLauncher {
    fun show(initialMinuteOfDay: Int, onSelected: (Int) -> Unit)
}

@Composable
private fun rememberTimePickerLauncher(): TimePickerLauncher {
    val context = LocalContext.current
    return remember(context) {
        TimePickerLauncher { initial, onSelected ->
            TimePickerDialog(
                context,
                { _, hour, minute -> onSelected(hour * 60 + minute) },
                initial / 60,
                initial % 60,
                true,
            ).show()
        }
    }
}

private fun formatMinuteOfDay(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
```

Na composição, resolver `val picker = timePickerLauncher ?: rememberTimePickerLauncher()` e manter `var quietHoursError by rememberSaveable { mutableStateOf<String?>(null) }`. Organizar as seções com títulos `Narração`, `Vibração`, `Não perturbe` e `Confirmação`. Inserir os seletores hápticos, botão de teste e texto de indisponibilidade. O botão usa:

```kotlin
enabled = settings.monitoringEnabled && hapticAvailable
```

Para cada horário, usar um `OutlinedButton` com a tag correspondente. A seleção do início aplica:

```kotlin
picker.show(settings.quietStartMinuteOfDay) { selected ->
    if (selected == settings.quietEndMinuteOfDay) {
        quietHoursError = "Início e fim precisam ser diferentes."
    } else {
        quietHoursError = null
        onQuietHoursChange(selected, settings.quietEndMinuteOfDay)
    }
}
```

A seleção do fim aplica a regra simétrica. Mostrar o erro com `Modifier.testTag("quiet_hours_error")`. Manter horários visíveis mesmo quando a função estiver desligada e mostrar o texto exato: `Durante este horário, narração e vibração são silenciadas. O monitoramento continua ativo.`

Em `MobiSentinelApp`, encaminhar todos os novos callbacks e `uiState.hapticAvailable`. Em `MainActivity`, ligar os callbacks:

```kotlin
onTestHaptics = { MonitoringService.testHaptics(this) },
onVibrateWifiChange = viewModel::setVibrateWifi,
onVibrateCellularChange = viewModel::setVibrateCellular,
onQuietHoursEnabledChange = viewModel::setQuietHoursEnabled,
onQuietHoursChange = viewModel::setQuietHours,
```

- [ ] **Step 4: Verificar tela, navegação e build**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.marcocardoso.mobisentinel.ui.SettingsScreenTest
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`; os controles persistem callbacks, horário igual mostra erro, ausência de vibrador desativa somente o teste háptico e a navegação existente continua compilando.

- [ ] **Step 5: Registrar a interface completa**

```powershell
git add app/src/main/java/br/com/marcocardoso/mobisentinel/MainActivity.kt app/src/main/java/br/com/marcocardoso/mobisentinel/ui/MobiSentinelApp.kt app/src/main/java/br/com/marcocardoso/mobisentinel/ui/SettingsScreen.kt app/src/androidTest/java/br/com/marcocardoso/mobisentinel/ui/SettingsScreenTest.kt
git commit -m "feat: add haptic and quiet hour settings UI"
```

---

### Task 7: Atualizar documentação e executar os gates de release

**Files:**
- Modify: `README.md`
- Modify: `PRIVACY.md`
- Modify: `docs/testing/manual-test-matrix.md`

**Interfaces:**
- Consumes: comportamento final das Tasks 1–6 e comandos oficiais do CI.
- Produces: documentação pública coerente com permissões, privacidade, defaults, DND e cobertura automatizada.

- [ ] **Step 1: Atualizar documentação sem editar o changelog gerado**

No `README.md`:

- mencionar vibração independente e horário silencioso no resumo e no passo de Configurações;
- documentar os padrões `2 × 120 ms` e `350 ms`, defaults desligados e teste manual;
- adicionar `VIBRATE` à tabela de permissões como permissão normal, sem diálogo;
- explicar que o app tenta vibrar fora do horário interno, mas Android/fabricante podem bloquear no DND;
- manter explícito que `INTERNET` não é declarada e não há sonda externa.

No `PRIVACY.md`, ampliar a lista DataStore para incluir os dois seletores hápticos, ativação do horário silencioso e minutos de início/fim; declarar que vibração e avaliação do horário são locais e não geram dados transmitidos.

Na matriz manual, adicionar cobertura automatizada para política de transição, limites do horário, persistência, UI, manifesto e cancelamento; registrar teste tátil físico como opcional e não executado, sem transformá-lo em gate.

Não editar `CHANGELOG.md`: os commits `feat:` desta branch devem gerar automaticamente a próxima versão minor via Release Please.

- [ ] **Step 2: Executar toda a verificação local reproduzível**

Run:

```powershell
.\gradlew.bat -p buildSrc test
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug
.\scripts\tests\privacy-manifest-test.ps1
.\scripts\tests\verify-release-apk-test.ps1
.\scripts\tests\release-workflow-test.ps1
.\scripts\tests\cross-platform-script-test.ps1
git diff --check
```

Expected: todos os comandos retornam código `0`; testes JVM têm zero falhas, lint tem zero erros e `assembleDebug` produz o APK.

- [ ] **Step 3: Executar a suíte instrumentada obrigatória**

Run em emulador Android 35 com Google APIs:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL` e zero falhas em testes instrumentados. Se não houver emulador local, abrir a PR e exigir o job `Android 35 emulator` verde antes do merge; isso não autoriza ignorar o gate.

- [ ] **Step 4: Revisar artefato e manifesto finais**

Run:

```powershell
$apk = 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw 'Debug APK was not produced' }
$aapt = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Filter aapt2.exe -Recurse |
    Sort-Object FullName -Descending | Select-Object -First 1
$permissions = & $aapt.FullName dump permissions $apk
$badging = & $aapt.FullName dump badging $apk
$permissions
if ($permissions -notmatch 'android.permission.VIBRATE') { throw 'VIBRATE is missing' }
if ($permissions -match 'android.permission.INTERNET') { throw 'INTERNET must stay absent' }
if ($permissions -match 'android.permission.ACCESS_NOTIFICATION_POLICY') {
    throw 'DND policy access must stay absent'
}
if ($badging -notmatch "package: name='br.com.marcocardoso.mobisentinel'") {
    throw 'Unexpected application package'
}
```

Expected: saída contém `android.permission.VIBRATE`, não contém `android.permission.INTERNET` nem `android.permission.ACCESS_NOTIFICATION_POLICY`, e o pacote permanece `br.com.marcocardoso.mobisentinel`.

- [ ] **Step 5: Registrar documentação e evidência**

```powershell
git add README.md PRIVACY.md docs/testing/manual-test-matrix.md
git commit -m "docs: document haptic alerts and quiet hours"
git status --short
```

Expected: commit criado e worktree limpa. Só então solicitar revisão de código e publicar a branch/PR; não mesclar enquanto o job de emulador Android 35 não estiver verde.
