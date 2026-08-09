package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class EdgePanelOverlayView(context: Context) : View(context), Choreographer.FrameCallback {
    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val leftWavePath = Path()
    private val rightWavePath = Path()
    private val smoothedLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val phaseOffsets = FloatArray(SEGMENTS) { index ->
        ((index * 0.6180339f) % 1f) * (PI * 2.0).toFloat()
    }

    private var rainbowShader: LinearGradient? = null
    private var lastFrameNanos = 0L
    private var colorPhase = 0f
    private var latestSnapshot = CapsuleRuntime.snapshot()
    private var attached = false

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rainbowShader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat(),
            intArrayOf(
                Color.rgb(255, 42, 205),
                Color.rgb(137, 68, 255),
                Color.rgb(33, 179, 255),
                Color.rgb(20, 255, 213),
                Color.rgb(145, 255, 43),
                Color.rgb(255, 218, 43),
                Color.rgb(255, 78, 153),
                Color.rgb(107, 68, 255),
                Color.rgb(17, 214, 255),
            ),
            null,
            Shader.TileMode.MIRROR,
        )
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!attached) return

        val deltaSeconds = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((frameTimeNanos - lastFrameNanos).coerceAtLeast(1L) / 1_000_000_000f)
                .coerceIn(1f / 240f, 1f / 20f)
        }
        lastFrameNanos = frameTimeNanos
        latestSnapshot = CapsuleRuntime.snapshot()

        val response = 1f - exp(-deltaSeconds * 13.5f)
        for (index in smoothedLevels.indices) {
            val target = latestSnapshot.levels.getOrNull(index) ?: 0f
            val factor = if (target > smoothedLevels[index]) response * 1.42f else response * .55f
            smoothedLevels[index] += (target - smoothedLevels[index]) * factor.coerceIn(0f, 1f)
        }

        val signal = latestSnapshot.signal
        val activeSpeed = if (signal > .008f) 23f + signal * 72f else 2.4f
        colorPhase = (colorPhase + deltaSeconds * activeSpeed) % 360f

        if (latestSnapshot.edgePanelsEnabled) {
            invalidate()
        } else if (visibility != INVISIBLE) {
            invalidate()
        }

        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!latestSnapshot.edgePanelsEnabled || width <= 0 || height <= 0) return

        val signal = latestSnapshot.signal
        val active = latestSnapshot.analyzerRunning
        val intensity = latestSnapshot.edgeIntensity
        val baseEnergy = average(smoothedLevels)
        val visualEnergy = if (active) max(baseEnergy, signal * .72f) else .02f
        val centerY = height * .50f
        val edgeInset = dp(5.5f)
        val innerBase = dp(10f)
        val maxBar = dp(33f) * intensity
        val time = colorPhase / 360f * (PI * 2.0).toFloat()

        buildWavePaths(
            centerY = centerY,
            edgeInset = edgeInset,
            innerBase = innerBase,
            visualEnergy = visualEnergy,
            time = time,
            intensity = intensity,
        )

        drawNeonPath(canvas, leftWavePath, intensity)
        drawNeonPath(canvas, rightWavePath, intensity)

        val segmentHeight = height.toFloat() / SEGMENTS
        for (segment in 0 until SEGMENTS) {
            val y = segmentHeight * (segment + .5f)
            val normalizedY = y / height.toFloat()
            val bandIndex = mirroredBandIndex(normalizedY)
            val raw = smoothedLevels.getOrNull(bandIndex) ?: 0f
            val idleFloor = if (active) .045f else .018f
            val level = max(idleFloor, raw.pow(.67f))
            val bulge = waveInset(normalizedY, time, visualEnergy, intensity)
            val barLength = dp(2.5f) + level * maxBar + abs(sin(time * .75f + phaseOffsets[segment])) * dp(2f)
            val hue = (colorPhase + normalizedY * 410f + segment * 2.1f) % 360f
            val alpha = ((.28f + level * .72f) * intensity).coerceIn(.20f, 1f)
            val color = hsv(hue, .91f, 1f, alpha)

            barPaint.color = color
            barPaint.strokeWidth = dp(.9f) + level * dp(2.1f)

            val leftStart = edgeInset + bulge + dp(2f)
            canvas.drawLine(leftStart, y, leftStart + barLength, y, barPaint)

            val rightStart = width - edgeInset - bulge - dp(2f)
            canvas.drawLine(rightStart, y, rightStart - barLength, y, barPaint)

            if (segment % 4 == 0 && level > .16f) {
                val sparkleAlpha = ((level - .12f) * .88f).coerceIn(0f, .72f)
                particlePaint.color = hsv((hue + 35f) % 360f, .72f, 1f, sparkleAlpha)
                val radius = dp(.8f) + level * dp(1.2f)
                val jitter = sin(phaseOffsets[segment] + time * 2.2f) * dp(6f)
                canvas.drawCircle(leftStart + barLength + dp(3f) + jitter, y, radius, particlePaint)
                canvas.drawCircle(rightStart - barLength - dp(3f) - jitter, y, radius, particlePaint)
            }
        }

        drawCornerGlow(canvas, visualEnergy, intensity)
    }

    private fun buildWavePaths(
        centerY: Float,
        edgeInset: Float,
        innerBase: Float,
        visualEnergy: Float,
        time: Float,
        intensity: Float,
    ) {
        leftWavePath.reset()
        rightWavePath.reset()

        val points = 72
        for (index in 0..points) {
            val normalizedY = index / points.toFloat()
            val y = normalizedY * height
            val inset = waveInset(normalizedY, time, visualEnergy, intensity)
            val leftX = edgeInset + innerBase + inset
            val rightX = width - edgeInset - innerBase - inset
            if (index == 0) {
                leftWavePath.moveTo(leftX, y)
                rightWavePath.moveTo(rightX, y)
            } else {
                leftWavePath.lineTo(leftX, y)
                rightWavePath.lineTo(rightX, y)
            }
        }
    }

    private fun waveInset(
        normalizedY: Float,
        time: Float,
        energy: Float,
        intensity: Float,
    ): Float {
        val middleShape = sin(normalizedY * PI.toFloat() * 3.15f + time * .70f)
        val fineShape = sin(normalizedY * PI.toFloat() * 8.5f - time * 1.15f)
        val centerPull = 1f - abs(normalizedY * 2f - 1f)
        return dp(3.5f) +
            (middleShape * dp(8.5f) + fineShape * dp(2.8f)) * (.32f + energy * .72f) * intensity +
            centerPull * dp(4f) * energy
    }

    private fun drawNeonPath(canvas: Canvas, path: Path, intensity: Float) {
        linePaint.shader = rainbowShader

        linePaint.alpha = (34 * intensity).toInt().coerceIn(18, 70)
        linePaint.strokeWidth = dp(17f) * intensity.coerceAtMost(1.35f)
        canvas.drawPath(path, linePaint)

        linePaint.alpha = (92 * intensity).toInt().coerceIn(48, 150)
        linePaint.strokeWidth = dp(6.2f)
        canvas.drawPath(path, linePaint)

        linePaint.alpha = 245
        linePaint.strokeWidth = dp(1.35f)
        canvas.drawPath(path, linePaint)

        linePaint.shader = null
        linePaint.alpha = 255
    }

    private fun drawCornerGlow(canvas: Canvas, energy: Float, intensity: Float) {
        val radius = dp(2.4f) + energy * dp(4.2f)
        val offset = dp(7f)
        val colors = floatArrayOf(colorPhase, colorPhase + 95f, colorPhase + 205f, colorPhase + 310f)
        val points = arrayOf(
            offset to offset,
            (width - offset) to offset,
            offset to (height - offset),
            (width - offset) to (height - offset),
        )
        for (index in points.indices) {
            particlePaint.color = hsv(colors[index] % 360f, .88f, 1f, (.30f + energy * .55f) * intensity)
            canvas.drawCircle(points[index].first, points[index].second, radius, particlePaint)
        }
    }

    private fun mirroredBandIndex(normalizedY: Float): Int {
        val cycle = normalizedY * 2f
        val mirrored = if (cycle <= 1f) cycle else 2f - cycle
        return (mirrored * (smoothedLevels.size - 1)).toInt().coerceIn(0, smoothedLevels.lastIndex)
    }

    private fun average(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        var sum = 0f
        for (value in values) sum += value
        return sum / values.size
    }

    private fun dp(value: Float): Float = value * density

    private fun hsv(
        hue: Float,
        saturation: Float,
        value: Float,
        alpha: Float = 1f,
    ): Int {
        val color = Color.HSVToColor(
            floatArrayOf((hue % 360f + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
        )
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    companion object {
        private const val SEGMENTS = 58
    }
}
