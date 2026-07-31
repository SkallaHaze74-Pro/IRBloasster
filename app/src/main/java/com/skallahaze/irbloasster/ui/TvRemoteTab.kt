package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.LivingRoomViewModel
import com.skallahaze.irbloasster.ir.LgIrCommand
import com.skallahaze.irbloasster.model.WebOsConnectionState

@Composable
fun TvRemoteTab(
    viewModel: LivingRoomViewModel,
    modifier: Modifier = Modifier
) {
    val connection by viewModel.connectionState.collectAsState()
    val status by viewModel.tvStatus.collectAsState()
    val apps by viewModel.tvApps.collectAsState()
    val inputs by viewModel.tvInputs.collectAsState()
    val preferredInput by viewModel.preferredInput.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(
                title = "LG TV",
                subtitle = status.systemModelName ?: "OLED55B19LA / webOS"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = if (connection == WebOsConnectionState.CONNECTED) "WLAN aktiv" else "IR bereit",
                        active = connection == WebOsConnectionState.CONNECTED
                    )
                    Text(
                        text = status.powerState ?: "Status unbekannt",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RemoteActionButton(
                        label = "Ein / Wake",
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = viewModel::wakeTv,
                        modifier = Modifier.weight(1f),
                        emphasized = true
                    )
                    RemoteActionButton(
                        label = "Aus über WLAN",
                        icon = Icons.Rounded.PowerOff,
                        onClick = viewModel::tvPowerOff,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteActionButton(
                        label = "Power IR",
                        icon = Icons.Rounded.Tv,
                        onClick = { viewModel.sendLgIr(LgIrCommand.POWER) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Lautstärke",
                subtitle = "Lang drücken hält die Änderung am Laufen"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RepeatRemoteButton(
                        label = "Leiser",
                        icon = Icons.Rounded.VolumeDown,
                        onRepeat = viewModel::volumeDown,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteActionButton(
                        label = if (status.muted == true) "Ton an" else "Stumm",
                        icon = Icons.Rounded.VolumeOff,
                        onClick = viewModel::toggleMute,
                        modifier = Modifier.weight(1f),
                        emphasized = status.muted == true
                    )
                    RepeatRemoteButton(
                        label = "Lauter",
                        icon = Icons.Rounded.VolumeUp,
                        onRepeat = viewModel::volumeUp,
                        modifier = Modifier.weight(1f)
                    )
                }

                var sliderValue by remember(status.volume) {
                    mutableFloatStateOf((status.volume ?: 20).toFloat())
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { viewModel.setVolume(sliderValue.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19
                )
                Text(
                    text = "${sliderValue.toInt()} %",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        item {
            SectionCard(
                title = "Navigation",
                subtitle = if (status.pointerConnected) "Magic-Remote-Socket verbunden" else "Beim ersten Tastendruck wird das Touchpad verbunden"
            ) {
                DPad(
                    onUp = { viewModel.pointerButton("UP") },
                    onDown = { viewModel.pointerButton("DOWN") },
                    onLeft = { viewModel.pointerButton("LEFT") },
                    onRight = { viewModel.pointerButton("RIGHT") },
                    onOk = viewModel::pointerClick
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RemoteActionButton(
                        label = "Home",
                        icon = Icons.Rounded.Home,
                        onClick = { viewModel.pointerButton("HOME") },
                        modifier = Modifier.weight(1f)
                    )
                    RemoteActionButton(
                        label = "Zurück",
                        icon = Icons.Rounded.ArrowBack,
                        onClick = { viewModel.pointerButton("BACK") },
                        modifier = Modifier.weight(1f)
                    )
                    RemoteActionButton(
                        label = "Quelle IR",
                        icon = Icons.Rounded.Input,
                        onClick = { viewModel.sendLgIr(LgIrCommand.INPUT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionCard(title = "Wiedergabe") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RemoteActionButton("Zurück", Icons.Rounded.FastRewind, viewModel::mediaRewind, Modifier.weight(1f))
                    RemoteActionButton("Play", Icons.Rounded.PlayArrow, viewModel::mediaPlay, Modifier.weight(1f), true)
                    RemoteActionButton("Pause", Icons.Rounded.Pause, viewModel::mediaPause, Modifier.weight(1f))
                    RemoteActionButton("Stop", Icons.Rounded.Stop, viewModel::mediaStop, Modifier.weight(1f))
                    RemoteActionButton("Vor", Icons.Rounded.FastForward, viewModel::mediaFastForward, Modifier.weight(1f))
                }
            }
        }

        item {
            SectionCard(
                title = "Eingänge",
                subtitle = if (inputs.isEmpty()) "Nach dem Verbinden automatisch vom TV geladen" else "Antippen zum Umschalten; Stern = Szene-Eingang"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    inputs.forEach { input ->
                        FilterChip(
                            selected = preferredInput == input.id,
                            onClick = {
                                viewModel.setPreferredInput(input.id)
                                viewModel.switchInput(input.id)
                            },
                            label = { Text(input.label) },
                            leadingIcon = if (preferredInput == input.id) {
                                { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }
                            } else null
                        )
                    }
                    if (inputs.isEmpty()) {
                        AssistChip(
                            onClick = viewModel::refreshTv,
                            label = { Text("Neu laden") },
                            leadingIcon = { Icon(Icons.Rounded.Input, null) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "TV-Apps",
                subtitle = status.foregroundAppTitle ?: status.foregroundAppId ?: "Installierte Apps werden lokal abgerufen"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    apps.take(30).forEach { app ->
                        AssistChip(
                            onClick = { viewModel.launchApp(app.id) },
                            label = { Text(app.title) }
                        )
                    }
                    if (apps.isEmpty()) {
                        AssistChip(
                            onClick = viewModel::refreshTv,
                            label = { Text("Apps laden") }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "IR-Fallback",
                subtitle = "Funktioniert auch ohne WLAN; Tasten am echten TV prüfen"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.sendLgIr(LgIrCommand.VOLUME_DOWN) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Vol −") }
                    OutlinedButton(
                        onClick = { viewModel.sendLgIr(LgIrCommand.MUTE) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Mute") }
                    OutlinedButton(
                        onClick = { viewModel.sendLgIr(LgIrCommand.VOLUME_UP) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Vol +") }
                }
                Button(
                    onClick = viewModel::connectTv,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("WLAN-Verbindung aktualisieren")
                }
            }
        }
    }
}

@Composable
private fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RemoteActionButton(
            label = "Hoch",
            icon = Icons.Rounded.KeyboardArrowUp,
            onClick = onUp,
            modifier = Modifier.width(105.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteActionButton(
                label = "Links",
                icon = Icons.Rounded.KeyboardArrowLeft,
                onClick = onLeft,
                modifier = Modifier.width(105.dp)
            )
            RemoteActionButton(
                label = "OK",
                icon = Icons.Rounded.CheckCircle,
                onClick = onOk,
                modifier = Modifier.width(105.dp),
                emphasized = true
            )
            RemoteActionButton(
                label = "Rechts",
                icon = Icons.Rounded.KeyboardArrowRight,
                onClick = onRight,
                modifier = Modifier.width(105.dp)
            )
        }
        RemoteActionButton(
            label = "Runter",
            icon = Icons.Rounded.KeyboardArrowDown,
            onClick = onDown,
            modifier = Modifier.width(105.dp)
        )
    }
}
