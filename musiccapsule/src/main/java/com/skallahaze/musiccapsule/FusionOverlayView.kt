package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Shared main-LIVE/Stage pattern layer and spectrum endpoint-orb renderer.
 *
 * Every timed decision now comes from SyncLearningRuntime. Patterns, endpoint
 * pulses and RGB phase therefore use the same learned beat instead of running
 * three slightly different clocks.
 */
class FusionOverlayView(context: Context) : View(context) {
    private enum class FusionPattern {
        RECTANGLE,
        INFINITY,
        UP_DOWN,
        HORIZONTAL,
        DIAMOND,
        CROSS,
        ZIGZAG,
        RING,
        STACKED,
    }

    private data class PatternPulse(
        var active: Boolean = false,
        var pattern: FusionPattern = FusionPattern.INFINITY,
        var progress: Float = 0f,
        var speed: Float = 1f,
        var strength: Float = 0f,
        var hue: Float = 0f,
        var predicted: Boolean = false,
        var phaseOffset: Float = 0f,
    )

    private val density = resources.displayMetrics.density
    private val profileScale = XiaomiDisplayProfile.visualScale(context)
    private val random = Random()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val levels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val pulses = Array(MAX_PATTERN_PULSES) { PatternPulse() }

    private var snapshot = CapsuleRuntime.snapshot()
    private var sync = SyncLearningRuntime.snapshot()
    private var neonIntensity = 1.35f
    private var stageMode = false
    private var lastFrameNanos = 0L
    private var beatEnvelope = 0f
    private var lastSyncBeatSequence = Long.MIN_VALUE
    private var lastSpawnAt = 0L
    private var lastPattern = FusionPattern.INFINITY

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun setStageMode(value: Boolean) {
        stageMode = value
        invalidate()
    }

    fun setSnapshot(value: CapsuleSnapshot, intensity: Float) {
        snapshot = value
        neonIntensity = intensity.coerceIn(.75f, 1.8f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val nowNanos = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .08f)
        }
        lastFrameNanos = nowNanos
        val nowMs = SystemClock.elapsedRealtime()

        snapshot = if (stageMode) CapsuleRuntime.snapshot() else snapshot
        sync = SyncLearningRuntime.snapshot()
        updateLevels(dt)
        detectSharedBeat(nowMs)
        updatePulses(dt)
        beatEnvelope *= exp(-dt * (6.4f + sync.tempoFactor * 3.1f))

        val stageContent = VisualTuningPreferences.stageContentMode(context)
        val liveMode = effectiveLivePatternMode()
        val visualMode = CapsulePreferences.visualLayerMode(context)

        if (stageMode) {
            if (stageContent != StageContentMode.FRAME_ONLY) {
                drawPatterns(canvas, stage = true)
            }
            if (stageContent == StageContentMode.FUSION) {
                drawEndpointAccents(canvas, drawBars = true)
            }
        } else {
            if (
                liveMode != LivePatternMode.OFF &&
                visualMode != VisualLayerMode.BORDER_ONLY &&
                visualMode != VisualLayerMode.CLEAN
            ) {
                drawPatterns(canvas, stage = false)
            }
            if (visualMode != VisualLayerMode.BORDER_ONLY && visualMode != VisualLayerMode.CLEAN) {
                drawEndpointAccents(canvas, drawBars = false)
            }
        }

