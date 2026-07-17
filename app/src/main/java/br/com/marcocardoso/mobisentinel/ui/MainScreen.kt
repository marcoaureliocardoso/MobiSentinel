package br.com.marcocardoso.mobisentinel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import br.com.marcocardoso.mobisentinel.monitoring.model.ConnectivityState

@Composable
fun MainScreen(
    uiState: MainUiState,
    onActivate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "MobiSentinel",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        AssistChip(
            onClick = {},
            label = {
                Text(
                    if (uiState.snapshot.serviceActive) {
                        "Monitoramento ativo"
                    } else {
                        "Monitoramento inativo"
                    },
                )
            },
        )
        ConnectionCard(
            tag = "wifi_status_card",
            transportLabel = "Wi-Fi",
            state = uiState.snapshot.wifi,
        )
        ConnectionCard(
            tag = "cellular_status_card",
            transportLabel = "Dados móveis",
            state = uiState.snapshot.cellular,
        )
        Spacer(Modifier.height(4.dp))
        if (!uiState.settings.monitoringEnabled) {
            Button(
                onClick = onActivate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activate_monitoring"),
            ) {
                Text("Ativar monitoramento")
            }
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("open_settings"),
        ) {
            Text("Configurações")
        }
    }
}

@Composable
private fun ConnectionCard(
    tag: String,
    transportLabel: String,
    state: ConnectivityState?,
) {
    val presentation = statePresentation(state)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = "$transportLabel: ${presentation.semanticText}"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(presentation.color, CircleShape),
            )
            Column {
                Text(
                    text = transportLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = presentation.visibleText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

private data class StatePresentation(
    val visibleText: String,
    val semanticText: String,
    val color: Color,
)

private fun statePresentation(state: ConnectivityState?): StatePresentation = when (state) {
    null -> StatePresentation("Verificando", "verificando", Color(0xFF64748B))
    ConnectivityState.DISCONNECTED ->
        StatePresentation("Desconectado", "desconectado", Color(0xFFB91C1C))
    ConnectivityState.CONNECTED_NO_INTERNET -> StatePresentation(
        "Conectado sem internet",
        "conectado sem internet",
        Color(0xFFB45309),
    )
    ConnectivityState.CONNECTED -> StatePresentation(
        "Conectado com internet",
        "conectado com internet",
        Color(0xFF15803D),
    )
}
