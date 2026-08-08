package com.skallahaze.irbloasster.capsule

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

class MusicCapsuleOverlayView(context: Context) : View(context) {
    var onExpandedChanged: ((Boolean) -> Unit)? = null
    var onMove: ((Float, Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(176, 220, 229, 245)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 255, 255, 255)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private val sourceLeafPath: Path = PathParser.createPathFromPathData(
        "M11.5,22V17.35C11,18.13 10,19.09 8.03,19.81C8.03,19.81 8.53,18.1 9.94,16.95C8.64,17.23 6.68,17.19 4,16C4,16 6.47,14.59 9.28,14.97C7.69,14 5.7,12.08 4.17,8.11C4.17,8.11 8.67,9.34 10.91,13.14C8.88,8.24 12,2 12,2C14.43,7.47 13.91,11.1 13.12,13.1C15.37,9.33 19.83,8.11 19.83,8.11C18.3,12.08 16.31,14 14.72,14.97C17.53,14.59 20,16 20,16C17.32,17.19 15.36,17.23 14.06,16.95C15.47,18.1 15.97,19.81 15.97,19.81C14,19.09 13,18.13 12.5,17.35V22H11.5Z",
    ) ?: Path()
    private val transformedLeafPath = Path()
    private val transformMatrix = Matrix()

    private var snapshot = MusicCapsuleRuntime.snapshot()
    private var expanded = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
    }

    fun setSnapshot(value: MusicCapsuleSnapshot) {
        snapshot = value
        if (expanded != value.expanded) expanded = value.expanded
        postInvalidateOnAnimation()
    }

    fun setExpanded(value: Boolean) {
        if (expanded == value) return
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
        val phase = (SystemClock.uptimeMillis() % 9000L) / 9000f
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(7, 10, 22),
                hsv(282f + phase * 40f, 0.68f, 0.22f),
                Color.rgb(5, 20, 26),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        backgroundPaint.setShadowLayer(dp(15f), 0f, dp(5f), Color.argb(110, 0, 0, 0))
        canvas.drawRoundRect(bounds, radius, radius, backgroundPaint)
        backgroundPaint.clearShadowLayer()

        borderPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(
                Color.argb(210, 64, 255, 190),
                Color.argb(200, 55, 196, 255),
                Color.argb(200, 232, 67, 255),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(dp(0.8f), dp(0.8f), width - dp(0.8f), height - dp(0.8f)),
            radius,
            radius,
            borderPaint,
        )
        borderPaint.shader = null

        val padding = dp(7f)
        val artSize = height - padding * 2f
        val artRect = RectF(padding, padding, padding + artSize, padding + artSize)
        drawArtworkOrLeaf(canvas, artRect, compact = true)

        val barsWidth = min(dp(86f), width * 0.28f)
        val barsRect = RectF(
            width - padding - barsWidth,
            padding + dp(3f),
            width - padding,
            height - padding - dp(3f),
        )
        drawLinearLevels(canvas, barsRect, snapshot.levels, compact = true)

        val textLeft = artRect.right + dp(10f)
        val textRight = barsRect.left - dp(8f)
        titlePaint.textSize = dp(13.5f)
        subtitlePaint.textSize = dp(10.5f)
        drawEllipsizedText(
            canvas,
            snapshot.title,
            textLeft,
            height * 0.45f,
            textRight - textLeft,
            titlePaint,
        )
        val subtitle = snapshot.artist.ifBlank {
            if (snapshot.analyserActive) "Audio-Visualizer aktiv" else "Antippen zum Öffnen"
        }
        drawEllipsizedText(
            canvas,
            subtitle,
            textLeft,
            height * 0.72f,
            textRight - textLeft,
            subtitlePaint,
        )

        if (snapshot.isPlaying) {
            glowPaint.color = Color.rgb(255, 55, 112)
            glowPaint.setShadowLayer(dp(6f), 0f, 0f, glowPaint.color)
            canvas.drawCircle(width - dp(7f), dp(7f), dp(2.2f), glowPaint)
            glowPaint.clearShadowLayer()
        }
    }

