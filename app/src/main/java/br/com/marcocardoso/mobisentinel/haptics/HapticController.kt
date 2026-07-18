package br.com.marcocardoso.mobisentinel.haptics

import br.com.marcocardoso.mobisentinel.monitoring.alerts.HapticPattern

interface HapticController {
    val isAvailable: Boolean

    fun play(pattern: HapticPattern)

    fun testPatterns()

    fun close()
}

internal interface HapticDevice {
    val isAvailable: Boolean

    fun play(pattern: HapticPattern)

    fun cancel()
}
