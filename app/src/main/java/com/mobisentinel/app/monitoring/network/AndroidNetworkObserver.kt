package com.mobisentinel.app.monitoring.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.Transport
import com.mobisentinel.app.monitoring.model.TransportSnapshot
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidNetworkObserver(
    private val connectivityManager: ConnectivityManager,
) : NetworkObserver {
    private val lock = Any()
    private val mutableStates = MutableSharedFlow<TransportSnapshot>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val states: Flow<TransportSnapshot> = mutableStates.asSharedFlow()

    private val wifiCallback = TransportCallback(Transport.WIFI)
    private val cellularCallback = TransportCallback(Transport.CELLULAR)
    private val wifiRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()
    private val cellularRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private var started = false
    private var seeding = false
    private var lastWifiState: ConnectivityState? = null
    private var lastCellularState: ConnectivityState? = null

    @Suppress("DEPRECATION")
    override fun start() {
        synchronized(lock) {
            if (started) return

            seeding = true
            wifiCallback.clear()
            cellularCallback.clear()
            lastWifiState = null
            lastCellularState = null

            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)
            connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)
            connectivityManager.allNetworks.forEach { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                    ?: return@forEach
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    wifiCallback.seed(network, capabilities)
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    cellularCallback.seed(network, capabilities)
                }
            }

            started = true
            seeding = false
            emitInitialState(Transport.WIFI, wifiCallback.currentState)
            emitInitialState(Transport.CELLULAR, cellularCallback.currentState)
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (!started) return

            started = false
            try {
                connectivityManager.unregisterNetworkCallback(wifiCallback)
            } catch (_: IllegalArgumentException) {
                // The platform may already have removed a callback during teardown.
            }
            try {
                connectivityManager.unregisterNetworkCallback(cellularCallback)
            } catch (_: IllegalArgumentException) {
                // The platform may already have removed a callback during teardown.
            }
            wifiCallback.clear()
            cellularCallback.clear()
            lastWifiState = null
            lastCellularState = null
            seeding = false
        }
    }

    private fun emitInitialState(transport: Transport, state: ConnectivityState) {
        setLastState(transport, state)
        mutableStates.tryEmit(TransportSnapshot(transport, state))
    }

    private fun emitIfChanged(transport: Transport, state: ConnectivityState) {
        val previous = when (transport) {
            Transport.WIFI -> lastWifiState
            Transport.CELLULAR -> lastCellularState
        }
        if (state == previous) return

        setLastState(transport, state)
        mutableStates.tryEmit(TransportSnapshot(transport, state))
    }

    private fun setLastState(transport: Transport, state: ConnectivityState) {
        when (transport) {
            Transport.WIFI -> lastWifiState = state
            Transport.CELLULAR -> lastCellularState = state
        }
    }

    private inner class TransportCallback(
        private val transport: Transport,
    ) : ConnectivityManager.NetworkCallback() {
        private val tracker = TransportNetworkTracker<Network>()
        var currentState: ConnectivityState = ConnectivityState.DISCONNECTED
            private set

        override fun onAvailable(network: Network) {
            update { tracker.onAvailable(network) }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            update {
                tracker.onCapabilitiesChanged(
                    network,
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }
        }

        override fun onLost(network: Network) {
            update { tracker.onLost(network) }
        }

        fun seed(network: Network, capabilities: NetworkCapabilities) {
            currentState = tracker.onAvailable(network)
            currentState = tracker.onCapabilitiesChanged(
                network,
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }

        fun clear() {
            currentState = tracker.clear()
        }

        private fun update(block: () -> ConnectivityState) {
            synchronized(lock) {
                currentState = block()
                if (started && !seeding) {
                    emitIfChanged(transport, currentState)
                }
            }
        }
    }
}
