package br.com.marcocardoso.mobisentinel.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import br.com.marcocardoso.mobisentinel.monitoring.alerts.HapticPattern
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

class AndroidHapticController(
    context: Context,
    scope: CoroutineScope,
) : HapticController {
    private val delegate = DefaultHapticController(scope, AndroidHapticDevice(context))

    override val isAvailable: Boolean
        get() = delegate.isAvailable

    override fun play(pattern: HapticPattern) {
        delegate.play(pattern)
    }

    override fun testPatterns() {
        delegate.testPatterns()
    }

    override fun close() {
        delegate.close()
    }

    companion object {
        fun isAvailable(context: Context): Boolean = AndroidHapticDevice(context).isAvailable
    }
}

private class AndroidHapticDevice(context: Context) : HapticDevice {
    private val vibrator = vibratorFor(context)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override val isAvailable: Boolean
        get() = bestEffort { vibrator?.hasVibrator() == true } ?: false

    override fun play(pattern: HapticPattern) {
        val effect = when (pattern) {
            HapticPattern.LOSS -> VibrationEffect.createWaveform(longArrayOf(0, 120, 120, 120), -1)
            HapticPattern.RECOVERY -> VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator?.vibrate(effect, attributes)
    }

    override fun cancel() {
        vibrator?.cancel()
    }

    private fun vibratorFor(context: Context): Vibrator? = bestEffort {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private inline fun <T> bestEffort(block: () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: RuntimeException) {
        null
    }
}
