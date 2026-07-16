package com.mobisentinel.app.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.MonitoringSettings
import com.mobisentinel.app.monitoring.model.MonitoringSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardsExposeCompleteStatusSemantics() {
        composeRule.setContent {
            MainScreen(
                uiState = MainUiState(
                    snapshot = MonitoringSnapshot(
                        wifi = ConnectivityState.CONNECTED,
                        cellular = ConnectivityState.CONNECTED_NO_INTERNET,
                    ),
                    settings = MonitoringSettings(monitoringEnabled = true),
                ),
                onActivate = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithTag("wifi_status_card")
            .assertContentDescriptionEquals("Wi-Fi: conectado com internet")
        composeRule.onNodeWithTag("cellular_status_card")
            .assertContentDescriptionEquals("Dados móveis: conectado sem internet")
    }

    @Test
    fun inactiveScreenShowsActivationAndInvokesCallbacksOnce() {
        var activationCount = 0
        var settingsCount = 0
        composeRule.setContent {
            MainScreen(
                uiState = MainUiState(),
                onActivate = { activationCount++ },
                onOpenSettings = { settingsCount++ },
            )
        }

        composeRule.onNodeWithTag("activate_monitoring").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("open_settings").assertIsDisplayed().performClick()

        assertEquals(1, activationCount)
        assertEquals(1, settingsCount)
    }
}
