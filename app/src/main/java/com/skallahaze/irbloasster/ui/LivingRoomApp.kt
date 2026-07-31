package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.consume
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

@Composable
fun LivingRoomApp(
    state: LivingRoomUiState,
    viewModel: LivingRoomViewModel
) {
    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                AppNavigation(
                    selected = state.selectedTab,
                    onSelected = viewModel::selectTab
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AppHeader(state)
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
    val connected = state.connectionPhase == ConnectionPhase.CONNECTED
    val phaseColor = when (state.connectionPhase) {
        ConnectionPhase.CONNECTED -> LivingGreen
        ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING, ConnectionPhase.PAIRING -> LivingAmber
        ConnectionPhase.ERROR -> LivingPink
        ConnectionPhase.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(LivingCyan.copy(alpha = 0.95f), LivingPink.copy(alpha = 0.95f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF081016))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LivingRoom Controller",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.statusMessage,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(phaseColor)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (connected) "ONLINE" else state.connectionPhase.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = phaseColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    selected: AppTab,
    onSelected: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        modifier = Modifier.navigationBarsPadding()
    ) {
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
                onClick = { onSelected(tab) },
                icon = { Icon(icon, contentDescription = tab.label) },
                label = { Text(tab.label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: LivingRoomUiState,
    viewModel: LivingRoomViewModel
) {
    ScreenList {
        item {
            HeroStatusCard(state, viewModel)
        }
        item {
            SectionTitle("Szenen", "Mehrere Geräte mit einem Tipp")
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(SceneType.entries) { scene ->
                    SceneCard(
                        scene = scene,
                        running = state.sceneRunning == scene,
                        enabled = state.sceneRunning == null,
                        onClick = { viewModel.runScene(scene) }
                    )
                }
            }
        }
        item {
            SectionTitle("Schnellzugriff", "WLAN-Steuerung mit IR-Fallback")
        }
        item {
            QuickControlGrid(state, viewModel)
        }
        item {
            DeviceOverview(state)
        }
        item {
            HintCard(
                title = "Hybrid-Steuerung",
                text = "LG webOS übernimmt Live-Status, Apps, Eingänge und Touchpad. Einschalten und Notfallbedienung können über Wake-on-LAN oder den IR-Blaster erfolgen."
            )
        }
    }
}

@Composable
private fun HeroStatusCard(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    val connected = state.connectionPhase == ConnectionPhase.CONNECTED
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f)
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            LivingCyan.copy(alpha = 0.11f),
                            LivingPink.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (connected) LivingGreen else LivingAmber,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (connected) "LG TV verbunden" else "LG TV bereitmachen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.settings.tvIp.ifBlank { "Noch keine TV-IP gespeichert" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.connectionPhase in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING, ConnectionPhase.PAIRING)) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (connected) viewModel::disconnectTv else viewModel::connectTv,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (connected) Icons.Default.LinkOff else Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (connected) "Trennen" else "Verbinden")
                }
                FilledTonalButton(
                    onClick = viewModel::wakeTv,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Einschalten")
                }
            }
        }
    }
}

