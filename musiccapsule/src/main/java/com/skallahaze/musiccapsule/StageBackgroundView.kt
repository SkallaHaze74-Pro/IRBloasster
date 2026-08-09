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
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.PathParser
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** AMOLED-black full-screen stage used when the phone is lying on a table. */
class StageBackgroundView(context: Context) : View(context) {
    private data class PatternPulse(
        var active: Boolean = false,
        var type: BeatPatternMode = BeatPatternMode.RECTANGLE,
        var progress: Float = 0f,
        var speed: Float = 1f,
        var strength: Float = 0f,
        var hue: Float = 0f,
        var predicted: Boolean = false,
        var phaseOffset: Float = 0f,
    )

    private val density = resources.displayMetrics.density
    private val random = Random()
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
    private val patternPath = Path()
    private val displayLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val patternPulses = Array(MAX_PATTERN_PULSES) { PatternPulse() }

    private var style = CapsulePreferences.stageStyle(context)
    private var lastFrameNanos = 0L
    private var phase = 0f
    private var displayBass = 0f
    private var displayMid = 0f
    private var displayTreble = 0f
    private var displayBeat = 0f
    private var lastAudioBeatSequence = Long.MIN_VALUE
    private var lastVisualBeatSequence = Long.MIN_VALUE
    private var lastPatternAt = 0L
    private var lastPatternType = BeatPatternMode.INFINITY

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

