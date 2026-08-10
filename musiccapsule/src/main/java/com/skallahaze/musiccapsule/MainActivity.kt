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
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var projectionManager: MediaProjectionManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var previewView: NeonPreviewView
    private lateinit var statusView: TextView
    private lateinit var overlayStateView: TextView
    private lateinit var notificationStateView: TextView
    private lateinit var audioStateView: TextView

    private lateinit var autoTuneButton: Button
    private lateinit var starButton: Button
    private lateinit var livePatternButton: Button
    private lateinit var endpointButton: Button
    private lateinit var motionButton: Button
    private lateinit var trailButton: Button
    private lateinit var beatFxButton: Button
    private lateinit var flowButton: Button
    private lateinit var visualButton: Button
    private lateinit var widgetButton: Button
    private lateinit var edgeButton: Button
    private lateinit var intensityButton: Button
    private lateinit var opacityButton: Button
    private lateinit var patternButton: Button
    private lateinit var sourceButton: Button
    private lateinit var lockScreenButton: Button
    private lateinit var stageStyleButton: Button
    private lateinit var stageContentButton: Button
    private lateinit var stageStripeButton: Button
    private lateinit var stageAwakeButton: Button

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, 300L)
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
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(2, 4, 13), Color.rgb(24, 4, 39), Color.rgb(2, 26, 31)),
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(32))
        }
        scroll.addView(root)

        root.addView(text("MUSIC CAPSULE", 11f, Color.rgb(91, 255, 210), bold = true).apply {
            letterSpacing = .18f
        })
        root.addView(text("Visual Fusion ${BuildConfig.VERSION_NAME}", 31f, Color.WHITE, bold = true).apply {
            setPadding(0, dp(3), 0, 0)
        })
        root.addView(text(
            "${XiaomiDisplayProfile.diagnosticLabel(this)} · Sync Learning · Musical Bands",
            12.2f,
            Color.rgb(170, 195, 226),
        ).apply { setPadding(0, dp(4), 0, dp(13)) })

        previewView = NeonPreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(282),
            ).also { it.bottomMargin = dp(15) }
        }
        root.addView(previewView)

        root.addView(autoCard())
        root.addView(liveFusionCard())
        root.addView(stageCard())
        root.addView(manualCard())
        root.addView(sourceCard())
        root.addView(permissionCard())

        val controlRow = horizontalRow().apply { setPadding(0, dp(2), 0, dp(12)) }
        controlRow.addView(primaryButton("LIVE STARTEN") { startEverything() }, weightedButtonParams(52))
        controlRow.addView(space(dp(10)))
        controlRow.addView(outlineButton("STOP") { stopEverything() }, weightedButtonParams(52))
        root.addView(controlRow)

        statusView = text("Bereit", 12.7f, Color.rgb(101, 255, 208), bold = true).apply {
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = gradientRounded(
                intArrayOf(Color.rgb(15, 19, 32), Color.rgb(29, 8, 42), Color.rgb(5, 29, 31)),
                dp(20).toFloat(),
                Color.rgb(71, 83, 112),
                dp(1).toFloat(),
            )
        }
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(14) },
        )

        root.addView(sectionCard("Warum nicht zu viel übereinander?", Color.rgb(105, 255, 205)).apply {
            addView(text(
                "Normal und Auto zeigen meistens nur ein Mittelmuster. Ein zweites darf nur bei einem kräftigen Beat kurz dazukommen. Stage und Haupt-LIVE nutzen dieselbe Sync-Uhr, damit es sichtbar bleibt ohne das Bild zuzudecken.",
                13.2f,
                Color.rgb(212, 222, 240),
            ))
        })

        refreshButtons()
        return scroll
    }

    private fun autoCard(): View = sectionCard("Smart Auto Tune", Color.rgb(255, 82, 218)).apply {
        addView(text(
            "Auto Lernen erkennt Tempo, Rhythmus-Sicherheit, Energie und Bassanteil. Striche, Muster und Farbtempo greifen auf dieselbe gelernte Takt-Uhr zu.",
            13.3f,
            Color.rgb(212, 222, 240),
        ).apply { setPadding(0, dp(6), 0, dp(10)) })

        autoTuneButton = neonButton("Auto Tune: Auto Lernen", Color.rgb(241, 79, 255)) {
            val next = CapsulePreferences.autoTuneMode(this@MainActivity).next()
            CapsulePreferences.setAutoTuneMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        addView(autoTuneButton, fullButtonParams())
    }

    private fun liveFusionCard(): View = sectionCard("Haupt-LIVE · Visual Fusion", Color.rgb(78, 224, 255)).apply {
        addView(text(
            "LOW führt die Seitenstriche, MID/HIGH führen obere und untere Details. Beat Auto mischt die bisherigen Formen mit Seitenwellen, Flat Bars, Serial Dots, Mirror Rain, Fire Dance, Strings, Bubbles, Snow und Glow Pulse.",
            13.3f,
            Color.rgb(212, 222, 240),
        ).apply { setPadding(0, dp(6), 0, dp(10)) })

        val patternRow = horizontalRow()
        livePatternButton = neonButton("LIVE-Muster: Auto", Color.rgb(255, 99, 220)) {
            val next = VisualTuningPreferences.livePatternMode(this@MainActivity).next()
            VisualTuningPreferences.setLivePatternMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        endpointButton = neonButton("Endpunkte: Stark", Color.rgb(255, 190, 71)) {
            val next = VisualTuningPreferences.endpointMode(this@MainActivity).next()
            VisualTuningPreferences.setEndpointMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        patternRow.addView(livePatternButton, weightedButtonParams())
        patternRow.addView(space(dp(9)))
        patternRow.addView(endpointButton, weightedButtonParams())
        addView(patternRow)

        visualButton = neonButton("Visual: Voll", Color.rgb(89, 255, 167)) {
            val next = CapsulePreferences.visualLayerMode(this@MainActivity).next()
            CapsulePreferences.setVisualLayerMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        addView(visualButton, fullButtonParams(topMargin = 9))

        val row = horizontalRow().apply { setPadding(0, dp(9), 0, 0) }
        edgeButton = neonButton("Paneele: AN", Color.rgb(71, 255, 166)) {
            val next = !CapsulePreferences.edgePanelsEnabled(this@MainActivity)
            CapsulePreferences.setEdgePanelsEnabled(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        widgetButton = neonButton("Widget: Kleine Kapsel", Color.rgb(78, 198, 255)) {
            val next = CapsulePreferences.displayMode(this@MainActivity).next()
            CapsulePreferences.setDisplayMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        row.addView(edgeButton, weightedButtonParams())
        row.addView(space(dp(9)))
        row.addView(widgetButton, weightedButtonParams())
        addView(row)

        val colorRow = horizontalRow().apply { setPadding(0, dp(9), 0, 0) }
        intensityButton = neonButton("Farbhelligkeit: 135%", Color.rgb(238, 79, 255)) {
            CapsulePreferences.nextIntensity(this@MainActivity)
            applyLiveSettings()
            refreshButtons()
        }
        opacityButton = neonButton("Transparenz: 88%", Color.rgb(88, 221, 255)) {
            VisualTuningPreferences.nextOpacity(this@MainActivity)
            applyLiveSettings()
            refreshButtons()
        }
        colorRow.addView(intensityButton, weightedButtonParams())
        colorRow.addView(space(dp(9)))
        colorRow.addView(opacityButton, weightedButtonParams())
        addView(colorRow)

        lockScreenButton = neonButton("Sperrbildschirm: AN", Color.rgb(255, 104, 196)) {
            val next = !CapsulePreferences.lockScreenEnabled(this@MainActivity)
            CapsulePreferences.setLockScreenEnabled(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        addView(lockScreenButton, fullButtonParams(topMargin = 9))
    }

    private fun stageCard(): View = sectionCard("AMOLED Stage · Fusion", Color.rgb(83, 202, 255)).apply {
        addView(text(
            "Stage behält AMOLED-Schwarz, Aura/Hanfblatt und Muster. Fusion + Striche zeigt jetzt sichtbar alle vier Kanten. Die Mitte atmet oval statt nur größer/kleiner zu zoomen.",
            13.3f,
            Color.rgb(212, 222, 240),
        ).apply { setPadding(0, dp(6), 0, dp(10)) })

        val rowOne = horizontalRow()
        stageStyleButton = neonButton("Stage: Neon Aura", Color.rgb(80, 210, 255)) {
            val next = CapsulePreferences.stageStyle(this@MainActivity).next()
            CapsulePreferences.setStageStyle(this@MainActivity, next)
            refreshButtons()
        }
        stageContentButton = neonButton("Inhalt: Fusion + Striche", Color.rgb(111, 255, 183)) {
            val next = VisualTuningPreferences.stageContentMode(this@MainActivity).next()
            VisualTuningPreferences.setStageContentMode(this@MainActivity, next)
            refreshButtons()
        }
        rowOne.addView(stageStyleButton, weightedButtonParams())
        rowOne.addView(space(dp(9)))
        rowOne.addView(stageContentButton, weightedButtonParams())
        addView(rowOne)

        val rowTwo = horizontalRow().apply { setPadding(0, dp(9), 0, 0) }
        patternButton = neonButton("Muster: Beat Auto", Color.rgb(255, 95, 220)) {
            val next = VisualTuningPreferences.patternMode(this@MainActivity).next()
            VisualTuningPreferences.setPatternMode(this@MainActivity, next)
            refreshButtons()
        }
        stageStripeButton = neonButton("Stage-Striche: Stark", Color.rgb(92, 233, 255)) {
            val next = VisualTuningPreferences.stageStripeMode(this@MainActivity).next()
            VisualTuningPreferences.setStageStripeMode(this@MainActivity, next)
            refreshButtons()
        }
        rowTwo.addView(patternButton, weightedButtonParams())
        rowTwo.addView(space(dp(9)))
        rowTwo.addView(stageStripeButton, weightedButtonParams())
        addView(rowTwo)

        stageAwakeButton = neonButton("Display wach: AN", Color.rgb(255, 103, 201)) {
            val next = !CapsulePreferences.stageKeepAwake(this@MainActivity)
            CapsulePreferences.setStageKeepAwake(this@MainActivity, next)
            refreshButtons()
        }
        addView(stageAwakeButton, fullButtonParams(topMargin = 9))

        addView(primaryButton("STAGE ÖFFNEN") { openStage() }, fullButtonParams(topMargin = 9))
        addView(text(
            "Stage selbst hat zusätzlich direkte Regler für Style, Muster, Inhalt, Striche, Endpunkte, Widget, Transparenz und Farbhelligkeit.",
            11.4f,
            Color.rgb(174, 190, 218),
        ).apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun manualCard(): View = sectionCard("Manuelles Feintuning", Color.rgb(177, 143, 255)).apply {
        addView(text(
            "Bewegung, Nachlauf, Sterne und Beat FX sind nur aktiv, wenn Auto Tune auf AUS steht. Flow und Musterwahl bleiben immer verfügbar.",
            12.5f,
            Color.rgb(190, 201, 225),
        ).apply { setPadding(0, dp(5), 0, dp(9)) })

        starButton = neonButton("Sterne: Mehr Beat", Color.rgb(255, 187, 68)) {
            val next = CapsulePreferences.starMode(this@MainActivity).next()
            CapsulePreferences.setStarMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        addView(starButton, fullButtonParams())

        val rowOne = horizontalRow().apply { setPadding(0, dp(9), 0, 0) }
        motionButton = neonButton("Bewegung: Seidig", Color.rgb(74, 229, 255)) {
            val next = CapsulePreferences.motionProfile(this@MainActivity).next()
            CapsulePreferences.setMotionProfile(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        trailButton = neonButton("Nachlauf: Kurz", Color.rgb(255, 124, 80)) {
            val next = CapsulePreferences.trailMode(this@MainActivity).next()
            CapsulePreferences.setTrailMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        rowOne.addView(motionButton, weightedButtonParams())
        rowOne.addView(space(dp(9)))
        rowOne.addView(trailButton, weightedButtonParams())
        addView(rowOne)

        val rowTwo = horizontalRow().apply { setPadding(0, dp(9), 0, 0) }
        flowButton = neonButton("Flow: Beat Auto", Color.rgb(66, 222, 255)) {
            val next = CapsulePreferences.flowMode(this@MainActivity).next()
            CapsulePreferences.setFlowMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        beatFxButton = neonButton("Beat FX: Brutal", Color.rgb(246, 78, 255)) {
            val next = CapsulePreferences.beatFxMode(this@MainActivity).next()
            CapsulePreferences.setBeatFxMode(this@MainActivity, next)
            applyLiveSettings()
            refreshButtons()
        }
        rowTwo.addView(flowButton, weightedButtonParams())
        rowTwo.addView(space(dp(9)))
        rowTwo.addView(beatFxButton, weightedButtonParams())
        addView(rowTwo)
    }

    private fun sourceCard(): View = sectionCard("Medienquelle", Color.rgb(255, 128, 72)).apply {
        addView(text(
            "YouTube nutzt sauberes internes Audio. SoundCloud läuft rootfrei über den Mikrofon-Fallback; nach Root kann später der direkte Systemmix auch mit Buds kommen.",
            13.2f,
            Color.rgb(212, 222, 240),
        ).apply { setPadding(0, dp(6), 0, dp(9)) })
        sourceButton = neonButton("Quelle: Automatisch", Color.rgb(91, 255, 210)) {
            val next = CapsulePreferences.sourceLock(this@MainActivity).next()
            CapsulePreferences.setSourceLock(this@MainActivity, next)
            requestNotificationRebind()
            applyLiveSettings()
            refreshButtons()
        }
        addView(sourceButton, fullButtonParams())
    }

    private fun permissionCard(): View = sectionCard("Einrichtung", Color.rgb(255, 195, 74)).apply {
        overlayStateView = stateText()
        addView(permissionRow(
            title = "1 · Overlay",
            body = "Rand, Paneele, Muster, Endpunkte und Stage",
            state = overlayStateView,
            action = "Erlauben",
        ) { openOverlaySettings() })

        notificationStateView = stateText()
        addView(permissionRow(
            title = "2 · Titel / Cover",
            body = "Medienquelle zuverlässig erkennen",
            state = notificationStateView,
            action = "Zugriff",
        ) { openNotificationAccess() })

        audioStateView = stateText()
        addView(permissionRow(
            title = "3 · Multi-Band-Audio",
            body = "Bass, Mitten, Höhen und Beats analysieren",
            state = audioStateView,
            action = "Audio starten",
        ) { startEverything() })
    }

    private fun startEverything() {
        if (!Settings.canDrawOverlays(this)) {
            statusView.text = "Zuerst Overlay erlauben und danach erneut LIVE STARTEN drücken."
            openOverlaySettings()
            return
        }
        if (!notificationAccessEnabled()) {
            statusView.text = "Für Titel und die richtige Quelle zuerst Benachrichtigungszugriff aktivieren."
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
        statusView.text = "Gesamter Bildschirm freigeben – Visual Fusion reagiert danach auf den Track."
    }

    private fun stopEverything() {
        CapsuleOverlayService.stop(this)
        startService(Intent(this, PlaybackAnalyzerService::class.java).apply {
            action = PlaybackAnalyzerService.ACTION_STOP
        })
        VisualBeatRuntime.clear()
        SyncLearningRuntime.clear()
        AutoTuneRuntime.clear()
        statusView.text = "Music Capsule, Paneele und Audioanalyse werden gestoppt."
    }

    private fun openStage() {
        if (!Settings.canDrawOverlays(this)) {
            statusView.text = "Für Stage zuerst Overlay erlauben."
            openOverlaySettings()
            return
        }
        CapsuleOverlayService.start(this)
        startActivity(Intent(this, StageActivity::class.java))
    }

    private fun applyLiveSettings() {
        if (CapsuleRuntime.snapshot().overlayRunning) {
            CapsuleOverlayService.applySettings(this)
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
        val visualBeat = VisualBeatRuntime.snapshot()
        val sync = SyncLearningRuntime.snapshot()
        val auto = AutoTuneRuntime.snapshot()
        val bpmValue = when {
            sync.bpm > 0f -> sync.bpm
            visualBeat.bpm > 0f -> visualBeat.bpm
            else -> 0f
        }
        val bpm = if (bpmValue > 0f) "${bpmValue.toInt()} BPM" else "BPM lernt"
        statusView.text = buildString {
            append(if (snapshot.overlayRunning) "VISUAL FUSION LIVE" else "Overlay aus")
            append(" · ")
            append(if (snapshot.analyzerRunning) "FFT LIVE" else "Audioanalyse aus")
            append(" · ${displaySizeLabel()}")
            append("\n${auto.autoMode.label} · ${sync.label}")
            append("\nLIVE-Muster ${VisualTuningPreferences.livePatternMode(this@MainActivity).label}")
            append(" · Endpunkte ${VisualTuningPreferences.endpointMode(this@MainActivity).label}")
            append(" · Stage ${VisualTuningPreferences.stageContentMode(this@MainActivity).label}")
            append(" / ${VisualTuningPreferences.stageStripeMode(this@MainActivity).label}")
            append("\nFarbe ${(CapsulePreferences.neonIntensity(this@MainActivity) * 100).toInt()}%")
            append(" · Transparenz ${(VisualTuningPreferences.opacity(this@MainActivity) * 100).toInt()}%")
            append(" · Muster ${VisualTuningPreferences.patternMode(this@MainActivity).label}")
            append("\nB ${percent(snapshot.bass)}% · M ${percent(snapshot.mid)}% · H ${percent(snapshot.treble)}%")
            append(" · Beat ${percent(maxOf(visualBeat.pulse, sync.beatStrength))}% · $bpm")
            append("\n")
            append(snapshot.title)
            if (snapshot.artist.isNotBlank()) append(" — ${snapshot.artist}")
            append("\n")
            append(snapshot.message)
        }
        refreshButtons()
    }

    private fun refreshButtons() {
        if (!::autoTuneButton.isInitialized) return
        val autoMode = CapsulePreferences.autoTuneMode(this)
        autoTuneButton.text = "Auto Tune: ${autoMode.label}"
        starButton.text = "Sterne: ${CapsulePreferences.starMode(this).label}"
        livePatternButton.text = "LIVE-Muster: ${VisualTuningPreferences.livePatternMode(this).label}"
        endpointButton.text = "Endpunkte: ${VisualTuningPreferences.endpointMode(this).label}"
        visualButton.text = "Visual: ${CapsulePreferences.visualLayerMode(this).label}"
        widgetButton.text = "Widget: ${CapsulePreferences.displayMode(this).label}"
        edgeButton.text = "Paneele: ${if (CapsulePreferences.edgePanelsEnabled(this)) "AN" else "AUS"}"
        intensityButton.text = "Farbhelligkeit: ${(CapsulePreferences.neonIntensity(this) * 100).toInt()}%"
        opacityButton.text = "Transparenz: ${(VisualTuningPreferences.opacity(this) * 100).toInt()}%"
        patternButton.text = "Muster: ${VisualTuningPreferences.patternMode(this).label}"
        stageStyleButton.text = "Stage: ${CapsulePreferences.stageStyle(this).label}"
        stageContentButton.text = "Inhalt: ${VisualTuningPreferences.stageContentMode(this).label}"
        stageStripeButton.text = "Stage-Striche: ${VisualTuningPreferences.stageStripeMode(this).label}"
        stageAwakeButton.text = "Display wach: ${if (CapsulePreferences.stageKeepAwake(this)) "AN" else "AUS"}"
        sourceButton.text = "Quelle: ${CapsulePreferences.sourceLock(this).label}"
        lockScreenButton.text = "Sperrbildschirm: ${if (CapsulePreferences.lockScreenEnabled(this)) "AN" else "AUS"}"
        motionButton.text = "Bewegung: ${CapsulePreferences.motionProfile(this).label}"
        trailButton.text = "Nachlauf: ${CapsulePreferences.trailMode(this).label}"
        beatFxButton.text = "Beat FX: ${CapsulePreferences.beatFxMode(this).label}"
        flowButton.text = "Flow: ${CapsulePreferences.flowMode(this).label}"

        val manual = autoMode == AutoTuneMode.OFF
        listOf(motionButton, trailButton, beatFxButton, starButton).forEach { button ->
            button.isEnabled = manual
            button.alpha = if (manual) 1f else .48f
        }
        flowButton.isEnabled = true
        flowButton.alpha = 1f
        livePatternButton.isEnabled = true
        livePatternButton.alpha = 1f
        patternButton.isEnabled = true
        patternButton.alpha = 1f

        previewView.setConfig(
            CapsulePreferences.displayMode(this),
            CapsulePreferences.edgePanelsEnabled(this),
            CapsulePreferences.neonIntensity(this),
            CapsulePreferences.sourceLock(this),
        )
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
            statusView.text = "LIVE – Musik starten. Sync Learning koppelt Striche und Muster an denselben Takt."
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
    ): View = LinearLayout(this).apply {
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

    private fun sectionCard(title: String, accent: Int): LinearLayout = LinearLayout(this).apply {
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

    private fun horizontalRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun stateText(): TextView = text("!", 17f, Color.rgb(255, 195, 74), bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(Color.rgb(37, 40, 55), dp(14).toFloat())
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = 0
        minWidth = 0
        setTextColor(Color.rgb(4, 10, 16))
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = gradientRounded(
            intArrayOf(Color.rgb(74, 255, 187), Color.rgb(71, 202, 255), Color.rgb(219, 77, 255)),
            dp(23).toFloat(),
        )
        setOnClickListener { onClick() }
    }

    private fun neonButton(label: String, accent: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = 0
        minWidth = 0
        setTextColor(Color.WHITE)
        textSize = 11.8f
        background = rounded(
            Color.rgb(18, 21, 34),
            dp(20).toFloat(),
            Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent)),
            dp(1).toFloat(),
        )
        setOnClickListener { onClick() }
    }

    private fun outlineButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = 0
        minWidth = 0
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

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun rounded(
        fill: Int,
        radius: Float,
        stroke: Int? = null,
        strokeWidth: Float = 0f,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(fill)
        if (stroke != null && strokeWidth > 0f) setStroke(strokeWidth.toInt(), stroke)
    }

    private fun gradientRounded(
        colors: IntArray,
        radius: Float,
        stroke: Int? = null,
        strokeWidth: Float = 0f,
    ): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        if (stroke != null && strokeWidth > 0f) setStroke(strokeWidth.toInt(), stroke)
    }

    private fun weightedButtonParams(height: Int = 48): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(height), 1f)

    private fun fullButtonParams(topMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            this.topMargin = dp(topMargin)
        }

    private fun space(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun displaySizeLabel(): String {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = manager.currentWindowMetrics.bounds
            return "${bounds.width()}×${bounds.height()}"
        }
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
        return "${metrics.widthPixels}×${metrics.heightPixels}"
    }

    private fun percent(value: Float): Int = (value.coerceIn(0f, 1f) * 100f).toInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_AUDIO_PERMISSIONS = 7101
        private const val REQUEST_MEDIA_PROJECTION = 7102
    }
}
