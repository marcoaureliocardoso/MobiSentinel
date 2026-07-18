package br.com.marcocardoso.mobisentinel.monitoring

import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.MonitoringSnapshot
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MonitoringStateStore {
    private val mutable = MutableStateFlow(MonitoringSnapshot())
    val snapshot: StateFlow<MonitoringSnapshot> = mutable.asStateFlow()

    fun setServiceActive(active: Boolean) {
        mutable.update { it.copy(serviceActive = active) }
    }

    fun setState(transport: Transport, state: ConnectivityState) {
        mutable.update { snapshot ->
            when (transport) {
                Transport.WIFI -> snapshot.copy(wifi = state)
                Transport.CELLULAR -> snapshot.copy(cellular = state)
            }
        }
    }
}
