package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class EdgePanelView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePath = Path()
    private val targetLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val displayLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

    private var snapshot = CapsuleRuntime.snapshot()
    private var neonIntensity = 1.35f
    private var lastFrameNanos = 0L
    private var colorPhase = 0f
    private var visualEnabled = true

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setSnapshot(value: CapsuleSnapshot, intensity: Float, enabled: Boolean) {
        snapshot = value
        neonIntensity = intensity.coerceIn(.75f, 1.8f)
        visualEnabled = enabled
        for (index in targetLevels.indices) {
            targetLevels[index] = value.levels.getOrNull(index)?.coerceIn(0f, 1f) ?: 0f
        }
        visibility = if (enabled) VISIBLE else GONE
        if (enabled) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visualEnabled || width <= 0 || height <= 0) return

        val nowNanos = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .08f)
        }
        lastFrameNanos = nowNanos

        val active = snapshot.signal > .004f
        val attack = 1f - kotlin.math.exp(-deltaSeconds * 28f)
        val release = 1f - kotlin.math.exp(-deltaSeconds * 9f)
        var energy = 0f
        for (index in displayLevels.indices) {
            val target = if (active) targetLevels[index] else 0f
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
            energy += displayLevels[index]
        }
        energy = (energy / displayLevels.size).coerceIn(0f, 1f)
        colorPhase = (colorPhase + deltaSeconds * (8f + energy * 42f)) % 360f

        drawPerimeter(canvas, energy)
        drawEdge(canvas, left = true, energy = energy)
        drawEdge(canvas, left = false, energy = energy)
        drawParticles(canvas, energy)

        if (active || displayLevels.any { it > .008f }) postInvalidateOnAnimation()
    }

    private fun drawPerimeter(canvas: Canvas, energy: Float) {
        val inset = dp(3.5f)
        val radius = dp(28f)
        val rect = RectF(inset, inset, width - inset, height - inset)
        val phase = colorPhase

        repeat(3) { pass ->
            strokePaint.strokeWidth = when (pass) {
                0 -> dp(8f) * neonIntensity
                1 -> dp(3.6f) * neonIntensity
                else -> dp(1.1f)
            }
            strokePaint.color = hsv(
                phase + pass * 76f,
                .88f,
                1f,
                when (pass) {
                    0 -> .045f + energy * .075f
                    1 -> .12f + energy * .16f
                    else -> .42f + energy * .34f
                } * neonIntensity.coerceAtMost(1.45f),
            )
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
        }
    }

    private fun drawEdge(canvas: Canvas, left: Boolean, energy: Float) {
        val segments = 58
        val top = dp(58f)
        val bottom = height - dp(58f)
        val usable = max(1f, bottom - top)
        val outerX = if (left) dp(5f) else width - dp(5f)
        val direction = if (left) 1f else -1f
        val time = SystemClock.uptimeMillis() / 1000f

        linePath.reset()
        for (step in 0..72) {
            val progress = step / 72f
            val y = top + usable * progress
            val wave = sin(progress * PI.toFloat() * 4.2f + time * .62f) * dp(2.2f)
            val body = sin(progress * PI.toFloat()) * dp(15f)
            val x = outerX + direction * (dp(1f) + body + wave * energy)
            if (step == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        repeat(3) { pass ->
            strokePaint.strokeWidth = when (pass) {
                0 -> dp(13f) * neonIntensity
                1 -> dp(5.2f) * neonIntensity
                else -> dp(1.5f)
            }
            strokePaint.color = hsv(
                colorPhase + if (left) 20f else 175f,
                .95f,
                1f,
                when (pass) {
                    0 -> .035f + energy * .075f
                    1 -> .11f + energy * .18f
                    else -> .58f + energy * .30f
                },
            )
            canvas.drawPath(linePath, strokePaint)
        }

        val barGap = usable / segments
        for (segment in 0 until segments) {
            val progress = segment / max(1f, (segments - 1).toFloat())
            val y = top + progress * usable
            val mirroredProgress = if (progress < .5f) progress * 2f else (1f - progress) * 2f
            val levelPosition = (progress * (displayLevels.size - 1)).coerceIn(0f, (displayLevels.size - 1).toFloat())
            val lowIndex = levelPosition.toInt()
            val highIndex = min(displayLevels.lastIndex, lowIndex + 1)
            val mix = levelPosition - lowIndex
            val level = displayLevels[lowIndex] * (1f - mix) + displayLevels[highIndex] * mix
            val shaped = level.pow(.62f)
            val breathing = if (snapshot.analyzerRunning) .055f else .018f
            val amount = max(breathing, shaped)
            val wave = sin(progress * PI.toFloat() * 4.2f + time * .62f) * dp(2.2f)
            val body = sin(progress * PI.toFloat()) * dp(15f)
            val baseX = outerX + direction * (dp(1f) + body + wave * energy)
            val length = dp(4f) + amount * dp(25f) * neonIntensity + mirroredProgress * dp(2.5f)
            val hue = colorPhase + progress * 310f + if (left) 0f else 58f
            val alpha = (.28f + amount * .72f).coerceIn(0f, 1f)

            strokePaint.strokeWidth = dp(1.1f) + amount * dp(2.2f)
            strokePaint.color = hsv(hue, .94f, 1f, alpha)
            canvas.drawLine(baseX, y, baseX + direction * length, y, strokePaint)

            strokePaint.strokeWidth = dp(5.5f) + amount * dp(7f)
            strokePaint.color = hsv(hue, .96f, 1f, alpha * .12f)
            canvas.drawLine(baseX, y, baseX + direction * length, y, strokePaint)

            if (segment % 3 == 0 && amount > .13f) {
                fillPaint.color = hsv(hue + 24f, .82f, 1f, alpha * .72f)
                canvas.drawCircle(baseX + direction * (length + dp(2f)), y, dp(.8f) + amount * dp(1.4f), fillPaint)
            }
        }
    }

    private fun drawParticles(canvas: Canvas, energy: Float) {
        if (energy < .02f) return
        val now = SystemClock.uptimeMillis() / 1000f
        val count = 30
        for (index in 0 until count) {
            val side = if (index % 2 == 0) 1f else -1f
            val seed = index * 1.731f
            val progress = ((now * (.025f + (index % 5) * .004f) + seed) % 1f)
            val y = dp(70f) + progress * (height - dp(140f))
            val distance = dp(13f) + abs(sin(seed + now * .37f)) * dp(25f)
            val x = if (side > 0) distance else width - distance
            val hue = colorPhase + progress * 320f + index * 11f
            val alpha = (.10f + energy * .42f) * (0.45f + abs(sin(seed + now * 1.3f)) * .55f)
            fillPaint.color = hsv(hue, .88f, 1f, alpha)
            canvas.drawCircle(x, y, dp(.65f) + energy * dp(1.15f), fillPaint)
        }
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
        val color = Color.HSVToColor(floatArrayOf((hue % 360f + 360f) % 360f, saturation, value))
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun dp(value: Float): Float = value * density
}