        val nowNanos = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .08f)
        }
        lastFrameNanos = nowNanos
        val nowMs = SystemClock.elapsedRealtime()

        style = CapsulePreferences.stageStyle(context)
        val snapshot = CapsuleRuntime.snapshot()
        val visualBeat = VisualBeatRuntime.snapshot()
        val attack = 1f - exp(-dt * 24f)
        val release = 1f - exp(-dt * 8.5f)
        for (index in displayLevels.indices) {
            val target = snapshot.levels.getOrNull(index) ?: 0f
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
        }
        displayBass = smooth(displayBass, snapshot.bass, dt, 27f, 7f)
        displayMid = smooth(displayMid, snapshot.mid, dt, 22f, 8f)
        displayTreble = smooth(displayTreble, snapshot.treble, dt, 31f, 11f)
        displayBeat = max(visualBeat.pulse, displayBeat * exp(-dt * 5.4f))
        phase = (phase + dt * (4f + displayTreble * 64f + displayBeat * 155f)) % 360f

        detectBeatPattern(snapshot, visualBeat, nowMs)
        updatePatterns(dt)

        when (style) {
            StageStyle.AMOLED_BLACK -> drawPureBlackAccent(canvas)
            StageStyle.NEON_AURA -> drawAura(canvas, drawLeaf = false)
            StageStyle.LEAF_AURA -> drawAura(canvas, drawLeaf = true)
        }
        drawBeatPatterns(canvas)

        postInvalidateOnAnimation()
    }

    private fun detectBeatPattern(
        snapshot: CapsuleSnapshot,
        visualBeat: VisualBeatSnapshot,
        nowMs: Long,
    ) {
        if (lastAudioBeatSequence == Long.MIN_VALUE || snapshot.beatSequence < lastAudioBeatSequence) {
            lastAudioBeatSequence = snapshot.beatSequence
        }
        if (lastVisualBeatSequence == Long.MIN_VALUE || visualBeat.sequence < lastVisualBeatSequence) {
            lastVisualBeatSequence = visualBeat.sequence
        }

        var triggered = false
        var predicted = false
        var strength = 0f

        if (snapshot.beatSequence > lastAudioBeatSequence) {
            triggered = true
            strength = max(.32f, max(snapshot.beat, snapshot.bass * .86f))
            lastAudioBeatSequence = snapshot.beatSequence
        } else if (
            visualBeat.sequence > lastVisualBeatSequence &&
            nowMs - lastPatternAt >= MIN_PATTERN_GAP_MS
        ) {
            triggered = true
            predicted = visualBeat.predicted
            strength = max(.25f, visualBeat.pulse)
        }
        lastVisualBeatSequence = max(lastVisualBeatSequence, visualBeat.sequence)

        if (triggered && nowMs - lastPatternAt >= MIN_PATTERN_GAP_MS) {
            lastPatternAt = nowMs
            spawnPattern(
                type = choosePattern(snapshot),
                strength = if (predicted) strength * .68f else strength,
                predicted = predicted,
            )
        }
    }

    /**
     * Feature-based and intentionally non-sequential. Repeating the same pattern
     * on consecutive beats is allowed, so the visuals follow the music instead
     * of walking through a fixed rectangle/up/down list.
     */
    private fun choosePattern(snapshot: CapsuleSnapshot): BeatPatternMode {
        val requested = VisualTuningPreferences.patternMode(context)
        if (requested != BeatPatternMode.AUTO) return requested

        if (random.nextFloat() < .34f) return lastPatternType

        val bass = max(displayBass, snapshot.bass)
        val mid = max(displayMid, snapshot.mid)
        val treble = max(displayTreble, snapshot.treble)
        val chosen = when {
            bass > .66f -> weightedChoice(
                BeatPatternMode.RECTANGLE to 34,
                BeatPatternMode.INFINITY to 34,
                BeatPatternMode.DIAMOND to 20,
                BeatPatternMode.UP_DOWN to 12,
            )
            mid > treble * 1.12f -> weightedChoice(
                BeatPatternMode.INFINITY to 40,
                BeatPatternMode.HORIZONTAL to 30,
                BeatPatternMode.RECTANGLE to 20,
                BeatPatternMode.DIAMOND to 10,
            )
            treble > mid * 1.14f -> weightedChoice(
                BeatPatternMode.UP_DOWN to 37,
                BeatPatternMode.DIAMOND to 27,
                BeatPatternMode.HORIZONTAL to 22,
                BeatPatternMode.INFINITY to 14,
            )
            else -> weightedChoice(
                BeatPatternMode.INFINITY to 28,
                BeatPatternMode.RECTANGLE to 24,
                BeatPatternMode.HORIZONTAL to 20,
                BeatPatternMode.UP_DOWN to 16,
                BeatPatternMode.DIAMOND to 12,
            )
        }
        lastPatternType = chosen
        return chosen
    }

    private fun weightedChoice(vararg choices: Pair<BeatPatternMode, Int>): BeatPatternMode {
        val total = choices.sumOf { it.second }.coerceAtLeast(1)
        var value = random.nextInt(total)
        choices.forEach { choice ->
            value -= choice.second
            if (value < 0) return choice.first
        }
        return choices.last().first
    }

    private fun spawnPattern(type: BeatPatternMode, strength: Float, predicted: Boolean) {
        if (type == BeatPatternMode.OFF) return
        val pulse = patternPulses.firstOrNull { !it.active }
            ?: patternPulses.minByOrNull { 1f - it.progress }
            ?: return
        pulse.active = true
        pulse.type = type
        pulse.progress = 0f
        pulse.speed = when (type) {
            BeatPatternMode.INFINITY -> 1.55f
            BeatPatternMode.RECTANGLE,
            BeatPatternMode.DIAMOND,
            -> 1.75f
            BeatPatternMode.UP_DOWN,
            BeatPatternMode.HORIZONTAL,
            -> 2.0f
            BeatPatternMode.AUTO,
            BeatPatternMode.OFF,
            -> 1.7f
        }
        pulse.strength = strength.coerceIn(.20f, 1f)
        pulse.hue = (phase + random.nextFloat() * 145f) % 360f
        pulse.predicted = predicted
        pulse.phaseOffset = random.nextFloat() * PI.toFloat() * 2f
        lastPatternType = type
    }

    private fun updatePatterns(dt: Float) {
        patternPulses.forEach { pulse ->
            if (!pulse.active) return@forEach
            pulse.progress += dt * pulse.speed
            if (pulse.progress >= 1f) pulse.active = false
        }
    }

    private fun drawBeatPatterns(canvas: Canvas) {
        val opacity = VisualTuningPreferences.opacity(context)
        val brightness = CapsulePreferences.neonIntensity(context).coerceIn(.75f, 1.8f)
        val centerX = width / 2f
        val centerY = height * .48f
        val minSide = min(width, height).toFloat()

        patternPulses.forEach { pulse ->
            if (!pulse.active) return@forEach
            val p = pulse.progress.coerceIn(0f, 1f)
            val envelope = sinf(p * PI.toFloat()).coerceAtLeast(0f)
            val alpha = envelope * pulse.strength *
                (if (pulse.predicted) .45f else .80f) * opacity
            if (alpha <= .01f) return@forEach
            val scale = .58f + p * .66f + pulse.strength * .08f
            val hue = pulse.hue + p * 95f
            strokePaint.shader = LinearGradient(
                centerX - minSide * .42f,
                centerY - minSide * .25f,
                centerX + minSide * .42f,
                centerY + minSide * .25f,
                intArrayOf(
                    hsv(hue, .95f, 1f, alpha),
                    hsv(hue + 105f, .91f, 1f, alpha * .78f),
                    hsv(hue + 220f, .95f, 1f, alpha),
                ),
                null,
                Shader.TileMode.MIRROR,
            )
            strokePaint.strokeWidth = dp(.75f + pulse.strength * 2.15f) *
                (.78f + brightness * .30f)

            when (pulse.type) {
                BeatPatternMode.RECTANGLE -> drawRectanglePattern(
                    canvas,
                    centerX,
                    centerY,
                    minSide,
                    scale,
                    pulse,
                )
                BeatPatternMode.INFINITY -> drawInfinityPattern(
                    canvas,
                    centerX,
                    centerY,
                    minSide,
                    scale,
                    pulse,
                )
                BeatPatternMode.UP_DOWN -> drawUpDownPattern(
                    canvas,
                    centerX,
                    centerY,
                    minSide,
                    p,
                    pulse,
                )
                BeatPatternMode.HORIZONTAL -> drawHorizontalPattern(
                    canvas,
                    centerX,
                    centerY,
                    minSide,
                    scale,
                    pulse,
                )
                BeatPatternMode.DIAMOND -> drawDiamondPattern(
                    canvas,
                    centerX,
                    centerY,
                    minSide,
                    scale,
                    pulse,
                )
                BeatPatternMode.AUTO,
                BeatPatternMode.OFF,
                -> Unit
            }
            strokePaint.shader = null
        }
    }

    private fun drawRectanglePattern(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val halfW = minSide * .31f * scale
        val halfH = minSide * .51f * scale * (1f + displayBass * .10f)
        val wobble = sinf(pulse.progress * PI.toFloat() * 2f + pulse.phaseOffset) * dp(7f)
        val rect = RectF(cx - halfW - wobble, cy - halfH, cx + halfW + wobble, cy + halfH)
        canvas.drawRoundRect(rect, minSide * .055f, minSide * .055f, strokePaint)
    }

    private fun drawInfinityPattern(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        patternPath.reset()
        val a = minSide * .34f * scale
        val b = minSide * .18f * scale * (1f + displayMid * .12f)
        val points = 128
        repeat(points + 1) { index ->
            val t = index / points.toFloat() * PI.toFloat() * 2f + pulse.phaseOffset * .08f
            val x = cx + a * sinf(t)
            val y = cy + b * sinf(t) * cosf(t)
            if (index == 0) patternPath.moveTo(x, y) else patternPath.lineTo(x, y)
        }
        canvas.drawPath(patternPath, strokePaint)
    }

    private fun drawUpDownPattern(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        progress: Float,
        pulse: PatternPulse,
    ) {
        val travel = minSide * .34f * sinf(progress * PI.toFloat())
        val halfW = minSide * (.20f + pulse.strength * .10f)
        val wave = sinf(progress * PI.toFloat() * 4f + pulse.phaseOffset) * dp(9f)
        canvas.drawLine(cx - halfW, cy - travel + wave, cx + halfW, cy - travel - wave, strokePaint)
        canvas.drawLine(cx - halfW, cy + travel - wave, cx + halfW, cy + travel + wave, strokePaint)
    }

    private fun drawHorizontalPattern(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        patternPath.reset()
        val halfW = minSide * .40f * scale
        val amplitude = minSide * .08f * (1f + displayTreble * .16f)
        val points = 96
        repeat(points + 1) { index ->
            val normalized = index / points.toFloat()
            val x = cx - halfW + normalized * halfW * 2f
            val y = cy + sinf(normalized * PI.toFloat() * 4f + pulse.phaseOffset) * amplitude
            if (index == 0) patternPath.moveTo(x, y) else patternPath.lineTo(x, y)
        }
        canvas.drawPath(patternPath, strokePaint)
    }

    private fun drawDiamondPattern(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val halfW = minSide * .29f * scale
        val halfH = minSide * .38f * scale * (1f + displayBass * .08f)
        val twist = sinf(pulse.progress * PI.toFloat()) * minSide * .04f
        patternPath.reset()
        patternPath.moveTo(cx, cy - halfH)
        patternPath.lineTo(cx + halfW + twist, cy)
        patternPath.lineTo(cx, cy + halfH)
        patternPath.lineTo(cx - halfW - twist, cy)
        patternPath.close()
        canvas.drawPath(patternPath, strokePaint)
    }

    private fun drawPureBlackAccent(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * (.06f + displayBeat * .035f)
        fillPaint.shader = RadialGradient(
            centerX,
            centerY,
            max(1f, radius * 3.2f),
            intArrayOf(
                hsv(phase + 190f, .90f, 1f, .10f + displayBeat * .12f),
                hsv(phase + 305f, .92f, 1f, .035f),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, radius * 3.2f, fillPaint)
        fillPaint.shader = null
    }

    private fun drawAura(canvas: Canvas, drawLeaf: Boolean) {
        val centerX = width / 2f
        val centerY = height * .46f
        val minSide = min(width, height).toFloat()
        val auraRadius = minSide * (.27f + displayBass * .055f + displayBeat * .035f)

        fillPaint.shader = RadialGradient(
            centerX,
            centerY,
            auraRadius * 1.55f,
            intArrayOf(
                hsv(phase + 180f, .88f, 1f, .18f + displayBass * .14f),
                hsv(phase + 270f, .92f, 1f, .10f + displayMid * .09f),
                hsv(phase + 330f, .92f, 1f, .045f),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, .34f, .66f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, auraRadius * 1.55f, fillPaint)
        fillPaint.shader = null

        repeat(5) { ring ->
            val level = displayLevels[(ring * 3).coerceAtMost(displayLevels.lastIndex)]
            val radius = auraRadius * (.45f + ring * .15f) + level * dp(22f) + displayBeat * dp(13f)
            strokePaint.shader = LinearGradient(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius,
                intArrayOf(
                    hsv(phase + ring * 32f, .94f, 1f, .55f),
                    hsv(phase + 120f + ring * 32f, .90f, 1f, .28f),
                    hsv(phase + 250f + ring * 32f, .93f, 1f, .50f),
                ),
                null,
                Shader.TileMode.MIRROR,
            )
            strokePaint.strokeWidth = dp(.7f + level * 1.5f + displayBeat * .7f)
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
        }
        strokePaint.shader = null

        drawRadialSpectrum(canvas, centerX, centerY, auraRadius * .82f)

        if (drawLeaf) {
            drawLeaf(canvas, centerX, centerY, auraRadius * 1.05f)
        } else {
            drawArtwork(canvas, CapsuleRuntime.snapshot().artwork, centerX, centerY, auraRadius * .48f)
        }
    }

    private fun drawRadialSpectrum(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val count = 48
        repeat(count) { index ->
            val band = ((index / count.toFloat()) * displayLevels.size)
                .toInt()
                .coerceIn(0, displayLevels.lastIndex)
            val level = displayLevels[band]
            val angle = index / count.toFloat() * PI.toFloat() * 2f - PI.toFloat() / 2f
            val start = radius
            val end = radius + dp(5f) + level * dp(34f) + displayBeat * dp(8f)
            strokePaint.color = hsv(
                phase + index * (300f / count),
                .92f,
                1f,
                .20f + level * .72f + displayBeat * .08f,
            )
            strokePaint.strokeWidth = dp(.75f + level * 1.25f)
            canvas.drawLine(
                cx + cosf(angle) * start,
                cy + sinf(angle) * start,
                cx + cosf(angle) * end,
                cy + sinf(angle) * end,
                strokePaint,
            )
        }
    }

    private fun drawLeaf(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val pulse = 1f + displayBass * .08f + displayBeat * .07f
        val half = size * pulse / 2f
        val target = RectF(cx - half, cy - half, cx + half, cy + half)
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
                hsv(phase + 110f, .82f, 1f, .78f),
                hsv(phase + 195f, .87f, 1f, .88f),
                hsv(phase + 295f, .78f, 1f, .82f),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(transformedLeafPath, fillPaint)
        fillPaint.shader = null
        strokePaint.color = Color.argb(
            (190f * VisualTuningPreferences.opacity(context)).toInt().coerceIn(0, 255),
            239,
            255,
            249,
        )
        strokePaint.strokeWidth = dp(1.0f + displayBeat * .5f)
        canvas.drawPath(transformedLeafPath, strokePaint)
    }

    private fun drawArtwork(
        canvas: Canvas,
        bitmap: Bitmap?,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        if (bitmap == null || bitmap.isRecycled) {
            fillPaint.color = hsv(phase + 165f, .82f, 1f, .45f)
            canvas.drawCircle(cx, cy, radius, fillPaint)
            return
        }
        val destination = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val save = canvas.save()
        canvas.clipRounded(destination, radius, radius)
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
        val effectiveValue = (value * (.62f + brightness * .34f)).coerceIn(0f, 1f)
        val effectiveAlpha = (alpha * opacity * (.70f + brightness * .20f)).coerceIn(0f, 1f)
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

    private companion object {
        const val MAX_PATTERN_PULSES = 7
        const val MIN_PATTERN_GAP_MS = 115L
    }
}
