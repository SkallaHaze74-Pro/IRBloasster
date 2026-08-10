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
 * Minimal main-LIVE style: only music-driven RGB stripes around all four edges.
 *
 * 1.6.4 keeps the coherent LOW/MID/HIGH roles from 1.6.3, raises stripe
 * visibility slightly, and makes endpoint dots truly belong to their stripe:
 * they are drawn at the exact calculated tip and fade out when that stripe has
 * no real musical energy instead of forming a permanent dotted wall.
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
    private var sync = SyncLearningRuntime.snapshot()
    private var intensity = 1.35f
    private var enabled = false
    private var lastFrameNanos = 0L
    private var beatPulse = 0f
    private var lastSyncBeatSequence = Long.MIN_VALUE

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun setSnapshot(value: CapsuleSnapshot, neonIntensity: Float, enabled: Boolean) {
        snapshot = value
        sync = SyncLearningRuntime.observe(context, value)
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
        sync = SyncLearningRuntime.snapshot()

        if (lastSyncBeatSequence == Long.MIN_VALUE || sync.beatSequence < lastSyncBeatSequence) {
            lastSyncBeatSequence = sync.beatSequence
        }
        if (sync.beatSequence > lastSyncBeatSequence) {
            beatPulse = max(beatPulse, .69f + sync.beatStrength * .31f)
            lastSyncBeatSequence = sync.beatSequence
        }

        val attack = 1f - exp(-dt * sync.attackRate)
        val release = 1f - exp(-dt * sync.releaseRate)
        for (index in displayLevels.indices) {
            val target = targetLevels[index]
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
        }

        val beatDecay = 6.7f + sync.tempoFactor * 3.3f
        beatPulse *= exp(-dt * beatDecay)
        val huePhase = SyncLearningRuntime.hueAt()

        val low = groupAverage(0, 3).pow(.72f)
        val mid = groupAverage(4, 9).pow(.74f)
        val high = groupAverage(10, 15).pow(.72f)

        drawHorizontalStripes(canvas, true, huePhase, low, mid, high)
        drawHorizontalStripes(canvas, false, huePhase, low, mid, high)
        drawVerticalStripes(canvas, true, huePhase, low)
        drawVerticalStripes(canvas, false, huePhase, low)

        postInvalidateOnAnimation()
    }

    private fun drawHorizontalStripes(
        canvas: Canvas,
        top: Boolean,
        huePhase: Float,
        low: Float,
        mid: Float,
        high: Float,
    ) {
        val segments = 76
        val inset = dp(7f)
        val usable = width - inset * 2f
        val gap = usable / max(1f, (segments - 1).toFloat())
        val baseY = if (top) dp(5.5f) else height - dp(5.5f)
        val direction = if (top) 1f else -1f
        val musicalEnvelope = if (top) {
            high * .56f + mid * .34f + low * .10f
        } else {
            mid * .58f + high * .28f + low * .14f
        }

        repeat(segments) { index ->
            val progress = index / max(1f, (segments - 1).toFloat())
            val band = mirroredBand(progress)
            val detail = displayLevels[band].coerceIn(0f, 1f).pow(.63f)
            val level = max(.012f, musicalEnvelope * .86f + detail * .14f)
            val beatWave = abs(
                sinf(progress * PI.toFloat() * 5f + huePhase * .012f),
            ) * beatPulse * (.70f + high * .30f)
            val length = dp(1.7f) + level * dp(10.2f) * intensity + beatWave * dp(5.5f)
            val x = inset + index * gap
            val hue = huePhase + progress * 430f + if (top) 0f else 165f
            paint.strokeWidth = dp(.80f) + level * dp(1.65f)
            paint.color = hsv(
                hue,
                .97f,
                1f,
                (.28f + level * .72f + beatPulse * .10f).coerceIn(.18f, 1f),
            )
            val endY = baseY + direction * length
            canvas.drawLine(x, baseY, x, endY, paint)

            if (VisualTuningPreferences.endpointMode(context) != EndpointMode.OFF && index % 3 == 0) {
                drawEndpointDot(canvas, x, endY, hue, level, beatPulse, horizontal = true)
            }
        }
    }

    private fun drawVerticalStripes(
        canvas: Canvas,
        left: Boolean,
        huePhase: Float,
        low: Float,
    ) {
        val segments = 78
        val inset = dp(6f)
        val usable = height - inset * 2f
        val gap = usable / max(1f, (segments - 1).toFloat())
        val baseX = if (left) dp(5.5f) else width - dp(5.5f)
        val direction = if (left) 1f else -1f

        repeat(segments) { index ->
            val progress = index / max(1f, (segments - 1).toFloat())
            val band = edgeBand(progress)
            val detail = displayLevels[band].coerceIn(0f, 1f).pow(.61f)
            val level = max(.012f, low * .82f + detail * .18f)
            val length = dp(2.8f) + level * dp(29.2f) * intensity + beatPulse * dp(5.1f)
            val y = inset + index * gap
            val hue = huePhase + progress * 520f + if (left) 42f else 218f
            paint.strokeWidth = dp(.92f) + level * dp(2.08f)
            paint.color = hsv(
                hue,
                .98f,
                1f,
                (.29f + level * .71f + beatPulse * .11f).coerceIn(.18f, 1f),
            )
            val endX = baseX + direction * length
            canvas.drawLine(baseX, y, endX, y, paint)

            if (VisualTuningPreferences.endpointMode(context) != EndpointMode.OFF) {
                drawEndpointDot(canvas, endX, y, hue, level, beatPulse, horizontal = false)
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

        val activity = (level * .84f + beat * .26f).coerceIn(0f, 1f)
        if (activity < .13f) return

        val strength = mode.strength
        val compact = if (horizontal) .72f else 1f
        val core = dp((.42f + activity * 1.20f + beat * .28f) * compact) * strength
        val glow = max(dp(if (horizontal) .95f else 1.20f), core * 2.75f)
        val alphaValue = (activity * .86f + beat * .10f).coerceIn(.12f, 1f)

        dotPaint.shader = RadialGradient(
            x,
            y,
            glow,
            intArrayOf(
                hsv(hue + 20f, .28f, 1f, alphaValue),
                hsv(hue, .84f, 1f, alphaValue * .30f),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, glow, dotPaint)
        dotPaint.shader = null
        dotPaint.color = hsv(hue + 22f, .12f, 1f, alphaValue)
        canvas.drawCircle(x, y, max(dp(.38f), core), dotPaint)
    }

    private fun groupAverage(start: Int, end: Int): Float {
        val safeStart = start.coerceIn(0, displayLevels.lastIndex)
        val safeEnd = end.coerceIn(safeStart, displayLevels.lastIndex)
        var total = 0f
        var count = 0
        for (index in safeStart..safeEnd) {
            total += displayLevels[index]
            count += 1
        }
        return if (count == 0) 0f else (total / count).coerceIn(0f, 1f)
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
        val brightness = (.68f + intensity * .32f).coerceIn(.70f, 1.23f)
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
