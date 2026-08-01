package com.skallahaze.irbloasster.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.data.ThemePreference
import com.skallahaze.irbloasster.ir.Sony_STR_DB870
import com.skallahaze.irbloasster.webos.WebOsClient
import com.skallahaze.irbloasster.webos.WebOsConnection
import com.skallahaze.irbloasster.webos.WebOsState

@Composable
internal fun SettingsScreen(
    irAvailable: Boolean,
    settings: SettingsRepository,
    webState: WebOsState,
    webOs: WebOsClient,
    onRawNec: (Long) -> Unit,
    onRawSirc: (Int, Int, Int) -> Unit,
    onMessage: (String) -> Unit,
) {
    var hostInput by rememberSaveable(settings.webOsHost) { mutableStateOf(settings.webOsHost) }
    var macInput by rememberSaveable(settings.webOsMac) { mutableStateOf(settings.webOsMac) }
    var necHex by rememberSaveable { mutableStateOf("20DF10EF") }
    var sircCommand by rememberSaveable { mutableStateOf("21") }
    var sircAddress by rememberSaveable { mutableStateOf("16") }
    var sircBits by rememberSaveable { mutableStateOf("12") }
    var ssapUri by rememberSaveable { mutableStateOf("ssap://system/getSystemInfo") }
    var ssapPayload by rememberSaveable { mutableStateOf("") }
    var showLab by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            eyebrow = "SETUP & ANALYSE",
            title = "SmartIR konfigurieren",
            subtitle = "Lokales webOS und Sony STR-DB870 CEL",
        )

        SectionCard {
            Text("LG TV im Heimnetz", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(webState.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { webOs.discoverTvs() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (webState.connection == WebOsConnection.DISCOVERING) "Suche läuft …" else "TV suchen")
                }
                OutlinedButton(
                    onClick = {
                        settings.setWebOsMac(macInput)
                        if (!webOs.wakeTv()) onMessage("Bitte eine gültige TV-MAC-Adresse eintragen")
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("TV wecken") }
            }

            if (webState.discoveredTvs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Gefundene Fernseher", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                webState.discoveredTvs.forEach { tv ->
                    OutlinedButton(
                        onClick = {
                            webOs.selectDiscoveredTv(tv)
                            hostInput = tv.host
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(tv.name, fontWeight = FontWeight.SemiBold)
                            Text(tv.host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TV-IP oder Hostname") },
                supportingText = { Text("Sichere Verbindung nutzt Port 3001, Port 3000 nur als Fallback") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = macInput,
                onValueChange = { macInput = it.take(17) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TV-MAC für Wake-on-LAN") },
                supportingText = { Text("Beispiel: AA:BB:CC:DD:EE:FF") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Automatisch wiederverbinden")
                    Text(
                        "Nach App-Start und kurzen Verbindungsabbrüchen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.autoConnect,
                    onCheckedChange = settings::setAutoConnect,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        settings.setWebOsHost(hostInput)
                        settings.setWebOsMac(macInput)
                        webOs.connect(hostInput)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (webState.connection == WebOsConnection.CONNECTED) "Neu verbinden" else "Verbinden")
                }
                OutlinedButton(
                    onClick = webOs::disconnect,
                    modifier = Modifier.weight(1f),
                ) { Text("Trennen") }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = webOs::forgetPairing) {
                Text("Kopplung und Zertifikat zurücksetzen")
            }

            if (webState.connection == WebOsConnection.CONNECTED) {
                Spacer(Modifier.height(8.dp))
                InfoLine("Transport", if (webState.secureTransport) "WSS 3001 · Zertifikat angeheftet" else "WS 3000 · Legacy-Fallback")
                InfoLine("Client-Key", if (settings.isWebOsClientKeyEncrypted()) "Android-Keystore verschlüsselt" else "lokal gespeichert")
                InfoLine("Modell", webState.modelName ?: "wird gelesen")
                InfoLine("App/Eingang", webState.currentApp ?: "unbekannt")
                InfoLine("Power", webState.powerState ?: "unbekannt")
                InfoLine("Lautstärke", webState.volume?.toString() ?: "unbekannt")
                InfoLine("Eingänge", webState.inputs.size.toString())
                InfoLine("Installierte Apps", webState.apps.size.toString())
                InfoLine("Magic Remote", if (webState.pointerReady) "bereit" else "wird verbunden")
                webState.certificateFingerprint.takeIf { it.isNotBlank() }?.let { fingerprint ->
                    InfoLine("TV-Zertifikat", fingerprint.take(23) + "…")
                }
            }
        }

        SectionCard {
            Text("Sony STR-DB870 CEL", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            InfoLine("Area Code", Sony_STR_DB870.AREA_CODE)
            InfoLine("Rückseitenkennung", Sony_STR_DB870.REAR_PANEL_MARKING)
            InfoLine("Originalfernbedienung", Sony_STR_DB870.SUPPLIED_REMOTE)
            InfoLine("Normalbetrieb", "AV1 fest · SIRC 12/15 Bit · 40 kHz")
            Spacer(Modifier.height(8.dp))
            Text(
                "Die neue Rückseitenaufnahme beseitigt die frühere Regionsunsicherheit. Sonys Anleitung ordnet dem STR-DB870 CEL die RM-U305A zu und schließt bei dieser Variante die Receiver-COMMAND-MODE-Umschaltung aus. Deshalb gibt es im normalen Bedienbereich keinen AV1/AV2-Schalter mehr.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "AV2 kann weiterhin bewusst im Rohcode-Labor mit Adresse 48 und 15 Bit untersucht werden. Die Seriennummer bleibt privat und wird nicht gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        BackupSection(
            settings = settings,
            onImported = {
                webOs.disconnect()
                hostInput = settings.webOsHost
                macInput = settings.webOsMac
            },
            onMessage = onMessage,
        )

        SectionCard {
            Text("Darstellung", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.themePreference == theme,
                        onClick = { settings.setThemePreference(theme) },
                        label = { Text(theme.title) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tastenvibration", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Kurzes Feedback nach erfolgreichem Befehl",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = settings::setHapticsEnabled,
                )
            }
        }

        SectionCard {
            Text("Diagnose", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            InfoLine("IR-Hardware", if (irAvailable) "erkannt" else "nicht erkannt")
            InfoLine("LG-Protokoll", "NEC 32 Bit · 38 kHz")
            InfoLine("Sony-Profil", "STR-DB870 CEL · RM-U305A · AV1")
            InfoLine("Sony-Protokoll", "SIRC 12 / 15 Bit · 40 kHz · 3 Frames")
            InfoLine("LG Netzwerk", webState.connection.name)
            if (webState.reconnectAttempt > 0) InfoLine("Reconnect", "Versuch ${webState.reconnectAttempt}")
        }

        OutlinedButton(
            onClick = { showLab = !showLab },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showLab) "Protokoll-Labor schließen" else "Protokoll-Labor öffnen")
        }

        AnimatedVisibility(showLab) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard {
                    Text("LG NEC-Code testen", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = necHex,
                        onValueChange = { value ->
                            necHex = value.filter { char ->
                                char.isDigit() || char.lowercaseChar() in 'a'..'f'
                            }.take(8)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("32-Bit Hex, z. B. 20DF10EF") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            necHex.toLongOrNull(16)?.let(onRawNec)
                                ?: onMessage("Ungültiger NEC-Hexcode")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("NEC senden") }
                }

                SectionCard {
                    Text("Sony SIRC-Code testen", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Standard für dein CEL-Gerät: Power = Command 21, Adresse 16, 12 Bit. Nur zur Diagnose lässt sich AV2 mit Adresse 48 und 15 Bit testen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sircCommand,
                            onValueChange = { sircCommand = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Command") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = sircAddress,
                            onValueChange = { sircAddress = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Adresse") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = sircBits,
                            onValueChange = { sircBits = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Bits") },
                            singleLine = true,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val command = sircCommand.toIntOrNull()
                            val address = sircAddress.toIntOrNull()
                            val bits = sircBits.toIntOrNull()
                            if (command == null || address == null || bits == null || bits !in setOf(12, 15, 20)) {
                                onMessage("Command, Adresse und Bitlänge 12/15/20 prüfen")
                            } else {
                                onRawSirc(command, address, bits)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("SIRC senden") }
                }

                SectionCard {
                    Text("webOS SSAP-Konsole", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ssapUri,
                        onValueChange = { ssapUri = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ssap:// URI") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ssapPayload,
                        onValueChange = { ssapPayload = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("JSON-Payload, optional") },
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!webOs.sendCustom(ssapUri, ssapPayload)) {
                                onMessage("TV nicht verbunden, URI oder JSON ungültig")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("SSAP-Befehl senden") }

                    webState.lastResponse?.let { response ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                response,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
