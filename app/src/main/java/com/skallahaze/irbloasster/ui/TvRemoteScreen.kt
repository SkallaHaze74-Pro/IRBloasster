package com.skallahaze.irbloasster.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.ir.LG_OLED55B1
import com.skallahaze.irbloasster.ir.LgCommand
import com.skallahaze.irbloasster.webos.WebOsClient
import com.skallahaze.irbloasster.webos.WebOsConnection
import com.skallahaze.irbloasster.webos.WebOsState
import kotlin.math.roundToInt

@Composable
internal fun TvRemoteScreen(
    irAvailable: Boolean,
    webState: WebOsState,
    webOs: WebOsClient,
    onLg: (LgCommand) -> Unit,
    onTv: (String, () -> Boolean, LgCommand) -> Unit,
) {
    var showNumbers by rememberSaveable { mutableStateOf(false) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    var keyboardText by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "LG REMOTE · B1 · DEUQJP",
                title = LG_OLED55B1.MODEL,
                subtitle = if (webState.connection == WebOsConnection.CONNECTED) {
                    listOfNotNull(
                        webState.modelName,
                        webState.currentApp,
                        webState.volume?.let { "Lautstärke $it" },
                    ).joinToString(" · ").ifBlank { "webOS verbunden" }
                } else if (irAvailable) {
                    "IR bereit · webOS optional"
                } else {
                    "webOS verbinden oder IR prüfen"
                },
            )
        }

        item {
            SectionCard {
                Text(
                    "Exaktes Geräteprofil gespeichert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                InfoLine("Produktcode", LG_OLED55B1.PRODUCT_CODE)
                InfoLine("Fertigung", "${LG_OLED55B1.MANUFACTURED} · ${LG_OLED55B1.ASSEMBLED_IN}")
                InfoLine("Display", "${LG_OLED55B1.PANEL_SIZE_INCH} Zoll · ${LG_OLED55B1.RESOLUTION} · ${LG_OLED55B1.NATIVE_REFRESH_HZ} Hz")
                InfoLine("Smart TV", LG_OLED55B1.WEB_OS_VERSION)
                InfoLine("HDMI", "4 Ports · HDMI 3 eARC · HDMI 3/4 bis 4K/120")
                InfoLine("Leistung", "${LG_OLED55B1.TYPICAL_POWER_W} W typisch · ${LG_OLED55B1.MAX_RATED_POWER_W} W Nennwert")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Die Seriennummer bleibt privat und wird nicht in der öffentlichen App oder auf GitHub gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RemoteKey(
                    "Power",
                    "⏻",
                    { onLg(LG_OLED55B1.POWER) },
                    Modifier.weight(1f),
                    primary = true,
                )
                RemoteKey(
                    "Home",
                    "⌂",
                    { onTv("Home", { webOs.sendButton("HOME") }, LG_OLED55B1.HOME) },
                    Modifier.weight(1f),
                )
                RemoteKey(
                    "Eingang",
                    "INPUT",
                    { onTv("Eingang", { webOs.sendButton("INPUT") }, LG_OLED55B1.INPUT) },
                    Modifier.weight(1f),
                )
                RemoteKey(
                    "Setup",
                    "⚙",
                    { onTv("Einstellungen", { webOs.sendButton("MENU") }, LG_OLED55B1.SETTINGS) },
                    Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VerticalRocker(
                        title = "VOL",
                        onUp = { onTv("Lauter", webOs::volumeUp, LG_OLED55B1.VOLUME_UP) },
                        onDown = { onTv("Leiser", webOs::volumeDown, LG_OLED55B1.VOLUME_DOWN) },
                    )
                    DPad(
                        onUp = { onTv("Hoch", { webOs.sendButton("UP") }, LG_OLED55B1.UP) },
                        onDown = { onTv("Runter", { webOs.sendButton("DOWN") }, LG_OLED55B1.DOWN) },
                        onLeft = { onTv("Links", { webOs.sendButton("LEFT") }, LG_OLED55B1.LEFT) },
                        onRight = { onTv("Rechts", { webOs.sendButton("RIGHT") }, LG_OLED55B1.RIGHT) },
                        onOk = { onTv("OK", { webOs.sendButton("ENTER") }, LG_OLED55B1.OK) },
                    )
                    VerticalRocker(
                        title = "CH",
                        onUp = { onTv("Kanal +", webOs::channelUp, LG_OLED55B1.CHANNEL_UP) },
                        onDown = { onTv("Kanal −", webOs::channelDown, LG_OLED55B1.CHANNEL_DOWN) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteKey(
                        "Zurück",
                        "↶",
                        { onTv("Zurück", { webOs.sendButton("BACK") }, LG_OLED55B1.BACK) },
                        Modifier.weight(1f),
                    )
                    RemoteKey(
                        "Stumm",
                        "MUTE",
                        { onTv("Stumm", webOs::toggleMute, LG_OLED55B1.MUTE) },
                        Modifier.weight(1f),
                    )
                    RemoteKey(
                        "Info",
                        "i",
                        { onTv("Info", { webOs.sendButton("INFO") }, LG_OLED55B1.INFO) },
                        Modifier.weight(1f),
                    )
                }
            }
        }

        if (webState.connection == WebOsConnection.CONNECTED) {
            item { SectionTitle("Direkte HDMI-Eingänge") }
            item {
                SectionCard {
                    Text(
                        "Die Anschlussbeschriftung deines Geräts bestätigt HDMI 3 mit eARC/ARC und HDMI 3/4 mit 4K@120 Hz.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionGrid(
                        actions = LG_OLED55B1.HDMI_PORTS.map { port ->
                            port.label to { webOs.switchInput(port.inputId) }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = webOs::refreshStatus,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("TV-Status, Apps und Eingänge neu laden")
                    }
                }
            }

            if (webState.inputs.isNotEmpty()) {
                item { SectionTitle("Vom Fernseher gemeldete Eingänge") }
                item {
                    SectionCard {
                        ActionGrid(
                            actions = webState.inputs.map { input ->
                                val suffix = if (input.connected) "" else " · nicht verbunden"
                                "${input.label}$suffix" to { webOs.switchInput(input.id) }
                            },
                        )
                    }
                }
            }

            item { SectionTitle("Magic-Remote Touchpad") }
            item {
                SectionCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { webOs.clickPointer() })
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    webOs.movePointer(
                                        (dragAmount.x * 1.8f).roundToInt(),
                                        (dragAmount.y * 1.8f).roundToInt(),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Wischen zum Bewegen\nTippen zum Klicken",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RemoteKey("Scroll hoch", "↑", { webOs.scrollPointer(-600) }, Modifier.weight(1f))
                        RemoteKey("Klick", "●", { webOs.clickPointer() }, Modifier.weight(1f), primary = true)
                        RemoteKey("Scroll runter", "↓", { webOs.scrollPointer(600) }, Modifier.weight(1f))
                    }
                }
            }
        }

        item { SectionTitle("Medien") }
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RemoteKey(
                        "Zurück",
                        "⏪",
                        { onTv("Zurückspulen", webOs::rewind, LG_OLED55B1.REWIND) },
                        Modifier.weight(1f),
                    )
                    RemoteKey(
                        "Play",
                        "▶",
                        { onTv("Play", webOs::play, LG_OLED55B1.PLAY) },
                        Modifier.weight(1f),
                        primary = true,
                    )
                    RemoteKey(
                        "Pause",
                        "Ⅱ",
                        { onTv("Pause", webOs::pause, LG_OLED55B1.PAUSE) },
                        Modifier.weight(1f),
                    )
                    RemoteKey(
                        "Stop",
                        "■",
                        { onTv("Stop", webOs::stop, LG_OLED55B1.STOP) },
                        Modifier.weight(1f),
                    )
                    RemoteKey(
                        "Vor",
                        "⏩",
                        { onTv("Vorspulen", webOs::fastForward, LG_OLED55B1.FAST_FORWARD) },
                        Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showNumbers = !showNumbers },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (showNumbers) "Ziffern schließen" else "Ziffern & Farben")
                }
                OutlinedButton(
                    onClick = { showKeyboard = !showKeyboard },
                    modifier = Modifier.weight(1f),
                    enabled = webState.connection == WebOsConnection.CONNECTED,
                ) {
                    Text("TV-Tastatur")
                }
            }
        }

        item {
            AnimatedVisibility(showNumbers) {
                SectionCard {
                    val digits = LG_OLED55B1.DIGITS.drop(1) + LG_OLED55B1.DIGITS.take(1)
                    digits.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { command ->
                                RemoteKey(
                                    command.label,
                                    command.label,
                                    { onLg(command) },
                                    Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ColorKey(Color(0xFFD32F2F), "Rot") { onLg(LG_OLED55B1.RED) }
                        ColorKey(Color(0xFF2E7D32), "Grün") { onLg(LG_OLED55B1.GREEN) }
                        ColorKey(Color(0xFFF9A825), "Gelb") { onLg(LG_OLED55B1.YELLOW) }
                        ColorKey(Color(0xFF1565C0), "Blau") { onLg(LG_OLED55B1.BLUE) }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(showKeyboard) {
                SectionCard {
                    Text("Text direkt an webOS senden", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyboardText,
                        onValueChange = { keyboardText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                if (keyboardText.isNotBlank()) webOs.insertText(keyboardText)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Senden") }
                        FilledTonalButton(
                            onClick = { webOs.sendEnter() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Enter") }
                        FilledTonalButton(
                            onClick = { webOs.deleteCharacters() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Löschen") }
                    }
                }
            }
        }

        if (webState.connection == WebOsConnection.CONNECTED) {
            item { SectionTitle("webOS Schnellstarts") }
            item {
                SectionCard {
                    ActionGrid(
                        actions = listOf(
                            "Live TV" to { webOs.launchApp("com.webos.app.livetv") },
                            "YouTube" to { webOs.launchApp("youtube.leanback.v4") },
                            "Netflix" to { webOs.launchApp("netflix") },
                            "TV ausschalten" to { webOs.powerOff() },
                        ),
                    )
                }
            }

            if (webState.apps.isNotEmpty()) {
                item { SectionTitle("Installierte TV-Apps") }
                item {
                    SectionCard {
                        val apps = webState.apps
                            .sortedBy { it.title.lowercase() }
                            .take(12)
                        ActionGrid(
                            actions = apps.map { app ->
                                app.title to { webOs.launchApp(app.id) }
                            },
                        )
                        if (webState.apps.size > apps.size) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${webState.apps.size - apps.size} weitere Apps wurden erkannt. Die ersten 12 werden kompakt angezeigt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
