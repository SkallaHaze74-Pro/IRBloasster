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
 * 1.6.1 Sync Fix: pattern lifetime and phase now follow the learned BPM instead
 * of a fixed animation speed. Endpoint orbs use the exact same interpolated
 * band level as their bar and are rendered after the centre patterns so they
 * stay visible in the normal LIVE view and in "Nur Striche".
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
    private var neonIntensity = 1.35f
    private var stageMode = false
    private var lastFrameNanos = 0L
    private var phase = 0f
    private var beatEnvelope = 0f
    private var tempoFactor = 1f
    private var lastAudioBeatSequence = Long.MIN_VALUE
    private var lastVisualBeatSequence = Long.MIN_VALUE
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
        updateTempoFactor(dt)
        updateLevels(dt)
        detectBeat(nowMs)
        updatePulses(dt)
        beatEnvelope = max(snapshot.beat, beatEnvelope * exp(-dt * (6.8f + tempoFactor * 1.7f)))

        // The Fusion hue follows tempo but stays intentionally slower than the
        // spectrum. Beat gives a short push; it no longer spins independently.
        val phaseSpeed = 7f + (tempoFactor - .72f).coerceAtLeast(0f) * 27f + beatEnvelope * 44f
        phase = (phase + dt * phaseSpeed) % 360f

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
            // In CLEAN / "Nur Striche" there are no centre patterns, but the
            // endpoint dots remain deliberately visible.
            if (visualMode != VisualLayerMode.BORDER_ONLY) {
                drawEndpointAccents(canvas, drawBars = false)
            }
        }

        postInvalidateOnAnimation()
    }

    private fun updateTempoFactor(dt: Float) {
        val beat = VisualBeatRuntime.snapshot()
        val bpm = when {
            beat.bpm in 55f..220f -> beat.bpm
            AutoTuneRuntime.snapshot().bpm in 55f..220f -> AutoTuneRuntime.snapshot().bpm
            else -> 0f
        }
        val target = if (bpm > 0f) {
            (bpm / 120f).coerceIn(.72f, 1.48f)
        } else {
            (.86f + snapshot.spectralFlux * .28f + snapshot.treble * .14f).coerceIn(.76f, 1.28f)
        }
        tempoFactor += (target - tempoFactor) * (1f - exp(-dt * 2.6f))
    }

    private fun updateLevels(dt: Float) {
        // Attack gets faster with BPM; release stays softer so the visual line
        // still looks smooth between two quick beats.
        val attackRate = 28f + tempoFactor * 20f
        val releaseRate = 10f + tempoFactor * 5.5f
        val attack = 1f - exp(-dt * attackRate)
        val release = 1f - exp(-dt * releaseRate)
        for (index in levels.indices) {
            val target = snapshot.levels.getOrNull(index) ?: 0f
            val factor = if (target > levels[index]) attack else release
            levels[index] += (target - levels[index]) * factor
        }
    }

    private fun detectBeat(nowMs: Long) {
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

        val visualBeat = VisualBeatRuntime.snapshot()
        if (
            lastAudioBeatSequence == Long.MIN_VALUE ||
            snapshot.beatSequence < lastAudioBeatSequence
        ) {
            lastAudioBeatSequence = snapshot.beatSequence
        }
        if (
            lastVisualBeatSequence == Long.MIN_VALUE ||
            visualBeat.sequence < lastVisualBeatSequence
        ) {
            lastVisualBeatSequence = visualBeat.sequence
        }

        var trigger = false
        var predicted = false
        var strength = 0f

        if (snapshot.beatSequence > lastAudioBeatSequence) {
            trigger = true
            strength = max(.32f, max(snapshot.beat, snapshot.bass * .90f))
            lastAudioBeatSequence = snapshot.beatSequence
        } else if (
            liveMode != LivePatternMode.BEAT_ONLY &&
            visualBeat.sequence > lastVisualBeatSequence &&
            nowMs - lastSpawnAt >= minimumPatternGapMs()
        ) {
            trigger = true
            predicted = visualBeat.predicted
            strength = max(.25f, visualBeat.pulse) * if (predicted) .58f else .78f
        }
        lastVisualBeatSequence = max(lastVisualBeatSequence, visualBeat.sequence)

        if (!trigger || nowMs - lastSpawnAt < minimumPatternGapMs()) return
        if (!stageMode && liveMode == LivePatternMode.SUBTLE && snapshot.beatSequence % 2L != 0L) return

        spawnPattern(
            pattern = choosePattern(),
            strength = strength.coerceIn(.18f, 1f),
            predicted = predicted,
            nowMs = nowMs,
        )
    }

    private fun minimumPatternGapMs(): Long {
        val bpm = VisualBeatRuntime.snapshot().bpm
        if (bpm !in 55f..220f) return 105L
        val quarter = 60_000f / bpm
        // About one third of a quarter note, bounded against double triggers.
        return (quarter * .31f).toLong().coerceIn(92L, 178L)
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
            -> if (strength > .68f) 2 else 1
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
            strength >= .86f -> pulses.maxByOrNull { it.progress }
            else -> null
        } ?: return

        pulse.active = true
        pulse.pattern = pattern
        pulse.progress = 0f
        val baseSpeed = when (pattern) {
            FusionPattern.INFINITY -> 1.55f
            FusionPattern.HORIZONTAL,
            FusionPattern.UP_DOWN,
            FusionPattern.ZIGZAG,
            -> 1.90f
            FusionPattern.RING -> 1.76f
            else -> 1.66f
        }
        // A 90 BPM song stays calmer; a 160 BPM song completes the same shape
        // faster, so the next beat does not feel visually late.
        pulse.speed = baseSpeed * tempoFactor.coerceIn(.76f, 1.42f)
        pulse.strength = strength
        pulse.hue = phase
        pulse.predicted = predicted
        pulse.phaseOffset = random.nextFloat() * PI.toFloat() * 2f
        lastPattern = pattern
        lastSpawnAt = nowMs
        beatEnvelope = max(beatEnvelope, strength)
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
            // Strong attack at the start keeps the shape visually on the beat.
            val attack = (1f - exp(-p * 18f)).coerceIn(0f, 1f)
            val release = (1f - p).pow(.62f)
            val envelope = attack * release
            val alpha = envelope * pulse.strength * baseAlpha * opacity *
                if (pulse.predicted) .56f else 1f
            if (alpha <= .012f) return@forEach
            val scale = baseScale * (.78f + p * .36f + pulse.strength * .08f)
            val hue = pulse.hue + p * (26f + tempoFactor * 18f)

            strokePaint.shader = LinearGradient(
                centerX - minSide * .42f,
                centerY - minSide * .30f,
                centerX + minSide * .42f,
                centerY + minSide * .30f,
                intArrayOf(
                    hsv(hue, .96f, 1f, alpha),
                    hsv(hue + 112f, .90f, 1f, alpha * .76f),
                    hsv(hue + 226f, .96f, 1f, alpha),
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
        val segments = 52
        val top = dp(8f)
        val bottom = height - dp(8f)
        val usable = max(1f, bottom - top)
        val time = SystemClock.uptimeMillis() / 1000f
        val beat = max(snapshot.beat, VisualBeatRuntime.snapshot().pulse)

        repeat(2) { side ->
            val left = side == 0
            val direction = if (left) 1f else -1f
            val outer = if (left) dp(4.5f) else width - dp(4.5f)
            repeat(segments) { segment ->
                val progress = segment / (segments - 1f)
                val band = edgeBandIndex(progress)
                val level = levels[band].coerceIn(0f, 1f).pow(.60f)
                val body = sinf(progress * PI.toFloat()) * dp(13f)
                val wave = sinf(progress * PI.toFloat() * 5f + time * (.44f + tempoFactor * .30f) + side) * dp(2.0f)
                val baseX = outer + direction * (dp(2f) + body + wave * (.18f + snapshot.mid * .58f))
                val length = dp(3.0f) + level * dp(if (stageMode) 24f else 28f) * neonIntensity + beat * dp(6.5f)
                val endX = baseX + direction * length
                val y = top + progress * usable
                val hue = phase + progress * 380f + side * 145f
                val visible = (.30f + level * .62f + beat * .18f).coerceIn(.28f, 1f)

                if (drawBars) {
                    strokePaint.shader = null
                    strokePaint.color = hsv(hue, .96f, 1f, visible * .74f * opacity)
                    strokePaint.strokeWidth = dp(.90f + level * 1.9f)
                    canvas.drawLine(baseX, y, endX, y, strokePaint)
                }

                val scale = endpoint.strength
                // Keep a real visible core even on quieter bands. This restores
                // the bright dot seen in the earlier build instead of a faint haze.
                val coreRadius = dp(.72f + level * 1.35f + beat * .72f) * scale
                val glowRadius = max(dp(2.2f), coreRadius * 3.15f)
                fillPaint.shader = RadialGradient(
                    endX,
                    y,
                    glowRadius,
                    intArrayOf(
                        hsv(hue + 18f, .35f, 1f, visible * .88f * opacity),
                        hsv(hue, .86f, 1f, visible * .34f * opacity),
                        Color.TRANSPARENT,
                    ),
                    null,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(endX, y, glowRadius, fillPaint)
                fillPaint.shader = null
                fillPaint.color = hsv(hue + 22f, .20f, 1f, max(.62f, visible) * opacity)
                canvas.drawCircle(endX, y, max(dp(.62f), coreRadius), fillPaint)
            }
        }
    }

    private fun edgeBandIndex(progress: Float): Int = when {
        progress < .32f -> (15f - progress / .32f * 5f).toInt().coerceIn(10, 15)
        progress < .72f -> (10f - (progress - .32f) / .40f * 6f).toInt().coerceIn(4, 10)
        else -> (4f - (progress - .72f) / .28f * 4f).toInt().coerceIn(0, 4)
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
        val brightness = (.63f + neonIntensity * .32f).coerceIn(.65f, 1.20f)
        val color = Color.HSVToColor(
            floatArrayOf(
                (hue % 360f + 360f) % 360f,
                saturation.coerceIn(0f, 1f),
                (value * brightness).coerceIn(0f, 1f),
            ),
        )
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
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
