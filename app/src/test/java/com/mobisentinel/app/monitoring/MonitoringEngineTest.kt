package com.mobisentinel.app.monitoring

import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.monitoring.model.Transport
import com.mobisentinel.app.monitoring.model.TransportSnapshot
import com.mobisentinel.app.monitoring.network.NetworkObserver
import com.mobisentinel.app.preferences.SettingsRepository
import com.mobisentinel.app.speech.Announcement
import com.mobisentinel.app.speech.SpeechAvailability
import com.mobisentinel.app.speech.SpeechController
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
        assertFalse(fixture.store.snapshot.value.serviceActive)
        assertTrue(fixture.speech.announcements.isEmpty())
    }

    @Test
    fun initialSnapshotsUpdateStoreWithoutSpeech() = runTest {
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
        fixture.engine.stop()
    }

    @Test
    fun confirmedLossAndRecoveryUpdateStoreAndSpeakExactCopy() = runTest {
        val fixture = Fixture(backgroundScope)
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
        fixture.settings.mutable.value = zeroDelaySettings.copy(narrateWifi = false)
        runCurrent()

        fixture.network.emit(Transport.WIFI, ConnectivityState.DISCONNECTED)
        runCurrent()

        assertEquals(ConnectivityState.DISCONNECTED, fixture.store.snapshot.value.wifi)
        assertTrue(fixture.speech.announcements.isEmpty())
        fixture.engine.stop()
    }

    @Test
    fun testVoiceDelegatesOnlyWhileEngineIsActive() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.engine.testVoice()
        fixture.engine.start()
        runCurrent()

        fixture.engine.testVoice()
        fixture.engine.stop()
        fixture.engine.testVoice()

        assertEquals(1, fixture.speech.testVoiceCount)
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
    ) {
        val network = FakeNetworkObserver()
        val settings = FakeSettingsRepository(initialSettings)
        val speech = RecordingSpeechController()
        val store = MonitoringStateStore()
        val engine = MonitoringEngine(scope, network, settings, speech, store)
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

        override suspend fun setLossDelaySeconds(seconds: Int) {
            mutable.value = mutable.value.copy(lossDelaySeconds = seconds)
        }

        override suspend fun setRecoveryDelaySeconds(seconds: Int) {
            mutable.value = mutable.value.copy(recoveryDelaySeconds = seconds)
        }
    }

    private class RecordingSpeechController : SpeechController {
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

    private companion object {
        val zeroDelaySettings = MonitoringSettings(lossDelaySeconds = 0, recoveryDelaySeconds = 0)
    }
}
