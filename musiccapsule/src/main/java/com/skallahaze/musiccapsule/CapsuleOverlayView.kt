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
    var onDisplayModeRequested: ((CapsuleDisplayMode) -> Unit)? = null
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
        color = Color.argb(184, 214, 226, 244)
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
    private val targetLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val displayLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

    private var snapshot = CapsuleRuntime.snapshot()
    private var expanded = false
    private var displayMode = CapsuleDisplayMode.MINI
    private var neonIntensity = 1.35f
    private var displaySignal = 0f
    private var lastFrameNanos = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setSnapshot(value: CapsuleSnapshot, intensity: Float) {
        snapshot = value
        expanded = value.expanded
        neonIntensity = intensity.coerceIn(.75f, 1.8f)
        for (index in targetLevels.indices) {
            targetLevels[index] = value.levels.getOrNull(index)?.coerceIn(0f, 1f) ?: 0f
        }
        postInvalidateOnAnimation()
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        postInvalidateOnAnimation()
    }

    fun setDisplayMode(value: CapsuleDisplayMode) {
        displayMode = value
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        smoothLevels()
        when {
            expanded -> drawExpanded(canvas)
            displayMode == CapsuleDisplayMode.RIM -> drawRim(canvas)
            else -> drawMini(canvas)
        }
        if (
            snapshot.signal > .003f ||
            displaySignal > .004f ||
            displayLevels.any { it > .006f }
        ) {
            postInvalidateOnAnimation()
        }
    }

    private fun smoothLevels() {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 1f / 60f else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .08f)
        }
        lastFrameNanos = now
        val attack = 1f - kotlin.math.exp(-dt * 30f)
        val release = 1f - kotlin.math.exp(-dt * 10f)
        for (index in displayLevels.indices) {
            val target = targetLevels[index]
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
        }
        val signalTarget = snapshot.signal.coerceIn(0f, 1f)
        val signalFactor = if (signalTarget > displaySignal) attack else release
        displaySignal += (signalTarget - displaySignal) * signalFactor
    }

    private fun drawRim(canvas: Canvas) {
        val inset = dp(2.2f)
        val bounds = RectF(inset, inset, width - inset, height - inset)
        val radius = height * .48f
        fillPaint.color = Color.argb(28, 3, 6, 14)
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        drawRainbowBorder(canvas, bounds, radius, strong = true)

        val iconRadius = height * .22f
        val iconCenterX = dp(17f)
        val iconCenterY = height / 2f
        fillPaint.color = hsv(135f + colorPhase(), .84f, 1f, .72f)
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, fillPaint)

        val barRect = RectF(width - dp(76f), dp(6f), width - dp(10f), height - dp(6f))
        drawLinearLevels(canvas, barRect, count = 9, floor = .035f)
    }

    private fun drawMini(canvas: Canvas) {
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = height * .48f
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.argb(244, 4, 7, 18),
                Color.argb(238, 24, 7, 37),
                Color.argb(240, 3, 22, 29),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        fillPaint.shader = null
        drawRainbowBorder(canvas, RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f)), radius, strong = false)

        val padding = dp(5.5f)
        val artSize = height - padding * 2f
        val artRect = RectF(padding, padding, padding + artSize, padding + artSize)
        drawArtworkOrLeaf(canvas, artRect, compact = true)

        val barsWidth = min(dp(66f), width * .26f)
        val barsRect = RectF(
            width - padding - barsWidth,
            padding + dp(3f),
            width - padding,
            height - padding - dp(3f),
        )
        drawLinearLevels(canvas, barsRect, count = 8, floor = .045f)

        val textLeft = artRect.right + dp(8f)
        val textRight = barsRect.left - dp(7f)
        titlePaint.textSize = dp(12.2f)
        subtitlePaint.textSize = dp(9.4f)
        drawEllipsizedText(canvas, snapshot.title, textLeft, height * .44f, textRight - textLeft, titlePaint)
        val subtitle = snapshot.artist.ifBlank {
            when {
                snapshot.analyzerRunning -> "Audioanalyse aktiv"
                else -> "Antippen zum Öffnen"
            }
        }
        drawEllipsizedText(canvas, subtitle, textLeft, height * .72f, textRight - textLeft, subtitlePaint)

        fillPaint.color = when {
            snapshot.signal > .01f -> Color.rgb(75, 255, 176)
            snapshot.analyzerRunning -> Color.rgb(255, 194, 76)
            else -> Color.rgb(122, 135, 158)
        }
        canvas.drawCircle(width - dp(5.5f), dp(5.5f), dp(2.0f), fillPaint)
    }

    private fun drawExpanded(canvas: Canvas) {
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val corner = dp(27f)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(2, 4, 12),
                Color.rgb(18, 7, 33),
                Color.rgb(3, 22, 29),
                Color.rgb(12, 5, 31),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(outer, corner, corner, fillPaint)
        fillPaint.shader = null
        drawRainbowBorder(canvas, RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f)), corner, strong = true)

        drawTopButtons(canvas)
        titlePaint.textSize = dp(17f)
        subtitlePaint.textSize = dp(11.5f)
        drawEllipsizedText(canvas, snapshot.title, dp(52f), dp(36f), width - dp(108f), titlePaint)
        drawEllipsizedText(
            canvas,
            snapshot.artist.ifBlank { packageDisplayName() },
            dp(52f),
            dp(56f),
            width - dp(108f),
            subtitlePaint,
        )

        val centerX = width * .5f
        val centerY = height * .43f
        val leafSize = min(width * .52f, height * .43f)
        drawRadialLevels(canvas, centerX, centerY, leafSize * .57f)
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
            RectF(dp(24f), height * .70f, width - dp(24f), height * .80f),
            count = 16,
            floor = .025f,
        )
        drawTransportControls(canvas)

        labelPaint.textSize = dp(10.2f)
        val sourceLock = CapsulePreferences.sourceLock(context)
        val status = when {
            snapshot.signal > .01f ->
                "LIVE FFT · ${(displaySignal * 100).toInt()}% · ${sourceLock.label} · ${snapshot.source}"
            snapshot.analyzerRunning ->
                "Capture aktiv · ${sourceLock.label} · wartet auf internes Audiosignal"
            else ->
                "Audioanalyse aus · Quelle ${sourceLock.label}"
        }
        canvas.drawText(status, dp(22f), height - dp(14f), labelPaint)
    }

    private fun drawTopButtons(canvas: Canvas) {
        val leftX = dp(26f)
        val rightX = width - dp(26f)
        val y = dp(26f)
        fillPaint.color = Color.argb(82, 255, 255, 255)
        canvas.drawCircle(leftX, y, dp(14f), fillPaint)
        canvas.drawCircle(rightX, y, dp(14f), fillPaint)

        strokePaint.color = Color.WHITE
        strokePaint.strokeWidth = dp(1.8f)
        canvas.drawLine(leftX - dp(6f), y, leftX + dp(6f), y, strokePaint)
        canvas.drawLine(rightX - dp(5f), y - dp(5f), rightX + dp(5f), y + dp(5f), strokePaint)
        canvas.drawLine(rightX + dp(5f), y - dp(5f), rightX - dp(5f), y + dp(5f), strokePaint)
    }

    private fun drawRainbowBorder(canvas: Canvas, rect: RectF, radius: Float, strong: Boolean) {
        val phase = colorPhase()
        val colors = intArrayOf(
            hsv(phase + 0f, .90f, 1f, 1f),
            hsv(phase + 52f, .92f, 1f, 1f),
            hsv(phase + 112f, .90f, 1f, 1f),
            hsv(phase + 174f, .88f, 1f, 1f),
            hsv(phase + 236f, .90f, 1f, 1f),
            hsv(phase + 300f, .92f, 1f, 1f),
            hsv(phase + 360f, .90f, 1f, 1f),
        )
        strokePaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colors, null, Shader.TileMode.CLAMP)
        strokePaint.strokeWidth = if (strong) dp(5.5f) * neonIntensity else dp(3.8f) * neonIntensity
        strokePaint.alpha = if (strong) 34 else 26
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.strokeWidth = if (strong) dp(2.2f) else dp(1.35f)
        strokePaint.alpha = 235
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun packageDisplayName(): String {
        return snapshot.packageName.substringAfterLast('.').ifBlank { "Music Capsule" }
    }

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
        val radius = if (expanded) rect.width() * .5f else rect.width() * .42f
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
        val pulse = 1f + displaySignal * if (compact) .035f else .065f
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

        val phase = colorPhase()
        fillPaint.shader = LinearGradient(
            target.left,
            target.top,
            target.right,
            target.bottom,
            intArrayOf(
                hsv(phase + 128f, .86f, 1f, 1f),
                hsv(phase + 188f, .84f, 1f, 1f),
                hsv(phase + 302f, .76f, 1f, 1f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(transformedLeafPath, fillPaint)
        fillPaint.shader = null

        strokePaint.strokeWidth = if (compact) dp(.65f) else dp(1.15f)
        strokePaint.color = Color.argb(224, 235, 255, 249)
        canvas.drawPath(transformedLeafPath, strokePaint)
    }

    private fun drawLinearLevels(
        canvas: Canvas,
        rect: RectF,
        count: Int,
        floor: Float,
    ) {
        val gap = if (count <= 9) dp(2.5f) else dp(3.5f)
        val barWidth = (rect.width() - gap * (count - 1)) / count
        for (index in 0 until count) {
            val sourceIndex = ((index.toFloat() / max(1, count - 1)) * displayLevels.lastIndex).toInt()
            val level = displayLevels.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
            val shaped = max(if (snapshot.analyzerRunning) floor else .012f, level.pow(.62f))
            val barHeight = rect.height() * shaped
            val left = rect.left + index * (barWidth + gap)
            fillPaint.color = hsv(
                colorPhase() + index * (280f / count),
                .90f,
                1f,
                if (snapshot.analyzerRunning) .96f else .34f,
            )
            canvas.drawRoundRect(
                RectF(left, rect.bottom - barHeight, left + barWidth, rect.bottom),
                barWidth * .48f,
                barWidth * .48f,
                fillPaint,
            )
        }
    }

    private fun drawRadialLevels(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val count = 44
        for (index in 0 until count) {
            val levelIndex = ((index.toFloat() / count) * displayLevels.size)
                .toInt()
                .coerceAtMost(displayLevels.lastIndex)
            val level = displayLevels[levelIndex].coerceIn(0f, 1f)
            val shaped = max(if (snapshot.analyzerRunning) .035f else .012f, level.pow(.64f))
            val angle = index.toDouble() / count * PI * 2.0 - PI / 2.0
            val outer = radius + dp(7f) + shaped * dp(35f)
            strokePaint.color = hsv(
                colorPhase() + 125f + index * (245f / count),
                .90f,
                1f,
                .26f + shaped * .72f,
            )
            strokePaint.strokeWidth = dp(1.0f) + shaped * dp(2.3f)
            canvas.drawLine(
                cx + cos(angle).toFloat() * radius,
                cy + sin(angle).toFloat() * radius,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                strokePaint,
            )
        }
    }

    private fun drawTransportControls(canvas: Canvas) {
        val y = height * .87f
        val xs = floatArrayOf(width * .32f, width * .50f, width * .68f)
        xs.forEachIndexed { index, x ->
            fillPaint.color = Color.argb(if (index == 1) 104 else 76, 255, 255, 255)
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
        if (x > width - dp(56f) && y < dp(56f)) {
            onExpandedChanged?.invoke(false)
            return
        }
        if (x < dp(56f) && y < dp(56f)) {
            val next = displayMode.next()
            onDisplayModeRequested?.invoke(next)
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

    private fun colorPhase(): Float {
        return ((SystemClock.uptimeMillis() % 16_000L) / 16_000f) * 360f + displaySignal * 48f
    }

    private fun dp(value: Float): Float = value * density

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
        val color = Color.HSVToColor(floatArrayOf((hue % 360f + 360f) % 360f, saturation, value))
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }
}
