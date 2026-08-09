package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Full-display, touch-through neon frame for the Xiaomi overlay.
 *
 * The window itself is laid out against maximum WindowMetrics by
 * [CapsuleOverlayService], so this view receives the real 1280 x 2772 canvas
 * on the Xiaomi 15T Pro instead of the application area between system bars.
 */
class EdgePanelView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePath = Path()
    private val symbolPath = Path()
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
        systemUiVisibility =
            SYSTEM_UI_FLAG_LAYOUT_STABLE or
                SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
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
        val attack = 1f - exp(-deltaSeconds * 29f)
        val release = 1f - exp(-deltaSeconds * 9f)
        var energy = 0f
        for (index in displayLevels.indices) {
            val target = if (active) targetLevels[index] else 0f
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
            energy += displayLevels[index]
        }
        energy = (energy / displayLevels.size).coerceIn(0f, 1f)

        // Even while idle the frame drifts very slowly; during music it becomes
        // a vivid rainbow. This avoids the previous mostly green/orange look.
        colorPhase = (colorPhase + deltaSeconds * (4.5f + energy * 66f)) % 360f

        drawPerimeter(canvas, energy)
        drawTopAndBottomRails(canvas, energy)
        drawEdge(canvas, left = true, energy = energy)
        drawEdge(canvas, left = false, energy = energy)
        drawSymbols(canvas, energy)
        drawParticles(canvas, energy)

        // Keep interpolation tied to the panel/display VSync (up to 144 Hz).
        postInvalidateOnAnimation()
    }

    private fun drawPerimeter(canvas: Canvas, energy: Float) {
        val inset = dp(2.4f)
        val radius = min(width, height) * .030f
        val rect = RectF(inset, inset, width - inset, height - inset)
        val colors = intArrayOf(
            hsv(colorPhase + 315f, .96f, 1f, 1f),
            hsv(colorPhase + 5f, .96f, 1f, 1f),
            hsv(colorPhase + 58f, .95f, 1f, 1f),
            hsv(colorPhase + 118f, .94f, 1f, 1f),
            hsv(colorPhase + 178f, .94f, 1f, 1f),
            hsv(colorPhase + 236f, .95f, 1f, 1f),
            hsv(colorPhase + 292f, .96f, 1f, 1f),
            hsv(colorPhase + 360f, .96f, 1f, 1f),
        )
        strokePaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            colors,
            null,
            Shader.TileMode.MIRROR,
        )

        val power = neonIntensity.coerceIn(.75f, 1.8f)
        strokePaint.alpha = (38 + energy * 40).toInt().coerceIn(30, 82)
        strokePaint.strokeWidth = dp(14f) * power
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.alpha = (115 + energy * 55).toInt().coerceIn(100, 190)
        strokePaint.strokeWidth = dp(5.2f) * power.coerceAtMost(1.35f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.alpha = 255
        strokePaint.strokeWidth = dp(1.35f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun drawTopAndBottomRails(canvas: Canvas, energy: Float) {
        val usableWidth = width - dp(30f)
        val startX = dp(15f)
        val yTop = dp(12f)
        val yBottom = height - dp(12f)
        val segments = 72
        val gap = usableWidth / segments

        for (index in 0 until segments) {
            val progress = index / max(1f, (segments - 1).toFloat())
            val mirrored = if (progress <= .5f) progress * 2f else (1f - progress) * 2f
            val bandIndex = (mirrored * displayLevels.lastIndex).toInt().coerceIn(0, displayLevels.lastIndex)
            val level = max(if (snapshot.analyzerRunning) .04f else .014f, displayLevels[bandIndex].pow(.62f))
            val hue = colorPhase + progress * 430f
            val alpha = (.24f + level * .74f).coerceIn(.20f, 1f)
            val x = startX + index * gap
            val length = dp(2f) + level * dp(10f) * neonIntensity

            strokePaint.color = hsv(hue, .95f, 1f, alpha)
            strokePaint.strokeWidth = dp(.9f) + level * dp(1.5f)
            canvas.drawLine(x, yTop, x, yTop + length, strokePaint)
            canvas.drawLine(x, yBottom, x, yBottom - length, strokePaint)
        }
    }

    private fun drawEdge(canvas: Canvas, left: Boolean, energy: Float) {
        val segments = 70
        val top = dp(8f)
        val bottom = height - dp(8f)
        val usable = max(1f, bottom - top)
        val outerX = if (left) dp(5f) else width - dp(5f)
        val direction = if (left) 1f else -1f
        val time = SystemClock.uptimeMillis() / 1000f

        linePath.reset()
        for (step in 0..96) {
            val progress = step / 96f
            val y = top + usable * progress
            val wave = sin(progress * PI.toFloat() * 5.1f + time * .78f) * dp(3.2f)
            val secondary = sin(progress * PI.toFloat() * 12.7f - time * 1.12f) * dp(1.3f)
            val body = sin(progress * PI.toFloat()) * dp(15.5f)
            val x = outerX + direction * (dp(2f) + body + (wave + secondary) * (.25f + energy))
            if (step == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        // Deliberately draw cyan + magenta core passes instead of a single hue.
        val coreHue = if (left) colorPhase + 188f else colorPhase + 316f
        strokePaint.strokeWidth = dp(15f) * neonIntensity
        strokePaint.color = hsv(coreHue, .96f, 1f, .045f + energy * .085f)
        canvas.drawPath(linePath, strokePaint)
        strokePaint.strokeWidth = dp(5.6f) * neonIntensity.coerceAtMost(1.35f)
        strokePaint.color = hsv(coreHue + 58f, .96f, 1f, .18f + energy * .18f)
        canvas.drawPath(linePath, strokePaint)
        strokePaint.strokeWidth = dp(1.55f)
        strokePaint.color = hsv(coreHue + 116f, .90f, 1f, .94f)
        canvas.drawPath(linePath, strokePaint)

        val barGap = usable / segments
        for (segment in 0 until segments) {
            val progress = segment / max(1f, (segments - 1).toFloat())
            val y = top + progress * usable
            val mirroredProgress = if (progress < .5f) progress * 2f else (1f - progress) * 2f
            val levelPosition = (mirroredProgress * (displayLevels.size - 1))
                .coerceIn(0f, (displayLevels.size - 1).toFloat())
            val lowIndex = levelPosition.toInt()
            val highIndex = min(displayLevels.lastIndex, lowIndex + 1)
            val mix = levelPosition - lowIndex
            val level = displayLevels[lowIndex] * (1f - mix) + displayLevels[highIndex] * mix
            val shaped = level.pow(.61f)
            val breathing = if (snapshot.analyzerRunning) .060f else .020f
            val amount = max(breathing, shaped)
            val wave = sin(progress * PI.toFloat() * 5.1f + time * .78f) * dp(3.2f)
            val secondary = sin(progress * PI.toFloat() * 12.7f - time * 1.12f) * dp(1.3f)
            val body = sin(progress * PI.toFloat()) * dp(15.5f)
            val baseX = outerX + direction * (dp(2f) + body + (wave + secondary) * (.25f + energy))
            val length = dp(5f) + amount * dp(31f) * neonIntensity + mirroredProgress * dp(3f)

            // Several full rainbow cycles along each edge guarantee visible color.
            val hue = (
                320f +
                    progress * 470f +
                    if (left) 0f else 142f +
                    colorPhase * .28f
                ) % 360f
            val alpha = (.34f + amount * .66f).coerceIn(0f, 1f)

            strokePaint.strokeWidth = dp(6.5f) + amount * dp(7f)
            strokePaint.color = hsv(hue, .98f, 1f, alpha * .15f)
            canvas.drawLine(baseX, y, baseX + direction * length, y, strokePaint)

            strokePaint.strokeWidth = dp(1.15f) + amount * dp(2.5f)
            strokePaint.color = hsv(hue, .95f, 1f, alpha)
            canvas.drawLine(baseX, y, baseX + direction * length, y, strokePaint)

            // Inner dotted lane from the reference design.
            if (segment % 2 == 0) {
                fillPaint.color = hsv(hue + 42f, .88f, 1f, .26f + amount * .60f)
                canvas.drawCircle(
                    baseX + direction * (length + dp(5f)),
                    y,
                    dp(.72f) + amount * dp(.72f),
                    fillPaint,
                )
            }
        }
    }

    private fun drawSymbols(canvas: Canvas, energy: Float) {
        val positions = floatArrayOf(.10f, .23f, .37f, .52f, .69f, .84f, .94f)
        positions.forEachIndexed { index, progress ->
            val y = height * progress
            val hueLeft = colorPhase + progress * 420f
            val hueRight = hueLeft + 150f
            val size = dp(3.2f) + energy * dp(2.8f)
            when (index % 4) {
                0 -> {
                    drawDiamond(canvas, dp(20f), y, size, hueLeft)
                    drawDiamond(canvas, width - dp(20f), y, size, hueRight)
                }

                1 -> {
                    drawSpark(canvas, dp(26f), y, size * 1.25f, hueLeft)
                    drawMusicNote(canvas, width - dp(24f), y, size * 1.10f, hueRight)
                }

                2 -> {
                    drawPulseGlyph(canvas, dp(23f), y, size * 1.25f, hueLeft)
                    drawPulseGlyph(canvas, width - dp(23f), y, size * 1.25f, hueRight, mirror = true)
                }

                else -> {
                    drawMusicNote(canvas, dp(24f), y, size, hueLeft)
                    drawSpark(canvas, width - dp(26f), y, size * 1.35f, hueRight)
                }
            }
        }
    }

    private fun drawDiamond(canvas: Canvas, x: Float, y: Float, size: Float, hue: Float) {
        symbolPath.reset()
        symbolPath.moveTo(x, y - size)
        symbolPath.lineTo(x + size * .72f, y)
        symbolPath.lineTo(x, y + size)
        symbolPath.lineTo(x - size * .72f, y)
        symbolPath.close()
        strokePaint.strokeWidth = dp(1.1f)
        strokePaint.color = hsv(hue, .92f, 1f, .90f)
        canvas.drawPath(symbolPath, strokePaint)
        fillPaint.color = hsv(hue + 42f, .82f, 1f, .18f)
        canvas.drawPath(symbolPath, fillPaint)
    }

    private fun drawSpark(canvas: Canvas, x: Float, y: Float, size: Float, hue: Float) {
        strokePaint.strokeWidth = dp(1.05f)
        strokePaint.color = hsv(hue, .90f, 1f, .88f)
        canvas.drawLine(x - size, y, x + size, y, strokePaint)
        canvas.drawLine(x, y - size, x, y + size, strokePaint)
        canvas.drawLine(x - size * .55f, y - size * .55f, x + size * .55f, y + size * .55f, strokePaint)
        canvas.drawLine(x + size * .55f, y - size * .55f, x - size * .55f, y + size * .55f, strokePaint)
    }

    private fun drawMusicNote(canvas: Canvas, x: Float, y: Float, size: Float, hue: Float) {
        strokePaint.strokeWidth = dp(1.25f)
        strokePaint.color = hsv(hue, .94f, 1f, .90f)
        val stemTop = y - size * 1.25f
        val stemBottom = y + size * .35f
        canvas.drawLine(x, stemTop, x, stemBottom, strokePaint)
        canvas.drawLine(x, stemTop, x + size * .92f, stemTop + size * .28f, strokePaint)
        fillPaint.color = hsv(hue + 35f, .90f, 1f, .90f)
        canvas.drawCircle(x - size * .28f, stemBottom + size * .30f, size * .42f, fillPaint)
    }

    private fun drawPulseGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        hue: Float,
        mirror: Boolean = false,
    ) {
        val direction = if (mirror) -1f else 1f
        symbolPath.reset()
        symbolPath.moveTo(x - direction * size, y)
        symbolPath.lineTo(x - direction * size * .42f, y)
        symbolPath.lineTo(x - direction * size * .16f, y - size * .72f)
        symbolPath.lineTo(x + direction * size * .12f, y + size * .82f)
        symbolPath.lineTo(x + direction * size * .42f, y - size * .42f)
        symbolPath.lineTo(x + direction * size, y)
        strokePaint.strokeWidth = dp(1.15f)
        strokePaint.color = hsv(hue, .92f, 1f, .88f)
        canvas.drawPath(symbolPath, strokePaint)
    }

    private fun drawParticles(canvas: Canvas, energy: Float) {
        val now = SystemClock.uptimeMillis() / 1000f
        val count = 42
        for (index in 0 until count) {
            val leftSide = index % 2 == 0
            val seed = index * 1.731f
            val progress = ((now * (.018f + (index % 6) * .0035f) + seed) % 1f)
            val y = dp(12f) + progress * (height - dp(24f))
            val distance = dp(11f) + abs(sin(seed + now * .37f)) * dp(35f)
            val x = if (leftSide) distance else width - distance
            val hue = colorPhase + progress * 440f + index * 13f
            val alpha = (.065f + energy * .38f) * (.42f + abs(sin(seed + now * 1.3f)) * .58f)
            fillPaint.color = hsv(hue, .90f, 1f, alpha)
            canvas.drawCircle(x, y, dp(.58f) + energy * dp(1.25f), fillPaint)
        }
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
        val color = Color.HSVToColor(
            floatArrayOf((hue % 360f + 360f) % 360f, saturation, value),
        )
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun dp(value: Float): Float = value * density
}