    private fun drawExpanded(canvas: Canvas) {
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val corner = dp(28f)
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(2, 4, 12),
                Color.rgb(9, 12, 31),
                Color.rgb(4, 18, 23),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        backgroundPaint.setShadowLayer(dp(24f), 0f, dp(8f), Color.argb(140, 0, 0, 0))
        canvas.drawRoundRect(outer, corner, corner, backgroundPaint)
        backgroundPaint.clearShadowLayer()

        borderPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.argb(230, 61, 255, 178),
                Color.argb(215, 58, 200, 255),
                Color.argb(220, 234, 70, 255),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f)),
            corner,
            corner,
            borderPaint,
        )
        borderPaint.shader = null

        drawCloseButton(canvas)

        titlePaint.textSize = dp(18f)
        subtitlePaint.textSize = dp(12f)
        drawEllipsizedText(canvas, snapshot.title, dp(22f), dp(38f), width - dp(86f), titlePaint)
        drawEllipsizedText(
            canvas,
            snapshot.artist.ifBlank { snapshot.packageName.ifBlank { "SmartIR Music Capsule" } },
            dp(22f),
            dp(58f),
            width - dp(86f),
            subtitlePaint,
        )

        val centerX = width * 0.5f
        val centerY = height * 0.43f
        val leafSize = min(width * 0.52f, height * 0.43f)
        val leafRect = RectF(
            centerX - leafSize / 2f,
            centerY - leafSize / 2f,
            centerX + leafSize / 2f,
            centerY + leafSize / 2f,
        )
        drawRadialLevels(canvas, centerX, centerY, leafSize * 0.55f)
        drawArtworkOrLeaf(canvas, leafRect, compact = false)

        val linearRect = RectF(
            dp(24f),
            height * 0.70f,
            width - dp(24f),
            height * 0.80f,
        )
        drawLinearLevels(canvas, linearRect, snapshot.levels, compact = false)

        drawTransportControls(canvas)

        labelPaint.textSize = dp(10.5f)
        val status = when {
            snapshot.analyserActive && snapshot.signal > 0.02f -> "LIVE FFT · ${(snapshot.signal * 100).toInt()}%"
            snapshot.analyserActive -> "FFT bereit · wartet auf Musik"
            else -> "Visualizer nicht verfügbar · Berechtigungen prüfen"
        }
        canvas.drawText(status, dp(22f), height - dp(15f), labelPaint)
    }

    private fun drawArtworkOrLeaf(canvas: Canvas, rect: RectF, compact: Boolean) {
        val artwork = snapshot.artwork
        if (artwork != null && !artwork.isRecycled) {
            drawArtwork(canvas, artwork, rect)
            if (!compact) {
                levelPaint.style = Paint.Style.STROKE
                levelPaint.strokeWidth = dp(2f)
                levelPaint.color = Color.argb(190, 76, 255, 190)
                canvas.drawOval(rect, levelPaint)
                levelPaint.style = Paint.Style.FILL
            }
            return
        }
        drawLeaf(canvas, rect, compact)
    }

    private fun drawArtwork(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val save = canvas.save()
        val radius = if (expanded) rect.width() * 0.5f else dp(15f)
        canvas.clipRoundRect(rect, radius, radius)
        val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val sourceWidth = rect.width() / scale
        val sourceHeight = rect.height() / scale
        val left = (bitmap.width - sourceWidth) / 2f
        val top = (bitmap.height - sourceHeight) / 2f
        val source = RectF(left, top, left + sourceWidth, top + sourceHeight)
        canvas.drawBitmap(bitmap, source, rect, levelPaint)
        canvas.restoreToCount(save)
    }

    private fun drawLeaf(canvas: Canvas, rect: RectF, compact: Boolean) {
        val pulse = if (snapshot.isPlaying) 1f + snapshot.signal * 0.06f else 1f
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

        val phase = ((SystemClock.uptimeMillis() % 7000L) / 7000f) * 360f
        levelPaint.shader = LinearGradient(
            target.left,
            target.top,
            target.right,
            target.bottom,
            intArrayOf(
                hsv(135f + phase * 0.18f, 0.88f, 1f),
                hsv(185f + phase * 0.22f, 0.78f, 1f),
                hsv(305f + phase * 0.15f, 0.72f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        levelPaint.setShadowLayer(
            if (compact) dp(7f) else dp(18f),
            0f,
            0f,
            Color.argb(190, 27, 255, 162),
        )
        canvas.drawPath(transformedLeafPath, levelPaint)
        levelPaint.clearShadowLayer()
        levelPaint.shader = null

        if (!compact) {
            levelPaint.style = Paint.Style.STROKE
            levelPaint.strokeWidth = dp(1.2f)
            levelPaint.color = Color.argb(200, 238, 255, 250)
            canvas.drawPath(transformedLeafPath, levelPaint)
            levelPaint.style = Paint.Style.FILL
        }
    }

    private fun drawLinearLevels(
        canvas: Canvas,
        rect: RectF,
        levels: FloatArray,
        compact: Boolean,
    ) {
        val count = if (compact) 7 else min(levels.size, 16)
        val gap = if (compact) dp(3.2f) else dp(4f)
        val barWidth = (rect.width() - gap * (count - 1)) / count
        val timeHue = ((SystemClock.uptimeMillis() % 10000L) / 10000f) * 360f
        for (index in 0 until count) {
            val sourceIndex = ((index.toFloat() / max(1, count - 1)) * (levels.size - 1)).toInt()
            val level = (levels.getOrNull(sourceIndex) ?: 0f).coerceIn(0f, 1f)
            val idle = if (snapshot.analyserActive) 0.08f else 0.03f
            val shaped = max(idle, level.pow(0.62f))
            val barHeight = rect.height() * shaped
            val left = rect.left + index * (barWidth + gap)
            val top = rect.bottom - barHeight
            val hue = (130f + index * (230f / count) + timeHue * 0.12f) % 360f
            levelPaint.color = hsv(hue, 0.82f, 1f, if (snapshot.analyserActive) 0.95f else 0.38f)
            canvas.drawRoundRect(
                RectF(left, top, left + barWidth, rect.bottom),
                barWidth * 0.45f,
                barWidth * 0.45f,
                levelPaint,
            )
        }
    }

    private fun drawRadialLevels(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val levels = snapshot.levels
        val count = 40
        val phase = ((SystemClock.uptimeMillis() % 12000L) / 12000f) * 360f
        for (index in 0 until count) {
            val levelIndex = ((index.toFloat() / count) * levels.size).toInt().coerceAtMost(levels.lastIndex)
            val level = (levels.getOrNull(levelIndex) ?: 0f).coerceIn(0f, 1f)
            val shaped = max(if (snapshot.analyserActive) 0.04f else 0.015f, level.pow(0.66f))
            val angle = index.toDouble() / count * PI * 2.0 - PI / 2.0
            val inner = radius
            val outer = radius + dp(8f) + shaped * dp(35f)
            val hue = (125f + index * (235f / count) + phase * 0.1f) % 360f
            levelPaint.color = hsv(hue, 0.84f, 1f, 0.28f + shaped * 0.68f)
            levelPaint.style = Paint.Style.STROKE
            levelPaint.strokeWidth = dp(1.1f) + shaped * dp(2.2f)
            levelPaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                levelPaint,
            )
        }
        levelPaint.style = Paint.Style.FILL
    }

    private fun drawCloseButton(canvas: Canvas) {
        val cx = width - dp(27f)
        val cy = dp(27f)
        iconPaint.color = Color.argb(110, 255, 255, 255)
        canvas.drawCircle(cx, cy, dp(15f), iconPaint)
        iconPaint.color = Color.WHITE
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - dp(5f), cy - dp(5f), cx + dp(5f), cy + dp(5f), iconPaint)
        canvas.drawLine(cx + dp(5f), cy - dp(5f), cx - dp(5f), cy + dp(5f), iconPaint)
        iconPaint.style = Paint.Style.FILL
    }

    private fun drawTransportControls(canvas: Canvas) {
        val centerY = height * 0.87f
        val previousX = width * 0.32f
        val playX = width * 0.50f
        val nextX = width * 0.68f
        val radius = dp(23f)

        drawControlCircle(canvas, previousX, centerY, radius)
        drawControlCircle(canvas, playX, centerY, radius * 1.12f)
        drawControlCircle(canvas, nextX, centerY, radius)

        iconPaint.color = Color.WHITE
        drawPreviousIcon(canvas, previousX, centerY)
        if (snapshot.isPlaying) drawPauseIcon(canvas, playX, centerY) else drawPlayIcon(canvas, playX, centerY)
        drawNextIcon(canvas, nextX, centerY)
    }

    private fun drawControlCircle(canvas: Canvas, x: Float, y: Float, radius: Float) {
        iconPaint.color = Color.argb(78, 255, 255, 255)
        iconPaint.setShadowLayer(dp(10f), 0f, 0f, Color.argb(150, 58, 214, 255))
        canvas.drawCircle(x, y, radius, iconPaint)
        iconPaint.clearShadowLayer()
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dp(1f)
        iconPaint.color = Color.argb(180, 108, 255, 216)
        canvas.drawCircle(x, y, radius, iconPaint)
        iconPaint.style = Paint.Style.FILL
    }

    private fun drawPlayIcon(canvas: Canvas, x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x - dp(5f), y - dp(8f))
            lineTo(x + dp(9f), y)
            lineTo(x - dp(5f), y + dp(8f))
            close()
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawPauseIcon(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRoundRect(
            RectF(x - dp(7f), y - dp(8f), x - dp(2f), y + dp(8f)),
            dp(1.5f),
            dp(1.5f),
            iconPaint,
        )
        canvas.drawRoundRect(
            RectF(x + dp(2f), y - dp(8f), x + dp(7f), y + dp(8f)),
            dp(1.5f),
            dp(1.5f),
            iconPaint,
        )
    }

    private fun drawPreviousIcon(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(x - dp(8f), y - dp(8f), x - dp(5f), y + dp(8f), iconPaint)
        val path = Path().apply {
            moveTo(x + dp(7f), y - dp(8f))
            lineTo(x - dp(4f), y)
            lineTo(x + dp(7f), y + dp(8f))
            close()
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawNextIcon(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(x + dp(5f), y - dp(8f), x + dp(8f), y + dp(8f), iconPaint)
        val path = Path().apply {
            moveTo(x - dp(7f), y - dp(8f))
            lineTo(x + dp(4f), y)
            lineTo(x - dp(7f), y + dp(8f))
            close()
        }
        canvas.drawPath(path, iconPaint)
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
        val ellipsis = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end -= 1
        }
        canvas.drawText(text.substring(0, end) + ellipsis, x, baseline, paint)
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
                val totalDistance = hypot(event.rawX - downRawX, event.rawY - downRawY)
                if (totalDistance > touchSlop) moved = true
                if (moved) {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    onMove?.invoke(dx, dy)
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

        if (y >= height * 0.80f && y <= height * 0.95f) {
            when {
                x < width * 0.41f -> MusicCapsuleMediaController.skipPrevious()
                x > width * 0.59f -> MusicCapsuleMediaController.skipNext()
                else -> MusicCapsuleMediaController.togglePlayPause()
            }
            return
        }

        onExpandedChanged?.invoke(false)
    }

    private fun dp(value: Float): Float = value * density

    private fun hsv(
        hue: Float,
        saturation: Float,
        value: Float,
        alpha: Float = 1f,
    ): Int {
        val color = Color.HSVToColor(floatArrayOf((hue % 360f + 360f) % 360f, saturation, value))
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }
}
