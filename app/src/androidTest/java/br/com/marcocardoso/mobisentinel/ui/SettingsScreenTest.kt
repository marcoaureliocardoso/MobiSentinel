package br.com.marcocardoso.mobisentinel.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.speech.SpeechAvailability
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeSettingsExposeValuesAndForwardAllChanges() {
        val wifiValues = mutableListOf<Boolean>()
        val cellularValues = mutableListOf<Boolean>()
        val lossValues = mutableListOf<Int>()
        val recoveryValues = mutableListOf<Int>()
        var testVoiceCount = 0
        var stopCount = 0
        composeRule.setContent {
            SettingsScreen(
                settings = MonitoringSettings(
                    monitoringEnabled = true,
                    narrateWifi = true,
                    narrateCellular = false,
                    lossDelaySeconds = 5,
                    recoveryDelaySeconds = 2,
                ),
                speechAvailability = SpeechAvailability.READY,
                onNarrateWifiChange = wifiValues::add,
                onNarrateCellularChange = cellularValues::add,
                onLossDelayChange = lossValues::add,
                onRecoveryDelayChange = recoveryValues::add,
                onTestVoice = { testVoiceCount++ },
                onOpenVoiceSettings = {},
                onStopMonitoring = { stopCount++ },
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("narrate_wifi").assertIsOn().performClick()
        composeRule.onNodeWithTag("narrate_cellular").assertIsOff().performClick()
        composeRule.onNodeWithText("5 segundos").assertIsDisplayed()
        composeRule.onNodeWithText("2 segundos").assertIsDisplayed()
        composeRule.onNodeWithTag("loss_delay")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        composeRule.onNodeWithTag("recovery_delay")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        composeRule.onNodeWithTag("test_voice").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("stop_monitoring").performClick()

        assertEquals(listOf(false), wifiValues)
        assertEquals(listOf(true), cellularValues)
        assertEquals(12, lossValues.last())
        assertEquals(4, recoveryValues.last())
        assertEquals(1, testVoiceCount)
        assertEquals(1, stopCount)
    }

    @Test
    fun unavailableSpeechShowsVoiceSettingsAction() {
        composeRule.setContent {
            SettingsScreen(
                settings = MonitoringSettings(monitoringEnabled = true),
                speechAvailability = SpeechAvailability.UNAVAILABLE,
                onNarrateWifiChange = {},
                onNarrateCellularChange = {},
                onLossDelayChange = {},
                onRecoveryDelayChange = {},
                onTestVoice = {},
                onOpenVoiceSettings = {},
                onStopMonitoring = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("open_voice_settings").assertIsDisplayed()
    }

    @Test
    fun inactiveMonitoringDisablesVoiceTest() {
        composeRule.setContent {
            SettingsScreen(
                settings = MonitoringSettings(monitoringEnabled = false),
                speechAvailability = SpeechAvailability.READY,
                onNarrateWifiChange = {},
                onNarrateCellularChange = {},
                onLossDelayChange = {},
                onRecoveryDelayChange = {},
                onTestVoice = {},
                onOpenVoiceSettings = {},
                onStopMonitoring = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("test_voice").assertIsNotEnabled()
    }
}
