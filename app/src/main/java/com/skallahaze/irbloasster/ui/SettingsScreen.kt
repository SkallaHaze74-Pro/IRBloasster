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
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.data.ThemePreference
import com.skallahaze.irbloasster.ir.SonyCommandMode
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
    var necHex by rememberSaveable { mutableStateOf("20DF10EF") }
    var sircCommand by rememberSaveable { mutableStateOf("21") }
    var sircAddress by rememberSaveable { mutableStateOf("16") }
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
            subtitle = "IR, Sony-Modus und lokales LG-webOS",
        )

        SectionCard {
            Text("LG TV über WLAN", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                webState.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP-Adresse, z. B. 192.168.178.45") },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        settings.setWebOsHost(hostInput)
                        webOs.connect(hostInput)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (webState.connection == WebOsConnection.CONNECTED) {
                            "Neu verbinden"
                        } else {
                            "Verbinden"
                        },
                    )
                }
                OutlinedButton(
                    onClick = webOs::disconnect,
                    modifier = Modifier.weight(1f),
                ) { Text("Trennen") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = webOs::forgetPairing) {
                Text("Gespeicherte TV-Kopplung löschen")
            }

            if (webState.connection == WebOsConnection.CONNECTED) {
                Spacer(Modifier.height(8.dp))
                InfoLine("Modell", webState.modelName ?: "wird gelesen")
                InfoLine("App/Eingang", webState.currentApp ?: "unbekannt")
                InfoLine("Power", webState.powerState ?: "unbekannt")
                InfoLine("Lautstärke", webState.volume?.toString() ?: "unbekannt")
            }
        }

        SectionCard {
            Text("Sony Command Mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SonyCommandMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.sonyMode == mode,
                        onClick = { settings.setSonyMode(mode) },
                        label = { Text(mode.title) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Standard ist AV1. AV2 wählen, falls dein Receiver darauf umgestellt wurde.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
            InfoLine(
                "Sony-Protokoll",
                if (settings.sonyMode == SonyCommandMode.AV1) {
                    "SIRC 12 Bit · 40 kHz"
                } else {
                    "SIRC 15 Bit · 40 kHz"
                },
            )
            InfoLine("LG Netzwerk", webState.connection.name)
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
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = sircCommand,
                            onValueChange = { value ->
                                sircCommand = value.filter { it.isDigit() }.take(3)
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Command") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = sircAddress,
                            onValueChange = { value ->
                                sircAddress = value.filter { it.isDigit() }.take(3)
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Adresse") },
                            singleLine = true,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val command = sircCommand.toIntOrNull()
                            val address = sircAddress.toIntOrNull()
                            if (command == null || address == null) {
                                onMessage("Command und Adresse müssen Zahlen sein")
                            } else {
                                onRawSirc(command, address, if (address > 31) 15 else 12)
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
