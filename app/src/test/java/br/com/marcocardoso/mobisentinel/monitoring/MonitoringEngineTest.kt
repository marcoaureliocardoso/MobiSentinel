package br.com.marcocardoso.mobisentinel.monitoring

import br.com.marcocardoso.mobisentinel.haptics.HapticController
import br.com.marcocardoso.mobisentinel.monitoring.alerts.HapticPattern
import br.com.marcocardoso.mobisentinel.monitoring.alerts.LocalMinuteProvider
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport
import br.com.marcocardoso.mobisentinel.monitoring.model.TransportSnapshot
import br.com.marcocardoso.mobisentinel.monitoring.network.NetworkObserver
import br.com.marcocardoso.mobisentinel.preferences.SettingsRepository
import br.com.marcocardoso.mobisentinel.speech.Announcement
import br.com.marcocardoso.mobisentinel.speech.SpeechAvailability
import br.com.marcocardoso.mobisentinel.speech.SpeechController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringEngineTest {
    @Test
    fun startIsIdempotentAndStartsObserverOnce() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.engine.start()
        fixture.engine.start()
        runCurrent()

        assertEquals(1, fixture.network.startCount)
        assertTrue(fixture.store.snapshot.value.serviceActive)
        fixture.engine.stop()
    }

    @Test
    fun stopIsIdempotentAndCancelsPendingWork() = runTest {
        val fixture = Fixture(
            backgroundScope,
            MonitoringSettings(lossDelaySeconds = 5, recoveryDelaySeconds = 2),
        )
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        fixture.network.emit(Transport.CELLULAR, ConnectivityState.CONNECTED)
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        fixture.network.emit(Transport.CELLULAR, ConnectivityState.DISCONNECTED)
        runCurrent()

        fixture.engine.stop()
        fixture.engine.stop()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(1, fixture.network.stopCount)
        assertEquals(1, fixture.speech.closeCount)
        assertEquals(1, fixture.haptics.closeCount)
        assertFalse(fixture.store.snapshot.value.serviceActive)
        assertTrue(fixture.speech.announcements.isEmpty())
    }

    @Test
    fun initialSnapshotsUpdateStoreWithoutAlerts() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.engine.start()
        runCurrent()

        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        fixture.network.emit(Transport.CELLULAR, ConnectivityState.CONNECTED_NO_INTERNET)
        runCurrent()

        assertEquals(ConnectivityState.CONNECTED, fixture.store.snapshot.value.wifi)
        assertEquals(
            ConnectivityState.CONNECTED_NO_INTERNET,
            fixture.store.snapshot.value.cellular,
        )
        assertTrue(fixture.speech.announcements.isEmpty())
        assertTrue(fixture.haptics.playedPatterns.isEmpty())
        fixture.engine.stop()
    }

    @Test
    fun confirmedWifiLossAndRecoveryUpdateStoreAndPlayHaptics() = runTest {
        val fixture = Fixture(backgroundScope, zeroDelaySettings.copy(vibrateWifi = true))
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        runCurrent()

        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        runCurrent()

        assertEquals(ConnectivityState.CONNECTED, fixture.store.snapshot.value.wifi)
        assertEquals(
            listOf(
                Announcement(Transport.WIFI, "Wi-Fi desconectado."),
                Announcement(Transport.WIFI, "Acesso à internet por Wi-Fi restabelecido."),
            ),
            fixture.speech.announcements,
        )
        assertEquals(listOf(HapticPattern.LOSS, HapticPattern.RECOVERY), fixture.haptics.playedPatterns)
        fixture.engine.stop()
    }

    @Test
    fun disconnectedAndConnectedWithoutInternetDoNotPlayHaptics() = runTest {
        val fixture = Fixture(backgroundScope, zeroDelaySettings.copy(vibrateWifi = true))
        fixture.engine.start()
        runCurrent()

        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED_NO_INTERNET)
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
        assertTrue(fixture.haptics.playedPatterns.isEmpty())
        fixture.engine.stop()
    }

    @Test
    fun vibrationSelectorsApplyToTheirOwnTransportOnly() = runTest {
        val wifiFixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(vibrateWifi = true, vibrateCellular = false),
        )
        wifiFixture.engine.start()
        runCurrent()
        wifiFixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        wifiFixture.network.emit(Transport.CELLULAR, ConnectivityState.CONNECTED)
        runCurrent()
        wifiFixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        wifiFixture.network.emit(Transport.CELLULAR, ConnectivityState.DISCONNECTED)
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS), wifiFixture.haptics.playedPatterns)
        wifiFixture.engine.stop()

        val cellularFixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(vibrateWifi = false, vibrateCellular = true),
        )
        cellularFixture.engine.start()
        runCurrent()
        cellularFixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        cellularFixture.network.emit(Transport.CELLULAR, ConnectivityState.CONNECTED)
        runCurrent()
        cellularFixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        cellularFixture.network.emit(Transport.CELLULAR, ConnectivityState.DISCONNECTED)
        runCurrent()
        assertEquals(listOf(HapticPattern.LOSS), cellularFixture.haptics.playedPatterns)
        cellularFixture.engine.stop()
    }

    @Test
    fun quietHoursSuppressAlertsButStillUpdateConfirmedState() = runTest {
        val fixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(
                vibrateWifi = true,
                quietHoursEnabled = true,
                quietStartMinuteOfDay = 60,
                quietEndMinuteOfDay = 120,
            ),
            minuteOfDay = 90,
        )
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
        assertTrue(fixture.speech.announcements.isEmpty())
        assertTrue(fixture.haptics.playedPatterns.isEmpty())
        fixture.engine.stop()
    }

    @Test
    fun wifiNarrationDisabledSuppressesOnlyWifi() = runTest {
        val fixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(narrateWifi = false, narrateCellular = true),
        )
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        fixture.network.emit(Transport.CELLULAR, ConnectivityState.CONNECTED)
        runCurrent()

        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        fixture.network.emit(Transport.CELLULAR, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(
            listOf(Announcement(Transport.CELLULAR, "Dados móveis desconectados.")),
            fixture.speech.announcements,
        )
        fixture.engine.stop()
    }

    @Test
    fun cellularNarrationDisabledSuppressesOnlyCellular() = runTest {
        val fixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(narrateWifi = true, narrateCellular = false),
        )
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        fixture.network.emit(Transport.CELLULAR, ConnectivityState.CONNECTED)
        runCurrent()

        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        fixture.network.emit(Transport.CELLULAR, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(
            listOf(Announcement(Transport.WIFI, "Wi-Fi desconectado.")),
            fixture.speech.announcements,
        )
        fixture.engine.stop()
    }

    @Test
    fun settingsChangesApplyToSubsequentTransitions() = runTest {
        val fixture = Fixture(
            backgroundScope,
            MonitoringSettings(lossDelaySeconds = 5, recoveryDelaySeconds = 2),
        )
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        runCurrent()
        fixture.settings.mutable.value = zeroDelaySettings.copy(narrateWifi = false, vibrateWifi = true)
        runCurrent()

        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
        assertTrue(fixture.speech.announcements.isEmpty())
        assertEquals(listOf(HapticPattern.LOSS), fixture.haptics.playedPatterns)
        fixture.engine.stop()
    }

    @Test
    fun speechFailureDoesNotBlockHapticsOrState() = runTest {
        val speech = RecordingSpeechController(failAnnounce = true)
        val fixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(vibrateWifi = true),
            speech = speech,
        )
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
        assertEquals(listOf(HapticPattern.LOSS), fixture.haptics.playedPatterns)
        fixture.engine.stop()
    }

    @Test
    fun hapticFailureDoesNotBlockSpeechOrState() = runTest {
        val haptics = RecordingHapticController(failPlay = true)
        val fixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(vibrateWifi = true),
            haptics = haptics,
        )
        fixture.engine.start()
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        runCurrent()
        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
        assertEquals(
            listOf(Announcement(Transport.WIFI, "Wi-Fi desconectado.")),
            fixture.speech.announcements,
        )
        fixture.engine.stop()
    }

    @Test
    fun closeFailuresDoNotPreventRemainingShutdownSteps() = runTest {
        val fixture = Fixture(
            backgroundScope,
            speech = RecordingSpeechController(closeFailure = IllegalStateException("speech close failed")),
        )
        fixture.engine.start()
        runCurrent()

        fixture.engine.stop()

        assertEquals(1, fixture.speech.closeCount)
        assertEquals(1, fixture.haptics.closeCount)
        assertEquals(1, fixture.network.stopCount)
        assertFalse(fixture.store.snapshot.value.serviceActive)
    }

    @Test
    fun cancellationDuringSpeechCloseFinishesShutdownAndPropagates() = runTest {
        val cancellation = CancellationException("speech close cancelled")
        val fixture = Fixture(
            backgroundScope,
            speech = RecordingSpeechController(closeFailure = cancellation),
        )
        fixture.engine.start()
        runCurrent()

        val thrown = try {
            fixture.engine.stop()
            null
        } catch (exception: Throwable) {
            exception
        }

        assertSame(cancellation, thrown)
        assertEquals(1, fixture.speech.closeCount)
        assertEquals(1, fixture.haptics.closeCount)
        assertEquals(1, fixture.network.stopCount)
        assertFalse(fixture.store.snapshot.value.serviceActive)
    }

    @Test
    fun errorDuringHapticCloseFinishesShutdownAndPropagates() = runTest {
        val error = AssertionError("haptics close failed")
        val fixture = Fixture(
            backgroundScope,
            haptics = RecordingHapticController(closeFailure = error),
        )
        fixture.engine.start()
        runCurrent()

        val thrown = try {
            fixture.engine.stop()
            null
        } catch (exception: Throwable) {
            exception
        }

        assertSame(error, thrown)
        assertEquals(1, fixture.speech.closeCount)
        assertEquals(1, fixture.haptics.closeCount)
        assertEquals(1, fixture.network.stopCount)
        assertFalse(fixture.store.snapshot.value.serviceActive)
    }

    @Test
    fun manualTestsDelegateOnlyWhileEngineIsActiveAndIgnoreAutomaticSettings() = runTest {
        val fixture = Fixture(
            backgroundScope,
            zeroDelaySettings.copy(
                narrateWifi = false,
                narrateCellular = false,
                vibrateWifi = false,
                vibrateCellular = false,
                quietHoursEnabled = true,
            ),
            minuteOfDay = 23 * 60,
        )
        fixture.engine.testVoice()
        fixture.engine.testHaptics()
        fixture.engine.start()
        runCurrent()

        fixture.engine.testVoice()
        fixture.engine.testHaptics()
        fixture.engine.stop()
        fixture.engine.testVoice()
        fixture.engine.testHaptics()

        assertEquals(1, fixture.speech.testVoiceCount)
        assertEquals(1, fixture.haptics.testPatternsCount)
    }

    @Test
    fun eventsAreIgnoredAfterStop() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.engine.start()
        runCurrent()
        fixture.engine.stop()

        fixture.network.emit(Transport.WIFI, ConnectivityState.CONNECTED)
        runCurrent()

        assertNull(fixture.store.snapshot.value.wifi)
        assertTrue(fixture.speech.announcements.isEmpty())
    }

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

    private class FakeNetworkObserver : NetworkObserver {
        private val mutableStates = MutableSharedFlow<TransportSnapshot>(extraBufferCapacity = 16)
        override val states: Flow<TransportSnapshot> = mutableStates
        var startCount = 0
        var stopCount = 0

        override fun start() {
            startCount++
        }

        override fun stop() {
            stopCount++
        }

        fun emit(transport: Transport, state: ConnectivityState) {
            check(mutableStates.tryEmit(TransportSnapshot(transport, state)))
        }
    }

    private class FakeSettingsRepository(initial: MonitoringSettings) : SettingsRepository {
        val mutable = MutableStateFlow(initial)
        override val settings: Flow<MonitoringSettings> = mutable

        override suspend fun setMonitoringEnabled(enabled: Boolean) {
            mutable.value = mutable.value.copy(monitoringEnabled = enabled)
        }

        override suspend fun setNarrateWifi(enabled: Boolean) {
            mutable.value = mutable.value.copy(narrateWifi = enabled)
        }

        override suspend fun setNarrateCellular(enabled: Boolean) {
            mutable.value = mutable.value.copy(narrateCellular = enabled)
        }

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

        override suspend fun setLossDelaySeconds(seconds: Int) {
            mutable.value = mutable.value.copy(lossDelaySeconds = seconds)
        }

        override suspend fun setRecoveryDelaySeconds(seconds: Int) {
            mutable.value = mutable.value.copy(recoveryDelaySeconds = seconds)
        }
    }

    private class RecordingSpeechController(
        private val failAnnounce: Boolean = false,
        private val closeFailure: Throwable? = null,
    ) : SpeechController {
        override val availability: StateFlow<SpeechAvailability> =
            MutableStateFlow(SpeechAvailability.READY)
        val announcements = mutableListOf<Announcement>()
        var testVoiceCount = 0
        var closeCount = 0

        override fun announce(announcement: Announcement) {
            if (failAnnounce) throw IllegalStateException("speech unavailable")
            announcements += announcement
        }

        override fun testVoice() {
            testVoiceCount++
        }

        override fun close() {
            closeCount++
            closeFailure?.let { throw it }
        }
    }

    private class RecordingHapticController(
        private val failPlay: Boolean = false,
        private val closeFailure: Throwable? = null,
    ) : HapticController {
        override val isAvailable: Boolean = true
        val playedPatterns = mutableListOf<HapticPattern>()
        var testPatternsCount = 0
        var closeCount = 0

        override fun play(pattern: HapticPattern) {
            if (failPlay) throw IllegalStateException("haptics unavailable")
            playedPatterns += pattern
        }

        override fun testPatterns() {
            testPatternsCount++
        }

        override fun close() {
            closeCount++
            closeFailure?.let { throw it }
        }
    }

    private companion object {
        val zeroDelaySettings = MonitoringSettings(lossDelaySeconds = 0, recoveryDelaySeconds = 0)
    }
}
