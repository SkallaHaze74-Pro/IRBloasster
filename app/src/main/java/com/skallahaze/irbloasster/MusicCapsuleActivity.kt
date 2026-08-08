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
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.skallahaze.irbloasster.capsule.MusicCapsuleOverlayService
import com.skallahaze.irbloasster.capsule.MusicCapsuleRuntime
import com.skallahaze.irbloasster.capsule.MusicCapsuleSnapshot
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.webos.LiveAudioCaptureService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class MusicCapsuleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(this)
        setContent {
            IRTheme(preference = settings.themePreference) {
                MusicCapsuleScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicCapsuleScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    var runtime by remember { mutableStateOf(MusicCapsuleRuntime.snapshot()) }
    var status by remember { mutableStateOf("Bereit für die Xiaomi Music Capsule") }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationAccess by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    var audioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            runtime = MusicCapsuleRuntime.snapshot()
            overlayAllowed = Settings.canDrawOverlays(context)
            notificationAccess = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)
            audioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            delay(350L)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        audioPermission = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        status = if (audioPermission) {
            "Audioanalyse-Berechtigung erteilt"
        } else {
            "RECORD_AUDIO wurde nicht erlaubt; echter FFT-Equalizer kann fehlen"
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
            status = "Kompatibilitäts-Capture startet; dient als Fallback für HyperOS"
        } else {
            status = "Audiofreigabe abgebrochen"
        }
    }

    fun requestAudioPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    fun startCapsule() {
        if (!Settings.canDrawOverlays(context)) {
            status = "Erst ‚Über anderen Apps anzeigen‘ erlauben"
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }
        if (!audioPermission) {
            requestAudioPermissions()
            status = "Audioanalyse erlauben und danach Kapsel erneut starten"
            return
        }
        MusicCapsuleOverlayService.start(context)
        status = "Music Capsule startet oben am Bildschirmrand"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartIR Music Capsule")
                        Text("Xiaomi · Root-free Overlay", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
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
                CapsulePreview(runtime)
            }

            item {
                SetupCard(
                    title = "1 · Über anderen Apps",
                    subtitle = "Damit die Kapsel über SoundCloud, Spotify, YouTube Music und dem Startbildschirm sichtbar bleibt.",
                    ready = overlayAllowed,
                    icon = Icons.Rounded.Layers,
                ) {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    ) {
                        Text(if (overlayAllowed) "Erlaubt" else "Overlay erlauben")
                    }
                }
            }

            item {
                SetupCard(
                    title = "2 · Echter Audio-Equalizer",
                    subtitle = "SmartIR versucht zuerst Androids globalen Audio-Visualizer. Root ist dafür nicht nötig; HyperOS kann den globalen Mix aber blockieren.",
                    ready = audioPermission,
                    icon = Icons.Rounded.GraphicEq,
                ) {
                    Button(onClick = ::requestAudioPermissions) {
                        Text(if (audioPermission) "Audio erlaubt" else "Audio erlauben")
                    }
                }
            }

            item {
                SetupCard(
                    title = "3 · Cover, Titel und Mediensteuerung",
                    subtitle = "Der Benachrichtigungszugriff liefert Albumcover, Titel, Künstler sowie Play/Pause/Nächster Titel.",
                    ready = notificationAccess,
                    icon = Icons.Rounded.NotificationsActive,
                ) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    ) {
                        Text(if (notificationAccess) "Zugriff aktiv" else "Zugriff öffnen")
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Music Capsule starten", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Klein: Cover + farbige FFT-Balken. Antippen: großer Hanf-/Neon-Visualizer. Ziehen: Position ändern. Im großen Modus unten Vorheriger, Play/Pause und Nächster.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = ::startCapsule) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start")
                            }
                            OutlinedButton(
                                onClick = {
                                    MusicCapsuleOverlayService.stop(context)
                                    status = "Music Capsule wird gestoppt"
                                },
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                        Text(
                            if (runtime.running) "LIVE · ${runtime.message}" else runtime.message,
                            color = if (runtime.running) Color(0xFF55FFD0) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("HyperOS-Kompatibilitätsmodus", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Nur benutzen, wenn die Kapsel sichtbar ist, aber die Balken trotz Musik still bleiben. Dabei fragt Android einmal nach Bildschirm-/Audiofreigabe; SmartIR verwendet nur erlaubtes internes Medienaudio und speichert nichts.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!audioPermission) {
                                        requestAudioPermissions()
                                        status = "Zuerst Audio erlauben"
                                    } else {
                                        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                                    }
                                },
                            ) {
                                Text("Capture-Fallback")
                            }
                            OutlinedButton(
                                onClick = {
                                    context.startService(
                                        Intent(context, LiveAudioCaptureService::class.java).apply {
                                            action = LiveAudioCaptureService.ACTION_STOP
                                        },
                                    )
                                    status = "Kompatibilitäts-Capture wird gestoppt"
                                },
                            ) {
                                Text("Fallback Stop")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    status,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CapsulePreview(runtime: MusicCapsuleSnapshot) {
    val transition = rememberInfiniteTransition(label = "capsule-preview")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_300),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF070A16), Color(0xFF1A0C25), Color(0xFF041A1F)),
                    ),
                    shape = RoundedCornerShape(30.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF67FFD1), Color(0xFF0B5B4D), Color(0xFF07101B)),
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(31.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        runtime.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        runtime.artist.ifBlank { "SmartIR Neon Capsule" },
                        color = Color(0xFFBDCADC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Canvas(modifier = Modifier.size(width = 92.dp, height = 52.dp)) {
                    val bars = 7
                    val gap = size.width * 0.055f
                    val barWidth = (size.width - gap * (bars - 1)) / bars
                    repeat(bars) { index ->
                        val runtimeLevel = runtime.levels.getOrNull(index * 2) ?: 0f
                        val demo = ((sin((phase * 2f * Math.PI + index * 0.72).toFloat()) + 1f) / 2f)
                        val level = if (runtime.signal > 0.01f) runtimeLevel else 0.18f + demo * 0.58f
                        val height = size.height * level.coerceIn(0.08f, 1f)
                        val x = index * (barWidth + gap)
                        drawLine(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.hsv((130f + index * 34f) % 360f, 0.78f, 1f),
                                    Color.hsv((190f + index * 36f) % 360f, 0.88f, 0.84f),
                                ),
                            ),
                            start = Offset(x + barWidth / 2f, size.height),
                            end = Offset(x + barWidth / 2f, size.height - height),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    subtitle: String,
    ready: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (ready) Color(0xFF55FFD0) else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (ready) "✓" else "!",
                    color = if (ready) Color(0xFF55FFD0) else Color(0xFFFFC857),
                    fontWeight = FontWeight.Bold,
                )
            }
            content()
        }
    }
}
