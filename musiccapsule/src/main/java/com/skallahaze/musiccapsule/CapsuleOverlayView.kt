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
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class CapsuleOverlayView(context: Context) : View(context), Choreographer.FrameCallback {
    var onModeChanged: ((CapsuleMode) -> Unit)? = null
    var onMove: ((Float, Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(186, 214, 226, 244)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(224, 255, 255, 255)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private val sourceLeafPath: Path = PathParser.createPathFromPathData(
        "M11.5,22V17.35C11,18.13 10,19.09 8.03,19.81C8.03,19.81 8.53,18.1 9.94,16.95C8.64,17.23 6.68,17.19 4,16C4,16 6.47,14.59 9.28,14.97C7.69,14 5.7,12.08 4.17,8.11C4.17,8.11 8.67,9.34 10.91,13.14C8.88,8.24 12,2 12,2C14.43,7.47 13.91,11.1 13.12,13.1C15.37,9.33 19.83,8.11 19.83,8.11C18.3,12.08 16.31,14 14.72,14.97C17.53,14.59 20,16 20,16C17.32,17.19 15.36,17.23 14.06,16.95C15.47,18.1 15.97,19.81 15.97,19.81C14,19.09 13,18.13 12.5,17.35V22H11.5Z",
    ) ?: Path()
    private val transformedLeafPath = Path()
    private val transformMatrix = Matrix()
    private val smoothedLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

    private var snapshot = CapsuleRuntime.snapshot()
    private var mode = snapshot.mode
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downAt = 0L
    private var moved = false
    private var attached = false
    private var lastFrameNanos = 0L
    private var colorPhase = 0f

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
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
        val delta = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((frameTimeNanos - lastFrameNanos).coerceAtLeast(1L) / 1_000_000_000f)
                .coerceIn(1f / 240f, 1f / 20f)
        }
        lastFrameNanos = frameTimeNanos

        val response = 1f - exp(-delta * 14f)
        for (index in smoothedLevels.indices) {
            val target = snapshot.levels.getOrNull(index) ?: 0f
            val factor = if (target > smoothedLevels[index]) response * 1.34f else response * .55f
            smoothedLevels[index] += (target - smoothedLevels[index]) * factor.coerceIn(0f, 1f)
        }
        val speed = if (snapshot.signal > .008f) 21f + snapshot.signal * 54f else 2.2f
        colorPhase = (colorPhase + delta * speed) % 360f
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun setSnapshot(value: CapsuleSnapshot) {
        snapshot = value
        mode = value.mode
    }

    fun setMode(value: CapsuleMode) {
        mode = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            CapsuleMode.RIM -> drawRim(canvas)
            CapsuleMode.COMPACT -> drawCompact(canvas)
            CapsuleMode.EXPANDED -> drawExpanded(canvas)
        }
    }

    private fun drawRim(canvas: Canvas) {
        val outer = RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f))
        val radius = height * .48f
        fillPaint.color = Color.argb(50, 4, 7, 15)
        canvas.drawRoundRect(outer, radius, radius, fillPaint)
        drawNeonBorder(canvas, outer, radius, glow = 1.22f)

        val bars = 10
        val totalWidth = width * .42f
        val startX = width / 2f - totalWidth / 2f
        val gap = dp(3f)
        val barWidth = (totalWidth - gap * (bars - 1)) / bars
        val baseline = height * .61f
        for (index in 0 until bars) {
            val sourceIndex = ((index.toFloat() / max(1, bars - 1)) * smoothedLevels.lastIndex).toInt()
            val level = max(.04f, smoothedLevels[sourceIndex].pow(.66f))
            val barHeight = dp(1.2f) + level * height * .42f
            fillPaint.color = hsv(colorPhase + index * 25f, .88f, 1f, .52f + level * .45f)
            canvas.drawRoundRect(
                RectF(
                    startX + index * (barWidth + gap),
                    baseline - barHeight,
                    startX + index * (barWidth + gap) + barWidth,
                    baseline,
                ),
                barWidth,
                barWidth,
                fillPaint,
            )
        }
    }

    private fun drawCompact(canvas: Canvas) {
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = height * .46f
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(4, 8, 18),
                Color.rgb(15, 8, 31),
                Color.rgb(2, 24, 30),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        fillPaint.shader = null
        drawNeonBorder(
            canvas,
            RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f)),
            radius,
            glow = 1f + snapshot.signal * .6f,
        )

        val padding = dp(6f)
        val artSize = height - padding * 2f
        val artRect = RectF(padding, padding, padding + artSize, padding + artSize)
        drawArtworkOrLeaf(canvas, artRect, compact = true)

        val minimizeWidth = dp(19f)
        val barsWidth = min(dp(70f), width * .23f)
        val barsRect = RectF(
            width - padding - minimizeWidth - barsWidth,
            padding + dp(5f),
            width - padding - minimizeWidth - dp(3f),
            height - padding - dp(5f),
        )
        drawLinearLevels(canvas, barsRect, count = 7, compact = true)

        val textLeft = artRect.right + dp(9f)
        val textRight = barsRect.left - dp(7f)
        titlePaint.textSize = dp(12.8f)
        subtitlePaint.textSize = dp(9.8f)
        drawEllipsizedText(canvas, snapshot.title, textLeft, height * .43f, textRight - textLeft, titlePaint)
        val subtitle = snapshot.artist.ifBlank {
            when {
                snapshot.analyzerRunning -> "Audioanalyse aktiv"
                else -> "Antippen zum Öffnen"
            }
        }
        drawEllipsizedText(canvas, subtitle, textLeft, height * .71f, textRight - textLeft, subtitlePaint)

        val dashCenterX = width - padding - minimizeWidth / 2f
        strokePaint.shader = null
        strokePaint.color = Color.argb(205, 223, 232, 246)
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawLine(
            dashCenterX - dp(4f),
            height * .50f,
            dashCenterX + dp(4f),
            height * .50f,
            strokePaint,
        )

        fillPaint.color = when {
            snapshot.signal > .01f -> Color.rgb(70, 255, 179)
            snapshot.analyzerRunning -> Color.rgb(255, 194, 74)
            else -> Color.rgb(114, 126, 151)
        }
        canvas.drawCircle(width - dp(7f), dp(7f), dp(2f), fillPaint)
    }

    private fun drawExpanded(canvas: Canvas) {
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val corner = dp(28f)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(1, 4, 12),
                Color.rgb(13, 9, 34),
                Color.rgb(2, 25, 29),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(outer, corner, corner, fillPaint)
        fillPaint.shader = null
        drawNeonBorder(
            canvas,
            RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f)),
            corner,
            glow = 1.18f,
        )

        drawCloseButton(canvas)
        titlePaint.textSize = dp(18f)
        subtitlePaint.textSize = dp(12f)
        drawEllipsizedText(canvas, snapshot.title, dp(22f), dp(38f), width - dp(86f), titlePaint)
        drawEllipsizedText(
            canvas,
            snapshot.artist.ifBlank { packageDisplayName() },
            dp(22f),
            dp(59f),
            width - dp(86f),
            subtitlePaint,
        )

        val centerX = width * .5f
        val centerY = height * .42f
        val leafSize = min(width * .52f, height * .42f)
        drawRadialLevels(canvas, centerX, centerY, leafSize * .58f)
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
            RectF(dp(24f), height * .69f, width - dp(24f), height * .79f),
            count = 16,
            compact = false,
        )
        drawTransportControls(canvas)

        labelPaint.textSize = dp(10.5f)
        val status = when {
            snapshot.signal > .01f -> "LIVE FFT · ${(snapshot.signal * 100).toInt()}% · ${snapshot.source}"
            snapshot.analyzerRunning -> "Capture aktiv · wartet auf internes Audiosignal"
            else -> "Audioanalyse aus · Music Capsule öffnen und starten"
        }
        canvas.drawText(status, dp(22f), height - dp(15f), labelPaint)
    }

    private fun drawNeonBorder(canvas: Canvas, rect: RectF, radius: Float, glow: Float) {
        val shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                hsv(colorPhase + 150f, .94f, 1f),
                hsv(colorPhase + 218f, .92f, 1f),
                hsv(colorPhase + 303f, .90f, 1f),
                hsv(colorPhase + 28f, .94f, 1f),
                hsv(colorPhase + 105f, .90f, 1f),
            ),
            null,
            Shader.TileMode.MIRROR,
        )
        strokePaint.shader = shader

        strokePaint.alpha = 48
        strokePaint.strokeWidth = dp(8f) * glow.coerceIn(.8f, 1.45f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.alpha = 120
        strokePaint.strokeWidth = dp(3.2f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.alpha = 245
        strokePaint.strokeWidth = dp(1.15f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun packageDisplayName(): String =
        snapshot.packageName.substringAfterLast('.').ifBlank { "Music Capsule" }

    private fun drawArtworkOrLeaf(canvas: Canvas, rect: RectF, compact: Boolean) {
        val artwork = snapshot.artwork
        if (artwork != null && !artwork.isRecycled) {
            drawArtwork(canvas, artwork, rect)
        } else {
            drawLeaf(canvas, rect, compact)
        }
    }

    private fun drawArtwork(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val save = canvas.save()
        val radius = if (mode == CapsuleMode.EXPANDED) rect.width() * .5f else dp(13f)
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
        val pulse = 1f + snapshot.signal * if (compact) .028f else .058f
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

        fillPaint.shader = LinearGradient(
            target.left,
            target.top,
            target.right,
            target.bottom,
            intArrayOf(
                hsv(colorPhase + 118f, .78f, 1f),
                hsv(colorPhase + 188f, .84f, 1f),
                hsv(colorPhase + 286f, .78f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(transformedLeafPath, fillPaint)
        fillPaint.shader = null

        strokePaint.color = Color.argb(224, 238, 255, 250)
        strokePaint.strokeWidth = if (compact) dp(.65f) else dp(1.2f)
        canvas.drawPath(transformedLeafPath, strokePaint)
    }

    private fun drawLinearLevels(
        canvas: Canvas,
        rect: RectF,
        count: Int,
        compact: Boolean,
    ) {
        val gap = if (compact) dp(2.8f) else dp(4f)
        val barWidth = (rect.width() - gap * (count - 1)) / count
        for (index in 0 until count) {
            val sourceIndex = ((index.toFloat() / max(1, count - 1)) * smoothedLevels.lastIndex).toInt()
            val level = smoothedLevels.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
            val floor = if (snapshot.analyzerRunning) .055f else .016f
            val shaped = max(floor, level.pow(.64f))
            val barHeight = rect.height() * shaped
            val left = rect.left + index * (barWidth + gap)
            fillPaint.color = hsv(
                colorPhase + 118f + index * (235f / count),
                .88f,
                1f,
                if (snapshot.analyzerRunning) .95f else .34f,
            )
            canvas.drawRoundRect(
                RectF(left, rect.bottom - barHeight, left + barWidth, rect.bottom),
                barWidth * .45f,
                barWidth * .45f,
                fillPaint,
            )
        }
    }

    private fun drawRadialLevels(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val count = 40
        for (index in 0 until count) {
            val levelIndex = ((index.toFloat() / count) * smoothedLevels.size)
                .toInt()
                .coerceAtMost(smoothedLevels.lastIndex)
            val level = smoothedLevels.getOrNull(levelIndex)?.coerceIn(0f, 1f) ?: 0f
            val shaped = max(if (snapshot.analyzerRunning) .032f else .01f, level.pow(.66f))
            val angle = index.toDouble() / count * PI * 2.0 - PI / 2.0
            val outer = radius + dp(7f) + shaped * dp(35f)
            strokePaint.color = hsv(
                colorPhase + 118f + index * (235f / count),
                .88f,
                1f,
                .26f + shaped * .70f,
            )
            strokePaint.strokeWidth = dp(1.05f) + shaped * dp(2.15f)
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
            fillPaint.color = Color.argb(if (index == 1) 104 else 72, 255, 255, 255)
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
                downAt = SystemClock.uptimeMillis()
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
                if (!moved) {
                    val heldFor = SystemClock.uptimeMillis() - downAt
                    if (heldFor >= 520L) handleLongPress() else handleTap(event.x, event.y)
                }
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

    private fun handleLongPress() {
        val next = when (mode) {
            CapsuleMode.RIM -> CapsuleMode.COMPACT
            CapsuleMode.COMPACT -> CapsuleMode.RIM
            CapsuleMode.EXPANDED -> CapsuleMode.COMPACT
        }
        onModeChanged?.invoke(next)
    }

    private fun handleTap(x: Float, y: Float) {
        when (mode) {
            CapsuleMode.RIM -> onModeChanged?.invoke(CapsuleMode.COMPACT)
            CapsuleMode.COMPACT -> {
                if (x > width - dp(30f)) onModeChanged?.invoke(CapsuleMode.RIM)
                else onModeChanged?.invoke(CapsuleMode.EXPANDED)
            }

            CapsuleMode.EXPANDED -> {
                if (x > width - dp(58f) && y < dp(58f)) {
                    onModeChanged?.invoke(CapsuleMode.COMPACT)
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
                onModeChanged?.invoke(CapsuleMode.COMPACT)
            }
        }
    }

    private fun dp(value: Float): Float = value * density

    private fun hsv(
        hue: Float,
        saturation: Float,
        value: Float,
        alpha: Float = 1f,
    ): Int {
        val color = Color.HSVToColor(
            floatArrayOf((hue % 360f + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
        )
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }
}
