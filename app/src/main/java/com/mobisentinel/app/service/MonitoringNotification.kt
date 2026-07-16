package com.mobisentinel.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mobisentinel.app.MainActivity
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.MonitoringSnapshot

object MonitoringNotification {
    const val CHANNEL_ID = "mobisentinel_monitoring"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoramento de conexões",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun startForeground(service: Service, snapshot: MonitoringSnapshot) {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            build(service, snapshot),
            foregroundType,
        )
    }

    fun update(context: Context, snapshot: MonitoringSnapshot) {
        if (
            notificationPermissionRequired(Build.VERSION.SDK_INT) &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, snapshot))
    }

    fun summary(snapshot: MonitoringSnapshot): String =
        "Wi-Fi: ${wifiState(snapshot.wifi)} • Dados móveis: ${cellularState(snapshot.cellular)}"

    private fun build(context: Context, snapshot: MonitoringSnapshot): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("MobiSentinel monitorando conexões")
            .setContentText(summary(snapshot))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Parar", stopIntent)
            .build()
    }

    private fun wifiState(state: ConnectivityState?): String = when (state) {
        null -> "verificando"
        ConnectivityState.DISCONNECTED -> "desconectado"
        ConnectivityState.CONNECTED_NO_INTERNET -> "sem internet"
        ConnectivityState.CONNECTED -> "com internet"
    }

    private fun cellularState(state: ConnectivityState?): String = when (state) {
        null -> "verificando"
        ConnectivityState.DISCONNECTED -> "desconectados"
        ConnectivityState.CONNECTED_NO_INTERNET -> "sem internet"
        ConnectivityState.CONNECTED -> "com internet"
    }
}

internal fun notificationPermissionRequired(sdkInt: Int): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU
