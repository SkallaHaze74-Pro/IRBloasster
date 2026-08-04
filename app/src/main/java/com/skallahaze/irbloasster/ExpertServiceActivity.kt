package com.skallahaze.irbloasster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.webos.ExpertFactoryMenu
import com.skallahaze.irbloasster.webos.ExpertServiceClient
import com.skallahaze.irbloasster.webos.ExpertServiceConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpertServiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(this)

        setContent {
            IRTheme(preference = settings.themePreference) {
                ExpertServiceRoute(
                    settings = settings,
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ExpertServiceRoute(
    settings: SettingsRepository,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val client = remember { ExpertServiceClient(context, settings) }

    DisposableEffect(client) {
        onDispose(client::close)
    }

    ExpertServiceScreen(client = client, onClose = onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpertServiceScreen(
    client: ExpertServiceClient,
    onClose: () -> Unit,
) {
    val state = client.state
    val context = LocalContext.current
    var acknowledged by remember { mutableStateOf(false) }
    var pendingMenu by remember { mutableStateOf<ExpertFactoryMenu?>(null) }
    var pendingReport by remember { mutableStateOf("") }
    var exportMessage by remember { mutableStateOf("") }

    val saveReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            exportMessage = "Speichern abgebrochen"
        } else {
            val result = runCatching {
                val output = context.contentResolver.openOutputStream(uri, "w")
                    ?: error("Datei konnte nicht geöffnet werden")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(pendingReport)
                }
            }
            exportMessage = result.fold(
                onSuccess = { "Expert-Vorprüfung gespeichert" },
                onFailure = { error ->
                    "Speichern fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
                },
            )
        }
    }

    val connected = state.connection == ExpertServiceConnection.CONNECTED
    val preflightReady = connected &&
        !state.preflightRunning &&
        state.preflightTotal > 0 &&
        state.preflightCompleted >= state.preflightTotal &&
        state.lastPreflightEpochMillis > 0L

    pendingMenu?.let { menu ->
        AlertDialog(
            onDismissRequest = { pendingMenu = null },
            icon = { Icon(Icons.Rounded.Engineering, contentDescription = null) },
            title = { Text("${menu.displayName} wirklich öffnen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(menu.description)
                    Text(
                        "SmartIR startet nur die originale LG-Factorywin-App. Das LG-Passwort bleibt erforderlich; es wird nichts automatisch geändert.",
                    )
                    if (menu == ExpertFactoryMenu.EZ_ADJUST) {
                        Text(
                            "Tool Options und White Balance können Panel, Bildabgleich, Anschlüsse oder Lizenzflags dauerhaft verändern. Vorher Werte fotografieren und den JSON-Bericht speichern.",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        client.launchFactoryMenu(menu)
                        pendingMenu = null
                    },
                ) {
                    Text("Originalmenü öffnen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMenu = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartIR Expert Service")
                        Text(
                            "LG Factorywin · Stock-Firmware-Pfad",
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
                    IconButton(onClick = { client.runPreflight() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Vorprüfung wiederholen")
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
                ExpertCard(
                    title = connectionTitle(state.connection),
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
                            onClick = { client.connect() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Verbinden")
                        }
                        OutlinedButton(
                            onClick = { client.runPreflight() },
                            enabled = connected,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Vorprüfung")
                        }
                    }
                }
            }

            if (state.preflightRunning) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = {
                                if (state.preflightTotal == 0) 0f
                                else state.preflightCompleted.toFloat() / state.preflightTotal.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${state.preflightCompleted}/${state.preflightTotal} read-only Abfragen",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            item {
                ExpertCard(
                    title = "Besserer Weg als Firmware überschreiben",
                    subtitle = "Die vorhandene, versteckte LG-App wird mit ihrem originalen Startparameter geöffnet.",
                ) {
                    ExpertValueRow("Factory-App", ExpertServiceClient.FACTORY_APP_ID)
                    ExpertValueRow("Root für das Öffnen", "Nein")
                    ExpertValueRow("Passwort-Bypass", "Nein")
                    ExpertValueRow("Automatische Schreibbefehle", "Nein")
                    Text(
                        "Damit bleiben Bootkette, Signaturen und Stock-Firmware unverändert. Änderungen passieren ausschließlich bewusst im originalen LG-Menü.",
                    )
                }
            }

            item {
                ExpertCard(
                    title = "Read-only Vorprüfung",
                    subtitle = "Modell, Firmware, Panelklasse, HDR-Fähigkeiten und aktuelle Basis-Bildwerte.",
                ) {
                    if (state.preflightValues.isEmpty()) {
                        Text("Noch keine Werte")
                    } else {
                        state.preflightValues.entries
                            .sortedBy { it.key.lowercase(Locale.ROOT) }
                            .take(24)
                            .forEach { (key, value) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(key, style = MaterialTheme.typography.labelMedium)
                                    Text(value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                    }

                    Button(
                        enabled = preflightReady,
                        onClick = {
                            pendingReport = sanitizeExpertReport(client.exportPreflightReport())
                            exportMessage = ""
                            saveReportLauncher.launch(expertReportFileName())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Vorprüfung als JSON sichern")
                    }

                    if (exportMessage.isNotBlank()) {
                        AssistChip(
                            onClick = { exportMessage = "" },
                            label = { Text(exportMessage) },
                        )
                    }
                }
            }

            item {
                ExpertCard(
                    title = "Was EZ-ADJUST enthält",
                    subtitle = "Aus der Firmware deines OLED55B19LA bestätigt.",
                ) {
                    Text("• ToolOPT1 · Produkt/Panel")
                    Text("• ToolOPT2 · Stromversorgung")
                    Text("• ToolOPT3 · Bild/Ton")
                    Text("• ToolOPT4 · Sonstiges")
                    Text("• ToolOPT5 · Anschlüsse/Schlüsselstatus")
                    Text("• ToolOPT6 · Energie/Land")
                    Text("• White Balance")
                    Text("• 22 Point White Balance")
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                            Text("Expertfreigabe", fontWeight = FontWeight.Bold)
                        }
                        ExpertServiceClient.SAFETY_WARNINGS.forEach { warning ->
                            Text("• $warning")
                        }
                        FilterChip(
                            selected = acknowledged,
                            onClick = { acknowledged = !acknowledged },
                            label = {
                                Text(
                                    if (acknowledged) {
                                        "Risiko verstanden"
                                    } else {
                                        "Risiko bestätigen"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                ExpertCard(
                    title = "LG EZ-ADJUST",
                    subtitle = "Tool Options, White Balance und 22-Punkt-Abgleich im originalen LG-Menü.",
                ) {
                    Button(
                        enabled = acknowledged && preflightReady,
                        onClick = { pendingMenu = ExpertFactoryMenu.EZ_ADJUST },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("EZ-ADJUST öffnen")
                    }
                    Text(
                        "Keine SmartIR-Schaltfläche schreibt Tool Options oder White-Balance-Werte. Das bleibt absichtlich im LG-Menü mit dessen Passwortabfrage.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                ExpertCard(
                    title = "LG IN-START",
                    subtitle = "System-, OLED-, HDMI- und Diagnoseinformationen. Kein automatischer Reset.",
                ) {
                    OutlinedButton(
                        enabled = acknowledged && preflightReady,
                        onClick = { pendingMenu = ExpertFactoryMenu.IN_START },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("IN-START öffnen")
                    }
                }
            }

            item {
                ExpertCard(
                    title = "Bewusst gesperrt",
                    subtitle = "Diese Befehlsfamilien sind in SmartIR nicht implementiert.",
                ) {
                    ExpertServiceClient.BLOCKED_WRITE_FAMILIES.forEach { family ->
                        Text("• $family")
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                item {
                    ExpertCard(
                        title = "Nicht unterstützte Abfragen",
                        subtitle = "Erfolgreiche Werte bleiben gültig.",
                    ) {
                        state.errors.forEach { error -> Text("• $error") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpertCard(
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
private fun ExpertValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun connectionTitle(connection: ExpertServiceConnection): String = when (connection) {
    ExpertServiceConnection.CONNECTED -> "Expert Service verbunden"
    ExpertServiceConnection.CONNECTING -> "Verbindung wird aufgebaut"
    ExpertServiceConnection.PAIRING -> "LG-Autorisierung"
    ExpertServiceConnection.ERROR -> "Verbindungsfehler"
    ExpertServiceConnection.DISCONNECTED -> "Expert Service offline"
}

private fun expertReportFileName(): String {
    val timestamp = SimpleDateFormat(
        "yyyy-MM-dd_HH-mm-ss",
        Locale.GERMANY,
    ).format(Date())
    return "SmartIR-Expert-Vorpruefung-$timestamp.json"
}

private fun sanitizeExpertReport(report: String): String = report
    .replace(EXPERT_IPV4_REGEX, "<redacted-ip>")

private val EXPERT_IPV4_REGEX = Regex(
    "(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9.])",
)
