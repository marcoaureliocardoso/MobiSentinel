package br.com.marcocardoso.mobisentinel.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
        val repository = createRepository(backgroundScope)

        assertEquals(MonitoringSettings(), repository.settings.first())
    }

    @Test
    fun eachSetterUpdatesTheSettingsFlow() = runTest {
        val repository = createRepository(backgroundScope)

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
        val repository = createRepository(backgroundScope)

        val negative = runCatching { repository.setLossDelaySeconds(-1) }.exceptionOrNull()
        val tooLarge = runCatching { repository.setRecoveryDelaySeconds(61) }.exceptionOrNull()

        assertTrue(negative is IllegalArgumentException)
        assertTrue(tooLarge is IllegalArgumentException)
        assertEquals(MonitoringSettings(), repository.settings.first())
    }

    private fun createRepository(scope: CoroutineScope): DataStoreSettingsRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = {
                context.preferencesDataStoreFile("settings-test-${UUID.randomUUID()}.preferences_pb")
            },
        )
        return DataStoreSettingsRepository(dataStore)
    }
}
