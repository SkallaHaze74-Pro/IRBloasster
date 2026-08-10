package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Minimal main-LIVE style requested from the Xiaomi recordings:
 * only short RGB spectrum stripes around all four display edges.
 * No solid frame, centre patterns, stars, symbols or shockwaves.
 */
class StripesOnlyView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val profileScale = XiaomiDisplayProfile.visualScale(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val targetLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val displayLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

    private var snapshot = CapsuleRuntime.snapshot()
    private var intensity = 1.35f
    private var enabled = false
    private var lastFrameNanos = 0L
    private var huePhase = 0f
    private var beatPulse = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun setSnapshot(value: CapsuleSnapshot, neonIntensity: Float, enabled: Boolean) {
        snapshot = value
        intensity = neonIntensity.coerceIn(.75f, 1.8f)
        this.enabled = enabled
        alpha = VisualTuningPreferences.opacity(context)
        for (index in targetLevels.indices) {
            targetLevels[index] = value.levels.getOrNull(index)?.coerceIn(0f, 1f) ?: 0f
        }
        visibility = if (enabled) VISIBLE else GONE
        if (enabled) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled || width <= 0 || height <= 0) return

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .08f)
        }
        lastFrameNanos = now

        val attack = 1f - exp(-dt * 25f)
        val release = 1f - exp(-dt * 9.5f)
        for (index in displayLevels.indices) {
            val target = targetLevels[index]
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
        }

        beatPulse = max(snapshot.beat, beatPulse * exp(-dt * 6.8f))
        huePhase = (huePhase + dt * (8f + snapshot.treble * 76f + beatPulse * 118f)) % 360f

        drawHorizontalStripes(canvas, top = true)
        drawHorizontalStripes(canvas, top = false)
        drawVerticalStripes(canvas, left = true)
        drawVerticalStripes(canvas, left = false)

        postInvalidateOnAnimation()
    }

    private fun drawHorizontalStripes(canvas: Canvas, top: Boolean) {
        val segments = 76
        val inset = dp(7f)
        val usable = width - inset * 2f
        val gap = usable / max(1f, (segments - 1).toFloat())
        val baseY = if (top) dp(5.5f) else height - dp(5.5f)
        val direction = if (top) 1f else -1f

        repeat(segments) { index ->
            val progress = index / max(1f, (segments - 1).toFloat())
            val band = mirroredBand(progress)
            val level = max(.025f, displayLevels[band].pow(.63f))
            val beatWave = kotlin.math.abs(
                sin(progress * PI.toFloat() * 5f + huePhase * .025f),
            ) * beatPulse
            val length = dp(1.8f) + level * dp(9.6f) * intensity + beatWave * dp(5.4f)
            val x = inset + index * gap
            paint.strokeWidth = dp(.72f) + level * dp(1.55f)
            paint.color = hsv(
                huePhase + progress * 430f + if (top) 0f else 165f,
                .97f,
                1f,
                .25f + level * .72f + beatPulse * .08f,
            )
            canvas.drawLine(x, baseY, x, baseY + direction * length, paint)
        }
    }

    private fun drawVerticalStripes(canvas: Canvas, left: Boolean) {
        val segments = 78
        val inset = dp(6f)
        val usable = height - inset * 2f
        val gap = usable / max(1f, (segments - 1).toFloat())
        val baseX = if (left) dp(5.5f) else width - dp(5.5f)
        val direction = if (left) 1f else -1f

        repeat(segments) { index ->
            val progress = index / max(1f, (segments - 1).toFloat())
            val band = edgeBand(progress)
            val level = max(.025f, displayLevels[band].pow(.61f))
            val length = dp(2.8f) + level * dp(27f) * intensity + beatPulse * dp(4.2f)
            val y = inset + index * gap
            paint.strokeWidth = dp(.82f) + level * dp(1.9f)
            paint.color = hsv(
                huePhase + progress * 520f + if (left) 42f else 218f,
                .98f,
                1f,
                .25f + level * .73f + beatPulse * .09f,
            )
            canvas.drawLine(baseX, y, baseX + direction * length, y, paint)
        }
    }

    private fun mirroredBand(progress: Float): Int {
        val mirrored = if (progress <= .5f) progress * 2f else (1f - progress) * 2f
        return (mirrored * displayLevels.lastIndex)
            .toInt()
            .coerceIn(0, displayLevels.lastIndex)
    }

    private fun edgeBand(progress: Float): Int = when {
        progress < .32f -> (15f - progress / .32f * 5f).toInt().coerceIn(10, 15)
        progress < .72f -> (10f - (progress - .32f) / .40f * 6f).toInt().coerceIn(4, 10)
        else -> (4f - (progress - .72f) / .28f * 4f).toInt().coerceIn(0, 4)
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
        val brightness = (.64f + intensity * .31f).coerceIn(.65f, 1.20f)
        val color = Color.HSVToColor(
            floatArrayOf(
                (hue % 360f + 360f) % 360f,
                saturation.coerceIn(0f, 1f),
                (value * brightness).coerceIn(0f, 1f),
            ),
        )
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun dp(value: Float): Float = value * density * profileScale
}
