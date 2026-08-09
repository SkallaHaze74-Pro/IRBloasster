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
    private lateinit var sourceButton: Button
    private lateinit var modeButton: Button
    private lateinit var edgeButton: Button
    private lateinit var intensityButton: Button
    private lateinit var previewView: NeonPreviewView
    private lateinit var projectionManager: MediaProjectionManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, 420L)
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
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 5, 15), Color.rgb(19, 4, 32), Color.rgb(2, 21, 27)),
            )
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(23), dp(16), dp(32))
        }
        scroll.addView(root)

        root.addView(text("MUSIC CAPSULE", 11f, Color.rgb(91, 255, 210), bold = true).apply {
            letterSpacing = .18f
        })
        root.addView(text("Edge Neon 1.1", 31f, Color.WHITE, bold = true).apply {
            setPadding(0, dp(3), 0, 0)
        })
        root.addView(text("Variante 2 · eigenständige Xiaomi-App · 144-Hz-Anfrage", 13f, Color.rgb(177, 194, 222)).apply {
            setPadding(0, dp(4), 0, dp(14))
        })

        previewView = NeonPreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(280),
            ).also { it.bottomMargin = dp(15) }
        }
        root.addView(previewView)

        root.addView(sectionCard("Medienquelle", Color.rgb(255, 91, 220)).apply {
            addView(text(
                "Der AUTO-Modus bewertet jetzt PLAYING-Status, letzte MediaSession-Aktivität und die frischeste Medienbenachrichtigung. Für absolute Sicherheit kannst du YouTube fest anheften – dann darf eine alte Twitch-Session nicht mehr übernehmen.",
                13.5f,
                Color.rgb(210, 219, 237),
            ).apply { setPadding(0, dp(6), 0, dp(11)) })
            sourceButton = neonButton("Quelle: Automatisch", Color.rgb(91, 255, 210)) {
                val next = CapsulePreferences.sourceLock(this@MainActivity).next()
                CapsulePreferences.setSourceLock(this@MainActivity, next)
                requestNotificationRebind()
                applyLiveSettings()
                refreshDesignButtons()
            }
            addView(sourceButton, fullButtonParams())
        })

        root.addView(sectionCard("Variante 2 · Design", Color.rgb(81, 205, 255)).apply {
            addView(text(
                "Mehrfarbige Neon-Paneele laufen berührungsfrei am kompletten Displayrand. Das obere Widget ist kleiner; im Randmodus bleiben fast nur die leuchtende Kontur und Mini-Balken sichtbar.",
                13.5f,
                Color.rgb(210, 219, 237),
            ).apply { setPadding(0, dp(6), 0, dp(10)) })

            val rowOne = horizontalRow()
            edgeButton = neonButton("Paneele: AN", Color.rgb(71, 255, 166)) {
                val next = !CapsulePreferences.edgePanelsEnabled(this@MainActivity)
                CapsulePreferences.setEdgePanelsEnabled(this@MainActivity, next)
                applyLiveSettings()
                refreshDesignButtons()
            }
            modeButton = neonButton("Widget: Klein", Color.rgb(78, 198, 255)) {
                val next = CapsulePreferences.displayMode(this@MainActivity).next()
                CapsulePreferences.setDisplayMode(this@MainActivity, next)
                applyLiveSettings()
                refreshDesignButtons()
            }
            rowOne.addView(edgeButton, weightedButtonParams())
            rowOne.addView(space(dp(9)))
            rowOne.addView(modeButton, weightedButtonParams())
            addView(rowOne)

            intensityButton = neonButton("Neon: 135%", Color.rgb(238, 79, 255)) {
                CapsulePreferences.nextIntensity(this@MainActivity)
                applyLiveSettings()
                refreshDesignButtons()
            }
            addView(intensityButton, fullButtonParams(topMargin = 9))
        })

        root.addView(sectionCard("Einrichtung", Color.rgb(255, 195, 74)).apply {
            overlayStateView = stateText()
            addView(permissionRow(
                title = "1 · Overlay",
                body = "Kapsel und Seiten-Paneele über anderen Apps",
                state = overlayStateView,
                action = "Erlauben",
            ) { openOverlaySettings() })

            notificationStateView = stateText()
            addView(permissionRow(
                title = "2 · Titel / Cover",
                body = "YouTube, SoundCloud, Spotify und Twitch erkennen",
                state = notificationStateView,
                action = "Zugriff",
            ) { openNotificationAccess() })

            audioStateView = stateText()
            addView(permissionRow(
                title = "3 · Echter Equalizer",
                body = "Internes Medienaudio analysieren; nichts wird gespeichert",
                state = audioStateView,
                action = "Audio starten",
            ) { startEverything() })
        })

        val controlRow = horizontalRow().apply { setPadding(0, dp(2), 0, dp(12)) }
        controlRow.addView(primaryButton("LIVE STARTEN") { startEverything() }, weightedButtonParams(height = 52))
        controlRow.addView(space(dp(10)))
        controlRow.addView(outlineButton("STOP") { stopEverything() }, weightedButtonParams(height = 52))
        root.addView(controlRow)

        statusView = text("Bereit", 13.5f, Color.rgb(101, 255, 208), bold = true).apply {
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = gradientRounded(
                intArrayOf(Color.rgb(15, 19, 32), Color.rgb(25, 10, 38), Color.rgb(5, 27, 29)),
                dp(20).toFloat(),
                Color.rgb(71, 83, 112),
                dp(1).toFloat(),
            )
        }
        root.addView(statusView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also { it.bottomMargin = dp(14) })

        root.addView(sectionCard("Bedienung", Color.rgb(105, 255, 205)).apply {
            addView(text(
                "Klein/Rand antippen → großer Hanf-Visualizer. Ziehen → Widget verschieben. Im großen Modus: links oben zwischen Klein und Rand wechseln, rechts oben schließen; unten Vorheriger, Play/Pause und Nächster.",
                13.5f,
                Color.rgb(210, 219, 237),
            ))
        })

        refreshDesignButtons()
        return scroll
    }

    private fun startEverything() {
        if (!Settings.canDrawOverlays(this)) {
            statusView.text = "Zuerst Overlay erlauben und danach erneut LIVE STARTEN drücken."
            openOverlaySettings()
            return
        }
        if (!notificationAccessEnabled()) {
            statusView.text = "Für die richtige YouTube-/SoundCloud-Anzeige zuerst Benachrichtigungszugriff aktivieren."
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
        statusView.text = "Systemfreigabe bestätigen – danach laufen Widget und Edge-Paneele zum echten Medienaudio."
    }

    private fun stopEverything() {
        CapsuleOverlayService.stop(this)
        startService(Intent(this, PlaybackAnalyzerService::class.java).apply {
            action = PlaybackAnalyzerService.ACTION_STOP
        })
        statusView.text = "Music Capsule, Paneele und Audioanalyse werden gestoppt."
    }

    private fun applyLiveSettings() {
        if (CapsuleRuntime.snapshot().overlayRunning) {
            CapsuleOverlayService.applySettings(this)
        }
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
        overlayStateView.text = if (overlay) "✓" else "!"
        overlayStateView.setTextColor(if (overlay) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))
        notificationStateView.text = if (notification) "✓" else "!"
        notificationStateView.setTextColor(if (notification) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))
        audioStateView.text = if (audio) "✓" else "!"
        audioStateView.setTextColor(if (audio) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))

        val snapshot = CapsuleRuntime.snapshot()
        val sourceLock = CapsulePreferences.sourceLock(this)
        statusView.text = buildString {
            append(if (snapshot.overlayRunning) "EDGE NEON LIVE" else "Overlay aus")
            append(" · ")
            append(if (snapshot.analyzerRunning) "FFT LIVE" else "Audioanalyse aus")
            append(" · Quelle ${sourceLock.label}\n")
            append(snapshot.title)
            if (snapshot.artist.isNotBlank()) append(" — ${snapshot.artist}")
            append("\n")
            append(snapshot.message)
        }
        refreshDesignButtons()
    }

    private fun refreshDesignButtons() {
        if (!::sourceButton.isInitialized) return
        val source = CapsulePreferences.sourceLock(this)
        val mode = CapsulePreferences.displayMode(this)
        val edge = CapsulePreferences.edgePanelsEnabled(this)
        val intensity = CapsulePreferences.neonIntensity(this)
        sourceButton.text = "Quelle: ${source.label}"
        modeButton.text = "Widget: ${if (mode == CapsuleDisplayMode.RIM) "Nur Rand" else "Klein"}"
        edgeButton.text = "Paneele: ${if (edge) "AN" else "AUS"}"
        intensityButton.text = "Neon: ${(intensity * 100).toInt()}%"
        previewView.setConfig(mode, edge, intensity, source)
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
            statusView.text = "LIVE – Musik starten. In AUTO gewinnt jetzt die frischeste aktive Session; YouTube kann zusätzlich fest angeheftet werden."
        } else {
            statusView.text = "Audiofreigabe abgebrochen."
        }
    }

    private fun permissionRow(
        title: String,
        body: String,
        state: TextView,
        action: String,
        onClick: () -> Unit,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))

            addView(state, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = dp(9)
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 14.5f, Color.WHITE, bold = true))
                addView(text(body, 11.5f, Color.rgb(171, 187, 213)))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(outlineButton(action, onClick), LinearLayout.LayoutParams(dp(96), dp(42)).apply {
                marginStart = dp(8)
            })
        }
    }

    private fun sectionCard(title: String, accent: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = gradientRounded(
                intArrayOf(Color.rgb(20, 23, 36), Color.rgb(28, 10, 40), Color.rgb(6, 29, 31)),
                dp(23).toFloat(),
                Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)),
                dp(1).toFloat(),
            )
            addView(text(title, 18f, accent, bold = true))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(14) }
        }
    }

    private fun horizontalRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun stateText(): TextView = text("!", 17f, Color.rgb(255, 195, 74), bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(Color.rgb(37, 40, 55), dp(14).toFloat())
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.rgb(4, 10, 16))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = gradientRounded(
                intArrayOf(Color.rgb(74, 255, 187), Color.rgb(71, 202, 255), Color.rgb(219, 77, 255)),
                dp(23).toFloat(),
            )
            setOnClickListener { onClick() }
        }
    }

    private fun neonButton(label: String, accent: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            background = rounded(
                Color.rgb(18, 21, 34),
                dp(20).toFloat(),
                Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent)),
                dp(1).toFloat(),
            )
            setOnClickListener { onClick() }
        }
    }

    private fun outlineButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.rgb(205, 215, 255))
            textSize = 12.5f
            background = rounded(
                Color.rgb(20, 23, 35),
                dp(20).toFloat(),
                Color.rgb(104, 118, 155),
                dp(1).toFloat(),
            )
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

    private fun rounded(
        fill: Int,
        radius: Float,
        stroke: Int? = null,
        strokeWidth: Float = 0f,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (stroke != null && strokeWidth > 0f) setStroke(strokeWidth.toInt(), stroke)
        }
    }

    private fun gradientRounded(
        colors: IntArray,
        radius: Float,
        stroke: Int? = null,
        strokeWidth: Float = 0f,
    ): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            if (stroke != null && strokeWidth > 0f) setStroke(strokeWidth.toInt(), stroke)
        }
    }

    private fun weightedButtonParams(height: Int = 48): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(height), 1f)
    }

    private fun fullButtonParams(topMargin: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            this.topMargin = dp(topMargin)
        }
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
