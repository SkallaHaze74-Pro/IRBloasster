package com.skallahaze.musiccapsule

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var overlayStateView: TextView
    private lateinit var notificationStateView: TextView
    private lateinit var audioStateView: TextView
    private lateinit var sourceStateView: TextView
    private lateinit var previewView: CapsuleDesignPreviewView
    private lateinit var edgeSwitch: Switch
    private lateinit var intensitySeek: SeekBar
    private lateinit var projectionManager: MediaProjectionManager

    private val modeButtons = mutableMapOf<CapsuleMode, Button>()
    private val sourceButtons = mutableMapOf<MediaSourceLock, Button>()
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
            setBackgroundColor(Color.rgb(3, 5, 13))
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17f), dp(24f), dp(17f), dp(32f))
        }
        scroll.addView(root)

        root.addView(text("Music Capsule", 31f, Color.WHITE, bold = true))
        root.addView(text("Neon Edge Panels · Mini Widget · Xiaomi 144 Hz", 14f, Color.rgb(151, 205, 255)).apply {
            setPadding(0, dp(3f), 0, dp(14f))
        })

        previewView = CapsuleDesignPreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(245f),
            )
        }
        root.addView(neonFrame(previewView).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.bottomMargin = dp(14f)
            layoutParams = params
        })

        root.addView(designCard())
        root.addView(sourceCard())
        root.addView(permissionCard())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3f), 0, dp(12f))
        }
        controls.addView(primaryButton("Alles starten") { startEverything() }, weightedParams())
        controls.addView(space(dp(10f)))
        controls.addView(outlineButton("Alles stoppen") { stopEverything() }, weightedParams())
        root.addView(controls)

        statusView = text("Bereit", 13.5f, Color.rgb(101, 255, 208), bold = true).apply {
            setPadding(dp(5f), dp(8f), dp(5f), dp(10f))
        }
        root.addView(statusView)

        root.addView(infoCard(
            title = "Bedienung am Overlay",
            body = "Mini Widget antippen → großer Hanf-/Neon-Visualizer. Rechts auf den kleinen Strich tippen oder das Widget lange halten → nur der leuchtende Rand bleibt. Rand antippen → Mini Widget zurück. Die Seiten-Paneele sind nicht touchbar und stören deine Apps nicht.",
            accent = Color.rgb(103, 232, 249),
        ))

        return scroll
    }

    private fun designCard(): View {
        val card = cardContainer()
        card.addView(text("Design · Variante 2", 20f, Color.rgb(238, 93, 255), bold = true))
        card.addView(text(
            "Mehrfarbige Neon-Paneele links und rechts, kleines Widget oben und ein eigener Nur-Rand-Modus.",
            13.5f,
            Color.rgb(205, 216, 235),
        ).apply { setPadding(0, dp(6f), 0, dp(9f)) })

        edgeSwitch = Switch(this).apply {
            text = "Seiten-Paneele aktiv"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = CapsulePreferences.edgePanelsEnabled(this@MainActivity)
            buttonTintList = ColorStateList.valueOf(Color.rgb(102, 255, 208))
            thumbTintList = ColorStateList.valueOf(Color.rgb(103, 232, 249))
            trackTintList = ColorStateList.valueOf(Color.rgb(88, 64, 145))
            setOnCheckedChangeListener { _, enabled ->
                CapsulePreferences.setEdgePanelsEnabled(this@MainActivity, enabled)
                CapsuleRuntime.updateEdgePanels(enabled, CapsulePreferences.edgeIntensity(this@MainActivity))
                refreshOverlayDesign()
            }
        }
        card.addView(edgeSwitch)

        val refreshRate = displayRefreshRate()
        card.addView(text(
            "Display-VSync: ${refreshRate.toInt()} Hz · Animation interpoliert auf die aktive Bildschirmrate",
            11.5f,
            Color.rgb(158, 184, 218),
        ).apply { setPadding(0, dp(2f), 0, dp(12f)) })

        card.addView(text("Widget-Startansicht", 13f, Color.rgb(101, 255, 208), bold = true))
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, dp(12f))
        }
        val modeLabels = linkedMapOf(
            CapsuleMode.RIM to "Nur Rand",
            CapsuleMode.COMPACT to "Mini",
            CapsuleMode.EXPANDED to "Groß",
        )
        modeLabels.forEach { (mode, label) ->
            val button = choiceButton(label) { selectMode(mode) }
            modeButtons[mode] = button
            modeRow.addView(button, weightedChoiceParams())
            if (mode != CapsuleMode.EXPANDED) modeRow.addView(space(dp(7f)))
        }
        card.addView(modeRow)

        card.addView(text("Neon-Intensität", 13f, Color.rgb(101, 255, 208), bold = true))
        intensitySeek = SeekBar(this).apply {
            max = 100
            progress = (((CapsulePreferences.edgeIntensity(this@MainActivity) - .55f) / 1.10f) * 100f)
                .toInt()
                .coerceIn(0, 100)
            progressTintList = ColorStateList.valueOf(Color.rgb(224, 74, 255))
            thumbTintList = ColorStateList.valueOf(Color.rgb(77, 238, 255))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val intensity = .55f + progress / 100f * 1.10f
                    CapsulePreferences.setEdgeIntensity(this@MainActivity, intensity)
                    CapsuleRuntime.updateEdgePanels(edgeSwitch.isChecked, intensity)
                    previewView.setPreferences(
                        CapsulePreferences.overlayMode(this@MainActivity),
                        edgeSwitch.isChecked,
                        intensity,
                        CapsulePreferences.sourceLock(this@MainActivity),
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    refreshOverlayDesign()
                }
            })
        }
        card.addView(intensitySeek)
        return card
    }

    private fun sourceCard(): View {
        val card = cardContainer()
        card.addView(text("Medienquelle", 20f, Color.rgb(93, 214, 255), bold = true))
        card.addView(text(
            "Auto nutzt jetzt die zuletzt wirklich aktive MediaSession. Falls Twitch trotzdem offen bleibt, kannst du YouTube fest anheften.",
            13.5f,
            Color.rgb(205, 216, 235),
        ).apply { setPadding(0, dp(6f), 0, dp(8f)) })

        sourceStateView = text("Quelle: Auto", 12.5f, Color.rgb(101, 255, 208), bold = true)
        card.addView(sourceStateView)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(9f), dp(4f), dp(3f))
        }
        scroll.addView(row)
        MediaSourceLock.entries.forEachIndexed { index, source ->
            val button = choiceButton(source.label) { selectSource(source) }
            sourceButtons[source] = button
            row.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42f),
            ).apply {
                if (index < MediaSourceLock.entries.lastIndex) marginEnd = dp(8f)
            })
        }
        card.addView(scroll)
        return card
    }

    private fun permissionCard(): View {
        val card = cardContainer()
        card.addView(text("Einrichtung", 20f, Color.rgb(101, 255, 208), bold = true))

        overlayStateView = stateText()
        card.addView(permissionRow(
            title = "1 · Overlay",
            state = overlayStateView,
            buttonText = "Erlauben",
        ) { openOverlaySettings() })

        notificationStateView = stateText()
        card.addView(permissionRow(
            title = "2 · Titel, Cover, YouTube/SoundCloud",
            state = notificationStateView,
            buttonText = "Zugriff",
        ) { openNotificationAccess() })

        audioStateView = stateText()
        card.addView(permissionRow(
            title = "3 · Echter Equalizer",
            state = audioStateView,
            buttonText = "Audio starten",
        ) { startEverything() })
        return card
    }

    private fun permissionRow(
        title: String,
        state: TextView,
        buttonText: String,
        onClick: () -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9f), 0, dp(4f))
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 14.5f, Color.WHITE, bold = true))
            addView(state)
        }
        row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(outlineButton(buttonText, onClick).apply {
            minWidth = 0
            setPadding(dp(14f), 0, dp(14f), 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42f)))
        return row
    }

    private fun selectMode(mode: CapsuleMode) {
        CapsulePreferences.setOverlayMode(this, mode)
        CapsuleRuntime.updateMode(mode)
        updateChoiceStyles()
        refreshOverlayDesign()
    }

    private fun selectSource(source: MediaSourceLock) {
        CapsulePreferences.setSourceLock(this, source)
        CapsuleRuntime.updateSourceLock(source)
        requestNotificationRebind()
        updateChoiceStyles()
        refreshOverlayDesign()
        statusView.text = "Quelle auf ${source.label} gesetzt."
    }

    private fun refreshOverlayDesign() {
        val mode = CapsulePreferences.overlayMode(this)
        val edges = CapsulePreferences.edgePanelsEnabled(this)
        val intensity = CapsulePreferences.edgeIntensity(this)
        val source = CapsulePreferences.sourceLock(this)
        CapsuleRuntime.updateOverlay(
            running = CapsuleRuntime.snapshot().overlayRunning,
            mode = mode,
            edgePanelsEnabled = edges,
            edgeIntensity = intensity,
            sourceLock = source,
        )
        previewView.setPreferences(mode, edges, intensity, source)
        if (Settings.canDrawOverlays(this)) CapsuleOverlayService.refresh(this)
    }

    private fun startEverything() {
        if (!Settings.canDrawOverlays(this)) {
            statusView.text = "Zuerst Overlay erlauben und danach erneut Start drücken."
            openOverlaySettings()
            return
        }
        if (!notificationAccessEnabled()) {
            statusView.text = "Für YouTube/SoundCloud zuerst Benachrichtigungszugriff aktivieren."
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

        refreshOverlayDesign()
        CapsuleOverlayService.start(this)
        requestNotificationRebind()
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        statusView.text = "Systemfreigabe bestätigen – danach reagieren Widget und Seiten-Paneele auf die Musik."
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
        notificationStateView.text = if (notification) "✓ Aktiv" else "! Fehlt – falsche/keine Quelle möglich"
        notificationStateView.setTextColor(if (notification) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))
        audioStateView.text = if (audio) "✓ Audio erlaubt" else "! Audiofreigabe fehlt"
        audioStateView.setTextColor(if (audio) Color.rgb(85, 255, 208) else Color.rgb(255, 195, 74))

        val mode = CapsulePreferences.overlayMode(this)
        val edges = CapsulePreferences.edgePanelsEnabled(this)
        val intensity = CapsulePreferences.edgeIntensity(this)
        val source = CapsulePreferences.sourceLock(this)
        edgeSwitch.isChecked = edges
        val expectedProgress = (((intensity - .55f) / 1.10f) * 100f).toInt().coerceIn(0, 100)
        if (!intensitySeek.isPressed && intensitySeek.progress != expectedProgress) intensitySeek.progress = expectedProgress
        previewView.setPreferences(mode, edges, intensity, source)
        sourceStateView.text = "Quelle: ${source.label}${if (source == MediaSourceLock.AUTO) " · Smart Auto" else " · fest angeheftet"}"
        updateChoiceStyles()

        val snapshot = CapsuleRuntime.snapshot()
        statusView.text = buildString {
            append(if (snapshot.overlayRunning) "Overlay LIVE" else "Overlay aus")
            append(" · ")
            append(if (snapshot.analyzerRunning) "FFT LIVE" else "FFT aus")
            append(" · ${modeName(mode)}")
            append("\n")
            append(snapshot.title)
            if (snapshot.artist.isNotBlank()) append(" — ${snapshot.artist}")
            if (snapshot.packageName.isNotBlank()) append("\n${snapshot.packageName}")
            append("\n")
            append(snapshot.message)
        }
    }

    private fun updateChoiceStyles() {
        val selectedMode = CapsulePreferences.overlayMode(this)
        modeButtons.forEach { (mode, button) -> styleChoiceButton(button, mode == selectedMode) }
        val selectedSource = CapsulePreferences.sourceLock(this)
        sourceButtons.forEach { (source, button) -> styleChoiceButton(button, source == selectedSource) }
    }

    private fun styleChoiceButton(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) Color.rgb(3, 7, 15) else Color.rgb(207, 220, 242))
        button.background = if (selected) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(74, 236, 255), Color.rgb(230, 75, 255)),
            ).apply { cornerRadius = dp(18f).toFloat() }
        } else {
            rounded(
                fill = Color.rgb(20, 23, 36),
                radius = dp(18f).toFloat(),
                stroke = Color.rgb(72, 80, 111),
                strokeWidth = dp(1f).toFloat(),
            )
        }
    }

    private fun notificationAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun recordAudioAllowed(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

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
            statusView.text = "LIVE – Musik starten. Titel/Cover kommen über MediaSession, Paneele und FFT über Playback-Capture."
        } else {
            statusView.text = "Audiofreigabe abgebrochen."
        }
    }

    private fun cardContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17f), dp(16f), dp(17f), dp(16f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(26, 27, 42), Color.rgb(16, 18, 31), Color.rgb(22, 12, 34)),
            ).apply {
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1f), Color.rgb(57, 65, 90))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.bottomMargin = dp(14f)
            layoutParams = params
        }
    }

    private fun neonFrame(child: View): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(38, 223, 255),
                    Color.rgb(112, 72, 255),
                    Color.rgb(238, 65, 255),
                    Color.rgb(44, 255, 188),
                ),
            ).apply { cornerRadius = dp(28f).toFloat() }
            addView(child)
        }
    }

    private fun infoCard(title: String, body: String, accent: Int): View {
        val card = cardContainer()
        card.addView(text(title, 18f, accent, bold = true))
        card.addView(text(body, 13.5f, Color.rgb(205, 216, 235)).apply {
            setPadding(0, dp(7f), 0, 0)
        })
        return card
    }

    private fun stateText(): TextView = text("Prüfe …", 12f, Color.rgb(255, 195, 74), bold = true)

    private fun primaryButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            setTextColor(Color.rgb(5, 8, 17))
            textSize = 14f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(83, 224, 255), Color.rgb(233, 74, 255)),
            ).apply { cornerRadius = dp(22f).toFloat() }
            setOnClickListener { onClick() }
        }
    }

    private fun outlineButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            setTextColor(Color.rgb(202, 215, 244))
            textSize = 13f
            background = rounded(
                Color.rgb(22, 25, 39),
                dp(22f).toFloat(),
                Color.rgb(89, 100, 137),
                dp(1f).toFloat(),
            )
            setOnClickListener { onClick() }
        }
    }

    private fun choiceButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            textSize = 12.5f
            setPadding(dp(15f), 0, dp(15f), 0)
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

    private fun weightedParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(50f), 1f)

    private fun weightedChoiceParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(42f), 1f)

    private fun space(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun displayRefreshRate(): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.mode?.refreshRate ?: 60f
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.refreshRate
        }
    }

    private fun modeName(mode: CapsuleMode): String = when (mode) {
        CapsuleMode.RIM -> "Nur Rand"
        CapsuleMode.COMPACT -> "Mini"
        CapsuleMode.EXPANDED -> "Groß"
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_AUDIO_PERMISSIONS = 7101
        private const val REQUEST_MEDIA_PROJECTION = 7102
    }
}
