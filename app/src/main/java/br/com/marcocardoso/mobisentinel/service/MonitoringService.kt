package br.com.marcocardoso.mobisentinel.service

import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.marcocardoso.mobisentinel.MobiSentinelApplication
import br.com.marcocardoso.mobisentinel.monitoring.MonitoringEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitoringService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private lateinit var engine: MonitoringEngine
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MobiSentinelApplication
        engine = app.createMonitoringEngine(scope)
        MonitoringNotification.createChannel(this)
        MonitoringNotification.startForeground(this, app.monitoringStateStore.snapshot.value)
        engine.start()
        notificationJob = scope.launch {
            app.monitoringStateStore.snapshot.collect {
                MonitoringNotification.update(this@MonitoringService, it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopFromUser()
            ACTION_TEST_VOICE -> engine.testVoice()
            ACTION_TEST_HAPTICS -> engine.testHaptics()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        engine.stop()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun stopFromUser() {
        val app = application as MobiSentinelApplication
        scope.launch {
            try {
                app.settingsRepository.setMonitoringEnabled(false)
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    ServiceCompat.stopForeground(
                        this@MonitoringService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "br.com.marcocardoso.mobisentinel.action.STOP"
        const val ACTION_TEST_VOICE = "br.com.marcocardoso.mobisentinel.action.TEST_VOICE"
        const val ACTION_TEST_HAPTICS = "br.com.marcocardoso.mobisentinel.action.TEST_HAPTICS"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        }

        fun stop(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java).setAction(ACTION_STOP),
            )
        }

        fun testVoice(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java).setAction(ACTION_TEST_VOICE),
            )
        }

        fun testHaptics(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java).setAction(ACTION_TEST_HAPTICS),
            )
        }
    }
}
