package br.com.marcocardoso.mobisentinel.monitoring.model

enum class Transport { WIFI, CELLULAR }

enum class ConnectivityState { DISCONNECTED, CONNECTED_NO_INTERNET, CONNECTED }

data class MonitoringSettings(
    val monitoringEnabled: Boolean = false,
    val narrateWifi: Boolean = true,
    val narrateCellular: Boolean = true,
    val vibrateWifi: Boolean = false,
    val vibrateCellular: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinuteOfDay: Int = DEFAULT_QUIET_START_MINUTE,
    val quietEndMinuteOfDay: Int = DEFAULT_QUIET_END_MINUTE,
    val lossDelaySeconds: Int = 5,
    val recoveryDelaySeconds: Int = 2,
) {
    init {
        require(lossDelaySeconds in 0..60)
        require(recoveryDelaySeconds in 0..60)
        require(quietStartMinuteOfDay in MINUTE_OF_DAY_RANGE)
        require(quietEndMinuteOfDay in MINUTE_OF_DAY_RANGE)
        require(quietStartMinuteOfDay != quietEndMinuteOfDay)
    }

    fun narrationEnabled(transport: Transport): Boolean = when (transport) {
        Transport.WIFI -> narrateWifi
        Transport.CELLULAR -> narrateCellular
    }

    fun vibrationEnabled(transport: Transport): Boolean = when (transport) {
        Transport.WIFI -> vibrateWifi
        Transport.CELLULAR -> vibrateCellular
    }

    fun isQuietAt(minuteOfDay: Int): Boolean {
        require(minuteOfDay in MINUTE_OF_DAY_RANGE)
        if (!quietHoursEnabled) return false

        return if (quietStartMinuteOfDay < quietEndMinuteOfDay) {
            minuteOfDay in quietStartMinuteOfDay until quietEndMinuteOfDay
        } else {
            minuteOfDay >= quietStartMinuteOfDay || minuteOfDay < quietEndMinuteOfDay
        }
    }

    companion object {
        val MINUTE_OF_DAY_RANGE = 0..1439
        const val DEFAULT_QUIET_START_MINUTE = 22 * 60
        const val DEFAULT_QUIET_END_MINUTE = 7 * 60
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
