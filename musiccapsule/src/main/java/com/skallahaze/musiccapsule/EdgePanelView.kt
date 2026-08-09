package com.skallahaze.musiccapsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Full-display, touch-through 144 Hz visualizer.
 *
 * Version 1.4 separates smooth base motion from crisp beat events. The base
 * spectrum uses selectable attack/release profiles, while Adaptive Beat Memory
 * learns recent intervals and fills an occasional missed kick. This keeps the
 * frame silky without making the beat disappear.
 */
class EdgePanelView(context: Context) : View(context) {
    private data class StarParticle(
        var active: Boolean = false,
        var x: Float = 0f,
        var y: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var gravity: Float = 0f,
        var age: Float = 0f,
        var life: Float = 1f,
        var size: Float = 1f,
        var hue: Float = 0f,
        var spin: Float = 0f,
        var rotation: Float = 0f,
        var strength: Float = 0f,
    )

    private data class Shockwave(
        var active: Boolean = false,
        var progress: Float = 0f,
        var speed: Float = 1f,
        var strength: Float = 0f,
        var hue: Float = 0f,
        var mode: ReactiveFlowMode = ReactiveFlowMode.INWARD,
    )

    private val density = resources.displayMetrics.density
    private val profileScale = XiaomiDisplayProfile.visualScale(context)
    private val edgeBudget = XiaomiDisplayProfile.edgeBudgetDp(context)
    private val random = Random()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePath = Path()
    private val symbolPath = Path()
    private val targetLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val displayLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val stars = Array(MAX_STARS) { StarParticle() }
    private val shockwaves = Array(MAX_SHOCKWAVES) { Shockwave() }

    private var snapshot = CapsuleRuntime.snapshot()
    private var neonIntensity = 1.35f
    private var beatFxMode = BeatFxMode.BRUTAL
    private var requestedFlowMode = ReactiveFlowMode.AUTO
    private var visualLayerMode = VisualLayerMode.FULL
    private var motionProfile = MotionProfile.SILKY
    private var trailMode = TrailMode.SHORT
    private var visualEnabled = true

    private var lastFrameNanos = 0L
    private var colorPhase = 0f
    private var colorVelocity = 0f
    private var displayBass = 0f
    private var displayMid = 0f
    private var displayTreble = 0f
    private var displayFlux = 0f
    private var beatPulse = 0f
    private var bottomFlash = 0f
    private var silenceStartedAt = 0L

    // Adaptive visual beat-memory state.
    private var lastAudioBeatSequence = Long.MIN_VALUE
    private var localBeatSequence = 0L
    private var localBassAverage = .12f
    private var localFluxAverage = .05f
    private var previousBass = 0f
    private var previousTreble = 0f
    private var lastVisualBeatAt = 0L
    private var estimatedBeatIntervalMs = 0f
    private var beatConfidence = 0f
    private var lastVisualBeatPublishAt = 0L

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        systemUiVisibility =
            SYSTEM_UI_FLAG_LAYOUT_STABLE or
                SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    fun setSnapshot(value: CapsuleSnapshot, intensity: Float, enabled: Boolean) {
        snapshot = value
        neonIntensity = intensity.coerceIn(.75f, 1.8f)
        beatFxMode = CapsulePreferences.beatFxMode(context)
        requestedFlowMode = CapsulePreferences.flowMode(context)
        visualLayerMode = CapsulePreferences.visualLayerMode(context)
        motionProfile = CapsulePreferences.motionProfile(context)
        trailMode = CapsulePreferences.trailMode(context)
        visualEnabled = enabled
        for (index in targetLevels.indices) {
            targetLevels[index] = value.levels.getOrNull(index)?.coerceIn(0f, 1f) ?: 0f
        }
        visibility = if (enabled) VISIBLE else GONE
        if (enabled) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visualEnabled || width <= 0 || height <= 0) return

