package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skallahaze.irbloasster.LivingRoomViewModel
import com.skallahaze.irbloasster.ir.LgIrKey
import com.skallahaze.irbloasster.model.AppTab
import com.skallahaze.irbloasster.model.ConnectionPhase
import com.skallahaze.irbloasster.model.LivingRoomUiState
import com.skallahaze.irbloasster.model.SceneType
import com.skallahaze.irbloasster.ui.theme.LivingAmber
import com.skallahaze.irbloasster.ui.theme.LivingCyan
import com.skallahaze.irbloasster.ui.theme.LivingGreen
import com.skallahaze.irbloasster.ui.theme.LivingPink
import com.skallahaze.irbloasster.ui.theme.LivingViolet
import kotlin.math.abs

@Composable
fun LivingRoomApp(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )
    Box(Modifier.fillMaxSize().background(background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { AppHeader(state) },
            bottomBar = { AppNavigation(state.selectedTab, viewModel::selectTab) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state.selectedTab) {
                    AppTab.HOME -> HomeScreen(state, viewModel)
                    AppTab.TV -> TvScreen(state, viewModel)
                    AppTab.SONY -> SonyScreen(state, viewModel)
                    AppTab.LAB -> CodeLabScreen(state, viewModel)
                    AppTab.SETTINGS -> SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(state: LivingRoomUiState) {
    val statusColor = when (state.connectionPhase) {
        ConnectionPhase.CONNECTED -> LivingGreen
        ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING, ConnectionPhase.PAIRING -> LivingAmber
        ConnectionPhase.ERROR -> LivingPink
        ConnectionPhase.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(
                Brush.linearGradient(listOf(LivingCyan, LivingPink))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Tv, null, tint = Color(0xFF071014))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("LivingRoom Controller", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                state.statusMessage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Text(
                if (state.connectionPhase == ConnectionPhase.CONNECTED) "ONLINE" else state.connectionPhase.name,
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppNavigation(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .97f)) {
        AppTab.entries.forEach { tab ->
            val icon = when (tab) {
                AppTab.HOME -> Icons.Default.Home
                AppTab.TV -> Icons.Default.Tv
                AppTab.SONY -> Icons.Default.Speaker
                AppTab.LAB -> Icons.Default.Science
                AppTab.SETTINGS -> Icons.Default.Settings
            }
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, tab.label) },
                label = { Text(tab.label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun HomeScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) = ScreenList {
    item { StatusCard(state, viewModel) }
    item { SectionTitle("Szenen", "Mehrere Geräte mit einem Tipp") }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(SceneType.entries) { scene -> SceneCard(scene, state.sceneRunning, viewModel::runScene) }
        }
    }
    item { SectionTitle("Schnellzugriff", "WLAN-Steuerung mit IR-Fallback") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("TV ein", Icons.Default.PowerSettingsNew, LivingPink, viewModel::wakeTv, Modifier.weight(1f))
                ActionButton("Lauter", Icons.Default.VolumeUp, LivingCyan, viewModel::tvVolumeUp, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Leiser", Icons.Default.VolumeDown, LivingViolet, viewModel::tvVolumeDown, Modifier.weight(1f))
                ActionButton(if (state.muted) "Ton an" else "Stumm", Icons.Default.VolumeOff, LivingAmber, viewModel::tvMute, Modifier.weight(1f))
            }
        }
    }
    item {
        PremiumCard {
            Text("Live-Übersicht", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Lautstärke", state.volume?.toString() ?: "–", Icons.Default.VolumeUp, Modifier.weight(1f))
                Metric("App", state.currentAppId.ifBlank { "–" }, Icons.Default.Apps, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Power", state.powerState.ifBlank { "–" }, Icons.Default.PowerSettingsNew, Modifier.weight(1f))
                Metric("Touchpad", if (state.pointerReady) "Bereit" else "–", Icons.Default.TouchApp, Modifier.weight(1f))
            }
        }
    }
    item {
        HintCard(
            "Hybrid-Steuerung",
            "LG webOS liefert Live-Status, Apps, Eingänge und Touchpad. Wake-on-LAN oder der IR-Blaster übernehmen Einschalten und Offline-Fallback."
        )
    }
}

@Composable
private fun StatusCard(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    val connected = state.connectionPhase == ConnectionPhase.CONNECTED
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f))
    ) {
        Column(
            Modifier.background(
                Brush.linearGradient(listOf(LivingCyan.copy(.12f), LivingPink.copy(.08f), Color.Transparent))
            ).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (connected) Icons.Default.Wifi else Icons.Default.WifiOff, null, tint = if (connected) LivingGreen else LivingAmber)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (connected) "LG TV verbunden" else "LG TV bereitmachen", fontWeight = FontWeight.Bold)
                    Text(state.settings.tvIp.ifBlank { "Noch keine TV-IP gespeichert" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.connectionPhase in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING, ConnectionPhase.PAIRING)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (connected) viewModel::disconnectTv else viewModel::connectTv,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (connected) Icons.Default.LinkOff else Icons.Default.Link, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (connected) "Trennen" else "Verbinden")
                }
                FilledTonalButton(onClick = viewModel::wakeTv, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PowerSettingsNew, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Einschalten")
                }
            }
        }
    }
}

