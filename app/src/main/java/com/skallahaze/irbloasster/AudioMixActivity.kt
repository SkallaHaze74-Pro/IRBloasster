package com.skallahaze.irbloasster

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.webos.LiveAudioCaptureService
import com.skallahaze.irbloasster.webos.LiveAudioRuntime
import com.skallahaze.irbloasster.webos.PhoneAudioHttpServer
import com.skallahaze.irbloasster.webos.WebOsClient
import com.skallahaze.irbloasster.webos.WebOsConnection
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webState = webOs.state
    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    var streamUrl by rememberSaveable { mutableStateOf("") }
    var directUrl by rememberSaveable { mutableStateOf("") }
    var sourceName by rememberSaveable { mutableStateOf("Noch keine Datei gewählt") }
    var status by rememberSaveable { mutableStateOf("Bereit für SmartIR Live Audio") }
    var tvVolume by remember { mutableFloatStateOf((webState.volume ?: 50).toFloat()) }
    var musicVolume by rememberSaveable { mutableFloatStateOf(30f) }
    var bridgeRunning by rememberSaveable { mutableStateOf(false) }
    var liveUrl by rememberSaveable { mutableStateOf(LiveAudioRuntime.streamUrl) }
    var liveRunning by rememberSaveable { mutableStateOf(LiveAudioRuntime.running) }
    var liveMessage by rememberSaveable { mutableStateOf(LiveAudioRuntime.message) }

    val bridgeInstalled = webState.apps.any { it.id == AUDIO_BRIDGE_APP_ID }

    LaunchedEffect(webState.volume) {
        webState.volume?.let { tvVolume = it.toFloat() }
    }

    LaunchedEffect(webState.connection) {
        if (webState.connection == WebOsConnection.CONNECTED) {
            webOs.refreshStatus()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            liveUrl = LiveAudioRuntime.streamUrl
            liveRunning = LiveAudioRuntime.running
            liveMessage = LiveAudioRuntime.message
            delay(400L)
        }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(context, LiveAudioCaptureService::class.java).apply {
                action = LiveAudioCaptureService.ACTION_START
                putExtra(LiveAudioCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(LiveAudioCaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            status = "Live-Audio-Capture startet …"
        } else {
            status = "Live-Audio-Freigabe abgebrochen"
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            status = "Audioaufnahme-Berechtigung wurde nicht erlaubt"
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
                    status = "Datei-Stream bereit: $url"
                }
                .onFailure { error ->
                    status = error.message ?: "Audiodatei konnte nicht vorbereitet werden"
                }
        }
    }

    fun selectedFileUrl(): String = directUrl.trim().ifBlank { streamUrl.trim() }

    fun sendBridge(action: String, explicitStreamUrl: String? = null): Boolean {
        if (webState.connection != WebOsConnection.CONNECTED) {
            status = "Zuerst mit dem LG TV verbinden"
            return false
        }

        val params = JSONObject()
            .put("action", action)
            .put("volume", musicVolume.toInt().coerceIn(0, 100))

        explicitStreamUrl?.takeIf { it.isNotBlank() }?.let { params.put("streamUrl", it) }

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

    fun startFileBridge() {
        if (!bridgeInstalled) {
            status = "SmartIR Audio Bridge ist auf dem TV noch nicht installiert"
            return
        }
        val url = selectedFileUrl()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            status = "Bitte Audiodatei wählen oder direkte http(s)-Audio-URL eintragen"
            return
        }
        if (sendBridge(action = "start", explicitStreamUrl = url)) {
            bridgeRunning = true
            status = "Datei/Web-Audio-Test gestartet"
        }
    }

    fun startLiveCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status = "Live-Audio benötigt Android 10 oder neuer"
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopLiveCapture() {
        context.startService(
            Intent(context, LiveAudioCaptureService::class.java).apply {
                action = LiveAudioCaptureService.ACTION_STOP
            },
        )
        status = "Live-Capture wird gestoppt"
    }

    fun startLiveBridge() {
        if (!bridgeInstalled) {
            status = "SmartIR Audio Bridge ist auf dem TV noch nicht installiert"
            return
        }
        val url = liveUrl
        if (!liveRunning || !url.startsWith("ws://")) {
            status = "Erst Live-Capture starten und auf LIVE warten"
            return
        }
        if (sendBridge(action = "live", explicitStreamUrl = url)) {
            bridgeRunning = true
            status = "LIVE-Mix gestartet · jetzt Musik-App starten/weiterlaufen lassen"
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
                        Text("Live-Audio + Web-Audio", style = MaterialTheme.typography.labelMedium)
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
                    subtitle = "TV-Steuerung über normales webOS-SSAP; Root ist für den Live-Test nicht nötig.",
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
                    title = "🔴 Live-Audio vom Handy",
                    subtitle = "Kein MP3-Download: SmartIR nimmt erlaubtes Medien-Audio des Handys live auf und streamt PCM nur im WLAN direkt zum TV.",
                ) {
                    Text(
                        if (liveRunning) "LIVE" else "AUS",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(liveMessage, style = MaterialTheme.typography.bodySmall)
                    if (liveUrl.isNotBlank()) {
                        Text(liveUrl, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = ::startLiveCapture) { Text("Live-Capture starten") }
                        OutlinedButton(onClick = ::stopLiveCapture) { Text("Capture Stop") }
                    }
                    Button(
                        onClick = ::startLiveBridge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("LIVE Mix starten")
                    }
                    Text(
                        "Android zeigt einmal die Systemfreigabe für Audio-/Bildschirmaufnahme. Es wird kein Video gespeichert. Manche Streaming-/DRM-Apps können interne Audioaufnahme blockieren.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                MixCard(
                    title = "🎵 Datei/URL Fallback",
                    subtitle = "Nur noch als Test/Fallback. Für normalen Betrieb ist Live-Audio gedacht.",
                ) {
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Audiodatei wählen")
                    }
                    Text(sourceName, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = directUrl,
                        onValueChange = { directUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Direkte Audio-URL (optional)") },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        singleLine = true,
                    )
                    OutlinedButton(onClick = ::startFileBridge) { Text("Fallback starten") }
                }
            }

            item {
                MixCard(
                    title = "📺 LG Master / TV",
                    subtitle = "Aktuell LG-Masterpegel. Ein echter ADEC1-only Regler braucht weiterhin UMI-Zugriff/Root.",
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
                    subtitle = "Nur der Web-Audio-Gain des Live-/Musikkanals. Dieser Regler darf den TV-Ton nicht verändern.",
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
                    subtitle = "Bluetooth bleibt unser späterer A2DP/AMIXER4-Backend. Ohne Root testen wir denselben Bedienweg jetzt live über WLAN + Web Audio.",
                ) {
                    OutlinedButton(onClick = ::stopBridge) { Text("TV Bridge Stop") }
                    Spacer(Modifier.height(4.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Live-Test: Capture starten → Systemfreigabe bestätigen → auf LIVE warten → LIVE Mix starten → Musik-App abspielen → am TV auf TV/HDMI wechseln.",
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
