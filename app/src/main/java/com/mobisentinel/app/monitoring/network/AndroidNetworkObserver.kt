@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.mobisentinel.app.monitoring.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.TransportSnapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidNetworkObserver(
    private val context: Context,
    private val connectivityManager: ConnectivityManager,
    private val telephonyManager: TelephonyManager,
    scope: CoroutineScope,
    cellularProbe: CellularValidationProbe,
) : NetworkObserver {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableStates = MutableSharedFlow<TransportSnapshot>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val wifiTracker = TransportNetworkTracker<Network>()
    private val cellularGate = ProbeNetworkEventGate<Network>()
    private val wifiRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()
    private val cellularRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    override val states: Flow<TransportSnapshot> = mutableStates.asSharedFlow()

    private val policy: CellularObservationPolicy
    private val probeCoordinator: CellularProbeCoordinator

    @Volatile
    private var started = false
    private var seeding = false
    private var wifiRegistered = false
    private var cellularRegistered = false
    private var airplaneRegistered = false
    private var mobileDataRegistered = false
    private var modernMobileDataCallback: Any? = null
    private var legacyMobileDataListener: PhoneStateListener? = null

    init {
        lateinit var coordinator: CellularProbeCoordinator
        policy = CellularObservationPolicy(
            refreshWifiState = ::refreshWifiState,
            triggerCellularProbe = { coordinator.trigger() },
            emit = { snapshot -> mutableStates.tryEmit(snapshot) },
        )
        coordinator = CellularProbeCoordinator(
            scope = scope,
            probe = cellularProbe,
            onProbeRunningChanged = { running ->
                synchronized(lock) {
                    if (running) {
                        cellularGate.onProbeStarted()
                    } else {
                        cellularGate.onProbeFinished()
                    }
                }
            },
            onResult = { result ->
                synchronized(lock) {
                    if (started) policy.onProbeResult(result)
                }
            },
        )
        probeCoordinator = coordinator
    }

    override fun start() = synchronized(lock) {
        if (started) return@synchronized

        seeding = true
        policy.reset()
        cellularGate.clear()
        wifiTracker.clear()
        try {
            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)
            wifiRegistered = true
            connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)
            cellularRegistered = true

            connectivityManager.allNetworks.forEach { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                    ?: return@forEach
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    cellularGate.seed(network)
                }
            }

            ContextCompat.registerReceiver(
                context,
                airplaneReceiver,
                IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            airplaneRegistered = true
            mobileDataRegistered = registerMobileDataListener()

            started = true
            seeding = false
            policy.emitInitialWifi()
            probeCoordinator.start()
        } catch (_: RuntimeException) {
            started = false
            cleanupRegistrationsLocked()
            policy.reset()
            cellularGate.clear()
            wifiTracker.clear()
            seeding = false
        }
    }

    override fun stop() = synchronized(lock) {
        if (
            !started &&
            !wifiRegistered &&
            !cellularRegistered &&
            !airplaneRegistered &&
            !mobileDataRegistered
        ) {
            return@synchronized
        }

        started = false
        cleanupRegistrationsLocked()
        policy.reset()
        cellularGate.clear()
        wifiTracker.clear()
        seeding = false
    }

    private fun refreshWifiState(): ConnectivityState = synchronized(lock) {
        val networks = connectivityManager.allNetworks.mapNotNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return@mapNotNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return@mapNotNull null
            }
            network to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        wifiTracker.replace(networks)
    }

    private fun updateWifi(block: () -> ConnectivityState) = synchronized(lock) {
        val state = block()
        if (started && !seeding) policy.onWifiStateChanged(state)
    }

    private fun routePassiveCellular(block: () -> Boolean) = synchronized(lock) {
        if (started && block()) policy.onPassiveCellularEvent()
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateWifi {
            wifiTracker.onAvailable(network)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) = updateWifi {
            wifiTracker.onCapabilitiesChanged(
                network,
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }

        override fun onLost(network: Network) = updateWifi {
            wifiTracker.onLost(network)
        }
    }

    private val cellularCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = routePassiveCellular {
            cellularGate.onAvailable(network)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) = routePassiveCellular {
            cellularGate.onCapabilitiesChanged(network)
        }

        override fun onLost(network: Network) = routePassiveCellular {
            cellularGate.onLost(network)
        }
    }

    private val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_AIRPLANE_MODE_CHANGED) return
            synchronized(lock) {
                if (started) policy.onAirplaneModeChanged()
            }
        }
    }

    private fun registerMobileDataListener(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            registerModernMobileDataListener()
            true
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            registerLegacyMobileDataListener()
            true
        }
        else -> false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernMobileDataListener() {
        val callback = object : TelephonyCallback(),
            TelephonyCallback.UserMobileDataStateListener {
            override fun onUserMobileDataStateChanged(enabled: Boolean) {
                triggerFromMobileDataSetting()
            }
        }
        telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
        modernMobileDataCallback = callback
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Suppress("DEPRECATION")
    private fun registerLegacyMobileDataListener() {
        runOnMainThread {
            val listener = object : PhoneStateListener() {
                override fun onUserMobileDataStateChanged(enabled: Boolean) {
                    triggerFromMobileDataSetting()
                }
            }
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_USER_MOBILE_DATA_STATE)
            legacyMobileDataListener = listener
        }
    }

    private fun triggerFromMobileDataSetting() {
        if (started) probeCoordinator.trigger()
    }

    @Suppress("DEPRECATION")
    private fun unregisterMobileDataListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterModernMobileDataListener()
        } else {
            runOnMainThread {
                val listener = legacyMobileDataListener
                try {
                    if (listener != null) {
                        telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
                    }
                } finally {
                    legacyMobileDataListener = null
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterModernMobileDataListener() {
        try {
            (modernMobileDataCallback as? TelephonyCallback)?.let(
                telephonyManager::unregisterTelephonyCallback,
            )
        } finally {
            modernMobileDataCallback = null
        }
    }

    private fun cleanupRegistrationsLocked() {
        probeCoordinator.stop()
        if (mobileDataRegistered) {
            try {
                unregisterMobileDataListener()
            } catch (_: RuntimeException) {
                // The platform may already have discarded the listener.
            }
            mobileDataRegistered = false
        }
        if (airplaneRegistered) {
            try {
                context.unregisterReceiver(airplaneReceiver)
            } catch (_: IllegalArgumentException) {
                // The receiver may already have been removed during teardown.
            }
            airplaneRegistered = false
        }
        if (cellularRegistered) {
            unregisterNetworkCallbackSafely(cellularCallback)
            cellularRegistered = false
        }
        if (wifiRegistered) {
            unregisterNetworkCallbackSafely(wifiCallback)
            wifiRegistered = false
        }
    }

    private fun unregisterNetworkCallbackSafely(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // The callback may already have been removed during teardown.
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }

        val completion = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        check(
            mainHandler.post {
                try {
                    block()
                } catch (caught: Throwable) {
                    failure.set(caught)
                } finally {
                    completion.countDown()
                }
            },
        )
        try {
            completion.await()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for the Android main thread", interrupted)
        }
        failure.get()?.let { throw IllegalStateException("Android main-thread operation failed", it) }
    }
}
