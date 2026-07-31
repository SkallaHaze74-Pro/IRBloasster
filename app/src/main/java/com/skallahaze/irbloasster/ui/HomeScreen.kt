package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ir.ConsumerIrSender
import com.skallahaze.irbloasster.ir.LG_OLED55B1
import com.skallahaze.irbloasster.ir.LgCommand
import com.skallahaze.irbloasster.ir.Nec
import com.skallahaze.irbloasster.ir.Sirc
import com.skallahaze.irbloasster.ir.SonyCommand
import com.skallahaze.irbloasster.ir.Sony_STR_DB870
import com.skallahaze.irbloasster.webos.WebOsClient
import com.skallahaze.irbloasster.webos.WebOsConnection
import com.skallahaze.irbloasster.webos.WebOsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Destination(
    val title: String,
    val icon: ImageVector,
) {
    HOME("Zuhause", Icons.Rounded.Home),
    TV("LG TV", Icons.Rounded.Tv),
    SONY("Sony", Icons.Rounded.VolumeUp),
    SETTINGS("Setup", Icons.Rounded.Settings),
}

internal enum class Scene(
    val title: String,
    val subtitle: String,
    val symbol: String,
) {
    TELEVISION("Fernsehen", "TV + Sony auf TV/SAT", "TV"),
    CINEMA("Heimkino", "TV + Sony auf DVD/LD", "▶"),
    MUSIC("Musik", "Sony auf CD und 2CH", "♫"),
    ALL_OFF("Alles aus", "Beide Geräte ausschalten", "OFF"),
}

