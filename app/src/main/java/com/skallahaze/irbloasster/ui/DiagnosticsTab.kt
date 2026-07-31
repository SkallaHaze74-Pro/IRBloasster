package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.LivingRoomViewModel
import com.skallahaze.irbloasster.model.DiagnosticDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsTab(
    viewModel: LivingRoomViewModel,
    modifier: Modifier = Modifier
) {
    val manualIp by viewModel.manualIp.collectAsState()
    val mac by viewModel.tvMacAddress.collectAsState()
    val preferredInput by viewModel.preferredInput.collectAsState()
    val connection by viewModel.connectionState.collectAsState()
    val error by viewModel.webOsError.collectAsState()
    val logs by viewModel.diagnostics.entries.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(
                title = "Verbindung",
                subtitle = "Manuelle Werte bleiben lokal auf diesem Handy"
            ) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = viewModel::setManualIp,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("TV-IP-Adresse") },
                    placeholder = { Text("192.168.178.50") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = viewModel::setTvMacAddress,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("TV-MAC für Wake-on-LAN") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = preferredInput,
                    onValueChange = viewModel::setPreferredInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bevorzugte Eingangs-ID für Szenen") },
                    placeholder = { Text("HDMI_1") },
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = viewModel::connectTv,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Link, null)
                        Text("Verbinden", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = viewModel::disconnectTv,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.LinkOff, null)
                        Text("Trennen", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::discoverTvs,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Search, null)
                        Text("Suchen", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = viewModel::refreshTv,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Refresh, null)
                        Text("Status", modifier = Modifier.padding(start = 6.dp))
                    }
                }

                LabelValueRow("webOS", connection.name)
                LabelValueRow("IR-Blaster", if (viewModel.irAvailable) "Vorhanden" else "Nicht erkannt")
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedButton(
                    onClick = viewModel::forgetPairingAndReconnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Client-Key löschen und neu koppeln")
                }
            }
        }

        item {
            SectionCard(
                title = "Datenschutz & Sicherheit",
                subtitle = "Keine ThinQ-Konten, Cloud-Schlüssel oder LG-Assets im Projekt"
            ) {
                LabelValueRow("TV Client-Key", "Android Keystore / AES-GCM")
                LabelValueRow("WSS-Zertifikat", "Trust on first use + Fingerprint")
                LabelValueRow("Steuerweg", "Lokales Heimnetz + IR")
                LabelValueRow("Diagnoselog", "Client-Keys automatisch geschwärzt")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Diagnoseprotokoll", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = viewModel::clearDiagnostics) {
                    Icon(Icons.Rounded.DeleteSweep, null)
                    Text("Leeren", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        items(logs.asReversed(), key = { it.timestampMillis.toString() + it.message.hashCode() }) { entry ->
            val color = when (entry.direction) {
                DiagnosticDirection.ERROR -> MaterialTheme.colorScheme.error
                DiagnosticDirection.WARN -> MaterialTheme.colorScheme.tertiary
                DiagnosticDirection.OUT -> MaterialTheme.colorScheme.secondary
                DiagnosticDirection.IN -> MaterialTheme.colorScheme.primary
                DiagnosticDirection.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "${formatTime(entry.timestampMillis)}  ${entry.direction}  ${entry.category}",
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    text = "Noch keine Einträge. Verbinden, suchen oder eine Taste senden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
