package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.LivingRoomViewModel
import com.skallahaze.irbloasster.model.WebOsConnectionState

@Composable
fun HomeTab(
    viewModel: LivingRoomViewModel,
    modifier: Modifier = Modifier
) {
    val connection by viewModel.connectionState.collectAsState()
    val status by viewModel.tvStatus.collectAsState()
    val discovered by viewModel.discoveredTvs.collectAsState()
    val discovering by viewModel.isDiscovering.collectAsState()
    val macro by viewModel.macroProgress.collectAsState()
    val manualIp by viewModel.manualIp.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(
                title = "Wohnzimmer",
                subtitle = "LG webOS + Sony Heimkino in einer Fernbedienung"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = connectionLabel(connection),
                        active = connection == WebOsConnectionState.CONNECTED
                    )
                    Text(
                        text = status.systemModelName ?: manualIp.ifBlank { "TV noch nicht gewählt" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LabelValueRow("Lautstärke", status.volume?.let { "$it %" } ?: "–")
                LabelValueRow("Ton", if (status.muted == true) "Stumm" else "Aktiv")
                LabelValueRow(
                    "Aktiv",
                    status.foregroundAppTitle
                        ?: status.foregroundAppId
                        ?: status.channelName
                        ?: status.inputId
                        ?: "–"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = viewModel::wakeTv,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(Icons.Rounded.Wifi, null)
                        Text("Aufwecken", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = viewModel::connectTv,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(Icons.Rounded.Link, null)
                        Text("Verbinden", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Szenen",
                subtitle = "Mehrere Geräte mit einem Fingertipp steuern"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RemoteActionButton(
                        label = "Filmabend",
                        icon = Icons.Rounded.Movie,
                        onClick = viewModel::runFilmScene,
                        modifier = Modifier.weight(1f),
                        emphasized = true
                    )
                    RemoteActionButton(
                        label = "Gaming",
                        icon = Icons.Rounded.Gamepad,
                        onClick = viewModel::runGamingScene,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteActionButton(
                        label = "Alles aus",
                        icon = Icons.Rounded.PowerOff,
                        onClick = viewModel::runAllOffScene,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (macro.running) {
                    LinearProgressIndicator(
                        progress = if (macro.totalSteps == 0) 0f
                        else macro.completedSteps.toFloat() / macro.totalSteps.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = macro.stepLabel ?: macro.macroName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (!macro.lastError.isNullOrBlank()) {
                    Text(
                        text = macro.lastError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "TV finden",
                subtitle = "Lokale SSDP-Suche – kein LG-Konto erforderlich"
            ) {
                Button(
                    onClick = viewModel::discoverTvs,
                    enabled = !discovering,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (discovering) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        androidx.compose.material3.Icon(Icons.Rounded.Search, null)
                    }
                    Text(
                        if (discovering) "Suche läuft …" else "LG webOS TVs suchen",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        items(discovered, key = { it.ipAddress }) { tv ->
            SectionCard(
                title = tv.name,
                subtitle = listOfNotNull(tv.modelName, tv.ipAddress).joinToString(" • ")
            ) {
                Button(
                    onClick = { viewModel.useDiscoveredTv(tv) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.Link, null)
                    Text("Auswählen und koppeln", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (discovered.isEmpty() && !discovering) {
            item {
                Text(
                    text = "Der TV kann alternativ im Diagnose-Tab per IP-Adresse eingetragen werden.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

private fun connectionLabel(state: WebOsConnectionState): String = when (state) {
    WebOsConnectionState.DISCONNECTED -> "Nicht verbunden"
    WebOsConnectionState.DISCOVERING -> "Suche"
    WebOsConnectionState.CONNECTING -> "Verbinden …"
    WebOsConnectionState.PAIRING -> "Am TV bestätigen"
    WebOsConnectionState.CONNECTED -> "Verbunden"
    WebOsConnectionState.ERROR -> "Fehler"
}
