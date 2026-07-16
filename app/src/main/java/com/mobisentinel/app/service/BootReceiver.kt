package com.mobisentinel.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobisentinel.app.MobiSentinelApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (
            intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as MobiSentinelApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = app.settingsRepository.settings.first().monitoringEnabled
                if (shouldRestartMonitoring(intent.action, enabled)) {
                    MonitoringService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun shouldRestartMonitoring(action: String?, enabled: Boolean): Boolean =
    enabled && (
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        )
