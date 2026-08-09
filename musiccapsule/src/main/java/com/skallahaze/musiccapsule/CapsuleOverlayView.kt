package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class CapsuleOverlayView(context: Context) : View(context) {
    var onExpandedChanged: ((Boolean) -> Unit)? = null
    var onMove: ((Float, Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 214, 226, 244)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private val sourceLeafPath: Path = PathParser.createPathFromPathData(
        "M11.5,22V17.35C11,18.13 10,19.09 8.03,19.81C8.03,19.81 8.53,18.1 9.94,16.95C8.64,17.23 6.68,17.19 4,16C4,16 6.47,14.59 9.28,14.97C7.69,14 5.7,12.08 4.17,8.11C4.17,8.11 8.67,9.34 10.91,13.14C8.88,8.24 12,2 12,2C14.43,7.47 13.91,11.1 13.12,13.1C15.37,9.33 19.83,8.11 19.83,8.11C18.3,12.08 16.31,14 14.72,14.97C17.53,14.59 20,16 20,16C17.32,17.19 15.36,17.23 14.06,16.95C15.47,18.1 15.97,19.81 15.97,19.81C14,19.09 13,18.13 12.5,17.35V22H11.5Z",
    ) ?: Path()
    private val transformedLeafPath = Path()
    private val transformMatrix = Matrix()

    private var snapshot = CapsuleRuntime.snapshot()
    private var expanded = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false

    init {
        isClickable = true
    }

    fun setSnapshot(value: CapsuleSnapshot) {
        snapshot = value
        expanded = value.expanded
        postInvalidateOnAnimation()
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (expanded) drawExpanded(canvas) else drawCompact(canvas)
    }

    private fun drawCompact(canvas: Canvas) {
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = height * 0.46f
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(Color.rgb(6, 9, 21), Color.rgb(30, 10, 39), Color.rgb(3, 24, 28)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        fillPaint.shader = null

        strokePaint.strokeWidth = dp(1.2f)
        strokePaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(Color.rgb(80, 255, 198), Color.rgb(56, 197, 255), Color.rgb(232, 70, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f)),
            radius,
            radius,
            strokePaint,
        )
        strokePaint.shader = null

        val padding = dp(7f)
        val artSize = height - padding * 2f
        val artRect = RectF(padding, padding, padding + artSize, padding + artSize)
        drawArtworkOrLeaf(canvas, artRect, compact = true)

        val barsWidth = min(dp(88f), width * 0.28f)
        val barsRect = RectF(
            width - padding - barsWidth,
            padding + dp(3f),
            width - padding,
            height - padding - dp(3f),
        )
        drawLinearLevels(canvas, barsRect, compact = true)

        val textLeft = artRect.right + dp(10f)
        val textRight = barsRect.left - dp(8f)
        titlePaint.textSize = dp(13.5f)
        subtitlePaint.textSize = dp(10.5f)
        drawEllipsizedText(canvas, snapshot.title, textLeft, height * 0.45f, textRight - textLeft, titlePaint)
        val subtitle = snapshot.artist.ifBlank {
            when {
                snapshot.analyzerRunning -> "Audioanalyse aktiv"
                else -> "Antippen zum Öffnen"
            }
        }
        drawEllipsizedText(canvas, subtitle, textLeft, height * 0.72f, textRight - textLeft, subtitlePaint)

        fillPaint.color = when {
            snapshot.signal > 0.01f -> Color.rgb(75, 255, 176)
            snapshot.analyzerRunning -> Color.rgb(255, 194, 76)
            else -> Color.rgb(122, 135, 158)
        }
        canvas.drawCircle(width - dp(7f), dp(7f), dp(2.3f), fillPaint)
    }

    private fun drawExpanded(canvas: Canvas) {
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val corner = dp(28f)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(Color.rgb(2, 4, 12), Color.rgb(10, 12, 34), Color.rgb(3, 24, 27)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(outer, corner, corner, fillPaint)
        fillPaint.shader = null

        strokePaint.strokeWidth = dp(1.2f)
        strokePaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(Color.rgb(72, 255, 183), Color.rgb(55, 195, 255), Color.rgb(232, 70, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f)),
            corner,
            corner,
            strokePaint,
        )
        strokePaint.shader = null

        drawCloseButton(canvas)
        titlePaint.textSize = dp(18f)
        subtitlePaint.textSize = dp(12f)
        drawEllipsizedText(canvas, snapshot.title, dp(22f), dp(38f), width - dp(86f), titlePaint)
        drawEllipsizedText(
            canvas,
            snapshot.artist.ifBlank { packageDisplayName() },
            dp(22f),
            dp(58f),
            width - dp(86f),
            subtitlePaint,
        )

        val centerX = width * 0.5f
        val centerY = height * 0.43f
        val leafSize = min(width * 0.52f, height * 0.43f)
        drawRadialLevels(canvas, centerX, centerY, leafSize * 0.57f)
        drawArtworkOrLeaf(
            canvas,
            RectF(
                centerX - leafSize / 2f,
                centerY - leafSize / 2f,
                centerX + leafSize / 2f,
                centerY + leafSize / 2f,
            ),
            compact = false,
        )

        drawLinearLevels(
            canvas,
            RectF(dp(24f), height * 0.70f, width - dp(24f), height * 0.80f),
            compact = false,
        )
        drawTransportControls(canvas)

        labelPaint.textSize = dp(10.5f)
        val status = when {
            snapshot.signal > 0.01f -> "LIVE FFT · ${(snapshot.signal * 100).toInt()}% · ${snapshot.source}"
            snapshot.analyzerRunning -> "Capture aktiv · wartet auf internes Audiosignal"
            else -> "Audioanalyse aus · App öffnen und Audio starten"
        }
        canvas.drawText(status, dp(22f), height - dp(15f), labelPaint)
    }

    private fun packageDisplayName(): String {
        return snapshot.packageName.substringAfterLast('.').ifBlank { "Music Capsule" }
    }

    private fun drawArtworkOrLeaf(canvas: Canvas, rect: RectF, compact: Boolean) {
        val artwork = snapshot.artwork
        if (artwork != null && !artwork.isRecycled) {
            drawArtwork(canvas, artwork, rect)
            return
        }
        drawLeaf(canvas, rect, compact)
    }

    private fun drawArtwork(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val save = canvas.save()
        val radius = if (expanded) rect.width() * 0.5f else dp(15f)
        canvas.clipRounded(rect, radius, radius)
        val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val sourceWidth = rect.width() / scale
        val sourceHeight = rect.height() / scale
        val left = (bitmap.width - sourceWidth) / 2f
        val top = (bitmap.height - sourceHeight) / 2f
        canvas.drawBitmapCropped(
            bitmap,
            RectF(left, top, left + sourceWidth, top + sourceHeight),
            rect,
            fillPaint,
        )
        canvas.restoreToCount(save)
    }

    private fun drawLeaf(canvas: Canvas, rect: RectF, compact: Boolean) {
        val pulse = 1f + snapshot.signal * if (compact) 0.035f else 0.065f
        val cx = rect.centerX()
        val cy = rect.centerY()
        val target = RectF(
            cx - rect.width() * pulse / 2f,
            cy - rect.height() * pulse / 2f,
            cx + rect.width() * pulse / 2f,
            cy + rect.height() * pulse / 2f,
        )
        transformMatrix.reset()
        transformMatrix.setRectToRect(RectF(0f, 0f, 24f, 24f), target, Matrix.ScaleToFit.CENTER)
        transformedLeafPath.reset()
        sourceLeafPath.transform(transformMatrix, transformedLeafPath)

        val phase = (SystemClock.uptimeMillis() % 9000L) / 9000f
        val colors = intArrayOf(
            hsv(132f + phase * 35f, .84f, 1f),
            hsv(188f + phase * 46f, .82f, 1f),
            hsv(300f + phase * 30f, .72f, 1f),
        )
        fillPaint.shader = LinearGradient(
            target.left,
            target.top,
            target.right,
            target.bottom,
            colors,
            null,
            Shader.TileMode.CLAMP,
        )
        fillPaint.color = Color.WHITE
        canvas.drawPath(transformedLeafPath, fillPaint)
        fillPaint.shader = null

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = if (compact) dp(.7f) else dp(1.3f)
        strokePaint.color = Color.argb(220, 235, 255, 249)
        canvas.drawPath(transformedLeafPath, strokePaint)
    }

    private fun drawLinearLevels(canvas: Canvas, rect: RectF, compact: Boolean) {
        val levels = snapshot.levels
        val count = if (compact) 7 else 16
        val gap = if (compact) dp(3.1f) else dp(4f)
        val barWidth = (rect.width() - gap * (count - 1)) / count
        for (index in 0 until count) {
            val sourceIndex = ((index.toFloat() / max(1, count - 1)) * levels.lastIndex).toInt()
            val level = (levels.getOrNull(sourceIndex) ?: 0f).coerceIn(0f, 1f)
            val floor = if (snapshot.analyzerRunning) 0.06f else 0.02f
            val shaped = max(floor, level.pow(.64f))
            val barHeight = rect.height() * shaped
            val left = rect.left + index * (barWidth + gap)
            fillPaint.color = hsv(132f + index * (225f / count), .84f, 1f, if (snapshot.analyzerRunning) .94f else .35f)
            canvas.drawRoundRect(
                RectF(left, rect.bottom - barHeight, left + barWidth, rect.bottom),
                barWidth * .45f,
                barWidth * .45f,
                fillPaint,
            )
        }
    }

    private fun drawRadialLevels(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val levels = snapshot.levels
        val count = 40
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        for (index in 0 until count) {
            val levelIndex = ((index.toFloat() / count) * levels.size).toInt().coerceAtMost(levels.lastIndex)
            val level = (levels.getOrNull(levelIndex) ?: 0f).coerceIn(0f, 1f)
            val shaped = max(if (snapshot.analyzerRunning) .035f else .012f, level.pow(.66f))
            val angle = index.toDouble() / count * PI * 2.0 - PI / 2.0
            val outer = radius + dp(7f) + shaped * dp(35f)
            strokePaint.color = hsv(125f + index * (235f / count), .84f, 1f, .28f + shaped * .68f)
            strokePaint.strokeWidth = dp(1.1f) + shaped * dp(2.2f)
            canvas.drawLine(
                cx + cos(angle).toFloat() * radius,
                cy + sin(angle).toFloat() * radius,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                strokePaint,
            )
        }
    }

    private fun drawCloseButton(canvas: Canvas) {
        val cx = width - dp(27f)
        val cy = dp(27f)
        fillPaint.color = Color.argb(92, 255, 255, 255)
        canvas.drawCircle(cx, cy, dp(15f), fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - dp(5f), cy - dp(5f), cx + dp(5f), cy + dp(5f), strokePaint)
        canvas.drawLine(cx + dp(5f), cy - dp(5f), cx - dp(5f), cy + dp(5f), strokePaint)
    }

    private fun drawTransportControls(canvas: Canvas) {
        val y = height * .87f
        val xs = floatArrayOf(width * .32f, width * .50f, width * .68f)
        xs.forEachIndexed { index, x ->
            fillPaint.color = Color.argb(if (index == 1) 96 else 72, 255, 255, 255)
            canvas.drawCircle(x, y, if (index == 1) dp(26f) else dp(23f), fillPaint)
        }
        fillPaint.color = Color.WHITE
        drawPreviousIcon(canvas, xs[0], y)
        if (snapshot.isPlaying) drawPauseIcon(canvas, xs[1], y) else drawPlayIcon(canvas, xs[1], y)
        drawNextIcon(canvas, xs[2], y)
    }

    private fun drawPlayIcon(canvas: Canvas, x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x - dp(5f), y - dp(8f))
            lineTo(x + dp(9f), y)
            lineTo(x - dp(5f), y + dp(8f))
            close()
        }
        canvas.drawPath(path, fillPaint)
    }

    private fun drawPauseIcon(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRoundRect(RectF(x - dp(7f), y - dp(8f), x - dp(2f), y + dp(8f)), dp(1.5f), dp(1.5f), fillPaint)
        canvas.drawRoundRect(RectF(x + dp(2f), y - dp(8f), x + dp(7f), y + dp(8f)), dp(1.5f), dp(1.5f), fillPaint)
    }

    private fun drawPreviousIcon(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(x - dp(8f), y - dp(8f), x - dp(5f), y + dp(8f), fillPaint)
        val path = Path().apply {
            moveTo(x + dp(7f), y - dp(8f))
            lineTo(x - dp(4f), y)
            lineTo(x + dp(7f), y + dp(8f))
            close()
        }
        canvas.drawPath(path, fillPaint)
    }

    private fun drawNextIcon(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(x + dp(5f), y - dp(8f), x + dp(8f), y + dp(8f), fillPaint)
        val path = Path().apply {
            moveTo(x - dp(7f), y - dp(8f))
            lineTo(x + dp(4f), y)
            lineTo(x - dp(7f), y + dp(8f))
            close()
        }
        canvas.drawPath(path, fillPaint)
    }

    private fun drawEllipsizedText(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        paint: TextPaint,
    ) {
        if (maxWidth <= 0f) return
        val text = value.ifBlank { " " }
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, baseline, paint)
            return
        }
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end -= 1
        canvas.drawText(text.substring(0, end) + "…", x, baseline, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.rawX - downRawX, event.rawY - downRawY) > touchSlop) moved = true
                if (moved) {
                    onMove?.invoke(event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) handleTap(event.x, event.y)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        if (!expanded) {
            onExpandedChanged?.invoke(true)
            return
        }
        if (x > width - dp(58f) && y < dp(58f)) {
            onExpandedChanged?.invoke(false)
            return
        }
        if (y >= height * .80f && y <= height * .95f) {
            when {
                x < width * .41f -> MediaControllerBridge.skipPrevious()
                x > width * .59f -> MediaControllerBridge.skipNext()
                else -> MediaControllerBridge.togglePlayPause()
            }
            return
        }
        onExpandedChanged?.invoke(false)
    }

    private fun dp(value: Float): Float = value * density

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Int {
        val color = Color.HSVToColor(floatArrayOf((hue % 360f + 360f) % 360f, saturation, value))
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }
}
