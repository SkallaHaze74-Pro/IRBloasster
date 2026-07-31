package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.ir.SonyHtRt3Profiles
import com.skallahaze.irbloasster.model.CommandLogEntry
import com.skallahaze.irbloasster.model.LgIrCommand
import com.skallahaze.irbloasster.model.LivingRoomScene
import com.skallahaze.irbloasster.model.SonyCommand
import com.skallahaze.irbloasster.model.UiState
import com.skallahaze.irbloasster.model.WebOsConnectionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val screenPadding = 16.dp

@Composable
fun OverviewScreen(state: UiState, viewModel: LivingRoomViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroStatusCard(state) }
        item {
            SectionCard(title = "Schnellstart", subtitle = "Mehrere Geräte mit einem Tastendruck") {
                SceneButtonGrid(state, viewModel)
            }
        }
        item {
            SectionCard(
                title = "LG OLED55B19LA",
                subtitle = state.webOsMessage,
                trailing = {
                    StatusBadge(
                        text = if (state.webOsStatus == WebOsConnectionStatus.CONNECTED) "Online" else "Offline",
                        positive = state.webOsStatus == WebOsConnectionStatus.CONNECTED
                    )
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(
                        label = "Power",
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = { viewModel.sendLgIr(LgIrCommand.POWER) },
                        emphasized = true,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        label = if (state.webOsStatus == WebOsConnectionStatus.CONNECTED) "Trennen" else "Verbinden",
                        icon = if (state.webOsStatus == WebOsConnectionStatus.CONNECTED) Icons.Rounded.LinkOff else Icons.Rounded.Link,
                        onClick = {
                            if (state.webOsStatus == WebOsConnectionStatus.CONNECTED) viewModel.disconnectTv()
                            else viewModel.connectTv()
                        },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldRemoteButton(
                        label = "Leiser",
                        icon = Icons.Rounded.VolumeDown,
                        onRepeat = {
                            if (state.webOsStatus == WebOsConnectionStatus.CONNECTED) viewModel.webOsVolumeDown()
                            else viewModel.sendLgIr(LgIrCommand.VOLUME_DOWN)
                        },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    HoldRemoteButton(
                        label = "Lauter",
                        icon = Icons.Rounded.VolumeUp,
                        onRepeat = {
                            if (state.webOsStatus == WebOsConnectionStatus.CONNECTED) viewModel.webOsVolumeUp()
                            else viewModel.sendLgIr(LgIrCommand.VOLUME_UP)
                        },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            SectionCard(
                title = "Sony HT-RT3",
                subtitle = state.sonyProfileName,
                trailing = {
                    StatusBadge(
                        text = if (state.irAvailable) "IR bereit" else "Kein IR",
                        positive = state.irAvailable
                    )
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(
                        label = "Power",
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = { viewModel.sendSony(SonyCommand.POWER) },
                        emphasized = true,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        label = "Eingang",
                        icon = Icons.Rounded.Input,
                        onClick = { viewModel.sendSony(SonyCommand.INPUT) },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item { RecentLogCard(state.logs) }
    }
}

@Composable
private fun HeroStatusCard(state: UiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 5.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dein Wohnzimmer, eine App", style = MaterialTheme.typography.headlineMedium)
            Text(
                "LG über WLAN + IR-Fallback, Sony über SIRC und frei testbare Profile.",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoPill(if (state.irAvailable) "IR bereit" else "IR nicht erkannt")
                InfoPill(if (state.webOsStatus == WebOsConnectionStatus.CONNECTED) "webOS online" else "webOS offline")
                state.volume?.let { InfoPill("TV $it %") }
                state.foregroundApp?.let { InfoPill(it) }
            }
        }
    }
}

@Composable
private fun SceneButtonGrid(state: UiState, viewModel: LivingRoomViewModel) {
    val scenes = LivingRoomScene.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        scenes.chunked(2).forEach { rowScenes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowScenes.forEach { scene ->
                    RemoteButton(
                        label = if (state.busyScene == scene) "${scene.label} …" else scene.label,
                        icon = if (scene == LivingRoomScene.ALL_OFF) Icons.Rounded.PowerOff else Icons.Rounded.AutoAwesome,
                        onClick = { viewModel.runScene(scene) },
                        enabled = state.busyScene == null,
                        emphasized = scene == LivingRoomScene.HOME_CINEMA,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowScenes.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun TvRemoteScreen(state: UiState, viewModel: LivingRoomViewModel) {
    val connected = state.webOsStatus == WebOsConnectionStatus.CONNECTED
    var textToSend by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(
                title = "LG TV Verbindung",
                subtitle = state.webOsMessage,
                trailing = { StatusBadge(if (connected) "Online" else "Offline", connected) }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(
                        label = "Suchen",
                        icon = Icons.Rounded.Search,
                        onClick = viewModel::discoverTvs,
                        enabled = !state.discoveryRunning,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        label = if (connected) "Aktualisieren" else "Verbinden",
                        icon = if (connected) Icons.Rounded.Refresh else Icons.Rounded.Wifi,
                        onClick = { if (connected) viewModel.refreshTv() else viewModel.connectTv() },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.discoveryRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Suche im lokalen WLAN …")
                    }
                }
                state.discoveredTvs.forEach { tv ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.useDiscoveredTv(tv) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tv, null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tv.name, fontWeight = FontWeight.SemiBold)
                                Text(tv.host, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Verbinden", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Power & Lautstärke", subtitle = "WLAN wenn verbunden, sonst IR") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(
                        label = "TV an",
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = {
                            if (state.tvMac.isNotBlank()) viewModel.wakeTv()
                            viewModel.sendLgIr(LgIrCommand.POWER_ON)
                        },
                        emphasized = true,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        label = "TV aus",
                        icon = Icons.Rounded.PowerOff,
                        onClick = {
                            if (connected) viewModel.webOsPowerOff()
                            else viewModel.sendLgIr(LgIrCommand.POWER_OFF)
                        },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldRemoteButton(
                        label = "Leiser",
                        icon = Icons.Rounded.VolumeDown,
                        onRepeat = {
                            if (connected) viewModel.webOsVolumeDown()
                            else viewModel.sendLgIr(LgIrCommand.VOLUME_DOWN)
                        },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        label = if (state.muted == true) "Ton an" else "Stumm",
                        icon = Icons.Rounded.VolumeOff,
                        onClick = {
                            if (connected) viewModel.toggleWebOsMute()
                            else viewModel.sendLgIr(LgIrCommand.MUTE)
                        },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    HoldRemoteButton(
                        label = "Lauter",
                        icon = Icons.Rounded.VolumeUp,
                        onRepeat = {
                            if (connected) viewModel.webOsVolumeUp()
                            else viewModel.sendLgIr(LgIrCommand.VOLUME_UP)
                        },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.volume?.let { InfoPill("Lautstärke $it %") }
                    state.muted?.let { InfoPill(if (it) "Stumm aktiv" else "Ton aktiv") }
                }
            }
        }

        item {
            SectionCard(title = "Navigation", subtitle = if (connected) "Magic Remote über WLAN" else "IR-Steuerkreuz") {
                DPad(
                    onUp = { tvDirection(connected, "UP", LgIrCommand.UP, viewModel) },
                    onDown = { tvDirection(connected, "DOWN", LgIrCommand.DOWN, viewModel) },
                    onLeft = { tvDirection(connected, "LEFT", LgIrCommand.LEFT, viewModel) },
                    onRight = { tvDirection(connected, "RIGHT", LgIrCommand.RIGHT, viewModel) },
                    onOk = { tvDirection(connected, "ENTER", LgIrCommand.OK, viewModel) },
                    hapticsEnabled = state.hapticsEnabled
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(
                        "Zurück",
                        { tvDirection(connected, "BACK", LgIrCommand.BACK, viewModel) },
                        icon = Icons.Rounded.ArrowBack,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        "Home",
                        { tvDirection(connected, "HOME", LgIrCommand.HOME, viewModel) },
                        icon = Icons.Rounded.Tv,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        "Menü",
                        { tvDirection(connected, "MENU", LgIrCommand.SETTINGS, viewModel) },
                        icon = Icons.Rounded.Settings,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (connected) {
            item {
                SectionCard(title = "Magic-Remote-Touchpad", subtitle = "Nur im lokalen WLAN") {
                    Touchpad(onMove = viewModel::pointerMove, onClick = viewModel::pointerClick)
                }
            }
        }

        item {
            SectionCard(title = "Sender & Medien") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldRemoteButton(
                        "Sender −",
                        { if (connected) viewModel.webOsChannelDown() else viewModel.sendLgIr(LgIrCommand.CHANNEL_DOWN) },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    HoldRemoteButton(
                        "Sender +",
                        { if (connected) viewModel.webOsChannelUp() else viewModel.sendLgIr(LgIrCommand.CHANNEL_UP) },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "⏪" to LgIrCommand.REWIND,
                        "▶" to LgIrCommand.PLAY,
                        "⏸" to LgIrCommand.PAUSE,
                        "⏹" to LgIrCommand.STOP,
                        "⏩" to LgIrCommand.FAST_FORWARD
                    ).forEach { (label, command) ->
                        RemoteButton(
                            label = label,
                            onClick = { viewModel.sendLgIr(command) },
                            hapticsEnabled = state.hapticsEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Eingänge",
                subtitle = if (connected) "Direkt aus dem TV geladen" else "Verbinden für direkte HDMI-Auswahl"
            ) {
                if (state.inputs.isEmpty()) {
                    RemoteButton(
                        label = "Eingang wechseln",
                        icon = Icons.Rounded.Input,
                        onClick = { viewModel.sendLgIr(LgIrCommand.INPUT) },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.inputs, key = { it.id }) { input ->
                            FilterChip(
                                selected = input.connected,
                                onClick = { viewModel.switchInput(input.id) },
                                label = { Text(input.label) },
                                leadingIcon = { Icon(Icons.Rounded.Input, null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }

        if (connected) {
            item {
                SectionCard(title = "Apps", subtitle = state.foregroundApp?.let { "Aktiv: $it" }) {
                    if (state.apps.isEmpty()) {
                        Text("Noch keine App-Liste geladen.")
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.apps.take(30), key = { it.id }) { app ->
                                FilterChip(
                                    selected = state.foregroundApp == app.id,
                                    onClick = { viewModel.launchApp(app.id) },
                                    label = { Text(app.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = { Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Text an den TV senden", subtitle = "Für Suchfelder und Bildschirmtastatur") {
                    OutlinedTextField(
                        value = textToSend,
                        onValueChange = { textToSend = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text") },
                        leadingIcon = { Icon(Icons.Rounded.Keyboard, null) },
                        singleLine = true
                    )
                    Button(
                        onClick = { if (textToSend.isNotBlank()) viewModel.insertText(textToSend) },
                        enabled = textToSend.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Senden")
                    }
                }
            }
        }
    }
}

private fun tvDirection(
    connected: Boolean,
    webOsName: String,
    irCommand: LgIrCommand,
    viewModel: LivingRoomViewModel
) {
    if (connected) viewModel.remoteButton(webOsName) else viewModel.sendLgIr(irCommand)
}

@Composable
fun SonyRemoteScreen(state: UiState, viewModel: LivingRoomViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(
                title = "Sony HT-RT3",
                subtitle = "IR-Profiltester für die Fernbedienung RMT-AH200U",
                trailing = { StatusBadge(if (state.irAvailable) "IR bereit" else "Kein IR", state.irAvailable) }
            ) {
                Text(state.irSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SonyHtRt3Profiles.all.forEachIndexed { index, profile ->
                    FilterChip(
                        selected = state.sonyProfileIndex == index,
                        onClick = { viewModel.selectSonyProfile(index) },
                        label = {
                            Column {
                                Text(profile.name, fontWeight = FontWeight.SemiBold)
                                Text(profile.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    )
                }
                Text(
                    "Die Profile sind Kandidaten. Mit Power, Lauter und Stumm testen und das funktionierende Profil ausgewählt lassen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Power & Eingang") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(
                        "Power",
                        { viewModel.sendSony(SonyCommand.POWER) },
                        icon = Icons.Rounded.PowerSettingsNew,
                        emphasized = true,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        "Eingang",
                        { viewModel.sendSony(SonyCommand.INPUT) },
                        icon = Icons.Rounded.Input,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(
                        "Diskret an",
                        { viewModel.sendSony(SonyCommand.POWER_ON) },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        "Diskret aus",
                        { viewModel.sendSony(SonyCommand.POWER_OFF) },
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionCard(title = "Lautstärke") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldRemoteButton(
                        "Leiser",
                        { viewModel.sendSony(SonyCommand.VOLUME_DOWN) },
                        icon = Icons.Rounded.VolumeDown,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RemoteButton(
                        "Stumm",
                        { viewModel.sendSony(SonyCommand.MUTE) },
                        icon = Icons.Rounded.VolumeOff,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    HoldRemoteButton(
                        "Lauter",
                        { viewModel.sendSony(SonyCommand.VOLUME_UP) },
                        icon = Icons.Rounded.VolumeUp,
                        hapticsEnabled = state.hapticsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionCard(title = "Klangfunktionen", subtitle = "Je nach HT-RT3-Profil verfügbar") {
                val commands = listOf(
                    SonyCommand.CLEAR_AUDIO,
                    SonyCommand.SOUND_FIELD,
                    SonyCommand.NIGHT,
                    SonyCommand.VOICE,
                    SonyCommand.SUBWOOFER_UP,
                    SonyCommand.SUBWOOFER_DOWN
                )
                commands.chunked(2).forEach { rowCommands ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowCommands.forEach { command ->
                            RemoteButton(
                                command.label,
                                { viewModel.sendSony(command) },
                                hapticsEnabled = state.hapticsEnabled,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCommands.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ScenesScreen(state: UiState, viewModel: LivingRoomViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Automationen",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
        items(LivingRoomScene.entries, key = { it.name }) { scene ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = state.busyScene == null) { viewModel.runScene(scene) },
                shape = MaterialTheme.shapes.large,
                color = if (scene == LivingRoomScene.HOME_CINEMA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (scene == LivingRoomScene.ALL_OFF) Icons.Rounded.PowerOff else Icons.Rounded.AutoAwesome, null)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(scene.label, style = MaterialTheme.typography.titleLarge)
                        Text(scene.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.busyScene == scene) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Start", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Text(
                "Die Abläufe verwenden Wake-on-LAN und diskrete IR-Codes, soweit konfiguriert. Einzelne Eingänge können je nach Geräteprofil später fein angepasst werden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@Composable
fun AnalysisScreen(state: UiState, viewModel: LivingRoomViewModel) {
    var protocol by remember { mutableStateOf("NEC") }
    var bits by remember { mutableIntStateOf(32) }
    var hexCode by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(
                title = "Hardware-Diagnose",
                subtitle = "Sicherer Sendetest ohne App-Absturz",
                trailing = { Icon(Icons.Rounded.BugReport, null) }
            ) {
                Text(state.irSummary)
                Text("LG-Profil: NEC 32 Bit · 38 kHz")
                Text("Sony-Profil: ${state.sonyProfileName}")
                Text(
                    "Ein Smartphone-IR-Blaster kann normalerweise nur senden. Der Analysemodus testet deshalb Kandidaten und protokolliert das Ergebnis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "IR-Labor", subtitle = "Eigene Hex-Codes gefahrlos ausprobieren") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = protocol == "NEC", onClick = { protocol = "NEC"; bits = 32 }, label = { Text("NEC 32") })
                    FilterChip(selected = protocol == "SIRC" && bits == 12, onClick = { protocol = "SIRC"; bits = 12 }, label = { Text("SIRC 12") })
                    FilterChip(selected = protocol == "SIRC" && bits == 15, onClick = { protocol = "SIRC"; bits = 15 }, label = { Text("SIRC 15") })
                    FilterChip(selected = protocol == "SIRC" && bits == 20, onClick = { protocol = "SIRC"; bits = 20 }, label = { Text("SIRC 20") })
                }
                OutlinedTextField(
                    value = hexCode,
                    onValueChange = { value ->
                        hexCode = value.filter { char ->
                            char.isDigit() || char.lowercaseChar() in 'a'..'f' || char == 'x' || char == 'X'
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hex-Code, z. B. 20DF10EF") },
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.sendRawIr(protocol, hexCode, bits) },
                    enabled = hexCode.isNotBlank() && state.irAvailable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Testsignal senden")
                }
            }
        }

        item {
            SectionCard(title = "Befehlsprotokoll", subtitle = "Die letzten ${state.logs.size} Ereignisse") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(viewModel.logText())) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Kopieren")
                    }
                    OutlinedButton(onClick = viewModel::clearLogs, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Leeren")
                    }
                }
                if (state.logs.isEmpty()) {
                    Text("Noch keine Befehle gesendet.")
                } else {
                    state.logs.take(40).forEachIndexed { index, entry ->
                        LogRow(entry)
                        if (index != state.logs.take(40).lastIndex) Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentLogCard(logs: List<CommandLogEntry>) {
    SectionCard(title = "Letzte Aktivität") {
        if (logs.isEmpty()) {
            Text("Noch keine Aktion ausgeführt.")
        } else {
            logs.take(4).forEachIndexed { index, entry ->
                LogRow(entry)
                if (index < logs.take(4).lastIndex) Divider()
            }
        }
    }
}

@Composable
private fun LogRow(entry: CommandLogEntry) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 5.dp).size(9.dp).background(
                if (entry.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                CircleShape
            )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.category, fontWeight = FontWeight.SemiBold)
            Text(entry.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            formatter.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsDialog(
    state: UiState,
    viewModel: LivingRoomViewModel,
    onDismiss: () -> Unit
) {
    var ip by remember(state.tvIp) { mutableStateOf(state.tvIp) }
    var mac by remember(state.tvMac) { mutableStateOf(state.tvMac) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Settings, null) },
        title = { Text("Living-Room-Einstellungen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("LG-TV-IP-Adresse") },
                    placeholder = { Text("192.168.178.50") },
                    leadingIcon = { Icon(Icons.Rounded.Wifi, null) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("LG-TV-MAC für Wake-on-LAN") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true
                )
                SettingSwitchRow("Dunkles Premium-Design", state.darkTheme, viewModel::setDarkTheme)
                SettingSwitchRow("Tastenvibration", state.hapticsEnabled, viewModel::setHaptics, Icons.Rounded.Vibration)
                OutlinedButton(onClick = viewModel::forgetTvPairing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.LinkOff, null)
                    Spacer(Modifier.width(7.dp))
                    Text("webOS-Kopplung vergessen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.saveTvSettings(ip, mac); onDismiss() }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
