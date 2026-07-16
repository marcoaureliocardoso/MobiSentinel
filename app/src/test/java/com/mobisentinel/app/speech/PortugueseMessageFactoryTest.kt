package com.mobisentinel.app.speech

import com.mobisentinel.app.monitoring.model.ConfirmedTransition
import com.mobisentinel.app.monitoring.model.ConnectivityState
import com.mobisentinel.app.monitoring.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Test

class PortugueseMessageFactoryTest {
    @Test
    fun currentStateMapsToExactBrazilianPortugueseCopy() {
        val cases = listOf(
            Case(Transport.WIFI, ConnectivityState.DISCONNECTED, "Wi-Fi desconectado."),
            Case(
                Transport.WIFI,
                ConnectivityState.CONNECTED_NO_INTERNET,
                "Wi-Fi conectado, mas sem acesso à internet.",
            ),
            Case(
                Transport.WIFI,
                ConnectivityState.CONNECTED,
                "Acesso à internet por Wi-Fi restabelecido.",
            ),
            Case(Transport.CELLULAR, ConnectivityState.DISCONNECTED, "Dados móveis desconectados."),
            Case(
                Transport.CELLULAR,
                ConnectivityState.CONNECTED_NO_INTERNET,
                "Dados móveis conectados, mas sem acesso à internet.",
            ),
            Case(
                Transport.CELLULAR,
                ConnectivityState.CONNECTED,
                "Acesso à internet por dados móveis restabelecido.",
            ),
        )

        cases.forEach { case ->
            val transition = ConfirmedTransition(
                transport = case.transport,
                previous = ConnectivityState.CONNECTED,
                current = case.state,
            )

            assertEquals(Announcement(case.transport, case.text), PortugueseMessageFactory.from(transition))
        }
    }

    private data class Case(
        val transport: Transport,
        val state: ConnectivityState,
        val text: String,
    )
}
