package com.skallahaze.musiccapsule

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var overlayStateView: TextView
    private lateinit var notificationStateView: TextView
    private lateinit var audioStateView: TextView
    private lateinit var projectionManager: MediaProjectionManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, 450L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        requestNotificationRebind()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(5, 7, 17))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(30))
        }
        scroll.addView(root)

        root.addView(text("Music Capsule", 30f, Color.WHITE, bold = true))
        root.addView(text("Eigenständige Xiaomi-App · nicht mehr an SmartIR gekoppelt", 14f, Color.rgb(173, 192, 219)).apply {
            setPadding(0, dp(4), 0, dp(18))
        })

        root.addView(infoCard(
            title = "Warum im Video nichts reagiert hat",
            body = "HyperOS hat den globalen Android-Visualizer zwar öffnen lassen, aber nur Nullen geliefert. Diese App benutzt deshalb echtes internes Playback-Capture. Außerdem war im Video Schritt 3 noch gelb: Ohne Benachrichtigungszugriff kann SoundCloud weder Titel noch Cover liefern.",
            accent = Color.rgb(255, 196, 79),
        ))

        overlayStateView = stateText()
        root.addView(actionCard(
            title = "1 · Über anderen Apps anzeigen",
            body = "Damit die Kapsel über SoundCloud, YouTube Music, Spotify und dem Startbildschirm sichtbar bleibt.",
            state = overlayStateView,
            buttonText = "Overlay erlauben",
        ) { openOverlaySettings() })

        notificationStateView = stateText()
        root.addView(actionCard(
            title = "2 · SoundCloud: Cover, Titel und Steuerung",
            body = "Benachrichtigungszugriff ist zwingend für SoundCloud-Metadaten und MediaSession-Steuerung. Die neue Version liest zusätzlich direkt die Medienbenachrichtigung als Fallback.",
            state = notificationStateView,
            buttonText = "Benachrichtigungszugriff öffnen",
        ) { openNotificationAccess() })

        audioStateView = stateText()
        root.addView(actionCard(
            title = "3 · Echter musikgesteuerter Equalizer",
            body = "Einmal Androids Audio-/Bildschirmfreigabe bestätigen. Es wird nur internes Medienaudio analysiert; nichts wird gespeichert, übertragen oder aufgenommen.",
            state = audioStateView,
            buttonText = "Audio + Kapsel starten",
        ) { startEverything() })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        controls.addView(primaryButton("Nur Kapsel") {
            if (!Settings.canDrawOverlays(this)) openOverlaySettings() else CapsuleOverlayService.start(this)
        }, weightedParams())
        controls.addView(space(dp(10)))
        controls.addView(outlineButton("Alles stoppen") { stopEverything() }, weightedParams())
        root.addView(controls)

        statusView = text("Bereit", 14f, Color.rgb(101, 255, 208), bold = true).apply {
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(statusView)

        root.addView(infoCard(
            title = "Bedienung",
            body = "Kleine Kapsel antippen → großer Hanf-/Neon-Visualizer. Ziehen → Position ändern. Im großen Modus: Vorheriger, Play/Pause und Nächster. Die Balken bleiben ohne echtes Audiosignal bewusst ruhig.",
            accent = Color.rgb(100, 208, 255),
        ))

        return scroll
    }

    private fun startEverything() {
        if (!Settings.canDrawOverlays(this)) {
            statusView.text = "Zuerst Overlay erlauben und danach erneut Start drücken."
            openOverlaySettings()
            return
        }
        if (!notificationAccessEnabled()) {
            statusView.text = "Für SoundCloud zuerst Benachrichtigungszugriff aktivieren."
            openNotificationAccess()
            return
        }
        if (!recordAudioAllowed()) {
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
            requestPermissions(permissions.toTypedArray(), REQUEST_AUDIO_PERMISSIONS)
            return
        }

        CapsuleOverlayService.start(this)
        requestNotificationRebind()
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        statusView.text = "Systemfreigabe bestätigen – danach reagiert der Equalizer auf SoundCloud."
    }

    private fun stopEverything() {
        CapsuleOverlayService.stop(this)
        startService(Intent(this, PlaybackAnalyzerService::class.java).apply {
            action = PlaybackAnalyzerService.ACTION_STOP
        })
        statusView.text = "Music Capsule und Audioanalyse werden gestoppt."
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun openNotificationAccess() {
        val component = ComponentName(this, CapsuleNotificationListener::class.java)
        val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        }
        runCatching { startActivity(detailIntent) }
            .onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun requestNotificationRebind() {
        if (!notificationAccessEnabled()) return
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, CapsuleNotificationListener::class.java),
            )
        }
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val notification = notificationAccessEnabled()
        val audio = recordAudioAllowed()
        overlayStateView.text = if (overlay) "✓ Erlaubt" else "! Fehlt"
        overlayStateView.setTextColor(if (overlay) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))
        notificationStateView.text = if (notification) "✓ Aktiv – SoundCloud kann gelesen werden" else "! Fehlt – deshalb ‚Keine Wiedergabe‘"
        notificationStateView.setTextColor(if (notification) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))
        audioStateView.text = if (audio) "✓ RECORD_AUDIO erlaubt" else "! Audiofreigabe fehlt"
        audioStateView.setTextColor(if (audio) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))

        val snapshot = CapsuleRuntime.snapshot()
        statusView.text = buildString {
            append(if (snapshot.overlayRunning) "Kapsel LIVE" else "Kapsel aus")
            append(" · ")
            append(if (snapshot.analyzerRunning) "Audioanalyse LIVE" else "Audioanalyse aus")
            append("\n")
            append(snapshot.title)
            if (snapshot.artist.isNotBlank()) append(" — ${snapshot.artist}")
            append("\n")
            append(snapshot.message)
        }
    }

    private fun notificationAccessEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun recordAudioAllowed(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSIONS && recordAudioAllowed()) {
            startEverything()
        } else if (requestCode == REQUEST_AUDIO_PERMISSIONS) {
            statusView.text = "RECORD_AUDIO wurde nicht erlaubt."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return
        if (resultCode == RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, PlaybackAnalyzerService::class.java).apply {
                    action = PlaybackAnalyzerService.ACTION_START
                    putExtra(PlaybackAnalyzerService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(PlaybackAnalyzerService.EXTRA_RESULT_DATA, data)
                },
            )
            CapsuleOverlayService.start(this)
            statusView.text = "LIVE – SoundCloud starten. Titel/Cover kommen über Schritt 2, FFT über Playback-Capture."
        } else {
            statusView.text = "Audiofreigabe abgebrochen."
        }
    }

    private fun actionCard(
        title: String,
        body: String,
        state: TextView,
        buttonText: String,
        onClick: () -> Unit,
    ): View {
        val card = cardContainer()
        card.addView(text(title, 19f, Color.WHITE, bold = true))
        card.addView(text(body, 14f, Color.rgb(205, 216, 235)).apply { setPadding(0, dp(7), 0, dp(8)) })
        card.addView(state)
        card.addView(primaryButton(buttonText, onClick).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48))
            params.topMargin = dp(10)
            layoutParams = params
        })
        return card
    }

    private fun infoCard(title: String, body: String, accent: Int): View {
        val card = cardContainer()
        card.addView(text(title, 18f, accent, bold = true))
        card.addView(text(body, 14f, Color.rgb(205, 216, 235)).apply { setPadding(0, dp(7), 0, 0) })
        return card
    }

    private fun cardContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = rounded(Color.rgb(29, 31, 41), dp(24).toFloat(), Color.rgb(54, 60, 76), dp(1).toFloat())
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(14)
            layoutParams = params
        }
    }

    private fun stateText(): TextView = text("Prüfe …", 13f, Color.rgb(255, 195, 74), bold = true)

    private fun primaryButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.rgb(7, 11, 21))
            textSize = 14f
            background = rounded(Color.rgb(112, 130, 255), dp(22).toFloat())
            setOnClickListener { onClick() }
        }
    }

    private fun outlineButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.rgb(190, 202, 255))
            textSize = 14f
            background = rounded(Color.rgb(23, 26, 38), dp(22).toFloat(), Color.rgb(105, 116, 150), dp(1).toFloat())
            setOnClickListener { onClick() }
        }
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null, strokeWidth: Float = 0f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (stroke != null && strokeWidth > 0f) setStroke(strokeWidth.toInt(), stroke)
        }
    }

    private fun weightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(48), 1f)
    }

    private fun space(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_AUDIO_PERMISSIONS = 7101
        private const val REQUEST_MEDIA_PROJECTION = 7102
    }
}
