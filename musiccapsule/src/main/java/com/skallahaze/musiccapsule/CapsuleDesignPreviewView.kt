package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.view.Choreographer
import android.view.View
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class CapsuleDesignPreviewView(context: Context) : View(context), Choreographer.FrameCallback {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 205, 218, 239)
    }
    private val leafSource = PathParser.createPathFromPathData(
        "M11.5,22V17.35C11,18.13 10,19.09 8.03,19.81C8.03,19.81 8.53,18.1 9.94,16.95C8.64,17.23 6.68,17.19 4,16C4,16 6.47,14.59 9.28,14.97C7.69,14 5.7,12.08 4.17,8.11C4.17,8.11 8.67,9.34 10.91,13.14C8.88,8.24 12,2 12,2C14.43,7.47 13.91,11.1 13.12,13.1C15.37,9.33 19.83,8.11 19.83,8.11C18.3,12.08 16.31,14 14.72,14.97C17.53,14.59 20,16 20,16C17.32,17.19 15.36,17.23 14.06,16.95C15.47,18.1 15.97,19.81 15.97,19.81C14,19.09 13,18.13 12.5,17.35V22H11.5Z",
    ) ?: Path()
    private val leafPath = Path()
    private val matrix = Matrix()

    private var attached = false
    private var lastFrameNanos = 0L
    private var phase = 0f
    private var mode = CapsuleMode.COMPACT
    private var edgesEnabled = true
    private var edgeIntensity = 1.12f
    private var sourceLock = MediaSourceLock.AUTO

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setPreferences(
        mode: CapsuleMode,
        edgesEnabled: Boolean,
        edgeIntensity: Float,
        sourceLock: MediaSourceLock,
    ) {
        this.mode = mode
        this.edgesEnabled = edgesEnabled
        this.edgeIntensity = edgeIntensity
        this.sourceLock = sourceLock
        invalidate()
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

    override fun doFrame(frameTimeNanos: Long) {
        if (!attached) return
        val delta = if (lastFrameNanos == 0L) 1f / 60f else
            ((frameTimeNanos - lastFrameNanos).coerceAtLeast(1L) / 1_000_000_000f)
                .coerceIn(1f / 240f, 1f / 20f)
        lastFrameNanos = frameTimeNanos
        phase = (phase + delta * 32f) % 360f
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(Color.rgb(2, 4, 11), Color.rgb(6, 8, 22), Color.rgb(2, 15, 20)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(outer, dp(28f), dp(28f), fillPaint)
        fillPaint.shader = null

        if (edgesEnabled) drawEdges(canvas)
        drawTopCapsule(canvas)

        subPaint.textSize = dp(10.5f)
        val label = "144 Hz VSync · ${sourceLock.label} · ${modeLabel()}"
        canvas.drawText(label, dp(18f), height - dp(15f), subPaint)
    }

    private fun drawEdges(canvas: Canvas) {
        val segments = 42
        val top = dp(18f)
        val bottom = height - dp(26f)
        val segmentHeight = (bottom - top) / segments
        val levels = CapsuleRuntime.snapshot().levels
        for (index in 0 until segments) {
            val y = top + (index + .5f) * segmentHeight
            val normalized = index / max(1f, (segments - 1).toFloat())
            val bandIndex = ((if (normalized <= .5f) normalized * 2f else (1f - normalized) * 2f) * (levels.size - 1))
                .toInt()
                .coerceIn(0, levels.lastIndex)
            val live = levels.getOrNull(bandIndex) ?: 0f
            val demo = ((sin(index * .53f + phase * .06f) + 1f) * .5f).pow(1.7f)
            val level = max(live, .12f + demo * .42f)
            val hue = (phase + normalized * 420f) % 360f
            val color = hsv(hue, .92f, 1f, .42f + level * .52f)
            val wave = sin(normalized * PI.toFloat() * 4.4f + phase * .025f) * dp(5f)
            val leftX = dp(8f) + wave + abs(sin(normalized * PI.toFloat())) * dp(4f)
            val rightX = width - leftX
            val length = dp(4f) + level * dp(18f) * edgeIntensity

            strokePaint.color = color
            strokePaint.strokeWidth = dp(.9f) + level * dp(1.4f)
            canvas.drawLine(leftX, y, leftX + length, y, strokePaint)
            canvas.drawLine(rightX, y, rightX - length, y, strokePaint)
        }

        strokePaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.rgb(255, 35, 205),
                Color.rgb(72, 88, 255),
                Color.rgb(24, 221, 255),
                Color.rgb(102, 255, 81),
                Color.rgb(255, 193, 42),
                Color.rgb(255, 45, 178),
            ),
            null,
            Shader.TileMode.MIRROR,
        )
        strokePaint.strokeWidth = dp(2f)
        strokePaint.alpha = 230
        canvas.drawLine(dp(7f), dp(12f), dp(7f), height - dp(25f), strokePaint)
        canvas.drawLine(width - dp(7f), dp(12f), width - dp(7f), height - dp(25f), strokePaint)
        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun drawTopCapsule(canvas: Canvas) {
        val centerX = width * .5f
        val top = dp(17f)
        val rect = when (mode) {
            CapsuleMode.RIM -> RectF(centerX - dp(72f), top, centerX + dp(72f), top + dp(18f))
            CapsuleMode.COMPACT -> RectF(centerX - min(width * .35f, dp(150f)), top, centerX + min(width * .35f, dp(150f)), top + dp(52f))
            CapsuleMode.EXPANDED -> RectF(centerX - min(width * .39f, dp(175f)), top, centerX + min(width * .39f, dp(175f)), top + dp(64f))
        }
        val radius = rect.height() * .48f
        fillPaint.color = Color.argb(210, 4, 7, 17)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        strokePaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                hsv(phase + 170f, .92f, 1f),
                hsv(phase + 250f, .90f, 1f),
                hsv(phase + 330f, .92f, 1f),
                hsv(phase + 50f, .92f, 1f),
            ),
            null,
            Shader.TileMode.MIRROR,
        )
        strokePaint.strokeWidth = dp(1.25f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.shader = null

        if (mode == CapsuleMode.RIM) return

        val art = RectF(rect.left + dp(7f), rect.top + dp(7f), rect.left + dp(45f), rect.bottom - dp(7f))
        fillPaint.color = Color.argb(120, 18, 25, 48)
        canvas.drawRoundRect(art, dp(12f), dp(12f), fillPaint)
        drawLeaf(canvas, art)

        textPaint.textSize = dp(11.5f)
        subPaint.textSize = dp(8.8f)
        val title = CapsuleRuntime.snapshot().title.takeUnless { it == "Keine Wiedergabe" }
            ?: "CLIMO - Heartbeat (Need You)"
        val artist = CapsuleRuntime.snapshot().artist.ifBlank { "CLIMO Official" }
        drawEllipsized(canvas, title, art.right + dp(8f), rect.top + dp(23f), rect.right - dp(66f), textPaint)
        drawEllipsized(canvas, artist, art.right + dp(8f), rect.top + dp(39f), rect.right - dp(66f), subPaint)

        val barsRect = RectF(rect.right - dp(55f), rect.top + dp(13f), rect.right - dp(10f), rect.bottom - dp(12f))
        val count = 7
        val gap = dp(2.2f)
        val barWidth = (barsRect.width() - gap * (count - 1)) / count
        for (index in 0 until count) {
            val demo = ((sin(phase * .05f + index * .84f) + 1f) * .5f).pow(1.4f)
            val h = barsRect.height() * (.12f + demo * .80f)
            fillPaint.color = hsv(phase + 130f + index * 34f, .90f, 1f)
            canvas.drawRoundRect(
                RectF(
                    barsRect.left + index * (barWidth + gap),
                    barsRect.bottom - h,
                    barsRect.left + index * (barWidth + gap) + barWidth,
                    barsRect.bottom,
                ),
                barWidth,
                barWidth,
                fillPaint,
            )
        }
    }

    private fun drawLeaf(canvas: Canvas, rect: RectF) {
        matrix.reset()
        matrix.setRectToRect(RectF(0f, 0f, 24f, 24f), rect, Matrix.ScaleToFit.CENTER)
        leafPath.reset()
        leafSource.transform(matrix, leafPath)
        fillPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                hsv(phase + 120f, .82f, 1f),
                hsv(phase + 205f, .84f, 1f),
                hsv(phase + 300f, .76f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(leafPath, fillPaint)
        fillPaint.shader = null
    }

    private fun drawEllipsized(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        right: Float,
        paint: TextPaint,
    ) {
        val maxWidth = right - x
        if (maxWidth <= 0f) return
        if (paint.measureText(value) <= maxWidth) {
            canvas.drawText(value, x, baseline, paint)
            return
        }
        var end = value.length
        while (end > 1 && paint.measureText(value.substring(0, end) + "…") > maxWidth) end -= 1
        canvas.drawText(value.substring(0, end) + "…", x, baseline, paint)
    }

    private fun modeLabel(): String = when (mode) {
        CapsuleMode.RIM -> "Nur Rand"
        CapsuleMode.COMPACT -> "Mini Widget"
        CapsuleMode.EXPANDED -> "Groß"
    }

    private fun dp(value: Float): Float = value * density

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Int {
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
}