@Composable
private fun SceneCard(scene: SceneType, runningScene: SceneType?, onRun: (SceneType) -> Unit) {
    val accent = when (scene) {
        SceneType.MOVIE -> LivingViolet
        SceneType.GAMING -> LivingCyan
        SceneType.TV_ONLY -> LivingGreen
        SceneType.ALL_OFF -> LivingPink
    }
    val icon = when (scene) {
        SceneType.MOVIE -> Icons.Default.Movie
        SceneType.GAMING -> Icons.Default.Gamepad
        SceneType.TV_ONLY -> Icons.Default.Tv
        SceneType.ALL_OFF -> Icons.Default.PowerOff
    }
    Card(
        onClick = { onRun(scene) },
        enabled = runningScene == null,
        modifier = Modifier.width(150.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(accent.copy(alpha = .12f)),
        border = BorderStroke(1.dp, accent.copy(alpha = .35f))
    ) {
        Column(Modifier.padding(16.dp)) {
            if (runningScene == scene) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp, color = accent)
            else Icon(icon, null, tint = accent)
            Spacer(Modifier.height(12.dp))
            Text(scene.label, fontWeight = FontWeight.Bold)
            Text(
                when (scene) {
                    SceneType.MOVIE -> "TV + HDMI + Sony"
                    SceneType.GAMING -> "Direkt zum Eingang"
                    SceneType.TV_ONLY -> "Nur Fernseher"
                    SceneType.ALL_OFF -> "TV und Sony aus"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TvScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) = ScreenList {
    item { StatusCard(state, viewModel) }
    item { SectionTitle("Fernbedienung", if (state.pointerReady) "Magic-Remote-Socket aktiv" else "D-Pad nutzt bei Bedarf IR") }
    item {
        PremiumCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Ein", Icons.Default.PowerSettingsNew, LivingPink, viewModel::wakeTv, Modifier.weight(1f))
                ActionButton("Aus", Icons.Default.PowerOff, LivingPink, viewModel::powerOffTv, Modifier.weight(1f))
                ActionButton("Home", Icons.Default.Home, LivingCyan, { viewModel.tvKey(LgIrKey.HOME) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            DPad(viewModel)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Vol –", Icons.Default.VolumeDown, LivingViolet, viewModel::tvVolumeDown, Modifier.weight(1f))
                ActionButton("Mute", Icons.Default.VolumeOff, LivingAmber, viewModel::tvMute, Modifier.weight(1f))
                ActionButton("Vol +", Icons.Default.VolumeUp, LivingViolet, viewModel::tvVolumeUp, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            VolumeSlider(state, viewModel)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconAction(Icons.Default.FastRewind, viewModel::mediaRewind, Modifier.weight(1f))
                IconAction(Icons.Default.PlayArrow, viewModel::mediaPlay, Modifier.weight(1f))
                IconAction(Icons.Default.Pause, viewModel::mediaPause, Modifier.weight(1f))
                IconAction(Icons.Default.Stop, viewModel::mediaStop, Modifier.weight(1f))
                IconAction(Icons.Default.FastForward, viewModel::mediaFastForward, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(viewModel::channelDown, Modifier.weight(1f)) { Text("Kanal –") }
                OutlinedButton(viewModel::channelUp, Modifier.weight(1f)) { Text("Kanal +") }
            }
        }
    }
    item { SectionTitle("Touchpad", "Wischen und tippen wie mit der Magic Remote") }
    item {
        TouchPad(
            enabled = state.pointerReady,
            onMove = viewModel::pointerMove,
            onScroll = viewModel::pointerScroll,
            onClick = viewModel::pointerClick
        )
    }
    item { TvTextInput(state, viewModel) }
    if (state.inputs.isNotEmpty()) {
        item { SectionTitle("Eingänge", "Direkt über webOS umschalten") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.inputs, key = { it.id }) { input ->
                    AssistChip(
                        onClick = { viewModel.switchInput(input.id) },
                        label = { Text(input.label.ifBlank { input.id }) },
                        leadingIcon = { Icon(Icons.Default.Input, null, Modifier.size(18.dp)) },
                        enabled = input.connected
                    )
                }
            }
        }
    }
    if (state.apps.isNotEmpty()) {
        item { SectionTitle("TV-Apps", "Installierte Anwendungen starten") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.apps.take(30), key = { it.id }) { app ->
                    AssistChip(
                        onClick = { viewModel.launchApp(app.id) },
                        label = { Text(app.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Apps, null, Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DPad(viewModel: LivingRoomViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(Icons.Default.KeyboardArrowUp) { viewModel.tvKey(LgIrKey.UP) }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.Default.KeyboardArrowLeft) { viewModel.tvKey(LgIrKey.LEFT) }
            Button(
                onClick = { viewModel.tvKey(LgIrKey.OK) },
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) { Text("OK", fontSize = 18.sp, fontWeight = FontWeight.Black) }
            RoundButton(Icons.Default.KeyboardArrowRight) { viewModel.tvKey(LgIrKey.RIGHT) }
        }
        RoundButton(Icons.Default.KeyboardArrowDown) { viewModel.tvKey(LgIrKey.DOWN) }
        OutlinedButton(onClick = { viewModel.tvKey(LgIrKey.BACK) }) {
            Icon(Icons.Default.ArrowBack, null)
            Spacer(Modifier.width(6.dp))
            Text("Zurück")
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick,
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .75f))
    ) { Icon(icon, null, Modifier.size(31.dp)) }
}

@Composable
private fun VolumeSlider(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    var volume by remember(state.volume) { mutableFloatStateOf((state.volume ?: 0).toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { viewModel.setTvVolume(volume.toInt()) },
            valueRange = 0f..100f,
            enabled = state.connectionPhase == ConnectionPhase.CONNECTED,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(state.volume?.toString() ?: "–", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TouchPad(enabled: Boolean, onMove: (Float, Float) -> Unit, onScroll: (Float, Float) -> Unit, onClick: () -> Unit) {
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f), LivingCyan.copy(.08f), LivingPink.copy(.08f))
            )
        ).border(
            1.dp,
            if (enabled) LivingCyan.copy(.45f) else MaterialTheme.colorScheme.outline.copy(.3f),
            RoundedCornerShape(28.dp)
        ).pointerInput(enabled) {
            if (enabled) detectTapGestures { onClick() }
        }.pointerInput(enabled) {
            if (enabled) detectDragGestures(
                onDragStart = { dragTotal = Offset.Zero },
                onDrag = { _, amount ->
                    dragTotal += amount
                    onMove(amount.x * 1.45f, amount.y * 1.45f)
                },
                onDragEnd = {
                    if (abs(dragTotal.y) > 160f) onScroll(0f, dragTotal.y / 3f)
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.TouchApp, null, Modifier.size(42.dp), tint = if (enabled) LivingCyan else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text(
                if (enabled) "Wischen · Tippen für OK" else "TV verbinden, um das Touchpad zu aktivieren",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TvTextInput(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    PremiumCard {
        Text("TV-Tastatur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Text an den Fernseher") },
            singleLine = true,
            enabled = state.connectionPhase == ConnectionPhase.CONNECTED,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton({ text = "" }, enabled = text.isNotEmpty()) { Icon(Icons.Default.Clear, "Leeren") } }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.sendText(text) },
                enabled = text.isNotBlank() && state.connectionPhase == ConnectionPhase.CONNECTED,
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Senden") }
            OutlinedButton(
                onClick = viewModel::sendEnter,
                enabled = state.connectionPhase == ConnectionPhase.CONNECTED,
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Enter") }
        }
    }
}

@Composable
private fun SonyScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) = ScreenList {
    val settings = state.settings
    item {
        PremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speaker, null, tint = LivingViolet, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sony Heimkino", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("SIRC-Testprofil · ${settings.sonyBits} Bit · Adresse ${settings.sonyAddress}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (state.irAvailable) "IR BEREIT" else "KEIN IR", color = if (state.irAvailable) LivingGreen else LivingPink, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    item {
        HintCard(
            "Erst testen, dann speichern",
            "Das LG-Paket belegt kein verifiziertes Profil für dein konkretes Sony-Gerät. Deshalb bleiben Adresse, Bitlänge und Befehle editierbar."
        )
    }
    item {
        PremiumCard {
            Button(
                onClick = { viewModel.sendSonyProfileCommand(settings.sonyPowerCommand) },
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Icon(Icons.Default.PowerSettingsNew, null)
                Spacer(Modifier.width(8.dp))
                Text("Power testen · ${settings.sonyPowerCommand}")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Leiser", Icons.Default.VolumeDown, LivingViolet, { viewModel.sendSonyProfileCommand(settings.sonyVolumeDownCommand) }, Modifier.weight(1f))
                ActionButton("Mute", Icons.Default.VolumeOff, LivingAmber, { viewModel.sendSonyProfileCommand(settings.sonyMuteCommand) }, Modifier.weight(1f))
                ActionButton("Lauter", Icons.Default.VolumeUp, LivingViolet, { viewModel.sendSonyProfileCommand(settings.sonyVolumeUpCommand) }, Modifier.weight(1f))
            }
        }
    }
    item {
        OutlinedButton(onClick = { viewModel.selectTab(AppTab.LAB) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Science, null)
            Spacer(Modifier.width(7.dp))
            Text("Sony-Profil im Code Lab bearbeiten")
        }
    }
}

@Composable
private fun CodeLabScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    var nec by rememberSaveable { mutableStateOf("20DF10EF") }
    var repeats by rememberSaveable { mutableIntStateOf(1) }
    var command by rememberSaveable(state.settings.sonyPowerCommand) { mutableStateOf(state.settings.sonyPowerCommand.toString()) }
    var address by rememberSaveable(state.settings.sonyAddress) { mutableStateOf(state.settings.sonyAddress.toString()) }
    var bits by rememberSaveable(state.settings.sonyBits) { mutableStateOf(state.settings.sonyBits.toString()) }
    var extended by rememberSaveable { mutableStateOf("0") }
    ScreenList {
        item { HintCard("Sicheres Testlabor", "Es wird nur der ausgewählte Code gesendet. Ein automatischer Vollbereichs-Scan ist bewusst nicht eingebaut.") }
        item {
            PremiumCard {
                Text("NEC 32 Bit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nec, { nec = it.take(10) }, label = { Text("Hex-Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Wiederholungen: $repeats")
                Slider(repeats.toFloat(), { repeats = it.toInt().coerceIn(1, 5) }, valueRange = 1f..5f, steps = 3)
                Button({ viewModel.sendCustomNec(nec, repeats) }, enabled = state.irAvailable, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("NEC senden")
                }
            }
        }
        item {
            PremiumCard {
                Text("Sony SIRC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("12, 15 oder 20 Bit · LSB-first · drei Frames", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                NumberField("Befehl (0–127)", command) { command = it }
                NumberField("Adresse", address) { address = it }
                NumberField("Bitlänge 12 / 15 / 20", bits) { bits = it }
                NumberField("Extended (nur 20 Bit)", extended) { extended = it }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.sendSony(command.toIntOrNull() ?: -1, address.toIntOrNull() ?: -1, bits.toIntOrNull() ?: 12, extended.toIntOrNull() ?: 0) },
                        enabled = state.irAvailable,
                        modifier = Modifier.weight(1f)
                    ) { Text("Testen") }
                    OutlinedButton(
                        onClick = {
                            val next = ((command.toIntOrNull() ?: 0) + 1).coerceAtMost(127)
                            command = next.toString()
                            viewModel.sendSony(next, address.toIntOrNull() ?: 0, bits.toIntOrNull() ?: 12, extended.toIntOrNull() ?: 0)
                        },
                        enabled = state.irAvailable,
                        modifier = Modifier.weight(1f)
                    ) { Text("Nächster") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val c = command.toIntOrNull() ?: return@OutlinedButton
                        val a = address.toIntOrNull() ?: return@OutlinedButton
                        val b = bits.toIntOrNull()?.takeIf { it in setOf(12, 15, 20) } ?: return@OutlinedButton
                        viewModel.updateSettings(state.settings.copy(sonyPowerCommand = c.coerceIn(0, 127), sonyAddress = a.coerceAtLeast(0), sonyBits = b))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Als Power-Profil speichern") }
            }
        }
        item { DiagnosticsCard(state, viewModel) }
    }
}

@Composable
private fun SettingsScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) = ScreenList {
    val settings = state.settings
    item {
        PremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = LivingCyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("TV im Netzwerk finden", fontWeight = FontWeight.Bold)
                    Text("SSDP webOS Second Screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(viewModel::discoverTvs) { Text("Suchen") }
            }
            state.discoveredTvs.forEach { tv ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { viewModel.selectDiscoveredTv(tv) }.padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tv, null)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) { Text(tv.name, fontWeight = FontWeight.SemiBold); Text(tv.ipAddress, style = MaterialTheme.typography.bodySmall) }
                    Icon(Icons.Default.Check, "Auswählen")
                }
            }
        }
    }
    item {
        PremiumCard {
            Text("LG webOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SettingField("TV-IP", settings.tvIp) { viewModel.updateSettings(settings.copy(tvIp = it)) }
            SettingField("TV-MAC für Wake-on-LAN", settings.tvMac) { viewModel.updateSettings(settings.copy(tvMac = it)) }
            SettingField("Bevorzugter Eingang, z. B. HDMI_1", settings.preferredInput) { viewModel.updateSettings(settings.copy(preferredInput = it)) }
            SettingToggle("Automatisch verbinden", settings.autoConnect) { viewModel.updateSettings(settings.copy(autoConnect = it)) }
            SettingToggle("IR-Fallback", settings.irFallback) { viewModel.updateSettings(settings.copy(irFallback = it)) }
            SettingToggle("Haptisches Feedback", settings.haptics) { viewModel.updateSettings(settings.copy(haptics = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(viewModel::connectTv, Modifier.weight(1f)) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(5.dp)); Text("Verbinden") }
                OutlinedButton(viewModel::clearPairing, Modifier.weight(1f)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(5.dp)); Text("Pairing löschen") }
            }
        }
    }
    item {
        PremiumCard {
            Text("Sony SIRC-Profil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SettingIntField("Adresse", settings.sonyAddress) { viewModel.updateSettings(settings.copy(sonyAddress = it)) }
            SettingIntField("Bitlänge", settings.sonyBits) { if (it in setOf(12, 15, 20)) viewModel.updateSettings(settings.copy(sonyBits = it)) }
            SettingIntField("Power-Befehl", settings.sonyPowerCommand) { viewModel.updateSettings(settings.copy(sonyPowerCommand = it.coerceIn(0, 127))) }
            SettingIntField("Lauter-Befehl", settings.sonyVolumeUpCommand) { viewModel.updateSettings(settings.copy(sonyVolumeUpCommand = it.coerceIn(0, 127))) }
            SettingIntField("Leiser-Befehl", settings.sonyVolumeDownCommand) { viewModel.updateSettings(settings.copy(sonyVolumeDownCommand = it.coerceIn(0, 127))) }
            SettingIntField("Mute-Befehl", settings.sonyMuteCommand) { viewModel.updateSettings(settings.copy(sonyMuteCommand = it.coerceIn(0, 127))) }
        }
    }
    item { DiagnosticsCard(state, viewModel) }
}

@Composable
private fun DiagnosticsCard(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = LivingAmber)
            Spacer(Modifier.width(8.dp))
            Text("Diagnoseprotokoll", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(viewModel::clearLogs) { Text("Leeren") }
        }
        if (state.logs.isEmpty()) Text("Noch keine Einträge", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else state.logs.take(18).forEachIndexed { index, line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = if (line.contains("Fehler", true)) LivingPink else MaterialTheme.colorScheme.onSurfaceVariant)
            if (index < state.logs.take(18).lastIndex) Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, accent: Color, action: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = action,
        modifier = modifier.height(54.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = .13f)),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun IconAction(icon: ImageVector, action: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = action, modifier = modifier, contentPadding = PaddingValues(8.dp)) { Icon(icon, null) }
}

@Composable
private fun Metric(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(13.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

@Composable
private fun SettingField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
}

@Composable
private fun SettingIntField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value.toString(),
        { it.filter(Char::isDigit).toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun HintCard(title: String, text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(LivingCyan.copy(alpha = .08f)),
        border = BorderStroke(1.dp, LivingCyan.copy(alpha = .22f))
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, tint = LivingCyan)
            Spacer(Modifier.width(10.dp))
            Column { Text(title, fontWeight = FontWeight.Bold); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PremiumCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.elevatedCardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .56f))
    ) { Column(Modifier.padding(17.dp), content = content) }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ScreenList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 5.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
        content = content
    )
}
