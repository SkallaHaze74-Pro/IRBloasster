package com.skallahaze.musiccapsule

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Small diagnostic launcher for the root-free Visualizer(0) output-mix path. */
class OutputMixProbeActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, 300L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
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
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(2, 4, 13), Color.rgb(28, 5, 39), Color.rgb(2, 28, 32)),
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(30), dp(18), dp(30))
        }
        scroll.addView(root)

        root.addView(text("MUSIC CAPSULE", 12f, Color.rgb(91, 255, 210), true))
        root.addView(text("SYSTEM MIX TEST", 29f, Color.WHITE, true).apply {
            setPadding(0, dp(5), 0, dp(8))
        })
        root.addView(text(
            "Rootfreier Android Output-Mix-Test · keine Bildschirmaufnahme nötig",
            13f,
            Color.rgb(180, 199, 228),
            false,
        ).apply { setPadding(0, 0, 0, dp(18)) })

        root.addView(card().apply {
            addView(text("Was wir damit prüfen", 18f, Color.rgb(92, 224, 255), true))
            addView(text(
                "Android Visualizer Session 0 soll den gesamten Audio-Output-Mix als Waveform + FFT liefern. Wenn dein Xiaomi damit SoundCloud direkt sieht, können Striche, Muster und Sync Learning rootfrei aus dem System-Mix laufen – auch ohne MediaProjection-Capture.",
                13.5f,
                Color.rgb(218, 227, 244),
                false,
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(card().apply {
            addView(text("Testablauf", 18f, Color.rgb(255, 105, 222), true))
            addView(text(
                "1. START SYSTEM MIX drücken\n2. SoundCloud starten und Musik abspielen\n3. Am besten zuerst mit deinen Buds testen\n4. Zurück hierher: ✅ SYSTEM MIX LIVE wäre der Jackpot\n\nDer Test speichert weder Audio noch FFT-Daten.",
                13.5f,
                Color.rgb(218, 227, 244),
                false,
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(primaryButton("START SYSTEM MIX") { startProbe() })
        root.addView(outlineButton("STOP SYSTEM MIX") { stopProbe() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply { topMargin = dp(10) }
        })
        root.addView(outlineButton("OVERLAY ERLAUBEN / PRÜFEN") { openOverlaySettings() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply { topMargin = dp(10) }
        })

        statusView = text("Bereit für SYSTEM MIX Test", 13f, Color.rgb(101, 255, 208), true).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(
                Color.rgb(13, 18, 31),
                dp(20).toFloat(),
                Color.rgb(83, 213, 255),
            )
        }
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) },
        )

        return scroll
    }

    private fun startProbe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        // Avoid two analyzers writing CapsuleRuntime at the same time.
        stopService(Intent(this, PlaybackAnalyzerService::class.java))
        SyncLearningRuntime.clear()
        OutputMixVisualizerService.start(this)

        if (Settings.canDrawOverlays(this)) {
            CapsuleOverlayService.start(this)
        }
        statusView.text = if (Settings.canDrawOverlays(this)) {
            "SYSTEM MIX startet … jetzt SoundCloud/YouTube abspielen"
        } else {
            "SYSTEM MIX startet. Für sichtbare Striche zusätzlich Overlay erlauben."
        }
    }

    private fun stopProbe() {
        OutputMixVisualizerService.stop(this)
        statusView.text = "SYSTEM MIX Test wird gestoppt"
    }

    private fun refreshStatus() {
        val snapshot = CapsuleRuntime.snapshot()
        val sync = SyncLearningRuntime.snapshot()
        val verdict = when (snapshot.source) {
            "output-mix-visualizer-live" -> "✅ OUTPUT-MIX LIEFERT ECHTE DATEN"
            "output-mix-visualizer-zero" -> "⚠️ OUTPUT-MIX LIEFERT NUR NULL/SEHR WENIG"
            "output-mix-visualizer-no-callback" -> "⚠️ KEINE VISUALIZER-CALLBACKS"
            "output-mix-visualizer-probing" -> "⏳ OUTPUT-MIX WIRD GEPRÜFT"
            else -> "Bereit / anderer Analyzer aktiv"
        }
        statusView.text = buildString {
            append(verdict)
            append("\nQuelle: ${snapshot.source}")
            append("\nSignal ${percent(snapshot.signal)}% · Bass ${percent(snapshot.bass)}% · Mitten ${percent(snapshot.mid)}% · Höhen ${percent(snapshot.treble)}%")
            append("\nBeat ${percent(snapshot.beat)}% · ${if (sync.bpm > 0f) "${sync.bpm.toInt()} BPM" else "BPM lernt"}")
            append("\n${sync.label}")
            append("\n\n${snapshot.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startProbe()
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            statusView.text = "RECORD_AUDIO wurde nicht erlaubt – Visualizer(0) darf dann nicht auf den Output-Mix zugreifen."
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

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(
            Color.rgb(18, 22, 36),
            dp(22).toFloat(),
            Color.rgb(75, 109, 145),
        )
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(14) }
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = 0
        setTextColor(Color.rgb(4, 10, 16))
        textSize = 14f
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(74, 255, 187), Color.rgb(71, 202, 255), Color.rgb(219, 77, 255)),
        ).apply { cornerRadius = dp(24).toFloat() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(54),
        )
        setOnClickListener { onClick() }
    }

    private fun outlineButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = 0
        setTextColor(Color.WHITE)
        textSize = 13f
        background = rounded(
            Color.rgb(18, 21, 34),
            dp(22).toFloat(),
            Color.rgb(91, 182, 235),
        )
        setOnClickListener { onClick() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun percent(value: Float): Int = (value.coerceIn(0f, 1f) * 100f).toInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_RECORD_AUDIO = 9201
    }
}
