package br.com.marcocardoso.mobisentinel.monitoring.alerts

import br.com.marcocardoso.mobisentinel.monitoring.model.ConfirmedTransition
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    private val policy = AlertPolicy()

    @Test
    fun defaultsDisableHapticsAndQuietHoursWithNighttimeSchedule() {
        val settings = MonitoringSettings()

        assertFalse(settings.vibrateWifi)
        assertFalse(settings.vibrateCellular)
        assertFalse(settings.quietHoursEnabled)
        assertEquals(22 * 60, settings.quietStartMinuteOfDay)
        assertEquals(7 * 60, settings.quietEndMinuteOfDay)
    }

    @Test
    fun nightlyQuietRangeIncludesStartAndExcludesEnd() {
        val settings = MonitoringSettings(
            quietHoursEnabled = true,
            quietStartMinuteOfDay = 22 * 60,
            quietEndMinuteOfDay = 7 * 60,
        )

        assertFalse(settings.isQuietAt(21 * 60 + 59))
        assertTrue(settings.isQuietAt(22 * 60))
        assertTrue(settings.isQuietAt(6 * 60 + 59))
        assertFalse(settings.isQuietAt(7 * 60))
    }

    @Test
    fun daytimeQuietRangeIncludesStartAndExcludesEnd() {
        val settings = MonitoringSettings(
            quietHoursEnabled = true,
            quietStartMinuteOfDay = 9 * 60,
            quietEndMinuteOfDay = 17 * 60,
        )

        assertFalse(settings.isQuietAt(8 * 60 + 59))
        assertTrue(settings.isQuietAt(9 * 60))
        assertTrue(settings.isQuietAt(16 * 60 + 59))
        assertFalse(settings.isQuietAt(17 * 60))
    }

    @Test
    fun disabledQuietHoursNeverSuppresses() {
        val settings = MonitoringSettings(quietHoursEnabled = false)

        assertFalse(settings.isQuietAt(22 * 60))
        assertFalse(settings.isQuietAt(2 * 60))
    }

    @Test
    fun quietHourLookupRejectsInvalidMinute() {
        val settings = MonitoringSettings()

        assertThrows(IllegalArgumentException::class.java) { settings.isQuietAt(-1) }
        assertThrows(IllegalArgumentException::class.java) { settings.isQuietAt(1440) }
    }

    @Test
    fun persistedQuietBoundariesRejectInvalidMinutes() {
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(quietStartMinuteOfDay = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(quietEndMinuteOfDay = 1440)
        }
    }

    @Test
    fun equalQuietBoundariesAreInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSettings(quietStartMinuteOfDay = 600, quietEndMinuteOfDay = 600)
        }
    }

    @Test
    fun connectedToDisconnectedUsesLossHapticPattern() {
        assertDecision(
            previous = ConnectivityState.CONNECTED,
            current = ConnectivityState.DISCONNECTED,
            expected = AlertDecision(narrate = true, hapticPattern = HapticPattern.LOSS),
        )
    }

    @Test
    fun connectedToNoInternetUsesLossHapticPattern() {
        assertDecision(
            previous = ConnectivityState.CONNECTED,
            current = ConnectivityState.CONNECTED_NO_INTERNET,
            expected = AlertDecision(narrate = true, hapticPattern = HapticPattern.LOSS),
        )
    }

    @Test
    fun disconnectedToConnectedUsesRecoveryHapticPattern() {
        assertDecision(
            previous = ConnectivityState.DISCONNECTED,
            current = ConnectivityState.CONNECTED,
            expected = AlertDecision(narrate = true, hapticPattern = HapticPattern.RECOVERY),
        )
    }

    @Test
    fun noInternetToConnectedUsesRecoveryHapticPattern() {
        assertDecision(
            previous = ConnectivityState.CONNECTED_NO_INTERNET,
            current = ConnectivityState.CONNECTED,
            expected = AlertDecision(narrate = true, hapticPattern = HapticPattern.RECOVERY),
        )
    }

    @Test
    fun changesWithinNoInternetClassDoNotVibrate() {
        assertDecision(
            previous = ConnectivityState.DISCONNECTED,
            current = ConnectivityState.CONNECTED_NO_INTERNET,
            expected = AlertDecision(narrate = true, hapticPattern = null),
        )
        assertDecision(
            previous = ConnectivityState.CONNECTED_NO_INTERNET,
            current = ConnectivityState.DISCONNECTED,
            expected = AlertDecision(narrate = true, hapticPattern = null),
        )
    }

    @Test
    fun voiceAndVibrationSelectorsAreIndependentPerTransport() {
        val settings = MonitoringSettings(
            narrateWifi = false,
            narrateCellular = true,
            vibrateWifi = true,
            vibrateCellular = false,
        )

        assertEquals(
            AlertDecision(narrate = false, hapticPattern = HapticPattern.LOSS),
            policy.decide(transition(Transport.WIFI), settings, minuteOfDay = 12 * 60),
        )
        assertEquals(
            AlertDecision(narrate = true, hapticPattern = null),
            policy.decide(transition(Transport.CELLULAR), settings, minuteOfDay = 12 * 60),
        )
    }

    @Test
    fun quietRangeSuppressesNarrationAndHaptics() {
        val settings = MonitoringSettings(
            vibrateWifi = true,
            quietHoursEnabled = true,
            quietStartMinuteOfDay = 22 * 60,
            quietEndMinuteOfDay = 7 * 60,
        )

        assertEquals(
            AlertDecision(narrate = false, hapticPattern = null),
            policy.decide(transition(Transport.WIFI), settings, minuteOfDay = 22 * 60),
        )
    }

    private fun assertDecision(
        previous: ConnectivityState,
        current: ConnectivityState,
        expected: AlertDecision,
    ) {
        val settings = MonitoringSettings(vibrateWifi = true)

        assertEquals(
            expected,
            policy.decide(
                transition = ConfirmedTransition(Transport.WIFI, previous, current),
                settings = settings,
                minuteOfDay = 12 * 60,
            ),
        )
    }

    private fun transition(transport: Transport): ConfirmedTransition = ConfirmedTransition(
        transport = transport,
        previous = ConnectivityState.CONNECTED,
        current = ConnectivityState.DISCONNECTED,
    )
}
