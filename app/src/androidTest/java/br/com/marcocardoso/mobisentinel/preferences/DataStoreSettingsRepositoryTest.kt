package br.com.marcocardoso.mobisentinel.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    @Test
    fun defaultsMatchMonitoringSettings() = runTest {
        val fixture = Fixture(backgroundScope)

        assertEquals(MonitoringSettings(), fixture.repository.settings.first())
    }

    @Test
    fun eachSetterUpdatesTheSettingsFlow() = runTest {
        val fixture = Fixture(backgroundScope)
        val repository = fixture.repository

        repository.setMonitoringEnabled(true)
        assertEquals(true, repository.settings.first().monitoringEnabled)

        repository.setNarrateWifi(false)
        assertEquals(false, repository.settings.first().narrateWifi)

        repository.setNarrateCellular(false)
        assertEquals(false, repository.settings.first().narrateCellular)

        repository.setLossDelaySeconds(12)
        assertEquals(12, repository.settings.first().lossDelaySeconds)

        repository.setRecoveryDelaySeconds(4)
        assertEquals(4, repository.settings.first().recoveryDelaySeconds)
    }

    @Test
    fun delaySettersRejectValuesOutsideSupportedRange() = runTest {
        val fixture = Fixture(backgroundScope)
        val repository = fixture.repository

        val negative = runCatching { repository.setLossDelaySeconds(-1) }.exceptionOrNull()
        val tooLarge = runCatching { repository.setRecoveryDelaySeconds(61) }.exceptionOrNull()

        assertTrue(negative is IllegalArgumentException)
        assertTrue(tooLarge is IllegalArgumentException)
        assertEquals(MonitoringSettings(), repository.settings.first())
    }

    @Test
    fun hapticAndQuietHoursSettersUpdateTheSettingsFlow() = runTest {
        val repository = Fixture(backgroundScope).repository

        repository.setVibrateWifi(true)
        assertEquals(true, repository.settings.first().vibrateWifi)

        repository.setVibrateCellular(true)
        assertEquals(true, repository.settings.first().vibrateCellular)

        repository.setQuietHoursEnabled(true)
        assertEquals(true, repository.settings.first().quietHoursEnabled)
    }

    @Test
    fun setQuietHoursPersistsTheCompletePair() = runTest {
        val repository = Fixture(backgroundScope).repository

        repository.setQuietHours(8 * 60, 17 * 60)

        val settings = repository.settings.first()
        assertEquals(8 * 60, settings.quietStartMinuteOfDay)
        assertEquals(17 * 60, settings.quietEndMinuteOfDay)
    }

    @Test
    fun equalQuietHourEndpointsAreRejectedWithoutChangingTheStoredPair() = runTest {
        val repository = Fixture(backgroundScope).repository
        repository.setQuietHours(8 * 60, 17 * 60)

        val exception = runCatching { repository.setQuietHours(420, 420) }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
        val settings = repository.settings.first()
        assertEquals(8 * 60, settings.quietStartMinuteOfDay)
        assertEquals(17 * 60, settings.quietEndMinuteOfDay)
    }

    @Test
    fun outOfRangeQuietHourEndpointsAreRejectedWithoutPartialWrites() = runTest {
        val repository = Fixture(backgroundScope).repository
        repository.setQuietHours(8 * 60, 17 * 60)

        val negative = runCatching { repository.setQuietHours(-1, 17 * 60) }.exceptionOrNull()
        val tooLarge = runCatching { repository.setQuietHours(8 * 60, 1440) }.exceptionOrNull()

        assertTrue(negative is IllegalArgumentException)
        assertTrue(tooLarge is IllegalArgumentException)
        val settings = repository.settings.first()
        assertEquals(8 * 60, settings.quietStartMinuteOfDay)
        assertEquals(17 * 60, settings.quietEndMinuteOfDay)
    }

    @Test
    fun manuallyStoredEqualQuietHourEndpointsNormalizeTheCompletePair() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.dataStore.edit { preferences ->
            preferences[quietStartMinuteOfDayKey] = 420
            preferences[quietEndMinuteOfDayKey] = 420
        }

        val settings = fixture.repository.settings.first()
        assertEquals(MonitoringSettings.DEFAULT_QUIET_START_MINUTE, settings.quietStartMinuteOfDay)
        assertEquals(MonitoringSettings.DEFAULT_QUIET_END_MINUTE, settings.quietEndMinuteOfDay)
    }

    @Test
    fun manuallyStoredOutOfRangeQuietHourEndpointNormalizesTheCompletePair() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.dataStore.edit { preferences ->
            preferences[quietStartMinuteOfDayKey] = 8 * 60
            preferences[quietEndMinuteOfDayKey] = 1440
        }

        val settings = fixture.repository.settings.first()
        assertEquals(MonitoringSettings.DEFAULT_QUIET_START_MINUTE, settings.quietStartMinuteOfDay)
        assertEquals(MonitoringSettings.DEFAULT_QUIET_END_MINUTE, settings.quietEndMinuteOfDay)
    }

    private class Fixture(scope: CoroutineScope) {
        val dataStore: DataStore<Preferences>
        val repository: DataStoreSettingsRepository

        init {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = {
                    context.preferencesDataStoreFile("settings-test-${UUID.randomUUID()}.preferences_pb")
                },
            )
            repository = DataStoreSettingsRepository(dataStore)
        }
    }

    private companion object {
        val quietStartMinuteOfDayKey = intPreferencesKey("quiet_start_minute_of_day")
        val quietEndMinuteOfDayKey = intPreferencesKey("quiet_end_minute_of_day")
    }
}