        postInvalidateOnAnimation()
    }

    private fun updateLevels(dt: Float) {
        val attack = 1f - exp(-dt * sync.attackRate)
        val release = 1f - exp(-dt * sync.releaseRate)
        for (index in levels.indices) {
            val target = snapshot.levels.getOrNull(index) ?: 0f
            val factor = if (target > levels[index]) attack else release
            levels[index] += (target - levels[index]) * factor
        }
    }

    private fun detectSharedBeat(nowMs: Long) {
        val liveMode = effectiveLivePatternMode()
        val patternEnabled = if (stageMode) {
            VisualTuningPreferences.stageContentMode(context) != StageContentMode.FRAME_ONLY &&
                VisualTuningPreferences.patternMode(context) != BeatPatternMode.OFF
        } else {
            liveMode != LivePatternMode.OFF &&
                VisualTuningPreferences.patternMode(context) != BeatPatternMode.OFF &&
                CapsulePreferences.visualLayerMode(context) != VisualLayerMode.BORDER_ONLY &&
                CapsulePreferences.visualLayerMode(context) != VisualLayerMode.CLEAN
        }
        if (!patternEnabled) return

        if (lastSyncBeatSequence == Long.MIN_VALUE || sync.beatSequence < lastSyncBeatSequence) {
            lastSyncBeatSequence = sync.beatSequence
        }
        if (sync.beatSequence <= lastSyncBeatSequence) return

        val sequence = sync.beatSequence
        lastSyncBeatSequence = sequence
        if (nowMs - lastSpawnAt < sync.patternGapMs) return
        if (!stageMode && liveMode == LivePatternMode.BEAT_ONLY && !sync.beatReliable) return
        if (!stageMode && liveMode == LivePatternMode.SUBTLE && sequence % 2L != 0L) return

        val strength = sync.beatStrength.coerceIn(.18f, 1f)
        beatEnvelope = max(beatEnvelope, strength)
        spawnPattern(
            pattern = choosePattern(),
            strength = strength,
            predicted = !sync.beatReliable,
            nowMs = nowMs,
        )
    }

    private fun effectiveLivePatternMode(): LivePatternMode {
        val requested = VisualTuningPreferences.livePatternMode(context)
        if (requested != LivePatternMode.AUTO) return requested
        val auto = AutoTuneRuntime.snapshot()
        return when {
            auto.energy > .67f || auto.bassBias > .64f -> LivePatternMode.STRONG
            auto.energy < .25f -> LivePatternMode.SUBTLE
            else -> LivePatternMode.BALANCED
        }
    }

    private fun maxConcurrentPatterns(strength: Float): Int {
        if (stageMode) return if (strength > .80f) 2 else 1
        return when (effectiveLivePatternMode()) {
            LivePatternMode.OFF -> 0
            LivePatternMode.SUBTLE -> 1
            LivePatternMode.BALANCED,
            LivePatternMode.BEAT_ONLY,
            -> if (strength > .82f) 2 else 1
            LivePatternMode.STRONG,
            LivePatternMode.AUTO,
            -> if (strength > .70f) 2 else 1
        }
    }

    private fun spawnPattern(
        pattern: FusionPattern,
        strength: Float,
        predicted: Boolean,
        nowMs: Long,
    ) {
        val maxActive = maxConcurrentPatterns(strength)
        if (maxActive <= 0) return
        val active = pulses.count { it.active }
        val pulse = when {
            active < maxActive -> pulses.firstOrNull { !it.active }
            strength >= .88f -> pulses.maxByOrNull { it.progress }
            else -> null
        } ?: return

        val typeScale = when (pattern) {
            FusionPattern.INFINITY -> .90f
            FusionPattern.HORIZONTAL,
            FusionPattern.UP_DOWN,
            FusionPattern.ZIGZAG,
            -> 1.08f
            FusionPattern.RING -> 1.02f
            else -> .96f
        }
        pulse.active = true
        pulse.pattern = pattern
        pulse.progress = 0f
        pulse.speed = (sync.patternSpeed * typeScale).coerceIn(1.25f, 3.75f)
        pulse.strength = strength
        pulse.hue = SyncLearningRuntime.hueAt(nowMs)
        pulse.predicted = predicted
        pulse.phaseOffset = random.nextFloat() * PI.toFloat() * 2f
        lastPattern = pattern
        lastSpawnAt = nowMs
    }

    private fun choosePattern(): FusionPattern {
        val requested = VisualTuningPreferences.patternMode(context)
        if (requested != BeatPatternMode.AUTO) {
            return when (requested) {
                BeatPatternMode.RECTANGLE -> FusionPattern.RECTANGLE
                BeatPatternMode.INFINITY -> FusionPattern.INFINITY
                BeatPatternMode.UP_DOWN -> FusionPattern.UP_DOWN
                BeatPatternMode.HORIZONTAL -> FusionPattern.HORIZONTAL
                BeatPatternMode.DIAMOND -> FusionPattern.DIAMOND
                BeatPatternMode.AUTO,
                BeatPatternMode.OFF,
                -> FusionPattern.INFINITY
            }
        }

        // Repetition is deliberately allowed; music, not a fixed sequence,
        // decides what comes next.
        if (random.nextFloat() < .31f) return lastPattern
        val bass = snapshot.bass
        val mid = snapshot.mid
        val treble = snapshot.treble
        return when {
            bass > .66f -> weightedChoice(
                FusionPattern.RING to 22,
                FusionPattern.STACKED to 20,
                FusionPattern.RECTANGLE to 18,
                FusionPattern.INFINITY to 18,
                FusionPattern.DIAMOND to 14,
                FusionPattern.CROSS to 8,
            )
            mid > treble * 1.12f -> weightedChoice(
                FusionPattern.INFINITY to 26,
                FusionPattern.HORIZONTAL to 22,
                FusionPattern.ZIGZAG to 18,
                FusionPattern.STACKED to 14,
                FusionPattern.RECTANGLE to 12,
                FusionPattern.CROSS to 8,
            )
            treble > mid * 1.14f -> weightedChoice(
                FusionPattern.UP_DOWN to 24,
                FusionPattern.ZIGZAG to 22,
                FusionPattern.CROSS to 18,
                FusionPattern.HORIZONTAL to 16,
                FusionPattern.DIAMOND to 12,
                FusionPattern.INFINITY to 8,
            )
            else -> weightedChoice(
                FusionPattern.INFINITY to 20,
                FusionPattern.HORIZONTAL to 16,
                FusionPattern.RECTANGLE to 14,
                FusionPattern.RING to 13,
                FusionPattern.DIAMOND to 11,
                FusionPattern.ZIGZAG to 10,
                FusionPattern.CROSS to 9,
                FusionPattern.STACKED to 7,
            )
        }
    }

    private fun weightedChoice(vararg choices: Pair<FusionPattern, Int>): FusionPattern {
        val total = choices.sumOf { it.second }.coerceAtLeast(1)
        var value = random.nextInt(total)
        choices.forEach { choice ->
            value -= choice.second
            if (value < 0) return choice.first
        }
        return choices.last().first
    }

    private fun updatePulses(dt: Float) {
        pulses.forEach { pulse ->
            if (!pulse.active) return@forEach
            pulse.progress += dt * pulse.speed
            if (pulse.progress >= 1f) pulse.active = false
        }
    }

    private fun drawPatterns(canvas: Canvas, stage: Boolean) {
        val opacity = VisualTuningPreferences.opacity(context)
        val centerX = width / 2f
        val centerY = height * if (stage) .48f else .54f
        val minSide = min(width, height).toFloat()
        val baseAlpha = if (stage) .68f else .31f
        val baseScale = if (stage) .90f else .60f

        pulses.forEach { pulse ->
            if (!pulse.active) return@forEach
            val p = pulse.progress.coerceIn(0f, 1f)
            // Near-instant visual attack, then a tempo-sized decay.
            val attack = (1f - exp(-p * 24f)).coerceIn(0f, 1f)
            val release = (1f - p).pow(.66f)
            val envelope = attack * release
            val alphaValue = envelope * pulse.strength * baseAlpha * opacity *
                if (pulse.predicted) .52f else 1f
            if (alphaValue <= .012f) return@forEach
            val scale = baseScale * (.80f + p * .32f + pulse.strength * .08f)
            val hue = pulse.hue + p * (18f + sync.tempoFactor * 13f)

            strokePaint.shader = LinearGradient(
                centerX - minSide * .42f,
                centerY - minSide * .30f,
                centerX + minSide * .42f,
                centerY + minSide * .30f,
                intArrayOf(
                    hsv(hue, .96f, 1f, alphaValue),
                    hsv(hue + 112f, .90f, 1f, alphaValue * .76f),
                    hsv(hue + 226f, .96f, 1f, alphaValue),
                ),
                null,
                Shader.TileMode.MIRROR,
            )
            strokePaint.strokeWidth = dp(.72f + pulse.strength * 1.82f) *
                (.72f + neonIntensity * .25f)

            when (pulse.pattern) {
                FusionPattern.RECTANGLE -> drawRectangle(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.INFINITY -> drawInfinity(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.UP_DOWN -> drawUpDown(canvas, centerX, centerY, minSide, p, pulse)
                FusionPattern.HORIZONTAL -> drawHorizontal(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.DIAMOND -> drawDiamond(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.CROSS -> drawCross(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.ZIGZAG -> drawZigzag(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.RING -> drawRing(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.STACKED -> drawStacked(canvas, centerX, centerY, minSide, scale, pulse)
            }
            strokePaint.shader = null
        }
    }

    private fun drawRectangle(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val halfW = minSide * .30f * scale
        val halfH = minSide * .48f * scale * (1f + snapshot.bass * .07f)
        val wobble = sinf(pulse.progress * PI.toFloat() * 2f + pulse.phaseOffset) * dp(5f)
        canvas.drawRoundRect(
            RectF(cx - halfW - wobble, cy - halfH, cx + halfW + wobble, cy + halfH),
            minSide * .045f,
            minSide * .045f,
            strokePaint,
        )
    }

    private fun drawInfinity(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        path.reset()
        val a = minSide * .34f * scale
        val b = minSide * .18f * scale * (1f + snapshot.mid * .10f)
        val points = 112
        repeat(points + 1) { index ->
            val t = index / points.toFloat() * PI.toFloat() * 2f + pulse.phaseOffset * .08f
            val x = cx + a * sinf(t)
            val y = cy + b * sinf(t) * cosf(t)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawUpDown(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        progress: Float,
        pulse: PatternPulse,
    ) {
        val travel = minSide * .31f * sinf(progress * PI.toFloat())
        val halfW = minSide * (.18f + pulse.strength * .09f)
        val wave = sinf(progress * PI.toFloat() * 4f + pulse.phaseOffset) * dp(7f)
        canvas.drawLine(cx - halfW, cy - travel + wave, cx + halfW, cy - travel - wave, strokePaint)
        canvas.drawLine(cx - halfW, cy + travel - wave, cx + halfW, cy + travel + wave, strokePaint)
    }

    private fun drawHorizontal(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        path.reset()
        val halfW = minSide * .40f * scale
        val amplitude = minSide * .075f * (1f + snapshot.treble * .14f)
        val points = 84
        repeat(points + 1) { index ->
            val normalized = index / points.toFloat()
            val x = cx - halfW + normalized * halfW * 2f
            val y = cy + sinf(normalized * PI.toFloat() * 4f + pulse.phaseOffset) * amplitude
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawDiamond(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val halfW = minSide * .28f * scale
        val halfH = minSide * .36f * scale
        val twist = sinf(pulse.progress * PI.toFloat()) * minSide * .035f
        path.reset()
        path.moveTo(cx, cy - halfH)
        path.lineTo(cx + halfW + twist, cy)
        path.lineTo(cx, cy + halfH)
        path.lineTo(cx - halfW - twist, cy)
        path.close()
        canvas.drawPath(path, strokePaint)
    }

    private fun drawCross(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val half = minSide * .25f * scale
        val yHalf = half * 1.22f
        val skew = sinf(pulse.phaseOffset + pulse.progress * PI.toFloat() * 2f) * dp(8f)
        canvas.drawLine(cx - half, cy - yHalf + skew, cx + half, cy + yHalf - skew, strokePaint)
        canvas.drawLine(cx + half, cy - yHalf - skew, cx - half, cy + yHalf + skew, strokePaint)
    }

    private fun drawZigzag(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        path.reset()
        val halfW = minSide * .37f * scale
        val amplitude = minSide * .10f * scale
        val points = 9
        repeat(points) { index ->
            val normalized = index / (points - 1f)
            val x = cx - halfW + normalized * halfW * 2f
            val y = cy + (if (index % 2 == 0) -amplitude else amplitude) *
                (1f + sinf(pulse.phaseOffset) * .12f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val radius = minSide * .27f * scale *
            (1f + snapshot.bass * .10f + sinf(pulse.progress * PI.toFloat()) * .08f)
        canvas.drawCircle(cx, cy, radius, strokePaint)
        if (pulse.strength > .68f) canvas.drawCircle(cx, cy, radius * .72f, strokePaint)
    }

    private fun drawStacked(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        repeat(3) { index ->
            val local = scale * (1f - index * .18f)
            val offset = (index - 1) * minSide * .055f
            val halfW = minSide * .29f * local
            val halfH = minSide * .39f * local
            canvas.drawRoundRect(
                RectF(cx - halfW + offset, cy - halfH, cx + halfW + offset, cy + halfH),
                minSide * .035f,
                minSide * .035f,
                strokePaint,
            )
        }
    }

    private fun drawEndpointAccents(canvas: Canvas, drawBars: Boolean) {
        val endpoint = VisualTuningPreferences.endpointMode(context)
        if (endpoint == EndpointMode.OFF) return
        val opacity = VisualTuningPreferences.opacity(context)
        val segments = 44
        val top = dp(8f)
        val bottom = height - dp(8f)
        val usable = max(1f, bottom - top)
        val time = SystemClock.uptimeMillis() / 1000f
        val phase = SyncLearningRuntime.hueAt()
        val beat = max(beatEnvelope, sync.beatStrength * .78f)

        repeat(2) { side ->
            val left = side == 0
            val direction = if (left) 1f else -1f
            val outer = if (left) dp(4.5f) else width - dp(4.5f)
            repeat(segments) { segment ->
                val progress = segment / (segments - 1f)
                val band = edgeBandIndex(progress)
                val level = levels[band].coerceIn(0f, 1f).pow(.62f)
                val body = sinf(progress * PI.toFloat()) * dp(13f)
                val wave = sinf(progress * PI.toFloat() * 5f + time * .62f + side) * dp(2.0f)
                val baseX = outer + direction * (dp(2f) + body + wave * (.20f + snapshot.mid * .58f))
                val length = dp(3.2f) + level * dp(if (stageMode) 24f else 27f) * neonIntensity +
                    beat * dp(5.2f)
                val endX = baseX + direction * length
                val y = top + progress * usable
                val hue = phase + progress * 430f + side * 145f
                val visible = (.19f + level * .75f + beat * .14f).coerceIn(.18f, 1f)

                if (drawBars) {
                    strokePaint.shader = null
                    strokePaint.color = hsv(hue, .96f, 1f, visible * .72f * opacity)
                    strokePaint.strokeWidth = dp(.85f + level * 1.75f)
                    canvas.drawLine(baseX, y, endX, y, strokePaint)
                }

                drawEndpointOrb(
                    canvas = canvas,
                    x = endX,
                    y = y,
                    hue = hue,
                    level = level,
                    beat = beat,
                    opacity = opacity,
                    endpoint = endpoint,
                )
            }
        }
    }

    private fun drawEndpointOrb(
        canvas: Canvas,
        x: Float,
        y: Float,
        hue: Float,
        level: Float,
        beat: Float,
        opacity: Float,
        endpoint: EndpointMode,
    ) {
        val strength = endpoint.strength
        val coreRadius = max(
            dp(.68f),
            dp(.62f + level * 1.32f + beat * .62f) * strength,
        )
        val glowRadius = max(dp(2.2f), coreRadius * 3.15f)
        val alphaValue = (.50f + level * .38f + beat * .12f).coerceIn(.50f, 1f)

        fillPaint.shader = RadialGradient(
            x,
            y,
            glowRadius,
            intArrayOf(
                hsv(hue + 18f, .30f, 1f, alphaValue * opacity),
                hsv(hue, .90f, 1f, alphaValue * .32f * opacity),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, glowRadius, fillPaint)
        fillPaint.shader = null
        fillPaint.color = hsv(hue + 22f, .14f, 1f, max(.74f, alphaValue) * opacity)
        canvas.drawCircle(x, y, coreRadius, fillPaint)
    }

    private fun edgeBandIndex(progress: Float): Int = when {
        progress < .32f -> (15f - progress / .32f * 5f).toInt().coerceIn(10, 15)
        progress < .72f -> (10f - (progress - .32f) / .40f * 6f).toInt().coerceIn(4, 10)
        else -> (4f - (progress - .72f) / .28f * 4f).toInt().coerceIn(0, 4)
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alphaValue: Float): Int {
        val brightness = (.63f + neonIntensity * .32f).coerceIn(.65f, 1.20f)
        val color = Color.HSVToColor(
            floatArrayOf(
                (hue % 360f + 360f) % 360f,
                saturation.coerceIn(0f, 1f),
                (value * brightness).coerceIn(0f, 1f),
            ),
        )
        return Color.argb(
            (alphaValue.coerceIn(0f, 1f) * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun sinf(value: Float): Float = kotlin.math.sin(value.toDouble()).toFloat()

    private fun cosf(value: Float): Float = kotlin.math.cos(value.toDouble()).toFloat()

    private fun dp(value: Float): Float = value * density * profileScale

    private companion object {
        const val MAX_PATTERN_PULSES = 3
    }
}
