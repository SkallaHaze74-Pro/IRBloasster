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
import com.skallahaze.irbloasster.webos.DeepLabConnection
import com.skallahaze.irbloasster.webos.DeepLabState
import com.skallahaze.irbloasster.webos.DeepLabValue
import com.skallahaze.irbloasster.webos.DeepTvLabClient

class TvLabProActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(this)

        setContent {
            IRTheme(preference = settings.themePreference) {
                TvLabProRoute(
                    settings = settings,
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun TvLabProRoute(
    settings: SettingsRepository,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val client = remember { DeepTvLabClient(context, settings) }

    DisposableEffect(client) {
        onDispose(client::close)
    }

    TvLabProScreen(client = client, onClose = onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvLabProScreen(
    client: DeepTvLabClient,
    onClose: () -> Unit,
) {
    val state = client.state
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showCapabilities by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartIR TV Lab Pro")
                        Text(
                            "Deep read-only Scanner",
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
                    IconButton(onClick = client::rescan) {
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
                StatusSection(
                    state = state,
                    onConnect = client::connectAndScan,
                    onRescan = client::rescan,
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
                            "${state.completedRequests}/${state.totalRequests} isolierte read-only Abfragen",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            item {
                LabCard(
                    title = "Gefundener Umfang",
                    subtitle = "Eine fehlerhafte Taste oder Kategorie blockiert den Rest nicht mehr.",
                ) {
                    ValueRow("Hardware/Funktionen", state.capabilities.size.toString())
                    ValueRow("Einstellungswerte", state.settings.values.sumOf { it.size }.toString())
                    ValueRow("Live-/Systemstatus", state.liveStatus.size.toString())
                    ValueRow("Installierte Apps", state.installedApps.size.toString())
                    ValueRow("PQ-Lesetreffer", state.pqSnapshots.size.toString())
                }
            }

            item {
                LabCard(
                    title = "System",
                    subtitle = "Modell, Firmware und sichere Systemdaten.",
                ) {
                    ValueList(state.systemInfo)
                }
            }

            item {
                LabCard(
                    title = "Hardware & Funktionen",
                    subtitle = "${state.capabilities.size} Werte über config/getConfigs",
                ) {
                    ValueList(
                        if (showCapabilities) state.capabilities else state.capabilities.take(12),
                    )
                    if (state.capabilities.size > 12) {
                        OutlinedButton(
                            onClick = { showCapabilities = !showCapabilities },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (showCapabilities) "Weniger anzeigen" else "Alle anzeigen")
                        }
                    }
                }
            }

            item {
                LabCard(
                    title = "Einzelne Einstellungsabfragen",
                    subtitle = "Ungültige option-Kandidaten erzeugen höchstens einen Einzelfehler.",
                ) {
                    val categories = if (showSettings) {
                        state.settings.entries.toList()
                    } else {
                        state.settings.entries.take(3)
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
                        if (state.settings.size > 3) {
                            OutlinedButton(
                                onClick = { showSettings = !showSettings },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (showSettings) "Weniger Kategorien" else "Alle Kategorien")
                            }
                        }
                    }
                }
            }

            item {
                LabCard(
                    title = "Live- und Dienststatus",
                    subtitle = "Software, Audio, Eingang, App, Power und erkannte Dienste.",
                ) {
                    ValueList(state.liveStatus.take(30))
                }
            }

            item {
                LabCard(
                    title = "Sichere versteckte LG-Apps",
                    subtitle = "Nur geprüfte offizielle Apps werden startbar gemacht.",
                ) {
                    if (state.availableSafeHiddenApps.isEmpty()) {
                        Text("Noch keine passenden Apps erkannt.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.availableSafeHiddenApps.forEach { appId ->
                                OutlinedButton(
                                    onClick = { client.launchSafeHiddenApp(appId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.padding(4.dp))
                                    Text(DeepTvLabClient.SAFE_HIDDEN_APPS[appId] ?: appId)
                                }
                            }
                        }
                    }
                }
            }

            item {
                LabCard(
                    title = "Installierte App-Inventur",
                    subtitle = "Read-only Liste; Factory-/Service-Apps werden nicht automatisch gestartet.",
                ) {
                    ValueList(if (showApps) state.installedApps else state.installedApps.take(15))
                    if (state.installedApps.size > 15) {
                        OutlinedButton(
                            onClick = { showApps = !showApps },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (showApps) "Weniger Apps" else "Alle Apps anzeigen")
                        }
                    }
                }
            }

            item {
                LabCard(
                    title = "External-PQ read-only Probe",
                    subtitle = "Nur Lesen mit bekannten command/picMode-Kandidaten; kein Zurückschreiben.",
                ) {
                    if (state.pqSnapshots.isEmpty()) {
                        Text("Kein gültiger PQ-Lesetreffer. Das ist auf diesem Modell/Bildmodus möglich.")
                    } else {
                        state.pqSnapshots.forEach { (label, value) ->
                            Text(label, fontWeight = FontWeight.Bold)
                            Text(
                                value.take(1_500),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item {
                LabCard(
                    title = "Bericht",
                    subtitle = "Ohne Client-Key, Zertifikat, Seriennummer, MAC oder IP-Adresse.",
                ) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(client.anonymizedReport()))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Deep-TV-Bericht kopieren")
                    }
                    if (copied) {
                        AssistChip(
                            onClick = { copied = false },
                            label = { Text("Deep-TV-Bericht kopiert") },
                        )
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                item {
                    LabCard(
                        title = "Nicht unterstützte Einzelabfragen",
                        subtitle = "${state.errors.size} Fehler; erfolgreiche Werte bleiben gültig.",
                    ) {
                        OutlinedButton(
                            onClick = { showErrors = !showErrors },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (showErrors) "Fehler ausblenden" else "Fehler anzeigen")
                        }
                        if (showErrors) {
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
                            Text("Sicherheitsgrenze", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Alles verfügbare Read-only wird geprüft. Service Reset, Panel-/Tool-Optionen, White Balance, LUT, NVRAM, EDID, OLED-Schutz und External-PQ-Schreiben bleiben gesperrt, bis ein getestetes Backup und Rollback existiert.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    state: DeepLabState,
    onConnect: () -> Unit,
    onRescan: () -> Unit,
) {
    LabCard(
        title = when (state.connection) {
            DeepLabConnection.CONNECTED -> "TV-Labor Pro verbunden"
            DeepLabConnection.CONNECTING -> "Verbindung wird aufgebaut"
            DeepLabConnection.PAIRING -> "TV-Autorisierung"
            DeepLabConnection.ERROR -> "Verbindungsfehler"
            DeepLabConnection.DISCONNECTED -> "TV-Labor Pro offline"
        },
        subtitle = state.message,
    ) {
        if (state.host.isNotBlank()) {
            Text(
                "${state.host} · ${if (state.secureTransport) "WSS 3001" else "lokale Verbindung"}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConnect,
                modifier = Modifier.weight(1f),
            ) {
                Text("Verbinden & Deep Scan")
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

@Composable
private fun LabCard(
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
private fun ValueList(values: List<DeepLabValue>) {
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
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
