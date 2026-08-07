package com.skallahaze.irbloasster

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.webos.PhoneAudioHttpServer
import com.skallahaze.irbloasster.webos.WebOsClient
import com.skallahaze.irbloasster.webos.WebOsConnection
import kotlinx.coroutines.launch
import org.json.JSONObject

class AudioMixActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(this)
        val webOs = WebOsClient(this, settings)
        val phoneServer = PhoneAudioHttpServer(this)

        setContent {
            IRTheme(preference = settings.themePreference) {
                AudioMixRoute(
                    settings = settings,
                    webOs = webOs,
                    phoneServer = phoneServer,
                    onClose = { finish() },
                )
            }
        }

        if (settings.autoConnect && settings.webOsHost.isNotBlank()) {
            android.os.Handler(mainLooper).postDelayed({ webOs.connect() }, 400L)
        }
    }
}

@Composable
private fun AudioMixRoute(
    settings: SettingsRepository,
    webOs: WebOsClient,
    phoneServer: PhoneAudioHttpServer,
    onClose: () -> Unit,
) {
    DisposableEffect(webOs, phoneServer) {
        onDispose {
            webOs.close()
            phoneServer.close()
        }
    }

    AudioMixScreen(
        settings = settings,
        webOs = webOs,
        phoneServer = phoneServer,
        onClose = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioMixScreen(
    settings: SettingsRepository,
    webOs: WebOsClient,
    phoneServer: PhoneAudioHttpServer,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val webState = webOs.state
    var streamUrl by rememberSaveable { mutableStateOf("") }
    var directUrl by rememberSaveable { mutableStateOf("") }
    var sourceName by rememberSaveable { mutableStateOf("Noch keine Musikquelle gewählt") }
    var status by rememberSaveable { mutableStateOf("Bereit für Web-Audio Mix Test") }
    var tvVolume by remember { mutableFloatStateOf((webState.volume ?: 50).toFloat()) }
    var musicVolume by rememberSaveable { mutableFloatStateOf(30f) }
    var bridgeRunning by rememberSaveable { mutableStateOf(false) }

    val bridgeInstalled = webState.apps.any { it.id == AUDIO_BRIDGE_APP_ID }

    LaunchedEffect(webState.volume) {
        webState.volume?.let { tvVolume = it.toFloat() }
    }

    LaunchedEffect(webState.connection) {
        if (webState.connection == WebOsConnection.CONNECTED) {
            webOs.refreshStatus()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Audiodatei wird für den TV vorbereitet …"
            phoneServer.prepare(uri)
                .onSuccess { url ->
                    streamUrl = url
                    sourceName = phoneServer.currentName().ifBlank { "Lokale Audiodatei" }
                    status = "Lokaler Stream bereit: $url"
                }
                .onFailure { error ->
                    status = error.message ?: "Audiodatei konnte nicht vorbereitet werden"
                }
        }
    }

    fun selectedStreamUrl(): String = directUrl.trim().ifBlank { streamUrl.trim() }

    fun sendBridge(action: String, includeStream: Boolean = false): Boolean {
        if (webState.connection != WebOsConnection.CONNECTED) {
            status = "Zuerst mit dem LG TV verbinden"
            return false
        }

        val params = JSONObject()
            .put("action", action)
            .put("volume", musicVolume.toInt().coerceIn(0, 100))

        if (includeStream) {
            val url = selectedStreamUrl()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                status = "Bitte Audiodatei wählen oder eine direkte http(s)-Audio-URL eintragen"
                return false
            }
            params.put("streamUrl", url)
        }

        val payload = JSONObject()
            .put("id", AUDIO_BRIDGE_APP_ID)
            .put("params", params)

        val sent = webOs.sendCustom(
            uri = "ssap://system.launcher/launch",
            payloadJson = payload.toString(),
        )
        if (!sent) status = "Bridge-Befehl konnte nicht an den TV gesendet werden"
        return sent
    }

    fun startBridge() {
        if (!bridgeInstalled) {
            status = "SmartIR Audio Bridge ist auf dem TV noch nicht installiert"
            return
        }
        if (sendBridge(action = "start", includeStream = true)) {
            bridgeRunning = true
            status = "Web-Audio Test gestartet. Erst prüfen, ob Musik in der Bridge hörbar ist; dann auf TV/HDMI wechseln."
        }
    }

    fun stopBridge() {
        if (sendBridge(action = "stop")) {
            bridgeRunning = false
            status = "Audio Bridge gestoppt"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartIR Audio Mix")
                        Text("Web-Audio Persistenz-Test", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { webOs.refreshStatus() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Status aktualisieren")
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
                MixCard(
                    title = "Verbindung",
                    subtitle = "Die Android-App steuert den TV per normalem webOS-SSAP. Root ist für diesen Test nicht nötig.",
                ) {
                    Text(
                        "${webState.connection} · ${webState.host.ifBlank { settings.webOsHost }}",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(webState.message, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { webOs.connect() }) { Text("Verbinden") }
                        OutlinedButton(onClick = { webOs.refreshStatus() }) { Text("Neu laden") }
                    }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (bridgeInstalled) "Audio Bridge installiert"
                                else "Audio Bridge fehlt noch",
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.BluetoothAudio, contentDescription = null)
                        },
                    )
                }
            }

            item {
                MixCard(
                    title = "🎵 Musikquelle",
                    subtitle = "Für den Test eine MP3/AAC/M4A-Datei wählen oder eine direkte Audio-URL eintragen.",
                ) {
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Audiodatei vom Handy wählen")
                    }
                    Text(sourceName, style = MaterialTheme.typography.bodySmall)
                    if (streamUrl.isNotBlank()) {
                        Text(streamUrl, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedTextField(
                        value = directUrl,
                        onValueChange = { directUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Direkte Audio-URL (optional)") },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        singleLine = true,
                    )
                }
            }

            item {
                MixCard(
                    title = "📺 LG Master / TV",
                    subtitle = "Aktuell der normale LG-Masterpegel. Er wirkt im Root-free Modus auf den TV-Ausgang und damit grundsätzlich auch auf gemischtes Audio. Ein echter ADEC1-only Regler braucht noch UMI-Zugriff/Root.",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(Icons.Rounded.Tv, contentDescription = null)
                        Text("${tvVolume.toInt()} %", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = tvVolume,
                        onValueChange = { tvVolume = it },
                        onValueChangeFinished = {
                            if (!webOs.setVolume(tvVolume.toInt())) {
                                status = "LG-Masterlautstärke konnte nicht gesetzt werden"
                            }
                        },
                        valueRange = 0f..100f,
                    )
                }
            }

            item {
                MixCard(
                    title = "🎵 Hintergrundmusik",
                    subtitle = "Nur der Web-Audio-Gain dieser Musikquelle. Dieser Regler darf den TV-Ton nicht mehr verändern.",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null)
                        Text("${musicVolume.toInt()} %", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = musicVolume,
                        onValueChange = { musicVolume = it },
                        onValueChangeFinished = {
                            if (bridgeRunning && !sendBridge(action = "volume")) {
                                status = "Musik-Gain konnte nicht aktualisiert werden"
                            }
                        },
                        valueRange = 0f..100f,
                    )
                }
            }

            item {
                MixCard(
                    title = "BT/WLAN Mix",
                    subtitle = "Bridge aktiviert mixDigitalSoundOutput(true) und testet Web Audio zuerst. Web Audio hat einen eigenen Gain und soll beim Wechsel auf TV/HDMI weiterlaufen.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = ::startBridge) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("Mix starten")
                        }
                        OutlinedButton(onClick = ::stopBridge) { Text("Stop") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Test: 1) Mix starten. 2) In der Bridge muss Musik hörbar sein. 3) Erst dann auf TV/HDMI wechseln. 4) Prüfen, ob die Musik weiterläuft. Hintergrundmusik-Regler darf den TV-Pegel nicht verändern.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MixCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

private const val AUDIO_BRIDGE_APP_ID = "com.skallahaze.smartir.audiobridge"
