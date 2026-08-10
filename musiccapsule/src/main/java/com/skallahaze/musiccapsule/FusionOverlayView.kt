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
 * Shared main-LIVE/Stage pattern layer.
 *
 * 1.6.4 keeps SyncLearningRuntime as the single timing clock, brightens the
 * centre patterns slightly, and makes the musical LOW/MID/HIGH side stripes
 * visible in FULL and Stage Fusion. Bars are independent from endpoint-orbs:
 * turning endpoints off no longer makes the Stage stripes disappear.
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
        SIDE_WAVES,
        FLAT_BARS,
        SERIAL_DOTS,
        MIRROR_RAIN,
        FIRE_DANCE,
        STRINGS,
        BUBBLES,
        SNOW,
        GLOW_PULSE,
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
        beatEnvelope *= exp(-dt * (6.6f + sync.tempoFactor * 3.2f))

        val stageContent = VisualTuningPreferences.stageContentMode(context)
        val liveMode = effectiveLivePatternMode()
        val visualMode = CapsulePreferences.visualLayerMode(context)

        if (stageMode) {
            if (stageContent != StageContentMode.FRAME_ONLY) {
                drawPatterns(canvas, stage = true)
            }
            if (stageContent == StageContentMode.FUSION) {
                val stripeMode = VisualTuningPreferences.stageStripeMode(context)
                if (stripeMode != StageStripeMode.OFF) {
                    drawSideAccents(
                        canvas = canvas,
                        drawBars = true,
                        stripeMultiplier = stripeMode.multiplier,
                        stage = true,
                    )
                    drawHorizontalStageStripes(canvas, stripeMode.multiplier)
                }
            }
        } else {
            if (
                liveMode != LivePatternMode.OFF &&
                visualMode != VisualLayerMode.BORDER_ONLY &&
                visualMode != VisualLayerMode.CLEAN
            ) {
                drawPatterns(canvas, stage = false)
            }

            // FULL gets one coherent LOW-led stripe layer on top of the old
            // decorative edge texture. Endpoints are calculated from these
            // exact same lines, so they no longer float beside the bar ends.
            if (visualMode == VisualLayerMode.FULL) {
                drawSideAccents(
                    canvas = canvas,
                    drawBars = true,
                    stripeMultiplier = 1.05f,
                    stage = false,
                )
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
        beatEnvelope = max(beatEnvelope, .32f + strength * .68f)
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
        if (stageMode) return if (strength > .82f) 2 else 1
        return when (effectiveLivePatternMode()) {
            LivePatternMode.OFF -> 0
            LivePatternMode.SUBTLE -> 1
            LivePatternMode.BALANCED,
            LivePatternMode.BEAT_ONLY,
            -> if (strength > .84f) 2 else 1
            LivePatternMode.STRONG,
            LivePatternMode.AUTO,
            -> if (strength > .72f) 2 else 1
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
            strength >= .90f -> pulses.maxByOrNull { it.progress }
            else -> null
        } ?: return

        val typeScale = when (pattern) {
            FusionPattern.INFINITY -> .90f
            FusionPattern.HORIZONTAL,
            FusionPattern.UP_DOWN,
            FusionPattern.ZIGZAG,
            FusionPattern.SIDE_WAVES,
            FusionPattern.STRINGS,
            -> 1.08f
            FusionPattern.RING,
            FusionPattern.GLOW_PULSE,
            FusionPattern.BUBBLES,
            -> 1.02f
            FusionPattern.MIRROR_RAIN,
            FusionPattern.FIRE_DANCE,
            FusionPattern.SNOW,
            -> 1.12f
            else -> .96f
        }
        pulse.active = true
        pulse.pattern = pattern
        pulse.progress = 0f
        pulse.speed = (sync.patternSpeed * typeScale).coerceIn(1.30f, 3.85f)
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
                BeatPatternMode.SIDE_WAVES -> FusionPattern.SIDE_WAVES
                BeatPatternMode.FLAT_BARS -> FusionPattern.FLAT_BARS
                BeatPatternMode.SERIAL_DOTS -> FusionPattern.SERIAL_DOTS
                BeatPatternMode.MIRROR_RAIN -> FusionPattern.MIRROR_RAIN
                BeatPatternMode.FIRE_DANCE -> FusionPattern.FIRE_DANCE
                BeatPatternMode.STRINGS -> FusionPattern.STRINGS
                BeatPatternMode.BUBBLES -> FusionPattern.BUBBLES
                BeatPatternMode.SNOW -> FusionPattern.SNOW
                BeatPatternMode.GLOW_PULSE -> FusionPattern.GLOW_PULSE
                BeatPatternMode.AUTO,
                BeatPatternMode.OFF,
                -> FusionPattern.INFINITY
            }
        }

        if (random.nextFloat() < .28f) return lastPattern
        val low = groupAverage(0, 3)
        val mid = groupAverage(4, 9)
        val high = groupAverage(10, 15)
        return when {
            low > .62f -> weightedChoice(
                FusionPattern.RING to 14,
                FusionPattern.GLOW_PULSE to 13,
                FusionPattern.STACKED to 12,
                FusionPattern.FLAT_BARS to 12,
                FusionPattern.FIRE_DANCE to 11,
                FusionPattern.RECTANGLE to 10,
                FusionPattern.BUBBLES to 9,
                FusionPattern.INFINITY to 8,
                FusionPattern.DIAMOND to 6,
                FusionPattern.SERIAL_DOTS to 5,
            )
            mid > high * 1.10f -> weightedChoice(
                FusionPattern.INFINITY to 15,
                FusionPattern.STRINGS to 14,
                FusionPattern.SIDE_WAVES to 13,
                FusionPattern.HORIZONTAL to 12,
                FusionPattern.FLAT_BARS to 10,
                FusionPattern.ZIGZAG to 9,
                FusionPattern.STACKED to 8,
                FusionPattern.BUBBLES to 7,
                FusionPattern.RECTANGLE to 6,
                FusionPattern.SERIAL_DOTS to 6,
            )
            high > mid * 1.12f -> weightedChoice(
                FusionPattern.MIRROR_RAIN to 15,
                FusionPattern.SNOW to 14,
                FusionPattern.UP_DOWN to 12,
                FusionPattern.ZIGZAG to 11,
                FusionPattern.CROSS to 10,
                FusionPattern.SIDE_WAVES to 9,
                FusionPattern.HORIZONTAL to 8,
                FusionPattern.SERIAL_DOTS to 8,
                FusionPattern.DIAMOND to 7,
                FusionPattern.STRINGS to 6,
            )
            else -> weightedChoice(
                FusionPattern.INFINITY to 10,
                FusionPattern.SIDE_WAVES to 9,
                FusionPattern.STRINGS to 9,
                FusionPattern.HORIZONTAL to 8,
                FusionPattern.SERIAL_DOTS to 8,
                FusionPattern.RING to 7,
                FusionPattern.FLAT_BARS to 7,
                FusionPattern.RECTANGLE to 7,
                FusionPattern.MIRROR_RAIN to 6,
                FusionPattern.BUBBLES to 6,
                FusionPattern.DIAMOND to 5,
                FusionPattern.ZIGZAG to 5,
                FusionPattern.GLOW_PULSE to 5,
                FusionPattern.CROSS to 4,
                FusionPattern.FIRE_DANCE to 4,
                FusionPattern.SNOW to 4,
                FusionPattern.STACKED to 4,
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
        val liveMode = effectiveLivePatternMode()
        val modeBoost = when (liveMode) {
            LivePatternMode.SUBTLE -> .88f
            LivePatternMode.STRONG -> 1.16f
            LivePatternMode.AUTO -> 1.08f
            else -> 1f
        }
        val baseAlpha = if (stage) .76f else .40f * modeBoost
        val baseScale = if (stage) .91f else .61f

        pulses.forEach { pulse ->
            if (!pulse.active) return@forEach
            val p = pulse.progress.coerceIn(0f, 1f)
            val attack = (1f - exp(-p * 28f)).coerceIn(0f, 1f)
            val release = (1f - p).pow(.66f)
            val envelope = attack * release
            val alphaValue = (
                envelope * pulse.strength * baseAlpha * opacity *
                    if (pulse.predicted) .54f else 1f
                ).coerceIn(0f, 1f)
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
                    hsv(hue + 112f, .90f, 1f, alphaValue * .82f),
                    hsv(hue + 226f, .96f, 1f, alphaValue),
                ),
                null,
                Shader.TileMode.MIRROR,
            )
            strokePaint.strokeWidth = dp(.82f + pulse.strength * 2.00f) *
                (.74f + neonIntensity * .27f)

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
                FusionPattern.SIDE_WAVES -> drawSideWaves(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.FLAT_BARS -> drawFlatBars(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.SERIAL_DOTS -> drawSerialDots(canvas, centerX, centerY, minSide, scale, pulse, hue, alphaValue)
                FusionPattern.MIRROR_RAIN -> drawMirrorRain(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.FIRE_DANCE -> drawFireDance(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.STRINGS -> drawStrings(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.BUBBLES -> drawBubbles(canvas, centerX, centerY, minSide, scale, pulse)
                FusionPattern.SNOW -> drawSnow(canvas, centerX, centerY, minSide, scale, pulse, hue, alphaValue)
                FusionPattern.GLOW_PULSE -> drawGlowPulse(canvas, centerX, centerY, minSide, scale, pulse, hue, alphaValue)
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
        val halfH = minSide * .48f * scale * (1f + groupAverage(0, 3) * .07f)
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
        val b = minSide * .18f * scale * (1f + groupAverage(4, 9) * .10f)
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
        val amplitude = minSide * .075f * (1f + groupAverage(10, 15) * .14f)
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
        val low = groupAverage(0, 3)
        val oval = sinf(pulse.progress * PI.toFloat() * 2f + pulse.phaseOffset)
        val rx = minSide * .27f * scale * (1f + low * .08f + oval * .08f)
        val ry = minSide * .27f * scale * (1f + low * .05f - oval * .06f)
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), strokePaint)
        if (pulse.strength > .68f) {
            canvas.drawOval(
                RectF(cx - rx * .72f, cy - ry * .72f, cx + rx * .72f, cy + ry * .72f),
                strokePaint,
            )
        }
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

    private fun drawSideWaves(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val halfH = minSide * .39f * scale
        val side = minSide * .24f * scale
        val amp = minSide * (.035f + groupAverage(4, 15) * .035f) * scale
        repeat(2) { directionIndex ->
            val direction = if (directionIndex == 0) -1f else 1f
            path.reset()
            val points = 72
            repeat(points + 1) { index ->
                val n = index / points.toFloat()
                val y = cy - halfH + n * halfH * 2f
                val x = cx + direction * side +
                    sinf(n * PI.toFloat() * 5f + pulse.phaseOffset) * amp * direction
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawFlatBars(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val low = groupAverage(0, 3)
        val mid = groupAverage(4, 9)
        val high = groupAverage(10, 15)
        val count = 7
        val spacing = minSide * .042f * scale
        repeat(count) { index ->
            val normalized = index / (count - 1f)
            val envelope = when {
                normalized < .34f -> high
                normalized < .68f -> mid
                else -> low
            }
            val widthScale = .42f + envelope * .38f + pulse.strength * .08f
            val halfW = minSide * widthScale * scale * .52f
            val y = cy + (index - (count - 1) / 2f) * spacing
            canvas.drawLine(cx - halfW, y, cx + halfW, y, strokePaint)
        }
    }

    private fun drawSerialDots(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
        hue: Float,
        alphaValue: Float,
    ) {
        val rx = minSide * .34f * scale
        val ry = minSide * .27f * scale
        val count = 28
        repeat(count) { index ->
            val t = index / count.toFloat() * PI.toFloat() * 2f + pulse.phaseOffset * .08f
            val x = cx + cosf(t) * rx
            val y = cy + sinf(t) * ry
            val travel = (index / count.toFloat() + pulse.progress * 1.6f) % 1f
            val head = (1f - abs(travel - .5f) * 2f).coerceIn(0f, 1f)
            val radius = dp(.65f + head * 2.2f + pulse.strength * .45f)
            fillPaint.color = hsv(hue + index * 10f, .88f, 1f, alphaValue * (.32f + head * .68f))
            canvas.drawCircle(x, y, radius, fillPaint)
        }
    }

    private fun drawMirrorRain(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val heightLocal = minSide * .34f * scale
        val columns = 8
        repeat(columns) { index ->
            val n = index / (columns - 1f)
            val xOffset = minSide * (.045f + n * .30f) * scale
            val travel = (pulse.progress + n * .22f) % 1f
            val y = cy - heightLocal + travel * heightLocal * 2f
            val streak = dp(7f + groupAverage(10, 15) * 14f)
            canvas.drawLine(cx - xOffset, y - streak, cx - xOffset, y + streak, strokePaint)
            canvas.drawLine(cx + xOffset, y - streak, cx + xOffset, y + streak, strokePaint)
        }
    }

    private fun drawFireDance(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val low = groupAverage(0, 3)
        val heightLocal = minSide * (.28f + low * .20f) * scale
        val baseWidth = minSide * .19f * scale
        repeat(3) { flame ->
            path.reset()
            val offset = (flame - 1) * baseWidth * .72f
            val points = 18
            repeat(points + 1) { index ->
                val n = index / points.toFloat()
                val y = cy + heightLocal * .48f - n * heightLocal
                val taper = 1f - n * .72f
                val x = cx + offset * taper +
                    sinf(n * PI.toFloat() * (4f + flame) + pulse.phaseOffset + flame) *
                    baseWidth * .22f * taper
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawStrings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val halfW = minSide * .38f * scale
        val amplitude = minSide * (.022f + groupAverage(4, 9) * .045f) * scale
        repeat(4) { stringIndex ->
            path.reset()
            val yBase = cy + (stringIndex - 1.5f) * minSide * .055f * scale
            val points = 72
            repeat(points + 1) { index ->
                val n = index / points.toFloat()
                val x = cx - halfW + n * halfW * 2f
                val y = yBase + sinf(
                    n * PI.toFloat() * (3f + stringIndex * .45f) +
                        pulse.phaseOffset + pulse.progress * PI.toFloat() * 2f,
                ) * amplitude
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawBubbles(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
    ) {
        val low = groupAverage(0, 3)
        repeat(7) { index ->
            val angle = index / 7f * PI.toFloat() * 2f + pulse.phaseOffset
            val distance = minSide * (.08f + (index % 3) * .055f) * scale
            val drift = minSide * .055f * pulse.progress
            val x = cx + cosf(angle) * (distance + drift)
            val y = cy + sinf(angle) * (distance + drift * .75f)
            val radius = minSide * (.018f + (index % 3) * .009f + low * .015f) * scale
            canvas.drawCircle(x, y, radius, strokePaint)
        }
    }

    private fun drawSnow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
        hue: Float,
        alphaValue: Float,
    ) {
        val halfW = minSide * .36f * scale
        val halfH = minSide * .34f * scale
        repeat(18) { index ->
            val seed = (index * 37 % 101) / 100f
            val x = cx - halfW + ((index * 53 % 97) / 96f) * halfW * 2f
            val travel = (seed + pulse.progress * (1.0f + (index % 4) * .08f)) % 1f
            val y = cy - halfH + travel * halfH * 2f
            val radius = dp(.45f + (index % 4) * .24f + groupAverage(10, 15) * .55f)
            fillPaint.color = hsv(hue + index * 7f, .48f, 1f, alphaValue * (.42f + (index % 3) * .16f))
            canvas.drawCircle(x, y, radius, fillPaint)
        }
    }

    private fun drawGlowPulse(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        minSide: Float,
        scale: Float,
        pulse: PatternPulse,
        hue: Float,
        alphaValue: Float,
    ) {
        val radius = minSide * (.16f + pulse.progress * .22f + groupAverage(0, 3) * .05f) * scale
        fillPaint.shader = RadialGradient(
            cx,
            cy,
            max(dp(1f), radius),
            intArrayOf(
                hsv(hue + 15f, .70f, 1f, alphaValue * .36f),
                hsv(hue + 120f, .86f, 1f, alphaValue * .17f),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, .46f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null
        canvas.drawCircle(cx, cy, radius * .72f, strokePaint)
    }

    private fun drawSideAccents(
        canvas: Canvas,
        drawBars: Boolean,
        stripeMultiplier: Float,
        stage: Boolean,
    ) {
        val endpoint = VisualTuningPreferences.endpointMode(context)
        val opacity = VisualTuningPreferences.opacity(context)
        val segments = if (stage) 52 else 48
        val top = dp(if (stage) 12f else 8f)
        val bottom = height - dp(if (stage) 12f else 8f)
        val usable = max(1f, bottom - top)
        val time = SystemClock.uptimeMillis() / 1000f
        val phase = SyncLearningRuntime.hueAt()
        val beat = max(beatEnvelope, sync.beatStrength * .82f)
        val low = groupAverage(0, 3).pow(.68f)
        val mid = groupAverage(4, 9).pow(.72f)

        repeat(2) { side ->
            val left = side == 0
            val direction = if (left) 1f else -1f
            val outer = if (left) dp(if (stage) 7f else 4.8f) else width - dp(if (stage) 7f else 4.8f)
            repeat(segments) { segment ->
                val progress = segment / (segments - 1f)
                val band = edgeBandIndex(progress)
                val detail = levels[band].coerceIn(0f, 1f).pow(.62f)
                val level = (low * .82f + detail * .18f).coerceIn(0f, 1f)
                val body = sinf(progress * PI.toFloat()) * dp(if (stage) 9f else 12f)
                val wave = sinf(progress * PI.toFloat() * 5f + time * .58f + side) * dp(1.7f)
                val baseX = outer + direction * (
                    dp(1.5f) + body + wave * (.12f + mid * .48f)
                    )
                val length = (
                    dp(if (stage) 4.4f else 3.6f) +
                        level * dp(if (stage) 31f else 29f) * neonIntensity +
                        beat * dp(if (stage) 7.5f else 5.5f)
                    ) * stripeMultiplier
                val endX = baseX + direction * length
                val y = top + progress * usable
                val hue = phase + progress * 430f + side * 145f
                val visible = (
                    .22f + level * .78f + beat * .16f
                    ).coerceIn(.18f, 1f)

                if (drawBars) {
                    strokePaint.shader = null
                    strokePaint.color = hsv(
                        hue,
                        .96f,
                        1f,
                        visible * (if (stage) .92f else .72f) * opacity,
                    )
                    strokePaint.strokeWidth = dp(
                        if (stage) .95f + level * 2.05f else .88f + level * 1.82f,
                    )
                    canvas.drawLine(baseX, y, endX, y, strokePaint)
                }

                if (endpoint != EndpointMode.OFF) {
                    drawEndpointOrb(
                        canvas = canvas,
                        x = endX,
                        y = y,
                        hue = hue,
                        level = level,
                        beat = beat,
                        opacity = opacity,
                        endpoint = endpoint,
                        stage = stage,
                    )
                }
            }
        }
    }

    private fun drawHorizontalStageStripes(canvas: Canvas, stripeMultiplier: Float) {
        val opacity = VisualTuningPreferences.opacity(context)
        val endpoint = VisualTuningPreferences.endpointMode(context)
        val phase = SyncLearningRuntime.hueAt()
        val low = groupAverage(0, 3).pow(.72f)
        val mid = groupAverage(4, 9).pow(.72f)
        val high = groupAverage(10, 15).pow(.70f)
        val beat = max(beatEnvelope, sync.beatStrength * .82f)
        val segments = 62
        val inset = dp(12f)
        val usable = width - inset * 2f
        val gap = usable / max(1f, (segments - 1).toFloat())

        repeat(segments) { index ->
            val progress = index / max(1f, (segments - 1).toFloat())
            val detail = levels[mirroredBandIndex(progress)].pow(.62f)
            val topLevel = (high * .56f + mid * .34f + detail * .10f).coerceIn(0f, 1f)
            val bottomLevel = (mid * .58f + high * .28f + low * .08f + detail * .06f).coerceIn(0f, 1f)
            val x = inset + index * gap
            val topStart = dp(7f)
            val bottomStart = height - dp(7f)
            val topLength = (
                dp(2.2f) + topLevel * dp(14f) * neonIntensity + beat * dp(3.5f)
                ) * stripeMultiplier
            val bottomLength = (
                dp(2.2f) + bottomLevel * dp(14f) * neonIntensity + beat * dp(3.5f)
                ) * stripeMultiplier
            val hueTop = phase + progress * 430f
            val hueBottom = phase + 170f + progress * 430f

            strokePaint.color = hsv(
                hueTop,
                .95f,
                1f,
                (.30f + topLevel * .68f + beat * .12f) * opacity,
            )
            strokePaint.strokeWidth = dp(.78f + topLevel * 1.55f)
            canvas.drawLine(x, topStart, x, topStart + topLength, strokePaint)

            strokePaint.color = hsv(
                hueBottom,
                .95f,
                1f,
                (.30f + bottomLevel * .68f + beat * .12f) * opacity,
            )
            strokePaint.strokeWidth = dp(.78f + bottomLevel * 1.55f)
            canvas.drawLine(x, bottomStart, x, bottomStart - bottomLength, strokePaint)

            if (endpoint != EndpointMode.OFF && index % 3 == 0) {
                drawEndpointOrb(
                    canvas,
                    x,
                    topStart + topLength,
                    hueTop,
                    topLevel,
                    beat,
                    opacity,
                    endpoint,
                    stage = true,
                    compact = true,
                )
                drawEndpointOrb(
                    canvas,
                    x,
                    bottomStart - bottomLength,
                    hueBottom,
                    bottomLevel,
                    beat,
                    opacity,
                    endpoint,
                    stage = true,
                    compact = true,
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
        stage: Boolean,
        compact: Boolean = false,
    ) {
        // No permanent dotted wall: the point only appears when the line has
        // real musical energy. Its centre is exactly the calculated line end.
        val activity = (level * .82f + beat * .28f).coerceIn(0f, 1f)
        if (activity < .13f) return

        val strength = endpoint.strength
        val compactScale = if (compact) .70f else 1f
        val stageScale = if (stage) 1.10f else 1f
        val coreRadius = dp(
            (.42f + activity * 1.18f + beat * .30f) * compactScale * stageScale,
        ) * strength
        val glowRadius = max(dp(.9f), coreRadius * 2.75f)
        val alphaValue = (activity * .86f + beat * .12f).coerceIn(.12f, 1f)

        fillPaint.shader = RadialGradient(
            x,
            y,
            glowRadius,
            intArrayOf(
                hsv(hue + 18f, .28f, 1f, alphaValue * opacity),
                hsv(hue, .88f, 1f, alphaValue * .30f * opacity),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, glowRadius, fillPaint)
        fillPaint.shader = null
        fillPaint.color = hsv(hue + 22f, .12f, 1f, alphaValue * opacity)
        canvas.drawCircle(x, y, max(dp(.38f), coreRadius), fillPaint)
    }

    private fun groupAverage(start: Int, end: Int): Float {
        val safeStart = start.coerceIn(0, levels.lastIndex)
        val safeEnd = end.coerceIn(safeStart, levels.lastIndex)
        var total = 0f
        var count = 0
        for (index in safeStart..safeEnd) {
            total += levels[index]
            count += 1
        }
        return if (count == 0) 0f else (total / count).coerceIn(0f, 1f)
    }

    private fun edgeBandIndex(progress: Float): Int = when {
        progress < .32f -> (15f - progress / .32f * 5f).toInt().coerceIn(10, 15)
        progress < .72f -> (10f - (progress - .32f) / .40f * 6f).toInt().coerceIn(4, 10)
        else -> (4f - (progress - .72f) / .28f * 4f).toInt().coerceIn(0, 4)
    }

    private fun mirroredBandIndex(progress: Float): Int {
        val mirrored = if (progress <= .5f) progress * 2f else (1f - progress) * 2f
        return (mirrored * levels.lastIndex).toInt().coerceIn(0, levels.lastIndex)
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alphaValue: Float): Int {
        val brightness = (.65f + neonIntensity * .33f).coerceIn(.68f, 1.22f)
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
