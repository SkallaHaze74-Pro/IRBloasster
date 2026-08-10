package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Minimal main-LIVE style requested from the Xiaomi recordings:
 * only short RGB spectrum stripes around all four display edges.
 * No solid frame, centre patterns, stars, symbols or shockwaves.
 *
 * 1.6 Sync Fix: bar attack/release and hue travel now follow learned tempo;
 * bright endpoint dots are rendered directly here so they remain visible even
 * when the heavier Fusion layer is disabled by the "Nur Striche" mode.
 */
class StripesOnlyView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val profileScale = XiaomiDisplayProfile.visualScale(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val targetLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val displayLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

    private var snapshot = CapsuleRuntime.snapshot()
    private var intensity = 1.35f
    private var enabled = false
    private var lastFrameNanos = 0L
    private var huePhase = 0f
    private var hueVelocity = 0f
    private var beatPulse = 0f
    private var tempoFactor = 1f

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

        updateTempo(dt)

        val attackRate = 31f + tempoFactor * 24f
        val releaseRate = 10.5f + tempoFactor * 5.5f
        val attack = 1f - exp(-dt * attackRate)
        val release = 1f - exp(-dt * releaseRate)
        for (index in displayLevels.indices) {
            val target = targetLevels[index]
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
        }

        val visualBeat = VisualBeatRuntime.snapshot()
        beatPulse = max(max(snapshot.beat, visualBeat.pulse), beatPulse * exp(-dt * (7.2f + tempoFactor * 1.8f)))

        // Slow base rotation with BPM-coupled acceleration. Beat adds a pulse,
        // but does not spin the colors several times faster than the bars.
        val desiredHueSpeed =
            4.5f +
                (tempoFactor - .72f).coerceAtLeast(0f) * 26f +
                snapshot.treble * 16f +
                beatPulse * 34f
        hueVelocity += (desiredHueSpeed - hueVelocity) * (1f - exp(-dt * 5.5f))
        huePhase = (huePhase + dt * hueVelocity) % 360f

        drawHorizontalStripes(canvas, top = true)
        drawHorizontalStripes(canvas, top = false)
        drawVerticalStripes(canvas, left = true)
        drawVerticalStripes(canvas, left = false)

        postInvalidateOnAnimation()
    }

    private fun updateTempo(dt: Float) {
        val visualBeat = VisualBeatRuntime.snapshot()
        val auto = AutoTuneRuntime.snapshot()
        val bpm = when {
            visualBeat.bpm in 55f..220f -> visualBeat.bpm
            auto.bpm in 55f..220f -> auto.bpm
            else -> 0f
        }
        val target = if (bpm > 0f) {
            (bpm / 120f).coerceIn(.72f, 1.48f)
        } else {
            (.88f + snapshot.spectralFlux * .24f + snapshot.treble * .12f).coerceIn(.78f, 1.26f)
        }
        tempoFactor += (target - tempoFactor) * (1f - exp(-dt * 2.6f))
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
            val level = max(.018f, displayLevels[band].pow(.63f))
            val beatWave = abs(
                sinf(progress * PI.toFloat() * 5f + huePhase * .018f),
            ) * beatPulse
            val length = dp(1.5f) + level * dp(9.4f) * intensity + beatWave * dp(5.8f)
            val x = inset + index * gap
            val hue = huePhase + progress * 430f + if (top) 0f else 165f
            paint.strokeWidth = dp(.72f) + level * dp(1.55f)
            paint.color = hsv(
                hue,
                .97f,
                1f,
                .22f + level * .74f + beatPulse * .08f,
            )
            canvas.drawLine(x, baseY, x, baseY + direction * length, paint)

            if (VisualTuningPreferences.endpointMode(context) != EndpointMode.OFF && index % 3 == 0) {
                drawEndpointDot(
                    canvas = canvas,
                    x = x,
                    y = baseY + direction * length,
                    hue = hue,
                    level = level,
                    beat = beatPulse,
                    horizontal = true,
                )
            }
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
            val level = max(.018f, displayLevels[band].pow(.61f))
            val length = dp(2.5f) + level * dp(27.5f) * intensity + beatPulse * dp(5.2f)
            val y = inset + index * gap
            val hue = huePhase + progress * 520f + if (left) 42f else 218f
            paint.strokeWidth = dp(.82f) + level * dp(1.95f)
            paint.color = hsv(
                hue,
                .98f,
                1f,
                .22f + level * .75f + beatPulse * .10f,
            )
            val endX = baseX + direction * length
            canvas.drawLine(baseX, y, endX, y, paint)

            if (VisualTuningPreferences.endpointMode(context) != EndpointMode.OFF) {
                drawEndpointDot(
                    canvas = canvas,
                    x = endX,
                    y = y,
                    hue = hue,
                    level = level,
                    beat = beatPulse,
                    horizontal = false,
                )
            }
        }
    }

    private fun drawEndpointDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        hue: Float,
        level: Float,
        beat: Float,
        horizontal: Boolean,
    ) {
        val mode = VisualTuningPreferences.endpointMode(context)
        if (mode == EndpointMode.OFF) return
        val strength = mode.strength
        val core = dp(
            (if (horizontal) .46f else .68f) +
                level * (if (horizontal) .85f else 1.28f) +
                beat * (if (horizontal) .36f else .62f),
        ) * strength
        val glow = max(dp(if (horizontal) 1.45f else 2.2f), core * 3.0f)
        val alpha = (.50f + level * .38f + beat * .12f).coerceIn(.48f, 1f)

        dotPaint.shader = RadialGradient(
            x,
            y,
            glow,
            intArrayOf(
                hsv(hue + 20f, .30f, 1f, alpha),
                hsv(hue, .86f, 1f, alpha * .34f),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, glow, dotPaint)
        dotPaint.shader = null
        dotPaint.color = hsv(hue + 22f, .16f, 1f, max(.70f, alpha))
        canvas.drawCircle(x, y, max(dp(.55f), core), dotPaint)
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

    private fun sinf(value: Float): Float = kotlin.math.sin(value.toDouble()).toFloat()

    private fun dp(value: Float): Float = value * density * profileScale
}
