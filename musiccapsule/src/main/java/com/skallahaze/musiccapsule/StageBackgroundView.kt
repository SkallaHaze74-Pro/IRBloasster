package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * AMOLED-black Stage background.
 *
 * 1.6.4 changes the centre from a simple zoom pulse into an organic oval
 * breathing motion. Width and height move against each other around the same
 * SyncLearning clock, so the aura/leaf opens and closes like the reference
 * video instead of merely becoming a larger circle.
 */
class StageBackgroundView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val sourceLeafPath: Path = PathParser.createPathFromPathData(
        "M11.5,22V17.35C11,18.13 10,19.09 8.03,19.81C8.03,19.81 8.53,18.1 9.94,16.95C8.64,17.23 6.68,17.19 4,16C4,16 6.47,14.59 9.28,14.97C7.69,14 5.7,12.08 4.17,8.11C4.17,8.11 8.67,9.34 10.91,13.14C8.88,8.24 12,2 12,2C14.43,7.47 13.91,11.1 13.12,13.1C15.37,9.33 19.83,8.11 19.83,8.11C18.3,12.08 16.31,14 14.72,14.97C17.53,14.59 20,16 20,16C17.32,17.19 15.36,17.23 14.06,16.95C15.47,18.1 15.97,19.81 15.97,19.81C14,19.09 13,18.13 12.5,17.35V22H11.5Z",
    ) ?: Path()
    private val transformedLeafPath = Path()
    private val transformMatrix = Matrix()
    private val displayLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

    private var style = CapsulePreferences.stageStyle(context)
    private var lastFrameNanos = 0L
    private var phase = 0f
    private var breathPhase = 0f
    private var displayBass = 0f
    private var displayMid = 0f
    private var displayTreble = 0f
    private var displayBeat = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setStageStyle(value: StageStyle) {
        style = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .08f)
        }
        lastFrameNanos = now

        style = CapsulePreferences.stageStyle(context)
        val snapshot = CapsuleRuntime.snapshot()
        val sync = SyncLearningRuntime.snapshot()
        val visualBeat = VisualBeatRuntime.snapshot()
        val attack = 1f - exp(-dt * max(24f, sync.attackRate * .72f))
        val release = 1f - exp(-dt * max(8.5f, sync.releaseRate * .78f))
        for (index in displayLevels.indices) {
            val target = snapshot.levels.getOrNull(index) ?: 0f
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
        }
        displayBass = smooth(displayBass, snapshot.bass, dt, 31f, 8.5f)
        displayMid = smooth(displayMid, snapshot.mid, dt, 25f, 9.5f)
        displayTreble = smooth(displayTreble, snapshot.treble, dt, 34f, 12f)
        displayBeat = max(
            max(visualBeat.pulse, sync.beatStrength * if (sync.beatReliable) .92f else .58f),
            displayBeat * exp(-dt * (6.1f + sync.tempoFactor * 2.0f)),
        )

        phase = SyncLearningRuntime.hueAt()
        val bpm = sync.bpm.takeIf { it in 55f..220f } ?: 105f
        val breathsPerSecond = (bpm / 60f * .48f).coerceIn(.55f, 1.65f)
        breathPhase = (breathPhase + dt * breathsPerSecond * PI.toFloat() * 2f) %
            (PI.toFloat() * 2f)

        when (style) {
            StageStyle.AMOLED_BLACK -> drawPureBlackAccent(canvas)
            StageStyle.NEON_AURA -> drawAura(canvas, drawLeaf = false)
            StageStyle.LEAF_AURA -> drawAura(canvas, drawLeaf = true)
        }

        postInvalidateOnAnimation()
    }

    /** Returns width/height multipliers that mostly exchange shape, not size. */
    private fun ovalScales(): Pair<Float, Float> {
        val wave = sinf(breathPhase)
        val beatOpen = displayBeat * .055f
        val widthScale = (
            1f + wave * .17f + displayMid * .035f + beatOpen
            ).coerceIn(.80f, 1.28f)
        val heightScale = (
            1f - wave * .13f + displayBass * .055f + beatOpen * .42f
            ).coerceIn(.82f, 1.24f)
        return widthScale to heightScale
    }

    private fun drawPureBlackAccent(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * .072f
        val (ovalX, ovalY) = ovalScales()
        val save = canvas.save()
        canvas.scale(ovalX, ovalY, centerX, centerY)
        fillPaint.shader = RadialGradient(
            centerX,
            centerY,
            max(1f, radius * 3.2f),
            intArrayOf(
                hsv(phase + 190f, .90f, 1f, .12f + displayBeat * .14f),
                hsv(phase + 305f, .92f, 1f, .042f),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, radius * 3.2f, fillPaint)
        fillPaint.shader = null
        canvas.restoreToCount(save)
    }

    private fun drawAura(canvas: Canvas, drawLeaf: Boolean) {
        val centerX = width / 2f
        val centerY = height * .46f
        val minSide = min(width, height).toFloat()
        val baseRadius = minSide * .27f
        val (ovalX, ovalY) = ovalScales()

        val auraSave = canvas.save()
        canvas.scale(ovalX, ovalY, centerX, centerY)
        fillPaint.shader = RadialGradient(
            centerX,
            centerY,
            baseRadius * 1.58f,
            intArrayOf(
                hsv(phase + 180f, .88f, 1f, .20f + displayBass * .15f),
                hsv(phase + 270f, .92f, 1f, .115f + displayMid * .10f),
                hsv(phase + 330f, .92f, 1f, .052f),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, .34f, .66f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, baseRadius * 1.58f, fillPaint)
        fillPaint.shader = null
        canvas.restoreToCount(auraSave)

        repeat(5) { ring ->
            val level = displayLevels[(ring * 3).coerceAtMost(displayLevels.lastIndex)]
            val radius = baseRadius * (.45f + ring * .15f) + level * dp(18f)
            val ringW = radius * ovalX + displayBeat * dp(4f)
            val ringH = radius * ovalY + displayBeat * dp(2.2f)
            strokePaint.shader = LinearGradient(
                centerX - ringW,
                centerY - ringH,
                centerX + ringW,
                centerY + ringH,
                intArrayOf(
                    hsv(phase + ring * 32f, .94f, 1f, .62f),
                    hsv(phase + 120f + ring * 32f, .90f, 1f, .34f),
                    hsv(phase + 250f + ring * 32f, .93f, 1f, .58f),
                ),
                null,
                Shader.TileMode.MIRROR,
            )
            strokePaint.strokeWidth = dp(.82f + level * 1.65f + displayBeat * .72f)
            canvas.drawOval(
                RectF(centerX - ringW, centerY - ringH, centerX + ringW, centerY + ringH),
                strokePaint,
            )
        }
        strokePaint.shader = null

        drawRadialSpectrum(
            canvas = canvas,
            cx = centerX,
            cy = centerY,
            radiusX = baseRadius * .82f * ovalX,
            radiusY = baseRadius * .82f * ovalY,
        )

        if (drawLeaf) {
            drawLeaf(
                canvas = canvas,
                cx = centerX,
                cy = centerY,
                widthSize = baseRadius * 1.06f * ovalX,
                heightSize = baseRadius * 1.06f * ovalY,
            )
        } else {
            drawArtwork(
                canvas,
                CapsuleRuntime.snapshot().artwork,
                centerX,
                centerY,
                baseRadius * .48f,
                ovalX,
                ovalY,
            )
        }
    }

    private fun drawRadialSpectrum(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radiusX: Float,
        radiusY: Float,
    ) {
        val count = 48
        repeat(count) { index ->
            val band = ((index / count.toFloat()) * displayLevels.size)
                .toInt()
                .coerceIn(0, displayLevels.lastIndex)
            val level = displayLevels[band]
            val angle = index / count.toFloat() * PI.toFloat() * 2f - PI.toFloat() / 2f
            val extension = dp(5f) + level * dp(30f) + displayBeat * dp(7f)
            val cos = cosf(angle)
            val sin = sinf(angle)
            val startX = cx + cos * radiusX
            val startY = cy + sin * radiusY
            val endX = cx + cos * (radiusX + extension)
            val endY = cy + sin * (radiusY + extension * .78f)
            strokePaint.color = hsv(
                phase + index * (300f / count),
                .92f,
                1f,
                .24f + level * .73f + displayBeat * .10f,
            )
            strokePaint.strokeWidth = dp(.82f + level * 1.35f)
            canvas.drawLine(startX, startY, endX, endY, strokePaint)
        }
    }

    private fun drawLeaf(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        widthSize: Float,
        heightSize: Float,
    ) {
        val beatWidth = 1f + displayBeat * .035f
        val beatHeight = 1f + displayBass * .035f
        val halfW = widthSize * beatWidth / 2f
        val halfH = heightSize * beatHeight / 2f
        val target = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
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
                hsv(phase + 110f, .82f, 1f, .82f),
                hsv(phase + 195f, .87f, 1f, .92f),
                hsv(phase + 295f, .78f, 1f, .86f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(transformedLeafPath, fillPaint)
        fillPaint.shader = null
        strokePaint.color = Color.argb(
            (205f * VisualTuningPreferences.opacity(context)).toInt().coerceIn(0, 255),
            239,
            255,
            249,
        )
        strokePaint.strokeWidth = dp(1.05f + displayBeat * .58f)
        canvas.drawPath(transformedLeafPath, strokePaint)
    }

    private fun drawArtwork(
        canvas: Canvas,
        bitmap: Bitmap?,
        cx: Float,
        cy: Float,
        radius: Float,
        ovalX: Float,
        ovalY: Float,
    ) {
        val halfW = radius * ovalX
        val halfH = radius * ovalY
        if (bitmap == null || bitmap.isRecycled) {
            fillPaint.color = hsv(phase + 165f, .82f, 1f, .48f)
            canvas.drawOval(RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH), fillPaint)
            return
        }
        val destination = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        val save = canvas.save()
        canvas.clipOval(destination)
        val scale = max(destination.width() / bitmap.width, destination.height() / bitmap.height)
        val sourceWidth = destination.width() / scale
        val sourceHeight = destination.height() / scale
        val sourceLeft = (bitmap.width - sourceWidth) / 2f
        val sourceTop = (bitmap.height - sourceHeight) / 2f
        fillPaint.alpha = (255f * VisualTuningPreferences.opacity(context)).toInt().coerceIn(0, 255)
        canvas.drawBitmapCropped(
            bitmap,
            RectF(sourceLeft, sourceTop, sourceLeft + sourceWidth, sourceTop + sourceHeight),
            destination,
            fillPaint,
        )
        fillPaint.alpha = 255
        canvas.restoreToCount(save)
    }

    private fun smooth(current: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
        val rate = if (target > current) attack else release
        return current + (target - current) * (1f - exp(-dt * rate))
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
        val brightness = CapsulePreferences.neonIntensity(context).coerceIn(.75f, 1.8f)
        val opacity = VisualTuningPreferences.opacity(context)
        val effectiveValue = (value * (.64f + brightness * .34f)).coerceIn(0f, 1f)
        val effectiveAlpha = (alpha * opacity * (.74f + brightness * .18f)).coerceIn(0f, 1f)
        val color = Color.HSVToColor(
            floatArrayOf((hue % 360f + 360f) % 360f, saturation, effectiveValue),
        )
        return Color.argb(
            (effectiveAlpha * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun sinf(value: Float): Float = sin(value.toDouble()).toFloat()

    private fun cosf(value: Float): Float = cos(value.toDouble()).toFloat()

    private fun dp(value: Float): Float = value * density
}
