package br.com.marcocardoso.mobisentinel.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun activeSettingsExposeValuesAndForwardExistingChanges() {
        val wifiValues = mutableListOf<Boolean>()
        val cellularValues = mutableListOf<Boolean>()
        val lossValues = mutableListOf<Int>()
        val recoveryValues = mutableListOf<Int>()
        var testVoiceCount = 0
        var stopCount = 0
        render(
            settings = MonitoringSettings(
                monitoringEnabled = true,
                narrateWifi = true,
                narrateCellular = false,
                lossDelaySeconds = 5,
                recoveryDelaySeconds = 2,
            ),
            onNarrateWifiChange = wifiValues::add,
            onNarrateCellularChange = cellularValues::add,
            onLossDelayChange = lossValues::add,
            onRecoveryDelayChange = recoveryValues::add,
            onTestVoice = { testVoiceCount++ },
            onStopMonitoring = { stopCount++ },
        )

        composeRule.onNodeWithTag("narrate_wifi").assertIsOn().performClick()
        composeRule.onNodeWithTag("narrate_cellular").assertIsOff().performClick()
        composeRule.onNodeWithText("5 segundos").assertIsDisplayed()
        composeRule.onNodeWithText("2 segundos").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("loss_delay")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        composeRule.onNodeWithTag("recovery_delay")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        composeRule.onNodeWithTag("test_voice").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("stop_monitoring").performScrollTo().performClick()

        assertEquals(listOf(false), wifiValues)
        assertEquals(listOf(true), cellularValues)
        assertEquals(12, lossValues.last())
        assertEquals(4, recoveryValues.last())
        assertEquals(1, testVoiceCount)
        assertEquals(1, stopCount)
    }

    @Test
    fun vibrationSwitchesReflectValuesAndForwardOppositeValues() {
        val wifiValues = mutableListOf<Boolean>()
        val cellularValues = mutableListOf<Boolean>()
        render(
            settings = MonitoringSettings(vibrateWifi = true, vibrateCellular = false),
            onVibrateWifiChange = wifiValues::add,
            onVibrateCellularChange = cellularValues::add,
        )

        composeRule.onNodeWithTag("vibrate_wifi").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("vibrate_cellular").performScrollTo().assertIsOff().performClick()

        assertEquals(listOf(false), wifiValues)
        assertEquals(listOf(true), cellularValues)
    }

    @Test
    fun quietHoursSwitchForwardsChangeWithoutChangingTimes() {
        val enabledValues = mutableListOf<Boolean>()
        val ranges = mutableListOf<Pair<Int, Int>>()
        render(
            settings = MonitoringSettings(quietHoursEnabled = false),
            onQuietHoursEnabledChange = enabledValues::add,
            onQuietHoursChange = { start, end -> ranges += start to end },
        )

        composeRule.onNodeWithTag("quiet_hours_enabled")
            .performScrollTo()
            .assertIsOff()
            .performClick()

        assertEquals(listOf(true), enabledValues)
        assertEquals(emptyList<Pair<Int, Int>>(), ranges)
    }

    @Test
    fun hapticTestIsEnabledWithMonitoringAndHardwareAndCallsOnce() {
        var testCount = 0
        render(
            settings = MonitoringSettings(monitoringEnabled = true),
            hapticAvailable = true,
            onTestHaptics = { testCount++ },
        )

        composeRule.onNodeWithTag("test_haptics")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, testCount)
    }

    @Test
    fun quietHoursControlsAlwaysShowCurrentTimes() {
        render(
            settings = MonitoringSettings(
                quietHoursEnabled = false,
                quietStartMinuteOfDay = 8 * 60 + 5,
                quietEndMinuteOfDay = 17 * 60 + 7,
            ),
        )

        composeRule.onNodeWithTag("quiet_start").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("08:05").assertIsDisplayed()
        composeRule.onNodeWithTag("quiet_end").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("17:07").assertIsDisplayed()
    }

    @Test
    fun timePickerLaunchersReceiveTheCurrentEndpointMinute() {
        val launcher = FakeTimePickerLauncher()
        render(
            settings = MonitoringSettings(
                quietStartMinuteOfDay = 6 * 60 + 15,
                quietEndMinuteOfDay = 20 * 60 + 45,
            ),
            timePickerLauncher = launcher,
        )

        composeRule.onNodeWithTag("quiet_start").performScrollTo().performClick()
        assertEquals(6 * 60 + 15, launcher.initialMinuteOfDay)
        composeRule.onNodeWithTag("quiet_end").performScrollTo().performClick()
        assertEquals(20 * 60 + 45, launcher.initialMinuteOfDay)
    }

    @Test
    fun equalStartShowsErrorAndDoesNotForwardRange() {
        val launcher = FakeTimePickerLauncher()
        val ranges = mutableListOf<Pair<Int, Int>>()
        render(
            settings = MonitoringSettings(),
            timePickerLauncher = launcher,
            onQuietHoursChange = { start, end -> ranges += start to end },
        )

        composeRule.onNodeWithTag("quiet_start").performScrollTo().performClick()
        composeRule.runOnIdle { launcher.select(MonitoringSettings.DEFAULT_QUIET_END_MINUTE) }

        composeRule.onNodeWithTag("quiet_hours_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Início e fim precisam ser diferentes.").assertIsDisplayed()
        assertEquals(emptyList<Pair<Int, Int>>(), ranges)
    }

    @Test
    fun equalEndShowsErrorAndDoesNotForwardRange() {
        val launcher = FakeTimePickerLauncher()
        val ranges = mutableListOf<Pair<Int, Int>>()
        render(
            settings = MonitoringSettings(),
            timePickerLauncher = launcher,
            onQuietHoursChange = { start, end -> ranges += start to end },
        )

        composeRule.onNodeWithTag("quiet_end").performScrollTo().performClick()
        composeRule.runOnIdle { launcher.select(MonitoringSettings.DEFAULT_QUIET_START_MINUTE) }

        composeRule.onNodeWithTag("quiet_hours_error").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<Pair<Int, Int>>(), ranges)
    }

    @Test
    fun validStartForwardsCompleteRangeAndClearsPreviousError() {
        val launcher = FakeTimePickerLauncher()
        val ranges = mutableListOf<Pair<Int, Int>>()
        render(
            settings = MonitoringSettings(),
            timePickerLauncher = launcher,
            onQuietHoursChange = { start, end -> ranges += start to end },
        )
        composeRule.onNodeWithTag("quiet_start").performScrollTo().performClick()
        composeRule.runOnIdle { launcher.select(MonitoringSettings.DEFAULT_QUIET_END_MINUTE) }
        composeRule.onNodeWithTag("quiet_hours_error").assertIsDisplayed()

        composeRule.onNodeWithTag("quiet_start").performClick()
        composeRule.runOnIdle { launcher.select(8 * 60) }

        assertEquals(listOf(8 * 60 to MonitoringSettings.DEFAULT_QUIET_END_MINUTE), ranges)
        composeRule.onAllNodesWithTag("quiet_hours_error").assertCountEquals(0)
    }

    @Test
    fun validEndForwardsCompleteRangeAndClearsPreviousError() {
        val launcher = FakeTimePickerLauncher()
        val ranges = mutableListOf<Pair<Int, Int>>()
        render(
            settings = MonitoringSettings(),
            timePickerLauncher = launcher,
            onQuietHoursChange = { start, end -> ranges += start to end },
        )
        composeRule.onNodeWithTag("quiet_end").performScrollTo().performClick()
        composeRule.runOnIdle { launcher.select(MonitoringSettings.DEFAULT_QUIET_START_MINUTE) }
        composeRule.onNodeWithTag("quiet_hours_error").assertIsDisplayed()

        composeRule.onNodeWithTag("quiet_end").performClick()
        composeRule.runOnIdle { launcher.select(18 * 60) }

        assertEquals(listOf(MonitoringSettings.DEFAULT_QUIET_START_MINUTE to 18 * 60), ranges)
        composeRule.onAllNodesWithTag("quiet_hours_error").assertCountEquals(0)
    }

    @Test
    fun unavailableHardwareDisablesTestButKeepsSwitchesEditable() {
        var testCount = 0
        render(
            settings = MonitoringSettings(monitoringEnabled = true),
            hapticAvailable = false,
            onTestHaptics = { testCount++ },
        )

        composeRule.onNodeWithTag("vibrate_wifi").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("vibrate_cellular").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("test_haptics").performScrollTo().assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Este aparelho não possui vibrador disponível.")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(0, testCount)
    }

    @Test
    fun inactiveMonitoringDisablesVoiceAndHapticTests() {
        render(
            settings = MonitoringSettings(monitoringEnabled = false),
            speechAvailability = SpeechAvailability.READY,
            hapticAvailable = true,
        )

        composeRule.onNodeWithTag("test_voice").assertIsNotEnabled()
        composeRule.onNodeWithTag("test_haptics").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun settingsSectionsAndQuietExplanationAreVisibleAfterScrolling() {
        render()

        composeRule.onNodeWithText("Narração").assertIsDisplayed()
        composeRule.onNodeWithText("Vibração").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Não perturbe").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Durante este horário, narração e vibração são silenciadas. " +
                "O monitoramento continua ativo.",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Confirmação").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailableSpeechShowsVoiceSettingsAction() {
        render(
            settings = MonitoringSettings(monitoringEnabled = true),
            speechAvailability = SpeechAvailability.UNAVAILABLE,
        )

        composeRule.onNodeWithTag("open_voice_settings").assertIsDisplayed()
    }

    private fun render(
        settings: MonitoringSettings = MonitoringSettings(),
        speechAvailability: SpeechAvailability = SpeechAvailability.READY,
        hapticAvailable: Boolean = true,
        onNarrateWifiChange: (Boolean) -> Unit = {},
        onNarrateCellularChange: (Boolean) -> Unit = {},
        onVibrateWifiChange: (Boolean) -> Unit = {},
        onVibrateCellularChange: (Boolean) -> Unit = {},
        onQuietHoursEnabledChange: (Boolean) -> Unit = {},
        onQuietHoursChange: (Int, Int) -> Unit = { _, _ -> },
        onLossDelayChange: (Int) -> Unit = {},
        onRecoveryDelayChange: (Int) -> Unit = {},
        onTestVoice: () -> Unit = {},
        onTestHaptics: () -> Unit = {},
        onOpenVoiceSettings: () -> Unit = {},
        onStopMonitoring: () -> Unit = {},
        timePickerLauncher: TimePickerLauncher? = null,
    ) {
        composeRule.setContent {
            SettingsScreen(
                settings = settings,
                speechAvailability = speechAvailability,
                hapticAvailable = hapticAvailable,
                onNarrateWifiChange = onNarrateWifiChange,
                onNarrateCellularChange = onNarrateCellularChange,
                onVibrateWifiChange = onVibrateWifiChange,
                onVibrateCellularChange = onVibrateCellularChange,
                onQuietHoursEnabledChange = onQuietHoursEnabledChange,
                onQuietHoursChange = onQuietHoursChange,
                onLossDelayChange = onLossDelayChange,
                onRecoveryDelayChange = onRecoveryDelayChange,
                onTestVoice = onTestVoice,
                onTestHaptics = onTestHaptics,
                onOpenVoiceSettings = onOpenVoiceSettings,
                onStopMonitoring = onStopMonitoring,
                onBack = {},
                timePickerLauncher = timePickerLauncher,
            )
        }
    }

    private class FakeTimePickerLauncher : TimePickerLauncher {
        var initialMinuteOfDay: Int? = null
            private set
        private var onSelected: ((Int) -> Unit)? = null

        override fun show(initialMinuteOfDay: Int, onSelected: (Int) -> Unit) {
            this.initialMinuteOfDay = initialMinuteOfDay
            this.onSelected = onSelected
        }

        fun select(minuteOfDay: Int) {
            checkNotNull(onSelected)(minuteOfDay)
        }
    }
}
