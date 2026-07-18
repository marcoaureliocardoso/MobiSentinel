package br.com.marcocardoso.mobisentinel.speech

import br.com.marcocardoso.mobisentinel.monitoring.model.ConfirmedTransition
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState
import br.com.marcocardoso.mobisentinel.monitoring.model.Transport

object PortugueseMessageFactory {
    fun from(transition: ConfirmedTransition): Announcement {
        val text = when (transition.transport) {
            Transport.WIFI -> when (transition.current) {
                ConnectivityState.DISCONNECTED -> "Wi-Fi desconectado."
                ConnectivityState.CONNECTED_NO_INTERNET ->
                    "Wi-Fi conectado, mas sem acesso à internet."
                ConnectivityState.CONNECTED -> "Acesso à internet por Wi-Fi restabelecido."
            }
            Transport.CELLULAR -> when (transition.current) {
                ConnectivityState.DISCONNECTED -> "Dados móveis desconectados."
                ConnectivityState.CONNECTED_NO_INTERNET ->
                    "Dados móveis conectados, mas sem acesso à internet."
                ConnectivityState.CONNECTED ->
                    "Acesso à internet por dados móveis restabelecido."
            }
        }
        return Announcement(transition.transport, text)
    }
}
