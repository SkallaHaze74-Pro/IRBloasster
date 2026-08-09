package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
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

        val phase = (SystemClock.uptimeMillis() % 10_000L) / 10_000f * 360f
        drawBorder(canvas, bounds, radius, phase)
        if (edgeEnabled) drawEdges(canvas, phase)
        drawCapsule(canvas, phase)

        textPaint.textSize = dp(10.5f)
        textPaint.color = Color.argb(210, 226, 235, 249)
        canvas.drawText("VARIANTE 2 · ${source.label.uppercase()}", dp(16f), height - dp(14f), textPaint)
        postInvalidateOnAnimation()
    }

    private fun drawBorder(canvas: Canvas, bounds: RectF, radius: Float, phase: Float) {
        strokePaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                hsv(phase, .90f, 1f, 1f),
                hsv(phase + 70f, .90f, 1f, 1f),
                hsv(phase + 150f, .88f, 1f, 1f),
                hsv(phase + 225f, .92f, 1f, 1f),
                hsv(phase + 310f, .90f, 1f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        strokePaint.strokeWidth = dp(4.5f) * intensity
        strokePaint.alpha = 30
        canvas.drawRoundRect(RectF(dp(2f), dp(2f), width - dp(2f), height - dp(2f)), radius, radius, strokePaint)
        strokePaint.strokeWidth = dp(1.2f)
        strokePaint.alpha = 220
        canvas.drawRoundRect(RectF(dp(2f), dp(2f), width - dp(2f), height - dp(2f)), radius, radius, strokePaint)
        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun drawEdges(canvas: Canvas, phase: Float) {
        val now = SystemClock.uptimeMillis() / 1000f
        val top = dp(46f)
        val bottom = height - dp(38f)
        val segments = 34
        val gap = (bottom - top) / segments
        for (side in 0..1) {
            val left = side == 0
            val outer = if (left) dp(8f) else width - dp(8f)
            val direction = if (left) 1f else -1f
            for (segment in 0 until segments) {
                val progress = segment / max(1f, (segments - 1).toFloat())
                val wave = (sin(now * 2.1f + segment * .57f + side * .8f) + 1f) * .5f
                val amount = (.12f + wave * .88f).pow(.65f)
                val body = sin(progress * PI.toFloat()) * dp(7f)
                val y = top + segment * gap
                val start = outer + direction * body
                val length = dp(3f) + amount * dp(12f) * intensity
                strokePaint.strokeWidth = dp(1.1f) + amount * dp(1.3f)
                strokePaint.color = hsv(phase + progress * 310f + side * 45f, .92f, 1f, .40f + amount * .58f)
                canvas.drawLine(start, y, start + direction * length, y, strokePaint)
            }
        }
    }

    private fun drawCapsule(canvas: Canvas, phase: Float) {
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
                hsv(phase + 10f, .94f, 1f, 1f),
                hsv(phase + 120f, .92f, 1f, 1f),
                hsv(phase + 235f, .90f, 1f, 1f),
                hsv(phase + 330f, .92f, 1f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, capsuleHeight * .5f, capsuleHeight * .5f, strokePaint)
        strokePaint.shader = null

        if (mode == CapsuleDisplayMode.RIM) return
        fillPaint.color = hsv(phase + 150f, .82f, 1f, .88f)
        canvas.drawCircle(rect.left + dp(21f), rect.centerY(), dp(14f), fillPaint)
        textPaint.textSize = dp(10.5f)
        textPaint.color = Color.WHITE
        canvas.drawText("CLIMO – Heartbeat …", rect.left + dp(43f), rect.centerY() - dp(1f), textPaint)
        textPaint.textSize = dp(8.5f)
        textPaint.color = Color.argb(160, 210, 220, 239)
        canvas.drawText(source.label, rect.left + dp(43f), rect.centerY() + dp(12f), textPaint)

        val barsLeft = rect.right - dp(51f)
        val baseline = rect.bottom - dp(8f)
        repeat(7) { index ->
            val wave = (sin(SystemClock.uptimeMillis() / 180f + index * .75f) + 1f) * .5f
            fillPaint.color = hsv(phase + index * 42f, .92f, 1f, .92f)
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
