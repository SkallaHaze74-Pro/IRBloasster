package com.skallahaze.musiccapsule

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Dedicated AMOLED-black music stage for a phone lying on a table. */
class StageActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var backgroundView: StageBackgroundView
    private lateinit var fusionView: FusionOverlayView
    private lateinit var controls: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var styleButton: Button
    private lateinit var patternButton: Button
    private lateinit var contentButton: Button
    private lateinit var stripeButton: Button
    private lateinit var endpointButton: Button
    private lateinit var widgetButton: Button
    private lateinit var opacityButton: Button
    private lateinit var brightnessButton: Button
    private lateinit var awakeButton: Button
    private var previousVisualMode: VisualLayerMode? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val snapshot = CapsuleRuntime.snapshot()
            fusionView.setSnapshot(snapshot, CapsulePreferences.neonIntensity(this@StageActivity))
            refreshStageStatus(snapshot)
            mainHandler.postDelayed(this, 180L)
        }
    }
    private val hideControlsRunnable = Runnable { controls.visibility = View.INVISIBLE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

        // The system overlay contributes only the outer frame while the Stage
        // itself owns centre patterns and its now-independent four-edge stripes.
        previousVisualMode = CapsulePreferences.visualLayerMode(this)
        CapsulePreferences.setVisualLayerMode(this, VisualLayerMode.BORDER_ONLY)

        root = buildContent()
        setContentView(root)
        applyKeepAwake()
        CapsuleOverlayService.start(this)
        CapsuleOverlayService.applySettings(this)

        val detector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleControls()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    cycleStyle()
                    showControlsTemporarily()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val next = !CapsulePreferences.stageKeepAwake(this@StageActivity)
                    CapsulePreferences.setStageKeepAwake(this@StageActivity, next)
                    applyKeepAwake()
                    refreshButtons()
                    showControlsTemporarily()
                }
            },
        )
        root.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        backgroundView.setStageStyle(CapsulePreferences.stageStyle(this))
        refreshButtons()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
        showControlsTemporarily()
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.removeCallbacks(hideControlsRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        previousVisualMode?.let { original ->
            CapsulePreferences.setVisualLayerMode(this, original)
            if (CapsuleRuntime.snapshot().overlayRunning) {
                CapsuleOverlayService.applySettings(this)
            }
        }
        previousVisualMode = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun buildContent(): FrameLayout {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }

        backgroundView = StageBackgroundView(this)
        frame.addView(
            backgroundView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        fusionView = FusionOverlayView(this).apply { setStageMode(true) }
        frame.addView(
            fusionView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        titleView = TextView(this).apply {
            text = "Music Capsule Stage 1.6.4"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = rounded(Color.argb(126, 3, 6, 15), dp(22).toFloat())
        }
        frame.addView(
            titleView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                leftMargin = dp(28)
                rightMargin = dp(28)
                topMargin = dp(20)
            },
        )

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = gradientRounded(
                intArrayOf(
                    Color.argb(222, 7, 10, 20),
                    Color.argb(222, 20, 6, 29),
                    Color.argb(222, 3, 23, 26),
                ),
                dp(24).toFloat(),
                Color.argb(180, 90, 231, 255),
                dp(1).toFloat(),
            )
        }

        statusView = TextView(this).apply {
            setTextColor(Color.rgb(109, 255, 214))
            textSize = 10.8f
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), dp(8))
        }
        controls.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val firstRow = horizontalRow()
        styleButton = stageButton("Style") { cycleStyle() }
        patternButton = stageButton("Muster") {
            val next = VisualTuningPreferences.patternMode(this@StageActivity).next()
            VisualTuningPreferences.setPatternMode(this@StageActivity, next)
            fusionView.invalidate()
            refreshButtons()
            showControlsTemporarily()
        }
        firstRow.addView(styleButton, weightedButtonParams())
        firstRow.addView(space(dp(8)))
        firstRow.addView(patternButton, weightedButtonParams())
        controls.addView(firstRow)

        val secondRow = horizontalRow().apply { setPadding(0, dp(8), 0, 0) }
        contentButton = stageButton("Inhalt") {
            val next = VisualTuningPreferences.stageContentMode(this@StageActivity).next()
            VisualTuningPreferences.setStageContentMode(this@StageActivity, next)
            fusionView.invalidate()
            refreshButtons()
            showControlsTemporarily()
        }
        stripeButton = stageButton("Striche") {
            val next = VisualTuningPreferences.stageStripeMode(this@StageActivity).next()
            VisualTuningPreferences.setStageStripeMode(this@StageActivity, next)
            fusionView.invalidate()
            refreshButtons()
            showControlsTemporarily()
        }
        secondRow.addView(contentButton, weightedButtonParams())
        secondRow.addView(space(dp(8)))
        secondRow.addView(stripeButton, weightedButtonParams())
        controls.addView(secondRow)

        val thirdRow = horizontalRow().apply { setPadding(0, dp(8), 0, 0) }
        endpointButton = stageButton("Endpunkte") {
            val next = VisualTuningPreferences.endpointMode(this@StageActivity).next()
            VisualTuningPreferences.setEndpointMode(this@StageActivity, next)
            fusionView.invalidate()
            refreshButtons()
            showControlsTemporarily()
        }
        widgetButton = stageButton("Widget") {
            val next = CapsulePreferences.displayMode(this@StageActivity).next()
            CapsulePreferences.setDisplayMode(this@StageActivity, next)
            CapsuleOverlayService.applySettings(this@StageActivity)
            refreshButtons()
            showControlsTemporarily()
        }
        thirdRow.addView(endpointButton, weightedButtonParams())
        thirdRow.addView(space(dp(8)))
        thirdRow.addView(widgetButton, weightedButtonParams())
        controls.addView(thirdRow)

        val fourthRow = horizontalRow().apply { setPadding(0, dp(8), 0, 0) }
        opacityButton = stageButton("Transparenz") {
            VisualTuningPreferences.nextOpacity(this@StageActivity)
            backgroundView.invalidate()
            fusionView.invalidate()
            CapsuleOverlayService.applySettings(this@StageActivity)
            refreshButtons()
            showControlsTemporarily()
        }
        brightnessButton = stageButton("Farbe") {
            CapsulePreferences.nextIntensity(this@StageActivity)
            CapsuleOverlayService.applySettings(this@StageActivity)
            backgroundView.invalidate()
            fusionView.invalidate()
            refreshButtons()
            showControlsTemporarily()
        }
        fourthRow.addView(opacityButton, weightedButtonParams())
        fourthRow.addView(space(dp(8)))
        fourthRow.addView(brightnessButton, weightedButtonParams())
        controls.addView(fourthRow)

        awakeButton = stageButton("Display wach") {
            val next = !CapsulePreferences.stageKeepAwake(this@StageActivity)
            CapsulePreferences.setStageKeepAwake(this@StageActivity, next)
            applyKeepAwake()
            refreshButtons()
            showControlsTemporarily()
        }
        controls.addView(
            awakeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46),
            ).apply { topMargin = dp(8) },
        )

        val close = stageButton("Stage schließen") { finish() }.apply {
            setTextColor(Color.rgb(255, 206, 236))
        }
        controls.addView(
            close,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46),
            ).apply { topMargin = dp(8) },
        )

        frame.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                leftMargin = dp(18)
                rightMargin = dp(18)
                bottomMargin = dp(22)
            },
        )
        return frame
    }

    private fun refreshStageStatus(snapshot: CapsuleSnapshot) {
        val sync = SyncLearningRuntime.snapshot()
        val auto = AutoTuneRuntime.snapshot()
        titleView.text = buildString {
            append(snapshot.title.ifBlank { "Music Capsule Stage" })
            if (snapshot.artist.isNotBlank()) append("  ·  ${snapshot.artist}")
        }
        statusView.text = buildString {
            append(if (snapshot.analyzerRunning) "LIVE" else "Audioanalyse aus")
            append(" · ${auto.autoMode.label}")
            append(" · ${VisualTuningPreferences.stageContentMode(this@StageActivity).label}")
            append(" · Striche ${VisualTuningPreferences.stageStripeMode(this@StageActivity).label}")
            if (sync.bpm > 0f) append(" · ${sync.bpm.toInt()} BPM")
            append("\nOval-Aura + Muster + vier Kanten folgen derselben Sync-Uhr")
            append("\nTippen: Steuerung · Doppelt: Style · Halten: Display wach")
        }
    }

    private fun cycleStyle() {
        val next = CapsulePreferences.stageStyle(this).next()
        CapsulePreferences.setStageStyle(this, next)
        backgroundView.setStageStyle(next)
        refreshButtons()
    }

    private fun refreshButtons() {
        styleButton.text = "Style: ${CapsulePreferences.stageStyle(this).label}"
        patternButton.text = "Muster: ${VisualTuningPreferences.patternMode(this).label}"
        contentButton.text = "Inhalt: ${VisualTuningPreferences.stageContentMode(this).label}"
        stripeButton.text = "Striche: ${VisualTuningPreferences.stageStripeMode(this).label}"
        endpointButton.text = "Endpunkte: ${VisualTuningPreferences.endpointMode(this).label}"
        widgetButton.text = "Widget: ${CapsulePreferences.displayMode(this).label}"
        opacityButton.text = "Transparenz: ${(VisualTuningPreferences.opacity(this) * 100).toInt()}%"
        brightnessButton.text = "Farbhelligkeit: ${(CapsulePreferences.neonIntensity(this) * 100).toInt()}%"
        awakeButton.text = "Display wach: ${if (CapsulePreferences.stageKeepAwake(this)) "AN" else "AUS"}"
    }

    private fun toggleControls() {
        if (controls.visibility == View.VISIBLE) {
            controls.visibility = View.INVISIBLE
            mainHandler.removeCallbacks(hideControlsRunnable)
        } else {
            showControlsTemporarily()
        }
    }

    private fun showControlsTemporarily() {
        controls.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 4_800L)
    }

    private fun applyKeepAwake() {
        if (CapsulePreferences.stageKeepAwake(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun enterImmersiveMode() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun horizontalRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun stageButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            textSize = 10.8f
            setTextColor(Color.WHITE)
            background = rounded(
                Color.argb(208, 16, 20, 34),
                dp(18).toFloat(),
                Color.argb(210, 83, 213, 255),
                dp(1).toFloat(),
            )
            setOnClickListener { onClick() }
        }
    }

    private fun weightedButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(46), 1f)

    private fun space(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
