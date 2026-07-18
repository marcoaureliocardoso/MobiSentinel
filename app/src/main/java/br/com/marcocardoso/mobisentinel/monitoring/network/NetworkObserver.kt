package br.com.marcocardoso.mobisentinel.monitoring.network

import br.com.marcocardoso.mobisentinel.monitoring.model.TransportSnapshot
import kotlinx.coroutines.flow.Flow

interface NetworkObserver {
    val states: Flow<TransportSnapshot>

    fun start()

    fun stop()
}