@Composable
private fun SceneCard(
    scene: SceneType,
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val icon = when (scene) {
        SceneType.MOVIE -> Icons.Default.Movie
        SceneType.GAMING -> Icons.Default.Gamepad
        SceneType.TV_ONLY -> Icons.Default.Tv
        SceneType.ALL_OFF -> Icons.Default.PowerOff
    }
    val accent = when (scene) {
        SceneType.MOVIE -> LivingViolet
        SceneType.GAMING -> LivingCyan
        SceneType.TV_ONLY -> LivingGreen
        SceneType.ALL_OFF -> LivingPink
    }

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(154.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = if (running) 0.25f else 0.12f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f))
    ) {
        Column(Modifier.padding(18.dp)) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = accent)
            } else {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(scene.label, fontWeight = FontWeight.Bold)
            Text(
                text = when (scene) {
                    SceneType.MOVIE -> "TV + HDMI + Sony"
                    SceneType.GAMING -> "Schnell zum Eingang"
                    SceneType.TV_ONLY -> "Nur den Fernseher"
                    SceneType.ALL_OFF -> "TV und Sony aus"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickControlGrid(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickButton("TV Power", Icons.Default.PowerSettingsNew, LivingPink, viewModel::wakeTv, Modifier.weight(1f))
            QuickButton("Lauter", Icons.Default.VolumeUp, LivingCyan, viewModel::tvVolumeUp, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickButton("Leiser", Icons.Default.VolumeDown, LivingViolet, viewModel::tvVolumeDown, Modifier.weight(1f))
            QuickButton(
                if (state.muted) "Ton an" else "Stumm",
                Icons.Default.VolumeOff,
                LivingAmber,
                viewModel::tvMute,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.13f))
    ) {
        Icon(icon, contentDescription = null, tint = accent)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun DeviceOverview(state: LivingRoomUiState) {
    PremiumCard {
        Text("Live-Übersicht", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
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

@Composable
private fun Metric(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    ScreenList {
        item {
            ConnectionCard(state, viewModel)
        }
        item {
            SectionTitle("Fernbedienung", if (state.pointerReady) "Magic-Remote-Socket aktiv" else "D-Pad nutzt bei Bedarf IR")
        }
        item {
            RemoteControlPanel(state, viewModel)
        }
        item {
            SectionTitle("Touchpad", "Wischen, tippen und scrollen")
        }
        item {
            TouchPad(
                enabled = state.pointerReady,
                onMove = viewModel::pointerMove,
                onScroll = viewModel::pointerScroll,
                onClick = viewModel::pointerClick
            )
        }
        item {
            TvTextInput(state, viewModel)
        }
        if (state.inputs.isNotEmpty()) {
            item { SectionTitle("Eingänge", "Direkt über webOS umschalten") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.inputs, key = { it.id }) { input ->
                        AssistChip(
                            onClick = { viewModel.switchInput(input.id) },
                            label = { Text(input.label.ifBlank { input.id }) },
                            leadingIcon = { Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(18.dp)) },
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
                            leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.connectionPhase == ConnectionPhase.CONNECTED) Icons.Default.CheckCircle else Icons.Default.Wifi,
                contentDescription = null,
                tint = if (state.connectionPhase == ConnectionPhase.CONNECTED) LivingGreen else LivingAmber,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("LG OLED / webOS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    state.settings.tvIp.ifBlank { "IP im Setup wählen" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { if (state.connectionPhase == ConnectionPhase.CONNECTED) viewModel.disconnectTv() else viewModel.connectTv() }) {
                Icon(
                    if (state.connectionPhase == ConnectionPhase.CONNECTED) Icons.Default.LinkOff else Icons.Default.Link,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun RemoteControlPanel(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    PremiumCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteSmallButton(Icons.Default.PowerSettingsNew, "Ein", LivingPink, viewModel::wakeTv, Modifier.weight(1f))
            RemoteSmallButton(Icons.Default.PowerOff, "Aus", LivingPink, viewModel::powerOffTv, Modifier.weight(1f))
            RemoteSmallButton(Icons.Default.Home, "Home", LivingCyan, { viewModel.tvKey(LgIrKey.HOME) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        DPad(viewModel)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteSmallButton(Icons.Default.VolumeDown, "Vol –", LivingViolet, viewModel::tvVolumeDown, Modifier.weight(1f))
            RemoteSmallButton(Icons.Default.VolumeOff, if (state.muted) "Ton an" else "Mute", LivingAmber, viewModel::tvMute, Modifier.weight(1f))
            RemoteSmallButton(Icons.Default.VolumeUp, "Vol +", LivingViolet, viewModel::tvVolumeUp, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        VolumeSlider(state, viewModel)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniIconButton(Icons.Default.FastRewind, viewModel::mediaRewind, Modifier.weight(1f))
            MiniIconButton(Icons.Default.PlayArrow, viewModel::mediaPlay, Modifier.weight(1f))
            MiniIconButton(Icons.Default.Pause, viewModel::mediaPause, Modifier.weight(1f))
            MiniIconButton(Icons.Default.Stop, viewModel::mediaStop, Modifier.weight(1f))
            MiniIconButton(Icons.Default.FastForward, viewModel::mediaFastForward, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = viewModel::channelDown, modifier = Modifier.weight(1f)) { Text("Kanal –") }
            OutlinedButton(onClick = viewModel::channelUp, modifier = Modifier.weight(1f)) { Text("Kanal +") }
        }
    }
}

@Composable
private fun DPad(viewModel: LivingRoomViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        RoundRemoteButton(Icons.Default.KeyboardArrowUp, "Hoch") { viewModel.tvKey(LgIrKey.UP) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundRemoteButton(Icons.Default.KeyboardArrowLeft, "Links") { viewModel.tvKey(LgIrKey.LEFT) }
            Button(
                onClick = { viewModel.tvKey(LgIrKey.OK) },
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(72.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            RoundRemoteButton(Icons.Default.KeyboardArrowRight, "Rechts") { viewModel.tvKey(LgIrKey.RIGHT) }
        }
        RoundRemoteButton(Icons.Default.KeyboardArrowDown, "Runter") { viewModel.tvKey(LgIrKey.DOWN) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.tvKey(LgIrKey.BACK) }) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Zurück")
        }
    }
}

@Composable
private fun RoundRemoteButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun VolumeSlider(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    var localVolume by remember(state.volume) { mutableFloatStateOf((state.volume ?: 0).toFloat()) }
    Column {
        Row {
            Text("Direkte Lautstärke", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(state.volume?.toString() ?: "–", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = localVolume,
            onValueChange = { localVolume = it },
            onValueChangeFinished = { viewModel.setTvVolume(localVolume.toInt()) },
            valueRange = 0f..100f,
            enabled = state.connectionPhase == ConnectionPhase.CONNECTED
        )
    }
}

@Composable
private fun TouchPad(
    enabled: Boolean,
    onMove: (Float, Float) -> Unit,
    onScroll: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    var totalDrag by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        LivingCyan.copy(alpha = 0.08f),
                        LivingPink.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                1.dp,
                if (enabled) LivingCyan.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(28.dp)
            )
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(onTap = { onClick() })
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { totalDrag = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            totalDrag += amount
                            onMove(amount.x * 1.45f, amount.y * 1.45f)
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(totalDrag.y) > 160f) {
                                onScroll(0f, totalDrag.y / 3f)
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = if (enabled) LivingCyan else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (enabled) "Wischen · Tippen für OK" else "Mit TV verbinden, um Touchpad zu aktivieren",
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
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Text an den Fernseher") },
            singleLine = true,
            enabled = state.connectionPhase == ConnectionPhase.CONNECTED,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { text = "" }, enabled = text.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = "Leeren")
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.sendText(text) },
                enabled = text.isNotBlank() && state.connectionPhase == ConnectionPhase.CONNECTED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Senden")
            }
            OutlinedButton(
                onClick = viewModel::sendEnter,
                enabled = state.connectionPhase == ConnectionPhase.CONNECTED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enter")
            }
        }
    }
}

@Composable
private fun RemoteSmallButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.14f))
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun MiniIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(8.dp)
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun SonyScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    val settings = state.settings
    ScreenList {
        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(LivingViolet.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Speaker, contentDescription = null, tint = LivingViolet)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sony Heimkino", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "SIRC-Testprofil · ${settings.sonyBits} Bit · Adresse ${settings.sonyAddress}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (state.irAvailable) "IR BEREIT" else "KEIN IR",
                        color = if (state.irAvailable) LivingGreen else LivingPink,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item {
            HintCard(
                title = "Erst testen, dann als Profil behalten",
                text = "Die Sony-Werte sind ein editierbares SIRC-Testprofil. Da aus dem LG-Paket kein gesichertes Modellprofil für dein Heimkino hervorgeht, werden die Tasten nicht als garantiert korrekt bezeichnet. Im Code Lab kannst du Adresse, Bitlänge und Befehle prüfen und speichern."
            )
        }
        item {
            PremiumCard {
                Text("Haupttasten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.sendSonyProfileCommand(settings.sonyPowerCommand) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LivingPink.copy(alpha = 0.82f), contentColor = Color(0xFF2A0010))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Power testen · Befehl ${settings.sonyPowerCommand}", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.sendSonyProfileCommand(settings.sonyVolumeDownCommand) },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.VolumeDown, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Leiser")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.sendSonyProfileCommand(settings.sonyMuteCommand) },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mute")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.sendSonyProfileCommand(settings.sonyVolumeUpCommand) },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lauter")
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.selectTab(AppTab.LAB) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Science, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sony-Profil im Code Lab bearbeiten")
            }
        }
    }
}

@Composable
private fun CodeLabScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    var necCode by rememberSaveable { mutableStateOf("20DF10EF") }
    var necRepeats by rememberSaveable { mutableIntStateOf(1) }
    var sonyCommand by rememberSaveable(state.settings.sonyPowerCommand) { mutableStateOf(state.settings.sonyPowerCommand.toString()) }
    var sonyAddress by rememberSaveable(state.settings.sonyAddress) { mutableStateOf(state.settings.sonyAddress.toString()) }
    var sonyBits by rememberSaveable(state.settings.sonyBits) { mutableStateOf(state.settings.sonyBits.toString()) }
    var sonyExtended by rememberSaveable { mutableStateOf("0") }

    ScreenList {
        item {
            HintCard(
                title = "Sicheres Testlabor",
                text = "Die App sendet nur den von dir ausgewählten Code. Es gibt bewusst keinen automatischen Vollbereichs-Scan, damit keine unerwarteten Service- oder Gerätemodi ausgelöst werden."
            )
        }
        item {
            PremiumCard {
                Text("NEC 32 Bit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Für LG und andere klassische IR-Geräte", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = necCode,
                    onValueChange = { necCode = it.take(10) },
                    label = { Text("Hex-Code, z. B. 20DF10EF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Wiederholungen: $necRepeats", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = necRepeats.toFloat(),
                    onValueChange = { necRepeats = it.toInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3
                )
                Button(
                    onClick = { viewModel.sendCustomNec(necCode, necRepeats) },
                    enabled = state.irAvailable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("NEC senden")
                }
            }
        }
        item {
            PremiumCard {
                Text("Sony SIRC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("12, 15 oder 20 Bit · LSB-first · drei Frames", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                NumberField("Befehl (0–127)", sonyCommand) { sonyCommand = it }
                Spacer(Modifier.height(8.dp))
                NumberField("Adresse", sonyAddress) { sonyAddress = it }
                Spacer(Modifier.height(8.dp))
                NumberField("Bitlänge (12 / 15 / 20)", sonyBits) { sonyBits = it }
                Spacer(Modifier.height(8.dp))
                NumberField("Extended (nur 20 Bit)", sonyExtended) { sonyExtended = it }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.sendSony(
                                command = sonyCommand.toIntOrNull() ?: -1,
                                address = sonyAddress.toIntOrNull() ?: -1,
                                bits = sonyBits.toIntOrNull() ?: 12,
                                extended = sonyExtended.toIntOrNull() ?: 0
                            )
                        },
                        enabled = state.irAvailable,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Testen")
                    }
                    OutlinedButton(
                        onClick = {
                            val next = ((sonyCommand.toIntOrNull() ?: 0) + 1).coerceAtMost(127)
                            sonyCommand = next.toString()
                            viewModel.sendSony(
                                command = next,
                                address = sonyAddress.toIntOrNull() ?: 0,
                                bits = sonyBits.toIntOrNull() ?: 12,
                                extended = sonyExtended.toIntOrNull() ?: 0
                            )
                        },
                        enabled = state.irAvailable,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Nächster")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val command = sonyCommand.toIntOrNull() ?: return@OutlinedButton
                        val address = sonyAddress.toIntOrNull() ?: return@OutlinedButton
                        val bits = sonyBits.toIntOrNull()?.takeIf { it in setOf(12, 15, 20) } ?: return@OutlinedButton
                        viewModel.updateSettings(
                            state.settings.copy(
                                sonyPowerCommand = command.coerceIn(0, 127),
                                sonyAddress = address.coerceAtLeast(0),
                                sonyBits = bits
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aktuellen Wert als Power-Profil speichern")
                }
            }
        }
        item {
            DiagnosticsCard(state, viewModel)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsScreen(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    val settings = state.settings
    ScreenList {
        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = LivingCyan)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TV im Netzwerk finden", fontWeight = FontWeight.Bold)
                        Text("SSDP webOS Second Screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = viewModel::discoverTvs) {
                        Text("Suchen")
                    }
                }
                if (state.discoveredTvs.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    state.discoveredTvs.forEach { tv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectDiscoveredTv(tv) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tv.name, fontWeight = FontWeight.SemiBold)
                                Text(tv.ipAddress, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.Check, contentDescription = "Auswählen")
                        }
                    }
                }
            }
        }
        item {
            PremiumCard {
                Text("LG webOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                SettingTextField(
                    label = "TV-IP",
                    value = settings.tvIp,
                    onChange = { viewModel.updateSettings(settings.copy(tvIp = it)) }
                )
                Spacer(Modifier.height(8.dp))
                SettingTextField(
                    label = "TV-MAC für Wake-on-LAN",
                    value = settings.tvMac,
                    onChange = { viewModel.updateSettings(settings.copy(tvMac = it)) }
                )
                Spacer(Modifier.height(8.dp))
                SettingTextField(
                    label = "Bevorzugter Eingang, z. B. HDMI_1",
                    value = settings.preferredInput,
                    onChange = { viewModel.updateSettings(settings.copy(preferredInput = it)) }
                )
                Spacer(Modifier.height(12.dp))
                SettingsToggle("Automatisch verbinden", settings.autoConnect) {
                    viewModel.updateSettings(settings.copy(autoConnect = it))
                }
                SettingsToggle("IR-Fallback", settings.irFallback) {
                    viewModel.updateSettings(settings.copy(irFallback = it))
                }
                SettingsToggle("Haptisches Feedback", settings.haptics) {
                    viewModel.updateSettings(settings.copy(haptics = it))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::connectTv, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Verbinden")
                    }
                    OutlinedButton(onClick = viewModel::clearPairing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pairing löschen")
                    }
                }
            }
        }
        item {
            PremiumCard {
                Text("Sony SIRC-Profil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                SettingIntField("Adresse", settings.sonyAddress) {
                    viewModel.updateSettings(settings.copy(sonyAddress = it))
                }
                Spacer(Modifier.height(8.dp))
                SettingIntField("Bitlänge", settings.sonyBits) {
                    if (it in setOf(12, 15, 20)) viewModel.updateSettings(settings.copy(sonyBits = it))
                }
                Spacer(Modifier.height(8.dp))
                SettingIntField("Power-Befehl", settings.sonyPowerCommand) {
                    viewModel.updateSettings(settings.copy(sonyPowerCommand = it.coerceIn(0, 127)))
                }
                Spacer(Modifier.height(8.dp))
                SettingIntField("Lauter-Befehl", settings.sonyVolumeUpCommand) {
                    viewModel.updateSettings(settings.copy(sonyVolumeUpCommand = it.coerceIn(0, 127)))
                }
                Spacer(Modifier.height(8.dp))
                SettingIntField("Leiser-Befehl", settings.sonyVolumeDownCommand) {
                    viewModel.updateSettings(settings.copy(sonyVolumeDownCommand = it.coerceIn(0, 127)))
                }
                Spacer(Modifier.height(8.dp))
                SettingIntField("Mute-Befehl", settings.sonyMuteCommand) {
                    viewModel.updateSettings(settings.copy(sonyMuteCommand = it.coerceIn(0, 127)))
                }
            }
        }
        item {
            DiagnosticsCard(state, viewModel)
        }
    }
}

@Composable
private fun SettingTextField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingIntField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.filter(Char::isDigit).toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun DiagnosticsCard(state: LivingRoomUiState, viewModel: LivingRoomViewModel) {
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = LivingAmber)
            Spacer(Modifier.width(10.dp))
            Text("Diagnoseprotokoll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clearLogs) { Text("Leeren") }
        }
        Spacer(Modifier.height(8.dp))
        if (state.logs.isEmpty()) {
            Text("Noch keine Einträge", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.logs.take(18).forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.contains("Fehler", ignoreCase = true)) LivingPink else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (index < state.logs.take(18).lastIndex) Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun ScreenList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun PremiumCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun HintCard(title: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LivingCyan.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, LivingCyan.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = LivingCyan)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
