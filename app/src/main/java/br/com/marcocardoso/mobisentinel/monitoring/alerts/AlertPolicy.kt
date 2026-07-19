package br.com.marcocardoso.mobisentinel.monitoring.alerts

import br.com.marcocardoso.mobisentinel.monitoring.model.ConfirmedTransition
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSettings
import java.time.LocalTime

enum class HapticPattern { LOSS, RECOVERY }

data class AlertDecision(
    val narrate: Boolean,
    val hapticPattern: HapticPattern?,
)

fun interface LocalMinuteProvider {
    fun currentMinuteOfDay(): Int
}

object SystemLocalMinuteProvider : LocalMinuteProvider {
    override fun currentMinuteOfDay(): Int {
        val time = LocalTime.now()
        return time.hour * 60 + time.minute
    }
}

class AlertPolicy {
    fun decide(
        transition: ConfirmedTransition,
        settings: MonitoringSettings,
        minuteOfDay: Int,
    ): AlertDecision {
        if (settings.isQuietAt(minuteOfDay)) {
            return AlertDecision(narrate = false, hapticPattern = null)
        }

        val hapticPattern = if (settings.vibrationEnabled(transition.transport)) {
            hapticPatternFor(transition)
        } else {
            null
        }
        return AlertDecision(
            narrate = settings.narrationEnabled(transition.transport),
            hapticPattern = hapticPattern,
        )
    }

    private fun hapticPatternFor(transition: ConfirmedTransition): HapticPattern? = when {
        transition.previous == ConnectivityState.CONNECTED &&
            transition.current != ConnectivityState.CONNECTED -> HapticPattern.LOSS
        transition.previous != ConnectivityState.CONNECTED &&
            transition.current == ConnectivityState.CONNECTED -> HapticPattern.RECOVERY
        else -> null
    }
}
