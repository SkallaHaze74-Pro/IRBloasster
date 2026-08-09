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
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
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
        val radius = dp(28f)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(Color.rgb(3, 5, 14), Color.rgb(14, 4, 28), Color.rgb(2, 18, 23)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        fillPaint.shader = null

        val time = SystemClock.uptimeMillis() / 1000f
        val beat = ((sin(time * 5.7f) + 1f) * .5f).pow(5.5f)
        val phase = (time * (24f + beat * 155f)) % 360f
        val flowMode = CapsulePreferences.flowMode(context)
        val beatFx = CapsulePreferences.beatFxMode(context)

        drawBorder(canvas, bounds, radius, phase, beat)
        if (edgeEnabled) drawEdges(canvas, phase, beat, flowMode)
        drawPreviewStars(canvas, phase, beat, beatFx)
        drawCapsule(canvas, phase, beat)

        textPaint.textSize = dp(9.7f)
        textPaint.color = Color.argb(215, 226, 235, 249)
        canvas.drawText(
            "BRUTAL REACTIVE · ${XiaomiDisplayProfile.smallestWidthDp(context)}dp · ${flowMode.label.uppercase()} · ${source.label.uppercase()}",
            dp(14f),
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
                hsv(phase + 70f, .94f, 1f, 1f),
                hsv(phase + 150f, .92f, 1f, 1f),
                hsv(phase + 225f, .96f, 1f, 1f),
                hsv(phase + 310f, .94f, 1f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        strokePaint.strokeWidth = dp(4.5f) * intensity * (1f + beat * .35f)
        strokePaint.alpha = (30 + beat * 45f).toInt()
        canvas.drawRoundRect(RectF(dp(2f), dp(2f), width - dp(2f), height - dp(2f)), radius, radius, strokePaint)
        strokePaint.strokeWidth = dp(1.2f + beat * .45f)
        strokePaint.alpha = 230
        canvas.drawRoundRect(RectF(dp(2f), dp(2f), width - dp(2f), height - dp(2f)), radius, radius, strokePaint)
        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun drawEdges(
        canvas: Canvas,
        phase: Float,
        beat: Float,
        flowMode: ReactiveFlowMode,
    ) {
        val now = SystemClock.uptimeMillis() / 1000f
        val top = dp(44f)
        val bottom = height - dp(34f)
        val segments = 36
        val gap = (bottom - top) / segments
        for (side in 0..1) {
            val left = side == 0
            val outer = if (left) dp(8f) else width - dp(8f)
            val direction = if (left) 1f else -1f
            for (segment in 0 until segments) {
                val progress = segment / max(1f, (segments - 1).toFloat())
                val flowCoordinate = when (flowMode) {
                    ReactiveFlowMode.UP -> 1f - progress
                    ReactiveFlowMode.DOWN -> progress
                    ReactiveFlowMode.OUTWARD -> 1f - abs(progress - .5f) * 2f
                    ReactiveFlowMode.AUTO,
                    ReactiveFlowMode.INWARD,
                    -> abs(progress - .5f) * 2f
                }
                val wave = (sin(now * (2.1f + beat * 5f) + segment * .57f + side * .8f) + 1f) * .5f
                val amount = (.10f + wave * .90f).pow(.63f)
                val body = sin(progress * PI.toFloat()) * dp(7f)
                val y = top + segment * gap
                val start = outer + direction * (body + beat * dp(5f))
                val length = dp(3f) + amount * dp(12f) * intensity + beat * dp(8f)
                strokePaint.strokeWidth = dp(1.1f) + amount * dp(1.3f)
                strokePaint.color = hsv(
                    phase + flowCoordinate * 470f + side * 55f,
                    .95f,
                    1f,
                    .38f + amount * .60f,
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
        if (beatFx == BeatFxMode.SMOOTH) return
        val now = SystemClock.uptimeMillis() / 1000f
        val count = if (beatFx == BeatFxMode.BRUTAL) 16 else 8
        repeat(count) { index ->
            val seed = index * 1.731f
            val progress = ((now * (.11f + index % 4 * .014f) + seed) % 1f)
            val x = width * (.10f + ((sin(seed * 2.3f) + 1f) * .5f) * .80f)
            val y = dp(18f) + progress * (height - dp(42f))
            val size = dp(.9f + beat * 1.8f + (index % 3) * .25f)
            drawStar(canvas, x, y, size, phase + index * 31f, .18f + beat * .70f)
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
            val radius = if (index % 2 == 0) size else size * .34f
            val angle = index * PI.toFloat() / 4f - PI.toFloat() / 2f
            val px = x + cos(angle) * radius
            val py = y + sin(angle) * radius
            if (index == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        fillPaint.color = hsv(hue, .82f, 1f, alpha.coerceIn(0f, 1f))
        canvas.drawPath(starPath, fillPaint)
    }

    private fun drawCapsule(canvas: Canvas, phase: Float, beat: Float) {
        val capsuleWidth = if (mode == CapsuleDisplayMode.RIM) width * .49f else width * .70f
        val capsuleHeight = if (mode == CapsuleDisplayMode.RIM) dp(22f) else dp(42f)
        val rect = RectF(
            (width - capsuleWidth) / 2f,
            dp(17f),
            (width + capsuleWidth) / 2f,
            dp(17f) + capsuleHeight,
        )
        fillPaint.color = if (mode == CapsuleDisplayMode.RIM) Color.argb(22, 3, 7, 18) else Color.argb(226, 5, 8, 20)
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
        strokePaint.strokeWidth = dp(1.5f + beat * .7f)
        canvas.drawRoundRect(rect, capsuleHeight * .5f, capsuleHeight * .5f, strokePaint)
        strokePaint.shader = null

        if (mode == CapsuleDisplayMode.RIM) return
        fillPaint.color = hsv(phase + 150f, .84f, 1f, .90f)
        canvas.drawCircle(rect.left + dp(21f), rect.centerY(), dp(14f + beat * 1.4f), fillPaint)
        textPaint.textSize = dp(10.5f)
        textPaint.color = Color.WHITE
        canvas.drawText("CLIMO – Heartbeat …", rect.left + dp(43f), rect.centerY() - dp(1f), textPaint)
        textPaint.textSize = dp(8.5f)
        textPaint.color = Color.argb(160, 210, 220, 239)
        canvas.drawText(source.label, rect.left + dp(43f), rect.centerY() + dp(12f), textPaint)

        val barsLeft = rect.right - dp(51f)
        val baseline = rect.bottom - dp(8f)
        repeat(7) { index ->
            val wave = (sin(SystemClock.uptimeMillis() / (180f - beat * 80f) + index * .75f) + 1f) * .5f
            fillPaint.color = hsv(phase + index * 42f, .94f, 1f, .94f)
            val h = dp(3f) + wave * dp(13f)
            canvas.drawRoundRect(
                RectF(barsLeft + index * dp(6f), baseline - h, barsLeft + index * dp(6f) + dp(3f), baseline),
                dp(1.5f),
                dp(1.5f),
                fillPaint,
            )
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
