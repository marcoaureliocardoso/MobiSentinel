package com.mobisentinel.app.monitoring.network

import com.mobisentinel.app.monitoring.model.ConnectivityState

class TransportNetworkTracker<K> {
    private val validationByNetwork = linkedMapOf<K, Boolean>()

    fun onAvailable(id: K): ConnectivityState {
        validationByNetwork.putIfAbsent(id, false)
        return aggregate()
    }

    fun onCapabilitiesChanged(id: K, validated: Boolean): ConnectivityState {
        validationByNetwork[id] = validated
        return aggregate()
    }

    fun onLost(id: K): ConnectivityState {
        validationByNetwork.remove(id)
        return aggregate()
    }

    fun clear(): ConnectivityState {
        validationByNetwork.clear()
        return aggregate()
    }

    private fun aggregate(): ConnectivityState = when {
        validationByNetwork.values.any { it } -> ConnectivityState.CONNECTED
        validationByNetwork.isNotEmpty() -> ConnectivityState.CONNECTED_NO_INTERNET
        else -> ConnectivityState.DISCONNECTED
    }
}