@Composable
fun SmartIrApp(
    ir: ConsumerIrSender,
    settings: SettingsRepository,
    webOs: WebOsClient,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var lastAction by rememberSaveable { mutableStateOf("Bereit") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val webState = webOs.state

    fun pulse() {
        if (settings.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun reportFailure(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun sendLg(command: LgCommand) {
        if (ir.transmit(command)) {
            lastAction = "LG · ${command.label}"
            pulse()
        } else {
            reportFailure("Kein nutzbarer IR-Blaster gefunden")
        }
    }

    fun sendSony(command: SonyCommand) {
        if (ir.transmit(command, settings.sonyMode)) {
            lastAction = "Sony ${settings.sonyMode.title} · ${command.label}"
            pulse()
        } else {
            reportFailure("Kein nutzbarer IR-Blaster gefunden")
        }
    }

    fun sendTv(
        label: String,
        networkAction: () -> Boolean,
        fallback: LgCommand,
    ) {
        val networkSent = webState.connection == WebOsConnection.CONNECTED && networkAction()
        if (networkSent) {
            lastAction = "LG webOS · $label"
            pulse()
        } else {
            sendLg(fallback)
        }
    }

    fun runScene(scene: Scene) {
        scope.launch {
            lastAction = "Szene · ${scene.title} läuft …"
            val results = mutableListOf<Boolean>()

            when (scene) {
                Scene.TELEVISION -> {
                    results += ir.transmit(LG_OLED55B1.POWER_ON)
                    delay(900)
                    results += ir.transmit(Sony_STR_DB870.POWER_ON, settings.sonyMode)
                    delay(550)
                    results += ir.transmit(Sony_STR_DB870.INPUT_TV_SAT, settings.sonyMode)
                }

                Scene.CINEMA -> {
                    results += ir.transmit(LG_OLED55B1.POWER_ON)
                    delay(900)
                    results += ir.transmit(Sony_STR_DB870.POWER_ON, settings.sonyMode)
                    delay(550)
                    results += ir.transmit(Sony_STR_DB870.INPUT_DVD_LD, settings.sonyMode)
                }

                Scene.MUSIC -> {
                    results += ir.transmit(Sony_STR_DB870.POWER_ON, settings.sonyMode)
                    delay(500)
                    results += ir.transmit(Sony_STR_DB870.INPUT_CD, settings.sonyMode)
                    delay(350)
                    results += ir.transmit(Sony_STR_DB870.MODE_2CH, settings.sonyMode)
                }

                Scene.ALL_OFF -> {
                    results += ir.transmit(Sony_STR_DB870.POWER_OFF, settings.sonyMode)
                    delay(500)
                    results += ir.transmit(LG_OLED55B1.POWER_OFF)
                }
            }

            if (results.all { it }) {
                lastAction = "Szene · ${scene.title} fertig"
                pulse()
            } else {
                lastAction = "Szene · ${scene.title} unvollständig"
                reportFailure("Mindestens ein IR-Befehl konnte nicht gesendet werden")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (destination) {
                Destination.HOME -> HomeDashboard(
                    irAvailable = ir.isAvailable,
                    webState = webState,
                    lastAction = lastAction,
                    onScene = ::runScene,
                    onTvPower = { sendLg(LG_OLED55B1.POWER) },
                    onSonyPower = { sendSony(Sony_STR_DB870.POWER) },
                    onTvVolumeUp = { sendTv("Lauter", webOs::volumeUp, LG_OLED55B1.VOLUME_UP) },
                    onTvVolumeDown = { sendTv("Leiser", webOs::volumeDown, LG_OLED55B1.VOLUME_DOWN) },
                    onOpenTv = { destination = Destination.TV },
                    onOpenSony = { destination = Destination.SONY },
                )

                Destination.TV -> TvRemoteScreen(
                    irAvailable = ir.isAvailable,
                    webState = webState,
                    webOs = webOs,
                    onLg = ::sendLg,
                    onTv = ::sendTv,
                )

                Destination.SONY -> SonyRemoteScreen(
                    mode = settings.sonyMode,
                    onModeChange = settings::setSonyMode,
                    onSony = ::sendSony,
                )

                Destination.SETTINGS -> SettingsScreen(
                    irAvailable = ir.isAvailable,
                    settings = settings,
                    webState = webState,
                    webOs = webOs,
                    onRawNec = { code ->
                        if (ir.transmit(LG_OLED55B1.FREQUENCY, Nec.encode(code))) {
                            lastAction = "Testlabor · NEC ${code.toString(16).uppercase()}"
                            pulse()
                        } else {
                            reportFailure("NEC-Test konnte nicht gesendet werden")
                        }
                    },
                    onRawSirc = { command, address, bits ->
                        val sent = runCatching {
                            ir.transmit(
                                Sony_STR_DB870.FREQUENCY,
                                Sirc.encode(command, address, bits),
                            )
                        }.getOrDefault(false)

                        if (sent) {
                            lastAction = "Testlabor · SIRC $bits Bit"
                            pulse()
                        } else {
                            reportFailure("SIRC-Werte ungültig oder IR nicht verfügbar")
                        }
                    },
                    onMessage = ::reportFailure,
                )
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    irAvailable: Boolean,
    webState: WebOsState,
    lastAction: String,
    onScene: (Scene) -> Unit,
    onTvPower: () -> Unit,
    onSonyPower: () -> Unit,
    onTvVolumeUp: () -> Unit,
    onTvVolumeDown: () -> Unit,
    onOpenTv: () -> Unit,
    onOpenSony: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "LIVING ROOM CONTROLLER",
                title = "SmartIR",
                subtitle = "LG OLED55B19LA + Sony STR-DB870",
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "IR-Blaster",
                    value = if (irAvailable) "Bereit" else "Nicht erkannt",
                    active = irAvailable,
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "LG webOS",
                    value = when (webState.connection) {
                        WebOsConnection.CONNECTED -> "Verbunden"
                        WebOsConnection.PAIRING -> "Bestätigen"
                        WebOsConnection.CONNECTING -> "Verbindet"
                        WebOsConnection.ERROR -> "Fehler"
                        WebOsConnection.DISCONNECTED -> "Optional"
                    },
                    active = webState.connection == WebOsConnection.CONNECTED,
                )
            }
        }

        item { SectionTitle("Szenen") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Scene.entries) { scene ->
                    SceneCard(
                        title = scene.title,
                        subtitle = scene.subtitle,
                        symbol = scene.symbol,
                        onClick = { onScene(scene) },
                    )
                }
            }
        }

        item { SectionTitle("Schnellsteuerung") }
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteKey("TV Power", "TV", onTvPower, Modifier.weight(1f), primary = true)
                    RemoteKey("Sony Power", "AV", onSonyPower, Modifier.weight(1f), primary = true)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteKey("TV leiser", "VOL −", onTvVolumeDown, Modifier.weight(1f))
                    RemoteKey("TV lauter", "VOL +", onTvVolumeUp, Modifier.weight(1f))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeviceCard(
                    modifier = Modifier.weight(1f),
                    title = "LG OLED",
                    subtitle = webState.currentApp ?: "TV-Fernbedienung",
                    symbol = "TV",
                    onClick = onOpenTv,
                )
                DeviceCard(
                    modifier = Modifier.weight(1f),
                    title = "Sony",
                    subtitle = "Receiver-Fernbedienung",
                    symbol = "AV",
                    onClick = onOpenSony,
                )
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Letzte Aktion", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(lastAction, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
