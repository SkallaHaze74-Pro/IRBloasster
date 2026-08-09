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
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

class NeonPreviewView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD,
        )
    }
    private val starPath = Path()
    private var mode = CapsuleDisplayMode.MINI
    private var edgeEnabled = true
    private var intensity = 1.35f
    private var source = MediaSourceLock.AUTO

    fun setConfig(
        displayMode: CapsuleDisplayMode,
        edgePanels: Boolean,
        neonIntensity: Float,
        sourceLock: MediaSourceLock,
    ) {
        mode = displayMode
        edgeEnabled = edgePanels
        intensity = neonIntensity.coerceIn(.75f, 1.8f)
        source = sourceLock
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = dp(27f)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(Color.rgb(3, 5, 14), Color.rgb(16, 4, 30), Color.rgb(2, 20, 25)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        fillPaint.shader = null

        val time = SystemClock.uptimeMillis() / 1000f
        val beat = ((sin(time * 5.35f) + 1f) * .5f).pow(6f)
        val phase = (time * (20f + beat * 130f)) % 360f
        val visualMode = CapsulePreferences.visualLayerMode(context)
        val flowMode = CapsulePreferences.flowMode(context)
        val beatFx = CapsulePreferences.beatFxMode(context)

        drawBorder(canvas, bounds, radius, phase, beat)
        if (edgeEnabled && visualMode != VisualLayerMode.BORDER_ONLY && visualMode != VisualLayerMode.BORDER_DROP) {
            drawEdges(canvas, phase, beat, flowMode, clean = visualMode == VisualLayerMode.CLEAN)
        }
        if (
            beatFx != BeatFxMode.SMOOTH &&
            (visualMode == VisualLayerMode.FULL || visualMode == VisualLayerMode.BORDER_DROP)
        ) {
            drawPreviewStars(canvas, phase, beat, beatFx)
        }
        if (mode != CapsuleDisplayMode.HIDDEN) drawCapsule(canvas, phase, beat)

        textPaint.textSize = dp(9.4f)
        textPaint.color = Color.argb(215, 226, 235, 249)
        canvas.drawText(
            "${visualMode.label.uppercase()} · ${CapsulePreferences.motionProfile(context).label.uppercase()} · BEAT MEMORY · ${source.label.uppercase()}",
            dp(13f),
            height - dp(13f),
            textPaint,
        )
        postInvalidateOnAnimation()
    }

    private fun drawBorder(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        phase: Float,
        beat: Float,
    ) {
        strokePaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                hsv(phase, .94f, 1f, 1f),
                hsv(phase + 72f, .94f, 1f, 1f),
                hsv(phase + 151f, .92f, 1f, 1f),
                hsv(phase + 230f, .96f, 1f, 1f),
                hsv(phase + 315f, .94f, 1f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        strokePaint.strokeWidth = dp(4f) * intensity * (1f + beat * .28f)
        strokePaint.alpha = (30 + beat * 45f).toInt()
        canvas.drawRoundRect(
            RectF(dp(2f), dp(2f), width - dp(2f), height - dp(2f)),
            radius,
            radius,
            strokePaint,
        )
        strokePaint.strokeWidth = dp(1.15f + beat * .4f)
        strokePaint.alpha = 235
        canvas.drawRoundRect(
            RectF(dp(2f), dp(2f), width - dp(2f), height - dp(2f)),
            radius,
            radius,
            strokePaint,
        )
        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun drawEdges(
        canvas: Canvas,
        phase: Float,
        beat: Float,
        flowMode: ReactiveFlowMode,
        clean: Boolean,
    ) {
        val now = SystemClock.uptimeMillis() / 1000f
        val top = dp(43f)
        val bottom = height - dp(34f)
        val segments = if (clean) 24 else 36
        val gap = (bottom - top) / segments
        repeat(2) { side ->
            val left = side == 0
            val outer = if (left) dp(8f) else width - dp(8f)
            val direction = if (left) 1f else -1f
            repeat(segments) { segment ->
                val progress = segment / max(1f, (segments - 1).toFloat())
                val flowCoordinate = when (flowMode) {
                    ReactiveFlowMode.UP -> 1f - progress
                    ReactiveFlowMode.DOWN -> progress
                    ReactiveFlowMode.OUTWARD -> 1f - abs(progress - .5f) * 2f
                    ReactiveFlowMode.AUTO,
                    ReactiveFlowMode.INWARD,
                    -> abs(progress - .5f) * 2f
                }
                val wave = (sin(now * (1.8f + beat * 4.2f) + segment * .57f + side * .8f) + 1f) * .5f
                val amount = (.09f + wave * .91f).pow(.68f)
                val body = sin(progress * PI.toFloat()) * dp(6f)
                val y = top + segment * gap
                val start = outer + direction * (body + beat * dp(4f))
                val length = dp(2.5f) + amount * dp(if (clean) 8f else 12f) * intensity + beat * dp(7f)
                strokePaint.strokeWidth = dp(1f) + amount * dp(1.2f)
                strokePaint.color = hsv(
                    phase + flowCoordinate * 470f + side * 55f,
                    .95f,
                    1f,
                    .34f + amount * .62f,
                )
                canvas.drawLine(start, y, start + direction * length, y, strokePaint)
            }
        }
    }

    private fun drawPreviewStars(
        canvas: Canvas,
        phase: Float,
        beat: Float,
        beatFx: BeatFxMode,
    ) {
        val now = SystemClock.uptimeMillis() / 1000f
        val count = if (beatFx == BeatFxMode.BRUTAL) 15 else 8
        repeat(count) { index ->
            val seed = index * 1.731f
            val progress = ((now * (.105f + index % 4 * .014f) + seed) % 1f)
            val x = width * (.10f + ((sin(seed * 2.3f) + 1f) * .5f) * .80f)
            val y = dp(18f) + progress * (height - dp(42f))
            val size = dp(.85f + beat * 1.6f + (index % 3) * .23f)
            drawStar(canvas, x, y, size, phase + index * 31f, .17f + beat * .72f)
        }
    }

    private fun drawStar(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        hue: Float,
        alpha: Float,
    ) {
        starPath.reset()
        repeat(8) { index ->
            val r = if (index % 2 == 0) size else size * .34f
            val angle = index * PI.toFloat() / 4f - PI.toFloat() / 2f
            val px = x + cos(angle) * r
            val py = y + sin(angle) * r
            if (index == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        fillPaint.color = hsv(hue, .82f, 1f, alpha.coerceIn(0f, 1f))
        canvas.drawPath(starPath, fillPaint)
    }

    private fun drawCapsule(canvas: Canvas, phase: Float, beat: Float) {
        val capsuleWidth = if (mode == CapsuleDisplayMode.RIM) width * .43f else width * .68f
        val capsuleHeight = if (mode == CapsuleDisplayMode.RIM) dp(18f) else dp(40f)
        val rect = RectF(
            (width - capsuleWidth) / 2f,
            dp(16f),
            (width + capsuleWidth) / 2f,
            dp(16f) + capsuleHeight,
        )
        fillPaint.color = if (mode == CapsuleDisplayMode.RIM) {
            Color.argb(20, 3, 7, 18)
        } else {
            Color.argb(226, 5, 8, 20)
        }
        canvas.drawRoundRect(rect, capsuleHeight * .5f, capsuleHeight * .5f, fillPaint)
        strokePaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                hsv(phase + 10f, .96f, 1f, 1f),
                hsv(phase + 120f, .94f, 1f, 1f),
                hsv(phase + 235f, .92f, 1f, 1f),
                hsv(phase + 330f, .94f, 1f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        strokePaint.strokeWidth = dp(1.35f + beat * .65f)
        canvas.drawRoundRect(rect, capsuleHeight * .5f, capsuleHeight * .5f, strokePaint)
        strokePaint.shader = null

        val barsLeft = if (mode == CapsuleDisplayMode.RIM) rect.centerX() - dp(25f) else rect.right - dp(50f)
        val baseline = rect.bottom - dp(if (mode == CapsuleDisplayMode.RIM) 4f else 8f)
        repeat(if (mode == CapsuleDisplayMode.RIM) 10 else 7) { index ->
            val wave = (sin(SystemClock.uptimeMillis() / (190f - beat * 75f) + index * .75f) + 1f) * .5f
            fillPaint.color = hsv(phase + index * 37f, .94f, 1f, .94f)
            val h = dp(2f) + wave * dp(if (mode == CapsuleDisplayMode.RIM) 8f else 12f)
            canvas.drawRoundRect(
                RectF(
                    barsLeft + index * dp(5f),
                    baseline - h,
                    barsLeft + index * dp(5f) + dp(2.5f),
                    baseline,
                ),
                dp(1.3f),
                dp(1.3f),
                fillPaint,
            )
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