        val nowNanos = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .075f)
        }
        lastFrameNanos = nowNanos
        val nowMs = SystemClock.elapsedRealtime()

        updateSilenceState(nowMs)
        updateAudioInterpolation(dt, nowMs)
        val flowMode = resolvedFlowMode()
        detectAdaptiveBeat(nowMs, flowMode)
        updateParticles(dt, nowMs)
        updateShockwaves(dt)
        updateColorPhase(dt)

        val energy = average(displayLevels)
        drawPerimeter(canvas, energy, flowMode)

        when (visualLayerMode) {
            VisualLayerMode.FULL -> {
                drawTopAndBottomRails(canvas, flowMode)
                drawEdge(canvas, left = true, flowMode = flowMode)
                drawEdge(canvas, left = false, flowMode = flowMode)
                drawShockwaves(canvas)
                drawSymbols(canvas, flowMode)
                drawAmbientParticles(canvas)
                drawStarRain(canvas)
                drawBottomBassFlash(canvas)
            }

            VisualLayerMode.CLEAN -> {
                drawTopAndBottomRails(canvas, flowMode)
                drawEdge(canvas, left = true, flowMode = flowMode)
                drawEdge(canvas, left = false, flowMode = flowMode)
                drawShockwaves(canvas)
                drawStarRain(canvas)
                drawBottomBassFlash(canvas)
            }

            VisualLayerMode.BORDER_ONLY -> Unit

            VisualLayerMode.BORDER_DROP -> {
                drawShockwaves(canvas)
                drawStarRain(canvas)
                drawBottomBassFlash(canvas)
            }
        }

        publishHeldBeat(nowMs)
        postInvalidateOnAnimation()
    }

    private fun updateSilenceState(nowMs: Long) {
        val hasSignal = snapshot.signal > SIGNAL_GATE ||
            snapshot.bass > .025f ||
            snapshot.mid > .02f ||
            snapshot.treble > .02f
        if (hasSignal) {
            silenceStartedAt = 0L
        } else if (silenceStartedAt == 0L) {
            silenceStartedAt = nowMs
        }
    }

    private fun silenceDuration(nowMs: Long): Long =
        if (silenceStartedAt == 0L) 0L else nowMs - silenceStartedAt

    private fun updateAudioInterpolation(dt: Float, nowMs: Long) {
        val silenceMs = silenceDuration(nowMs)
        val silence = silenceMs >= SILENCE_FAST_FADE_MS
        val attackRate = motionProfile.attackRate
        val releaseRate = if (silence) trailMode.silenceFadeRate else motionProfile.releaseRate
        val attack = 1f - exp(-dt * attackRate)
        val release = 1f - exp(-dt * releaseRate)

        for (index in displayLevels.indices) {
            val target = if (silence) 0f else targetLevels[index]
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
            if (silenceMs >= SILENCE_SNAP_MS && displayLevels[index] < .012f) {
                displayLevels[index] = 0f
            }
        }

        displayBass = smoothFeature(displayBass, if (silence) 0f else snapshot.bass, dt, attackRate, releaseRate)
        displayMid = smoothFeature(displayMid, if (silence) 0f else snapshot.mid, dt, attackRate * .88f, releaseRate)
        displayTreble = smoothFeature(displayTreble, if (silence) 0f else snapshot.treble, dt, attackRate * 1.08f, releaseRate * 1.08f)
        displayFlux = smoothFeature(displayFlux, if (silence) 0f else snapshot.spectralFlux, dt, attackRate * 1.14f, releaseRate * 1.2f)

        beatPulse *= exp(-dt * trailMode.beatDecayRate)
        bottomFlash *= exp(-dt * trailMode.beatDecayRate * .86f)
        if (silenceMs >= SILENCE_SNAP_MS) {
            beatPulse *= exp(-dt * 18f)
            bottomFlash *= exp(-dt * 18f)
        }
    }

    private fun smoothFeature(
        current: Float,
        target: Float,
        dt: Float,
        attackRate: Float,
        releaseRate: Float,
    ): Float {
        val rate = if (target > current) attackRate else releaseRate
        val factor = 1f - exp(-dt * rate)
        return current + (target - current) * factor
    }

    private fun detectAdaptiveBeat(nowMs: Long, flowMode: ReactiveFlowMode) {
        val audioSequence = snapshot.beatSequence
        if (lastAudioBeatSequence == Long.MIN_VALUE || audioSequence < lastAudioBeatSequence) {
            lastAudioBeatSequence = audioSequence
        }

        if (audioSequence > lastAudioBeatSequence) {
            val count = min(3L, audioSequence - lastAudioBeatSequence).toInt()
            repeat(count) {
                if (nowMs - lastVisualBeatAt >= visualMinimumGap()) {
                    registerBeat(
                        strength = max(.36f, max(snapshot.beat, displayBass * .84f)),
                        predicted = false,
                        nowMs = nowMs,
                        flowMode = flowMode,
                    )
                }
            }
            lastAudioBeatSequence = audioSequence
        } else {
            val bassRise = max(0f, displayBass - previousBass)
            val trebleRise = max(0f, displayTreble - previousTreble)
            val bassRatio = displayBass / max(.035f, localBassAverage)
            val fluxRatio = displayFlux / max(.025f, localFluxAverage)
            val score =
                bassRise * 3.7f +
                    max(0f, bassRatio - 1f) * .52f +
                    max(0f, fluxRatio - 1f) * .30f +
                    trebleRise * .18f

            val threshold = when (beatFxMode) {
                BeatFxMode.SMOOTH -> .25f
                BeatFxMode.REACTIVE -> .145f
                BeatFxMode.BRUTAL -> .085f
            }

            if (
                silenceDuration(nowMs) < SILENCE_FAST_FADE_MS &&
                snapshot.signal > SIGNAL_GATE &&
                score >= threshold &&
                nowMs - lastVisualBeatAt >= visualMinimumGap()
            ) {
                val strength = ((score - threshold) / max(.08f, .72f - threshold))
                    .coerceIn(.22f, 1f)
                registerBeat(strength, predicted = false, nowMs, flowMode)
            } else {
                maybePredictBeat(nowMs, flowMode)
            }
        }

        previousBass = displayBass
        previousTreble = displayTreble
        localBassAverage += (displayBass - localBassAverage) * .035f
        localFluxAverage += (displayFlux - localFluxAverage) * .045f
    }

    private fun visualMinimumGap(): Long = when (beatFxMode) {
        BeatFxMode.SMOOTH -> 250L
        BeatFxMode.REACTIVE -> 185L
        BeatFxMode.BRUTAL -> 145L
    }

    private fun maybePredictBeat(nowMs: Long, flowMode: ReactiveFlowMode) {
        if (beatConfidence < .42f || estimatedBeatIntervalMs !in 230f..1_050f) return
        if (silenceDuration(nowMs) >= SILENCE_FAST_FADE_MS || snapshot.signal <= SIGNAL_GATE) return

        val elapsed = nowMs - lastVisualBeatAt
        val dueAt = estimatedBeatIntervalMs * .92f
        val latestAt = estimatedBeatIntervalMs * 1.23f
        val supportingEnergy = displayBass > localBassAverage * .82f ||
            displayFlux > localFluxAverage * .86f
        if (elapsed >= dueAt && elapsed <= latestAt && supportingEnergy) {
            val strength = (.31f + displayBass * .34f + displayFlux * .18f).coerceIn(.28f, .66f)
            registerBeat(strength, predicted = true, nowMs, flowMode)
        } else if (elapsed > latestAt) {
            beatConfidence = (beatConfidence - .08f).coerceAtLeast(0f)
        }
    }

    private fun registerBeat(
        strength: Float,
        predicted: Boolean,
        nowMs: Long,
        flowMode: ReactiveFlowMode,
    ) {
        val safeStrength = strength.coerceIn(.18f, 1f)
        val interval = nowMs - lastVisualBeatAt
        if (!predicted && lastVisualBeatAt > 0L && interval in 230L..1_100L) {
            estimatedBeatIntervalMs = if (estimatedBeatIntervalMs == 0f) {
                interval.toFloat()
            } else {
                estimatedBeatIntervalMs * .72f + interval * .28f
            }
            beatConfidence = (beatConfidence + .15f).coerceAtMost(1f)
        } else if (predicted) {
            beatConfidence = (beatConfidence - .015f).coerceAtLeast(0f)
        }

        lastVisualBeatAt = nowMs
        localBeatSequence += 1L
        beatPulse = max(beatPulse, .56f + safeStrength * .44f)
        colorPhase = (colorPhase + 13f + safeStrength * 34f * beatFxMode.multiplier) % 360f

        val dropEnergy = max(snapshot.beat, displayBass * .92f).coerceIn(0f, 1f)
        val starsAllowed = visualLayerMode == VisualLayerMode.FULL ||
            visualLayerMode == VisualLayerMode.CLEAN ||
            visualLayerMode == VisualLayerMode.BORDER_DROP
        if (!predicted && starsAllowed && dropEnergy >= starThreshold()) {
            spawnStars(dropEnergy)
        }
        val waveStrength = max(safeStrength, dropEnergy)
        if (visualLayerMode != VisualLayerMode.BORDER_ONLY && waveStrength >= shockwaveThreshold()) {
            spawnShockwave(waveStrength, flowMode)
        }

        VisualBeatRuntime.publish(
            pulse = beatPulse,
            bpm = estimatedBpm(),
            confidence = beatConfidence,
            sequence = localBeatSequence,
            predicted = predicted,
        )
    }

    private fun publishHeldBeat(nowMs: Long) {
        if (nowMs - lastVisualBeatPublishAt < 70L) return
        lastVisualBeatPublishAt = nowMs
        VisualBeatRuntime.publish(
            pulse = beatPulse,
            bpm = estimatedBpm(),
            confidence = beatConfidence,
            sequence = localBeatSequence,
            predicted = false,
        )
    }

    private fun estimatedBpm(): Float =
        if (estimatedBeatIntervalMs in 230f..1_050f) 60_000f / estimatedBeatIntervalMs else 0f

    private fun starThreshold(): Float = when (beatFxMode) {
        BeatFxMode.SMOOTH -> 1.1f
        BeatFxMode.REACTIVE -> .68f
        BeatFxMode.BRUTAL -> .52f
    }

    private fun shockwaveThreshold(): Float = when (beatFxMode) {
        BeatFxMode.SMOOTH -> .58f
        BeatFxMode.REACTIVE -> .40f
        BeatFxMode.BRUTAL -> .28f
    }

    private fun updateColorPhase(dt: Float) {
        val silent = silenceStartedAt != 0L &&
            SystemClock.elapsedRealtime() - silenceStartedAt >= SILENCE_FAST_FADE_MS
        val desired = if (silent) {
            .55f
        } else {
            (
                3.1f +
                    displayTreble * 82f +
                    displayFlux * 112f +
                    beatPulse * 205f +
                    displayBass * 34f
                ) * beatFxMode.multiplier
        }
        val smoothing = 1f - exp(-dt * 8f)
        colorVelocity += (desired - colorVelocity) * smoothing
        colorPhase = (colorPhase + dt * colorVelocity) % 360f
    }

    private fun resolvedFlowMode(): ReactiveFlowMode {
        if (requestedFlowMode != ReactiveFlowMode.AUTO) return requestedFlowMode
        return when (((localBeatSequence / 6L) % 8L).toInt()) {
            0, 1, 2, 3, 4 -> ReactiveFlowMode.INWARD
            5 -> ReactiveFlowMode.UP
            6 -> ReactiveFlowMode.DOWN
            else -> ReactiveFlowMode.OUTWARD
        }
    }

    private fun drawPerimeter(canvas: Canvas, energy: Float, flowMode: ReactiveFlowMode) {
        val inset = dp(2.2f)
        val radius = min(width, height) * .030f
        val rect = RectF(inset, inset, width - inset, height - inset)
        val flowShift = flowMode.ordinal * 37f
        val colors = intArrayOf(
            hsv(colorPhase + flowShift + 315f, .98f, 1f, 1f),
            hsv(colorPhase + flowShift + 12f, .98f, 1f, 1f),
            hsv(colorPhase + flowShift + 65f, .97f, 1f, 1f),
            hsv(colorPhase + flowShift + 125f, .96f, 1f, 1f),
            hsv(colorPhase + flowShift + 184f, .96f, 1f, 1f),
            hsv(colorPhase + flowShift + 244f, .97f, 1f, 1f),
            hsv(colorPhase + flowShift + 301f, .98f, 1f, 1f),
            hsv(colorPhase + flowShift + 370f, .98f, 1f, 1f),
        )
        strokePaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            colors,
            null,
            Shader.TileMode.MIRROR,
        )
        val power = neonIntensity.coerceIn(.75f, 1.8f)
        val beatBoost = 1f + beatPulse * .34f
        strokePaint.alpha = (30 + energy * 26 + beatPulse * 42).toInt().coerceIn(25, 105)
        strokePaint.strokeWidth = dp(10.5f) * power * beatBoost
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.alpha = (110 + energy * 48 + beatPulse * 52).toInt().coerceIn(100, 225)
        strokePaint.strokeWidth = dp(4.1f) * power.coerceAtMost(1.38f) * (1f + beatPulse * .18f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.alpha = 255
        strokePaint.strokeWidth = dp(1.2f + beatPulse * .55f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun drawTopAndBottomRails(canvas: Canvas, flowMode: ReactiveFlowMode) {
        val usableWidth = width - dp(28f)
        val startX = dp(14f)
        val yTop = dp(10.5f)
        val yBottom = height - dp(10.5f)
        val segments = 72
        val gap = usableWidth / segments
        for (index in 0 until segments) {
            val progress = index / max(1f, (segments - 1).toFloat())
            val bandIndex = railBandIndex(progress)
            val level = max(if (snapshot.analyzerRunning) .025f else .008f, displayLevels[bandIndex].pow(.62f))
            val flow = flowCoordinate(progress, flowMode)
            val hue = colorPhase + flow * 610f + progress * 140f
            val x = startX + index * gap
            val beatWave = abs(sin(progress * PI.toFloat() * 4f + colorPhase * .025f)) * beatPulse
            val length = dp(1.4f) + level * dp(8.8f) * neonIntensity + beatWave * dp(7.5f)
            strokePaint.color = hsv(hue, .97f, 1f, (.20f + level * .72f + beatPulse * .12f).coerceIn(.15f, 1f))
            strokePaint.strokeWidth = dp(.76f) + level * dp(1.45f)
            canvas.drawLine(x, yTop, x, yTop + length, strokePaint)
            canvas.drawLine(x, yBottom, x, yBottom - length, strokePaint)
        }
    }

    private fun drawEdge(canvas: Canvas, left: Boolean, flowMode: ReactiveFlowMode) {
        val segments = 70
        val top = dp(7f)
        val bottom = height - dp(7f)
        val usable = max(1f, bottom - top)
        val outerX = if (left) dp(4.5f) else width - dp(4.5f)
        val direction = if (left) 1f else -1f
        val time = SystemClock.uptimeMillis() / 1000f
        val travel = flowTravel(time, flowMode)
        val panelBody = dp(edgeBudget * .70f)

        linePath.reset()
        for (step in 0..96) {
            val progress = step / 96f
            val y = top + usable * progress
            val wave = sin(progress * PI.toFloat() * 5.15f + travel) * dp(3.1f)
            val secondary = sin(progress * PI.toFloat() * 13.1f - travel * 1.35f) * dp(1.1f)
            val body = sin(progress * PI.toFloat()) * panelBody
            val bassPush = displayBass * beatPulse * sin(progress * PI.toFloat()) * dp(8.5f)
            val x = outerX + direction * (
                dp(2f) + body + (wave + secondary) * (.18f + displayMid * .80f) + bassPush
                )
            if (step == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        val coreHue = if (left) colorPhase + 184f else colorPhase + 312f
        strokePaint.strokeWidth = dp(10f) * neonIntensity * (1f + beatPulse * .13f)
        strokePaint.color = hsv(coreHue, .98f, 1f, .035f + displayBass * .052f + beatPulse * .050f)
        canvas.drawPath(linePath, strokePaint)
        strokePaint.strokeWidth = dp(4.1f) * neonIntensity.coerceAtMost(1.38f)
        strokePaint.color = hsv(coreHue + 60f, .98f, 1f, .14f + displayMid * .14f + beatPulse * .11f)
        canvas.drawPath(linePath, strokePaint)
        strokePaint.strokeWidth = dp(1.3f + beatPulse * .38f)
        strokePaint.color = hsv(coreHue + 118f, .93f, 1f, .95f)
        canvas.drawPath(linePath, strokePaint)

        val barGap = usable / segments
        for (segment in 0 until segments) {
            val progress = segment / max(1f, (segments - 1).toFloat())
            val y = top + progress * usable
            val level = displayLevels[edgeBandIndex(progress)].pow(.62f)
            val wave = sin(progress * PI.toFloat() * 5.15f + travel) * dp(3.1f)
            val secondary = sin(progress * PI.toFloat() * 13.1f - travel * 1.35f) * dp(1.1f)
            val body = sin(progress * PI.toFloat()) * panelBody
            val bassPush = displayBass * beatPulse * sin(progress * PI.toFloat()) * dp(8.5f)
            val baseX = outerX + direction * (
                dp(2f) + body + (wave + secondary) * (.18f + displayMid * .80f) + bassPush
                )
            val flowBeat = flowPulse(progress, flowMode)
            val length = dp(3.4f) +
                level * dp(26f) * neonIntensity +
                zoneEnergy(progress) * dp(4f) +
                flowBeat * beatPulse * dp(13f)
            val hue = flowingHue(progress, left, flowMode)
            val alpha = (.24f + level * .68f + beatPulse * .12f).coerceIn(0f, 1f)
            strokePaint.strokeWidth = dp(1f) + level * dp(2.25f) + beatPulse * dp(.30f)
            strokePaint.color = hsv(hue, .97f, 1f, alpha)
            canvas.drawLine(baseX, y, baseX + direction * length, y, strokePaint)
        }
    }

    private fun drawShockwaves(canvas: Canvas) {
        val minSide = min(width, height).toFloat()
        shockwaves.forEach { wave ->
            if (!wave.active) return@forEach
            val alpha = ((1f - wave.progress).pow(1.5f) * wave.strength).coerceIn(0f, 1f)
            strokePaint.shader = null
            strokePaint.color = hsv(wave.hue + wave.progress * 90f, .92f, 1f, alpha)
            strokePaint.strokeWidth = dp(.8f) + (1f - wave.progress) * dp(3.5f) * wave.strength
            when (wave.mode) {
                ReactiveFlowMode.UP -> {
                    val y = height * (1f - wave.progress)
                    canvas.drawLine(dp(10f), y, width - dp(10f), y, strokePaint)
                }
                ReactiveFlowMode.DOWN -> {
                    val y = height * wave.progress
                    canvas.drawLine(dp(10f), y, width - dp(10f), y, strokePaint)
                }
                ReactiveFlowMode.OUTWARD -> {
                    val inset = (1f - wave.progress) * minSide * .43f
                    val rect = RectF(inset, inset * .52f, width - inset, height - inset * .52f)
                    if (rect.width() > 1f && rect.height() > 1f) {
                        canvas.drawRoundRect(rect, minSide * .04f, minSide * .04f, strokePaint)
                    }
                }
                ReactiveFlowMode.AUTO,
                ReactiveFlowMode.INWARD,
                -> {
                    val inset = wave.progress * minSide * .19f
                    val rect = RectF(inset, inset * .52f, width - inset, height - inset * .52f)
                    if (rect.width() > 1f && rect.height() > 1f) {
                        canvas.drawRoundRect(rect, minSide * .04f, minSide * .04f, strokePaint)
                    }
                }
            }
        }
    }

    private fun drawSymbols(canvas: Canvas, flowMode: ReactiveFlowMode) {
        val positions = floatArrayOf(.08f, .19f, .32f, .47f, .63f, .78f, .91f)
        positions.forEachIndexed { index, progress ->
            val y = height * progress
            val zone = zoneEnergy(progress)
            val size = dp(2.7f) + zone * dp(2.4f) + beatPulse * dp(1.5f)
            val leftHue = flowingHue(progress, true, flowMode)
            val rightHue = flowingHue(progress, false, flowMode)
            when (index % 3) {
                0 -> {
                    drawDiamond(canvas, dp(19f), y, size, leftHue)
                    drawDiamond(canvas, width - dp(19f), y, size, rightHue)
                }
                1 -> {
                    drawSpark(canvas, dp(24f), y, size * (1f + displayTreble), leftHue)
                    drawSpark(canvas, width - dp(24f), y, size * (1f + displayTreble), rightHue)
                }
                else -> {
                    drawPulseGlyph(canvas, dp(22f), y, size * (1f + displayBass), leftHue)
                    drawPulseGlyph(canvas, width - dp(22f), y, size * (1f + displayBass), rightHue, mirror = true)
                }
            }
        }
    }

    private fun drawDiamond(canvas: Canvas, x: Float, y: Float, size: Float, hue: Float) {
        symbolPath.reset()
        symbolPath.moveTo(x, y - size)
        symbolPath.lineTo(x + size * .72f, y)
        symbolPath.lineTo(x, y + size)
        symbolPath.lineTo(x - size * .72f, y)
        symbolPath.close()
        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = hsv(hue, .94f, 1f, .88f)
        canvas.drawPath(symbolPath, strokePaint)
    }

    private fun drawSpark(canvas: Canvas, x: Float, y: Float, size: Float, hue: Float) {
        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = hsv(hue, .93f, 1f, .84f)
        canvas.drawLine(x - size, y, x + size, y, strokePaint)
        canvas.drawLine(x, y - size, x, y + size, strokePaint)
        canvas.drawLine(x - size * .55f, y - size * .55f, x + size * .55f, y + size * .55f, strokePaint)
        canvas.drawLine(x + size * .55f, y - size * .55f, x - size * .55f, y + size * .55f, strokePaint)
    }

    private fun drawPulseGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        hue: Float,
        mirror: Boolean = false,
    ) {
        val direction = if (mirror) -1f else 1f
        symbolPath.reset()
        symbolPath.moveTo(x - direction * size, y)
        symbolPath.lineTo(x - direction * size * .42f, y)
        symbolPath.lineTo(x - direction * size * .16f, y - size * .72f)
        symbolPath.lineTo(x + direction * size * .12f, y + size * .82f)
        symbolPath.lineTo(x + direction * size * .42f, y - size * .42f)
        symbolPath.lineTo(x + direction * size, y)
        strokePaint.strokeWidth = dp(1.1f)
        strokePaint.color = hsv(hue, .94f, 1f, .86f)
        canvas.drawPath(symbolPath, strokePaint)
    }

    private fun drawAmbientParticles(canvas: Canvas) {
        val now = SystemClock.uptimeMillis() / 1000f
        repeat(26) { index ->
            val leftSide = index % 2 == 0
            val seed = index * 1.731f
            val progress = ((now * (.012f + index % 6 * .003f) + seed) % 1f)
            val y = dp(11f) + progress * (height - dp(22f))
            val distance = dp(10f) + abs(sin(seed + now * .37f)) * dp(27f)
            val x = if (leftSide) distance else width - distance
            val alpha = (.035f + displayTreble * .28f + displayFlux * .15f) *
                (.42f + abs(sin(seed + now * 1.3f)) * .58f)
            fillPaint.color = hsv(colorPhase + progress * 440f + index * 13f, .92f, 1f, alpha)
            canvas.drawCircle(x, y, dp(.46f) + displayTreble * dp(.95f), fillPaint)
        }
    }

    private fun spawnStars(strength: Float) {
        val count = when (beatFxMode) {
            BeatFxMode.SMOOTH -> 0
            BeatFxMode.REACTIVE -> 4 + (strength * 8f).toInt()
            BeatFxMode.BRUTAL -> 8 + (strength * 20f).toInt()
        }
        repeat(count) {
            val star = stars.firstOrNull { !it.active }
                ?: stars.minByOrNull { it.life - it.age }
                ?: return@repeat
            val edgeBiased = random.nextFloat() < .70f
            val left = random.nextBoolean()
            star.active = true
            star.x = if (edgeBiased) {
                val edgeRange = width * (.07f + random.nextFloat() * .17f)
                if (left) edgeRange else width - edgeRange
            } else {
                width * (.12f + random.nextFloat() * .76f)
            }
            star.y = -random.nextFloat() * height * .08f - dp(4f)
            star.vx = (random.nextFloat() - .5f) * width * (.016f + strength * .030f)
            star.vy = height * (.16f + random.nextFloat() * .08f + strength * .12f)
            star.gravity = height * (.024f + random.nextFloat() * .018f)
            star.age = 0f
            star.life = (1.15f + random.nextFloat() * .70f) * trailMode.particleLifeScale
            star.size = dp(.9f + random.nextFloat() * 1.4f + strength * 1.3f)
            star.hue = (colorPhase + random.nextFloat() * 230f + strength * 90f) % 360f
            star.spin = (random.nextFloat() - .5f) * 8f
            star.rotation = random.nextFloat() * PI.toFloat() * 2f
            star.strength = strength
        }
    }

    private fun updateParticles(dt: Float, nowMs: Long) {
        val silenceMs = silenceDuration(nowMs)
        val ageMultiplier = if (silenceMs >= SILENCE_SNAP_MS) 3.2f else 1f
        stars.forEach { star ->
            if (!star.active) return@forEach
            star.age += dt * ageMultiplier
            star.vy += star.gravity * dt
            star.x += star.vx * dt
            star.y += star.vy * dt
            star.rotation += star.spin * dt
            if (star.y >= height - dp(7f)) {
                bottomFlash = max(bottomFlash, star.strength)
                star.active = false
            } else if (star.age >= star.life || star.x < -dp(30f) || star.x > width + dp(30f)) {
                star.active = false
            }
        }
    }

    private fun drawStarRain(canvas: Canvas) {
        stars.forEach { star ->
            if (!star.active) return@forEach
            val progress = (star.age / star.life).coerceIn(0f, 1f)
            val alpha = (sin(progress * PI).toFloat().pow(.72f) * (.45f + star.strength * .55f))
                .coerceIn(0f, 1f)
            val hue = star.hue + progress * 85f
            strokePaint.color = hsv(hue, .90f, 1f, alpha * .42f)
            strokePaint.strokeWidth = max(dp(.5f), star.size * .27f)
            canvas.drawLine(
                star.x,
                star.y - star.size * (2.6f + star.strength * 2.2f),
                star.x - star.vx * .025f,
                star.y,
                strokePaint,
            )
            drawStar(canvas, star.x, star.y, star.size, star.rotation, hue, alpha)
        }
    }

    private fun drawStar(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        rotation: Float,
        hue: Float,
        alpha: Float,
    ) {
        symbolPath.reset()
        repeat(8) { index ->
            val radius = if (index % 2 == 0) size else size * .34f
            val angle = rotation + index * (PI.toFloat() * 2f / 8f) - PI.toFloat() / 2f
            val px = x + cos(angle) * radius
            val py = y + sin(angle) * radius
            if (index == 0) symbolPath.moveTo(px, py) else symbolPath.lineTo(px, py)
        }
        symbolPath.close()
        fillPaint.color = hsv(hue, .78f, 1f, alpha)
        canvas.drawPath(symbolPath, fillPaint)
    }

    private fun spawnShockwave(strength: Float, flowMode: ReactiveFlowMode) {
        val wave = shockwaves.firstOrNull { !it.active }
            ?: shockwaves.minByOrNull { 1f - it.progress }
            ?: return
        wave.active = true
        wave.progress = 0f
        wave.speed = .78f + strength * .88f
        wave.strength = strength
        wave.hue = colorPhase + strength * 120f
        wave.mode = flowMode
    }

    private fun updateShockwaves(dt: Float) {
        shockwaves.forEach { wave ->
            if (!wave.active) return@forEach
            wave.progress += dt * wave.speed
            if (wave.progress >= 1f) wave.active = false
        }
    }

    private fun drawBottomBassFlash(canvas: Canvas) {
        if (bottomFlash <= .01f) return
        val centerX = width / 2f
        val y = height - dp(4f)
        val halfWidth = width * (.12f + bottomFlash * .37f)
        strokePaint.shader = LinearGradient(
            centerX - halfWidth,
            y,
            centerX + halfWidth,
            y,
            intArrayOf(
                Color.TRANSPARENT,
                hsv(colorPhase + 150f, .94f, 1f, bottomFlash * .58f),
                hsv(colorPhase + 285f, .96f, 1f, bottomFlash * .88f),
                hsv(colorPhase + 35f, .94f, 1f, bottomFlash * .58f),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        strokePaint.strokeWidth = dp(2f) + bottomFlash * dp(6f)
        canvas.drawLine(centerX - halfWidth, y, centerX + halfWidth, y, strokePaint)
        strokePaint.shader = null
    }

    private fun railBandIndex(progress: Float): Int {
        val mirrored = if (progress <= .5f) progress * 2f else (1f - progress) * 2f
        return (mirrored * displayLevels.lastIndex).toInt().coerceIn(0, displayLevels.lastIndex)
    }

    private fun edgeBandIndex(progress: Float): Int = when {
        progress < .32f -> (15f - progress / .32f * 5f).toInt().coerceIn(10, 15)
        progress < .72f -> (10f - (progress - .32f) / .40f * 6f).toInt().coerceIn(4, 10)
        else -> (4f - (progress - .72f) / .28f * 4f).toInt().coerceIn(0, 4)
    }

    private fun zoneEnergy(progress: Float): Float = when {
        progress < .32f -> displayTreble
        progress < .72f -> displayMid
        else -> displayBass
    }

    private fun flowCoordinate(progress: Float, mode: ReactiveFlowMode): Float = when (mode) {
        ReactiveFlowMode.UP -> 1f - progress
        ReactiveFlowMode.DOWN -> progress
        ReactiveFlowMode.OUTWARD -> 1f - abs(progress - .5f) * 2f
        ReactiveFlowMode.AUTO,
        ReactiveFlowMode.INWARD,
        -> abs(progress - .5f) * 2f
    }

    private fun flowingHue(progress: Float, left: Boolean, mode: ReactiveFlowMode): Float {
        val sideOffset = if (left) 0f else 148f
        return (
            colorPhase +
                flowCoordinate(progress, mode) * 590f +
                progress * 122f +
                sideOffset +
                displayTreble * 95f +
                beatPulse * 55f
            ) % 360f
    }

    private fun flowTravel(time: Float, mode: ReactiveFlowMode): Float {
        val speed = .54f + displayMid * 1.20f + beatPulse * 1.1f
        return when (mode) {
            ReactiveFlowMode.UP -> -time * speed
            ReactiveFlowMode.DOWN -> time * speed
            ReactiveFlowMode.OUTWARD -> -time * speed * .75f
            ReactiveFlowMode.AUTO,
            ReactiveFlowMode.INWARD,
            -> time * speed * .75f
        }
    }

    private fun flowPulse(progress: Float, mode: ReactiveFlowMode): Float {
        val time = SystemClock.uptimeMillis() / 1000f
        val coordinate = flowCoordinate(progress, mode)
        return ((sin(coordinate * PI.toFloat() * 5f - time * (3.4f + beatPulse * 5.4f)) + 1f) * .5f)
            .pow(1.8f)
    }

    private fun average(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        var total = 0f
        for (value in values) total += value
        return total / values.size
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
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

    private fun dp(value: Float): Float = value * density * profileScale

    private companion object {
        const val MAX_STARS = 88
        const val MAX_SHOCKWAVES = 5
        const val SIGNAL_GATE = .0045f
        const val SILENCE_FAST_FADE_MS = 220L
        const val SILENCE_SNAP_MS = 720L
    }
}
