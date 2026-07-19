package com.mobisentinel.haptics

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidHapticControllerCompatibilityTest {
    @Test
    fun `uses modern vibration attributes on Android 13 and a localized legacy fallback`() {
        val source = Files.readString(androidHapticControllerPath())

        assertTrue(
            source.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU"),
            "Android 13+ must select the modern Vibrator overload",
        )
        assertTrue(
            source.contains("VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION)"),
            "Android 13+ must use VibrationAttributes for notification haptics",
        )
        assertTrue(
            source.contains("vibrator.vibrate(effect, vibrationAttributes)"),
            "Android 13+ must call Vibrator.vibrate with VibrationAttributes",
        )
        assertTrue(
            source.contains("@Suppress(\"DEPRECATION\")\n            vibrator.vibrate(effect, legacyAttributes)"),
            "The deprecated Android 8-12 overload must remain a narrowly suppressed fallback",
        )
    }

    private fun androidHapticControllerPath(): Path {
        val relativePath = Path.of(
            "app",
            "src",
            "main",
            "java",
            "br",
            "com",
            "marcocardoso",
            "mobisentinel",
            "haptics",
            "AndroidHapticController.kt",
        )
        return generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .map { it.resolve(relativePath) }
            .firstOrNull(Files::isRegularFile)
            ?: error("Unable to locate AndroidHapticController.kt from ${System.getProperty("user.dir")}")
    }
}
