package com.mobisentinel.app.monitoring.network

import com.mobisentinel.app.monitoring.model.TransportSnapshot
import kotlinx.coroutines.flow.Flow

interface NetworkObserver {
    val states: Flow<TransportSnapshot>

    fun start()

    fun stop()
}
