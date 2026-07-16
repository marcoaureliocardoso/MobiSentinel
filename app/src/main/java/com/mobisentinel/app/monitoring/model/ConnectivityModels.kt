package com.mobisentinel.app.monitoring.model

enum class Transport { WIFI, CELLULAR }

enum class ConnectivityState { DISCONNECTED, CONNECTED_NO_INTERNET, CONNECTED }

data class MonitoringSettings(
    val monitoringEnabled: Boolean = false,
    val narrateWifi: Boolean = true,
    val narrateCellular: Boolean = true,
    val lossDelaySeconds: Int = 5,
    val recoveryDelaySeconds: Int = 2,
) {
    init {
        require(lossDelaySeconds in 0..60)
        require(recoveryDelaySeconds in 0..60)
    }

    fun narrationEnabled(transport: Transport): Boolean = when (transport) {
        Transport.WIFI -> narrateWifi
        Transport.CELLULAR -> narrateCellular
    }
}

data class TransportSnapshot(
    val transport: Transport,
    val state: ConnectivityState,
)

data class MonitoringSnapshot(
    val wifi: ConnectivityState? = null,
    val cellular: ConnectivityState? = null,
    val serviceActive: Boolean = false,
)

data class ConfirmedTransition(
    val transport: Transport,
    val previous: ConnectivityState,
    val current: ConnectivityState,
)
