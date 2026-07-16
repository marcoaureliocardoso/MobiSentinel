package com.mobisentinel.app.ui

import androidx.lifecycle.viewModelScope
import com.mobisentinel.app.monitoring.MonitoringStateStore
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.monitoring.model.Transport
import com.mobisentinel.app.preferences.SettingsRepository
import com.mobisentinel.app.speech.SpeechAvailability
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
        val viewModel = MainViewModel(store, repository, speech)
        collectState(viewModel)

        advanceUntilIdle()

        assertEquals(
            MainUiState(store.snapshot.value, settings, SpeechAvailability.READY),
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

    private class Fixture {
        val store = MonitoringStateStore()
        val repository = FakeSettingsRepository(MonitoringSettings())
        val speech = MutableStateFlow(SpeechAvailability.INITIALIZING)
        val viewModel = MainViewModel(store, repository, speech)
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

        override suspend fun setLossDelaySeconds(seconds: Int) {
            mutableSettings.value = mutableSettings.value.copy(lossDelaySeconds = seconds)
        }

        override suspend fun setRecoveryDelaySeconds(seconds: Int) {
            mutableSettings.value = mutableSettings.value.copy(recoveryDelaySeconds = seconds)
        }
    }
}
