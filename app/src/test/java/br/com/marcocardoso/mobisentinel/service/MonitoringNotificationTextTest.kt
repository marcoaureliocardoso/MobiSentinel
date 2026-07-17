package br.com.marcocardoso.mobisentinel.service

import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringNotificationTextTest {
    @Test
    fun notificationPermissionIsRequiredStartingOnAndroid13() {
        assertEquals(false, notificationPermissionRequired(sdkInt = 32))
        assertEquals(true, notificationPermissionRequired(sdkInt = 33))
        assertEquals(true, notificationPermissionRequired(sdkInt = 36))
    }

    @Test
    fun unknownStatesAreShownAsChecking() {
        assertEquals(
            "Wi-Fi: verificando • Dados móveis: verificando",
            MonitoringNotification.summary(MonitoringSnapshot(serviceActive = true)),
        )
    }

    @Test
    fun connectedWifiAndDisconnectedCellularUseCompactCopy() {
        assertEquals(
            "Wi-Fi: com internet • Dados móveis: desconectados",
            MonitoringNotification.summary(
                MonitoringSnapshot(
                    wifi = ConnectivityState.CONNECTED,
                    cellular = ConnectivityState.DISCONNECTED,
                    serviceActive = true,
                ),
            ),
        )
    }

    @Test
    fun unvalidatedWifiAndConnectedCellularUseCompactCopy() {
        assertEquals(
            "Wi-Fi: sem internet • Dados móveis: com internet",
            MonitoringNotification.summary(
                MonitoringSnapshot(
                    wifi = ConnectivityState.CONNECTED_NO_INTERNET,
                    cellular = ConnectivityState.CONNECTED,
                    serviceActive = true,
                ),
            ),
        )
    }
}
