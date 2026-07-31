package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.skallahaze.irbloasster.ir.SonyCommand

@Composable
fun SonyRemoteTab(
    viewModel: LivingRoomViewModel,
    modifier: Modifier = Modifier
) {
    val profileIndex by viewModel.sonyProfileIndex.collectAsState()
    val profile = viewModel.sonyProfiles[profileIndex]

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(
            title = "Sony Heimkino",
            subtitle = "SIRC-Code-Assistent für dein echtes Gerät"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (viewModel.irAvailable) "IR-Blaster bereit" else "Kein IR-Blaster erkannt",
                    active = viewModel.irAvailable
                )
                Text(
                    text = "${profile.bits} Bit • Adresse ${profile.address}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        SectionCard(
            title = profile.name,
            subtitle = profile.note
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = viewModel::previousSonyProfile) {
                    Icon(Icons.Rounded.ChevronLeft, null)
                    Text("Vorheriges")
                }
                Text(
                    text = "${profileIndex + 1} / ${viewModel.sonyProfiles.size}",
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(onClick = viewModel::nextSonyProfile) {
                    Text("Nächstes")
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
            Text(
                text = "Beginne mit Power. Reagiert das Gerät nicht, wähle das nächste Profil. Erst nach dem Test gilt ein Profil als bestätigt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(
            title = "Fernbedienung",
            subtitle = "Sony SIRC wird mit 40 kHz und drei Wiederholungen gesendet"
        ) {
            RemoteActionButton(
                label = "Power testen",
                icon = Icons.Rounded.PowerSettingsNew,
                onClick = { viewModel.sendSony(SonyCommand.POWER) },
                modifier = Modifier.fillMaxWidth(),
                emphasized = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RepeatRemoteButton(
                    label = "Leiser",
                    icon = Icons.Rounded.VolumeDown,
                    onRepeat = { viewModel.sendSony(SonyCommand.VOLUME_DOWN) },
                    modifier = Modifier.weight(1f)
                )
                RemoteActionButton(
                    label = "Stumm",
                    icon = Icons.Rounded.VolumeOff,
                    onClick = { viewModel.sendSony(SonyCommand.MUTE) },
                    modifier = Modifier.weight(1f)
                )
                RepeatRemoteButton(
                    label = "Lauter",
                    icon = Icons.Rounded.VolumeUp,
                    onRepeat = { viewModel.sendSony(SonyCommand.VOLUME_UP) },
                    modifier = Modifier.weight(1f)
                )
            }

            RemoteActionButton(
                label = "Nächster Eingang",
                icon = Icons.Rounded.Input,
                onClick = { viewModel.sendSony(SonyCommand.INPUT_NEXT) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard(
            title = "Testablauf",
            subtitle = "So finden wir das passende Profil ohne proprietäre Code-Datenbank"
        ) {
            LabelValueRow("1", "Power testen")
            LabelValueRow("2", "Lautstärke + / − prüfen")
            LabelValueRow("3", "Mute und Eingang prüfen")
            LabelValueRow("4", "Profilnummer notieren")
            Button(
                onClick = { viewModel.sendSony(SonyCommand.POWER) },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.irAvailable
            ) {
                Icon(Icons.Rounded.Speaker, null)
                Text("Aktuelles Profil erneut testen", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
