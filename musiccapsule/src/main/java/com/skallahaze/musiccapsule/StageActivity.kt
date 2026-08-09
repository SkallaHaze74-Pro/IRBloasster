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
    private lateinit var controls: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var styleButton: Button
    private lateinit var visualButton: Button
    private lateinit var widgetButton: Button
    private lateinit var awakeButton: Button

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStageStatus()
            mainHandler.postDelayed(this, 300L)
        }
    }
    private val hideControlsRunnable = Runnable { controls.visibility = View.INVISIBLE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        root = buildContent()
        setContentView(root)
        applyKeepAwake()
        CapsuleOverlayService.start(this)

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

        titleView = TextView(this).apply {
            text = "Music Capsule Stage"
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
            textSize = 11f
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

        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        styleButton = stageButton("Style") { cycleStyle() }
        visualButton = stageButton("Visual") {
            val next = CapsulePreferences.visualLayerMode(this@StageActivity).next()
            CapsulePreferences.setVisualLayerMode(this@StageActivity, next)
            CapsuleOverlayService.applySettings(this@StageActivity)
            refreshButtons()
            showControlsTemporarily()
        }
        firstRow.addView(styleButton, weightedButtonParams())
        firstRow.addView(space(dp(8)))
        firstRow.addView(visualButton, weightedButtonParams())
        controls.addView(firstRow)

        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        widgetButton = stageButton("Widget") {
            val next = CapsulePreferences.displayMode(this@StageActivity).next()
            CapsulePreferences.setDisplayMode(this@StageActivity, next)
            CapsuleOverlayService.applySettings(this@StageActivity)
            refreshButtons()
            showControlsTemporarily()
        }
        awakeButton = stageButton("Wach") {
            val next = !CapsulePreferences.stageKeepAwake(this@StageActivity)
            CapsulePreferences.setStageKeepAwake(this@StageActivity, next)
            applyKeepAwake()
            refreshButtons()
            showControlsTemporarily()
        }
        secondRow.addView(widgetButton, weightedButtonParams())
        secondRow.addView(space(dp(8)))
        secondRow.addView(awakeButton, weightedButtonParams())
        controls.addView(secondRow)

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

    private fun refreshStageStatus() {
        val snapshot = CapsuleRuntime.snapshot()
        val beat = VisualBeatRuntime.snapshot()
        val auto = AutoTuneRuntime.snapshot()
        titleView.text = buildString {
            append(snapshot.title.ifBlank { "Music Capsule Stage" })
            if (snapshot.artist.isNotBlank()) append("  ·  ${snapshot.artist}")
        }
        statusView.text = buildString {
            append(if (snapshot.analyzerRunning) "LIVE" else "Audioanalyse aus")
            append(" · ${auto.autoMode.label}")
            append(" · ${auto.label}")
            if (beat.bpm > 0f) append(" · ${beat.bpm.toInt()} BPM")
            append("\nEinmal tippen: Steuerung · Doppelt: Style · Halten: Display wach")
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
        visualButton.text = "Visual: ${CapsulePreferences.visualLayerMode(this).label}"
        widgetButton.text = "Widget: ${CapsulePreferences.displayMode(this).label}"
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
        mainHandler.postDelayed(hideControlsRunnable, 4_200L)
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

    private fun stageButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            textSize = 11.5f
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
