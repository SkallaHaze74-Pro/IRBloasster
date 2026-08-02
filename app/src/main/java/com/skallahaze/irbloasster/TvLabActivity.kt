package com.skallahaze.irbloasster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.webos.TvLabAdvice
import com.skallahaze.irbloasster.webos.TvLabAdviceLevel
import com.skallahaze.irbloasster.webos.TvLabClient
import com.skallahaze.irbloasster.webos.TvLabConnection
import com.skallahaze.irbloasster.webos.TvLabProfile
import com.skallahaze.irbloasster.webos.TvLabValue

class TvLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(this)

        setContent {
            IRTheme(preference = settings.themePreference) {
                TvLabRoute(
                    settings = settings,
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun TvLabRoute(
    settings: SettingsRepository,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val client = remember { TvLabClient(context, settings) }

    DisposableEffect(client) {
        onDispose(client::close)
    }

    TvLabScreen(client = client, onClose = onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvLabScreen(
    client: TvLabClient,
    onClose: () -> Unit,
) {
    val state = client.state
    val clipboard = LocalClipboardManager.current
    var expandedCapabilities by remember { mutableStateOf(false) }
    var expandedSettings by remember { mutableStateOf(false) }
    var copiedMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartIR TV Lab")
                        Text(
                            "Read-only Scanner & Kalibrierassistent",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { client.rescan() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Neu scannen")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LabStatusCard(
                    connection = state.connection,
                    message = state.message,
                    host = state.host,
                    secure = state.secureTransport,
                    onConnect = { client.connectAndScan() },
                    onRescan = { client.rescan() },
                )
            }

            if (state.scanning) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = {
                                if (state.totalRequests == 0) 0f
                                else state.completedRequests.toFloat() / state.totalRequests.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${state.completedRequests}/${state.totalRequests} read-only Abfragen",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            item {
                LabSection(
                    title = "Kalibrierprofil",
                    subtitle = "Die Analyse schreibt keine Bildwerte und trennt SDR, HDR und Gaming.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvLabProfile.entries.chunked(2).forEach { rowProfiles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowProfiles.forEach { profile ->
                                    FilterChip(
                                        selected = state.profile == profile,
                                        onClick = { client.setProfile(profile) },
                                        label = { Text(profile.title) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowProfiles.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                LabSection(
                    title = "Smart-Kalibrieranalyse",
                    subtitle = "Regelbasiert aus den echten TV-Werten. Kamera-/Colorimeter-Messung folgt als eigene Messstufe.",
                ) {
                    if (state.advice.isEmpty()) {
                        Text("Nach dem ersten Scan erscheinen hier konkrete Prüf- und Optimierungshinweise.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.advice.forEach { AdviceCard(it) }
                        }
                    }
                }
            }

            item {
                LabSection(
                    title = "Sichere LG-Werkzeuge",
                    subtitle = "Nur geprüfte offizielle Apps; keine Factory-, Reset-, Panel- oder Service-Menüs.",
                ) {
                    if (state.availableHiddenApps.isEmpty()) {
                        Text("Noch keine passenden Apps gemeldet oder der Scan ist nicht abgeschlossen.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.availableHiddenApps.forEach { appId ->
                                OutlinedButton(
                                    onClick = { client.launchSafeHiddenApp(appId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.padding(4.dp))
                                    Text(TvLabClient.SAFE_HIDDEN_APPS[appId] ?: appId)
                                }
                            }
                        }
                    }
                }
            }

            item {
                LabSection(
                    title = "System",
                    subtitle = "Modell, Firmware, SDK und Boarddaten ohne Seriennummern.",
                ) {
                    ValueList(state.systemInfo)
                }
            }

            item {
                LabSection(
                    title = "Gefundene Hardware & Funktionen",
                    subtitle = "${state.capabilities.size} Werte über ssap://config/getConfigs",
                ) {
                    ValueList(
                        if (expandedCapabilities) state.capabilities else state.capabilities.take(10),
                    )
                    if (state.capabilities.size > 10) {
                        OutlinedButton(
                            onClick = { expandedCapabilities = !expandedCapabilities },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (expandedCapabilities) "Weniger anzeigen" else "Alle anzeigen")
                        }
                    }
                }
            }

            item {
                LabSection(
                    title = "Sichere Einstellungswerte",
                    subtitle = "Bild, Ton, Netzwerk, CEC und allgemeine Statuswerte.",
                ) {
                    val categories = if (expandedSettings) {
                        state.settings.entries.toList()
                    } else {
                        state.settings.entries.take(2)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        categories.forEach { (category, values) ->
                            Text(
                                category.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            ValueList(values)
                        }
                        if (state.settings.size > 2) {
                            OutlinedButton(
                                onClick = { expandedSettings = !expandedSettings },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (expandedSettings) "Weniger anzeigen" else "Alle Kategorien anzeigen")
                            }
                        }
                    }
                }
            }

            item {
                LabSection(
                    title = "Backup & Export",
                    subtitle = "Der normale Bericht ist anonymisiert. PQ-Daten werden getrennt und nur read-only kopiert.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(client.anonymizedReport()))
                                copiedMessage = "Anonymisierter TV-Bericht kopiert"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("TV-Bericht kopieren")
                        }
                        OutlinedButton(
                            enabled = state.pqSnapshot.isNotBlank(),
                            onClick = {
                                clipboard.setText(AnnotatedString(client.pqBackup()))
                                copiedMessage = "Read-only PQ-Snapshot kopiert"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("PQ-Snapshot separat kopieren")
                        }
                        if (copiedMessage.isNotBlank()) {
                            AssistChip(
                                onClick = { copiedMessage = "" },
                                label = { Text(copiedMessage) },
                            )
                        }
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                item {
                    LabSection(
                        title = "Nicht verfügbare Abfragen",
                        subtitle = "Einzelne 401-/Endpoint-Fehler sind modell- und berechtigungsabhängig; der Rest des Scans bleibt gültig.",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.errors.forEach { Text("• $it") }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Science, contentDescription = null)
                            Text(
                                "Kalibriergrenze",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "Eine Smartphone-Kamera ist kein Colorimeter. SmartIR kann Testbilder, Wiederholungsmessungen und Abweichungen automatisieren, aber White Balance, LUT und External-PQ werden erst mit validierter Messhardware und vollständigem Rollback freigeschaltet.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabStatusCard(
    connection: TvLabConnection,
    message: String,
    host: String,
    secure: Boolean,
    onConnect: () -> Unit,
    onRescan: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when (connection) {
                    TvLabConnection.CONNECTED -> "TV-Labor verbunden"
                    TvLabConnection.CONNECTING -> "Verbindung wird aufgebaut"
                    TvLabConnection.PAIRING -> "TV-Autorisierung"
                    TvLabConnection.ERROR -> "Verbindungsfehler"
                    TvLabConnection.DISCONNECTED -> "TV-Labor offline"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(message)
            if (host.isNotBlank()) {
                Text(
                    "$host · ${if (secure) "WSS 3001" else "lokale Verbindung"}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Verbinden & scannen")
                }
                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Neu scannen")
                }
            }
        }
    }
}

@Composable
private fun LabSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun ValueList(values: List<TvLabValue>) {
    if (values.isEmpty()) {
        Text("Noch keine Werte")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { item ->
            Column {
                Text(item.key, style = MaterialTheme.typography.labelMedium)
                Text(item.value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AdviceCard(advice: TvLabAdvice) {
    val color = when (advice.level) {
        TvLabAdviceLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
        TvLabAdviceLevel.CHECK -> MaterialTheme.colorScheme.tertiaryContainer
        TvLabAdviceLevel.IMPORTANT -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(advice.title, fontWeight = FontWeight.Bold)
            Text(advice.detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
