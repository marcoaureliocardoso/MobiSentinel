package br.com.marcocardoso.mobisentinel.ui

import androidx.lifecycle.viewModelScope
import br.com.marcocardoso.mobisentinel.monitoring.MonitoringStateStore
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport
import br.com.marcocardoso.mobisentinel.preferences.SettingsRepository
import br.com.marcocardoso.mobisentinel.speech.SpeechAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val createdViewModels = mutableListOf<MainViewModel>()

    @Test
    fun combinesInitialSnapshotSettingsAndSpeechAvailability() = viewModelTest {
        val store = MonitoringStateStore().apply {
            setServiceActive(true)
            setState(Transport.WIFI, ConnectivityState.CONNECTED)
        }
        val settings = MonitoringSettings(monitoringEnabled = true, lossDelaySeconds = 9)
        val repository = FakeSettingsRepository(settings)
        val speech = MutableStateFlow(SpeechAvailability.READY)
        val viewModel = MainViewModel(store, repository, speech, hapticAvailable = true)

        assertEquals(true, viewModel.uiState.value.hapticAvailable)
        collectState(viewModel)

        advanceUntilIdle()

        assertEquals(
            MainUiState(
                store.snapshot.value,
                settings,
                SpeechAvailability.READY,
                hapticAvailable = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun snapshotChangesReachUiState() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.store.setState(Transport.CELLULAR, ConnectivityState.CONNECTED_NO_INTERNET)
        advanceUntilIdle()

        assertEquals(
            ConnectivityState.CONNECTED_NO_INTERNET,
            fixture.viewModel.uiState.value.snapshot.cellular,
        )
    }

    @Test
    fun wifiTogglePersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setNarrateWifi(false)
        advanceUntilIdle()

        assertEquals(false, fixture.viewModel.uiState.value.settings.narrateWifi)
    }

    @Test
    fun cellularTogglePersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setNarrateCellular(false)
        advanceUntilIdle()

        assertEquals(false, fixture.viewModel.uiState.value.settings.narrateCellular)
    }

    @Test
    fun hapticAvailabilityRemainsInUiStateAfterFlowsCombine() = viewModelTest {
        val fixture = Fixture(hapticAvailable = true)
        collectState(fixture.viewModel)

        advanceUntilIdle()

        assertEquals(true, fixture.viewModel.uiState.value.hapticAvailable)
    }

    @Test
    fun vibrateWifiTogglePersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setVibrateWifi(true)
        advanceUntilIdle()

        assertEquals(true, fixture.viewModel.uiState.value.settings.vibrateWifi)
    }

    @Test
    fun vibrateCellularTogglePersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setVibrateCellular(true)
        advanceUntilIdle()

        assertEquals(true, fixture.viewModel.uiState.value.settings.vibrateCellular)
    }

    @Test
    fun quietHoursTogglePersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setQuietHoursEnabled(true)
        advanceUntilIdle()

        assertEquals(true, fixture.viewModel.uiState.value.settings.quietHoursEnabled)
    }

    @Test
    fun quietHoursPersistAtomicallyAndUpdateUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setQuietHours(8 * 60, 17 * 60)
        advanceUntilIdle()

        assertEquals(8 * 60, fixture.viewModel.uiState.value.settings.quietStartMinuteOfDay)
        assertEquals(17 * 60, fixture.viewModel.uiState.value.settings.quietEndMinuteOfDay)
    }

    @Test
    fun lossDelayPersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setLossDelaySeconds(12)
        advanceUntilIdle()

        assertEquals(12, fixture.viewModel.uiState.value.settings.lossDelaySeconds)
    }

    @Test
    fun recoveryDelayPersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.setRecoveryDelaySeconds(4)
        advanceUntilIdle()

        assertEquals(4, fixture.viewModel.uiState.value.settings.recoveryDelaySeconds)
    }

    @Test
    fun activationPersistsAndUpdatesUi() = viewModelTest {
        val fixture = Fixture()
        collectState(fixture.viewModel)

        fixture.viewModel.activate()
        advanceUntilIdle()

        assertEquals(true, fixture.viewModel.uiState.value.settings.monitoringEnabled)
    }

    private fun viewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            createdViewModels.forEach { it.viewModelScope.cancel() }
            advanceUntilIdle()
            createdViewModels.clear()
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.collectState(viewModel: MainViewModel) {
        createdViewModels += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private class Fixture(hapticAvailable: Boolean = false) {
        val store = MonitoringStateStore()
        val repository = FakeSettingsRepository(MonitoringSettings())
        val speech = MutableStateFlow(SpeechAvailability.INITIALIZING)
        val viewModel = MainViewModel(store, repository, speech, hapticAvailable)
    }

    private class FakeSettingsRepository(initial: MonitoringSettings) : SettingsRepository {
        private val mutableSettings = MutableStateFlow(initial)
        override val settings: Flow<MonitoringSettings> = mutableSettings

        override suspend fun setMonitoringEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(monitoringEnabled = enabled)
        }

        override suspend fun setNarrateWifi(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(narrateWifi = enabled)
        }

        override suspend fun setNarrateCellular(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(narrateCellular = enabled)
        }

        override suspend fun setVibrateWifi(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(vibrateWifi = enabled)
        }

        override suspend fun setVibrateCellular(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(vibrateCellular = enabled)
        }

        override suspend fun setQuietHoursEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(quietHoursEnabled = enabled)
        }

        override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
            mutableSettings.value = mutableSettings.value.copy(
                quietStartMinuteOfDay = startMinuteOfDay,
                quietEndMinuteOfDay = endMinuteOfDay,
            )
        }

        override suspend fun setLossDelaySeconds(seconds: Int) {
            mutableSettings.value = mutableSettings.value.copy(lossDelaySeconds = seconds)
        }

        override suspend fun setRecoveryDelaySeconds(seconds: Int) {
            mutableSettings.value = mutableSettings.value.copy(recoveryDelaySeconds = seconds)
        }
    }
}
