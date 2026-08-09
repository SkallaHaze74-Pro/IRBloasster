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
 * Full-display, touch-through Brutal Reactive frame for the Xiaomi 15T Pro.
 *
 * The effect is tuned against the measured 1280 × 2772 / 393 dp / 144 Hz
 * profile. Audio analysis publishes bass, mids, treble, spectral flux and
 * discrete beat events; this view interpolates those values on every display
 * VSync so the animation remains fluid at 144 Hz without running the FFT 144
 * times per second.
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
    private var visualEnabled = true
    private var lastFrameNanos = 0L
    private var colorPhase = 0f
    private var displayBass = 0f
    private var displayMid = 0f
    private var displayTreble = 0f
    private var displayFlux = 0f
    private var beatPulse = 0f
    private var bassEnvelope = 0f
    private var bottomFlash = 0f
    private var lastBeatSequence = Long.MIN_VALUE
    private var lastBassTriggerAt = 0L

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
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, .075f)
        }
        lastFrameNanos = nowNanos

        updateAudioInterpolation(deltaSeconds)
        val flowMode = resolvedFlowMode()
        detectAndTriggerBeats(flowMode)
        updateParticles(deltaSeconds)
        updateShockwaves(deltaSeconds)

        val speedMultiplier = beatFxMode.multiplier
        val colorSpeed = (
            4.2f +
                displayTreble * 116f +
                displayFlux * 158f +
                beatPulse * 315f +
                displayBass * 58f
            ) * speedMultiplier
        colorPhase = (colorPhase + deltaSeconds * colorSpeed) % 360f

        val energy = average(displayLevels)
        drawPerimeter(canvas, energy, flowMode)
        drawTopAndBottomRails(canvas, flowMode)
        drawEdge(canvas, left = true, flowMode = flowMode)
        drawEdge(canvas, left = false, flowMode = flowMode)
        drawShockwaves(canvas)
        drawSymbols(canvas, flowMode)
        drawAmbientParticles(canvas)
        drawStarRain(canvas)
        drawBottomBassFlash(canvas)

        // VSync interpolation follows the active Xiaomi display mode (up to 144 Hz).
        postInvalidateOnAnimation()
    }

    private fun updateAudioInterpolation(deltaSeconds: Float) {
        val active = snapshot.signal > .0035f || snapshot.analyzerRunning
        val attack = 1f - exp(-deltaSeconds * 32f)
        val release = 1f - exp(-deltaSeconds * 10f)
        for (index in displayLevels.indices) {
            val target = if (active) targetLevels[index] else 0f
            val factor = if (target > displayLevels[index]) attack else release
            displayLevels[index] += (target - displayLevels[index]) * factor
        }

        displayBass = smoothFeature(displayBass, snapshot.bass, deltaSeconds, attackRate = 35f, releaseRate = 7f)
        displayMid = smoothFeature(displayMid, snapshot.mid, deltaSeconds, attackRate = 27f, releaseRate = 8f)
        displayTreble = smoothFeature(displayTreble, snapshot.treble, deltaSeconds, attackRate = 38f, releaseRate = 13f)
        displayFlux = smoothFeature(displayFlux, snapshot.spectralFlux, deltaSeconds, attackRate = 42f, releaseRate = 15f)
        beatPulse = max(snapshot.beat, beatPulse * exp(-deltaSeconds * 8.5f))
        bassEnvelope = max(displayBass, bassEnvelope * exp(-deltaSeconds * 3.4f))
        bottomFlash *= exp(-deltaSeconds * 5.6f)
    }

    private fun smoothFeature(
        current: Float,
        target: Float,
        deltaSeconds: Float,
        attackRate: Float,
        releaseRate: Float,
    ): Float {
        val rate = if (target > current) attackRate else releaseRate
        val factor = 1f - exp(-deltaSeconds * rate)
        return current + (target - current) * factor
    }

    private fun detectAndTriggerBeats(flowMode: ReactiveFlowMode) {
        val sequence = snapshot.beatSequence
        if (lastBeatSequence == Long.MIN_VALUE || sequence < lastBeatSequence) {
            lastBeatSequence = sequence
            return
        }

        if (sequence > lastBeatSequence) {
            val missed = min(3L, sequence - lastBeatSequence).toInt()
            repeat(missed) {
                triggerBeat(max(snapshot.beat, displayBass * .72f), flowMode)
            }
            lastBeatSequence = sequence
            return
        }

        // Microphone fallback can smooth a kick so much that no discrete onset is
        // emitted. A guarded bass threshold still produces one visual burst.
        val now = SystemClock.uptimeMillis()
        if (
            beatFxMode != BeatFxMode.SMOOTH &&
            displayBass > .72f &&
            beatPulse < .12f &&
            now - lastBassTriggerAt > 280L
        ) {
            lastBassTriggerAt = now
            triggerBeat(displayBass * .76f, flowMode)
        }
    }

    private fun triggerBeat(strengthInput: Float, flowMode: ReactiveFlowMode) {
        val strength = strengthInput.coerceIn(.08f, 1f)
        beatPulse = max(beatPulse, strength)
        bassEnvelope = max(bassEnvelope, strength)
        colorPhase = (colorPhase + 12f + strength * 32f * beatFxMode.multiplier) % 360f

        val shockwaveThreshold = if (beatFxMode == BeatFxMode.BRUTAL) .16f else .28f
        if (strength >= shockwaveThreshold) spawnShockwave(strength, flowMode)

        val starThreshold = when (beatFxMode) {
            BeatFxMode.SMOOTH -> 1.1f
            BeatFxMode.REACTIVE -> .48f
            BeatFxMode.BRUTAL -> .25f
        }
        if (strength >= starThreshold) spawnStars(strength)
    }

    private fun resolvedFlowMode(): ReactiveFlowMode {
        if (requestedFlowMode != ReactiveFlowMode.AUTO) return requestedFlowMode
        // Inward is intentionally dominant; every few beats the direction flips
        // up, down or outward for a visible musical transition.
        return when (((snapshot.beatSequence / 6L) % 8L).toInt()) {
            0, 1, 2, 3, 4 -> ReactiveFlowMode.INWARD
            5 -> ReactiveFlowMode.UP
            6 -> ReactiveFlowMode.DOWN
            else -> ReactiveFlowMode.OUTWARD
        }
    }

    private fun drawPerimeter(
        canvas: Canvas,
        energy: Float,
        flowMode: ReactiveFlowMode,
    ) {
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
        val beatBoost = 1f + beatPulse * .48f
        strokePaint.alpha = (34 + energy * 32 + beatPulse * 38).toInt().coerceIn(28, 105)
        strokePaint.strokeWidth = dp(12f) * power * beatBoost
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.alpha = (112 + energy * 55 + beatPulse * 45).toInt().coerceIn(100, 220)
        strokePaint.strokeWidth = dp(4.7f) * power.coerceAtMost(1.38f) * (1f + beatPulse * .20f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.alpha = 255
        strokePaint.strokeWidth = dp(1.25f + beatPulse * .45f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        strokePaint.shader = null
        strokePaint.alpha = 255
    }

    private fun drawTopAndBottomRails(canvas: Canvas, flowMode: ReactiveFlowMode) {
        val usableWidth = width - dp(28f)
        val startX = dp(14f)
        val yTop = dp(10.5f)
        val yBottom = height - dp(10.5f)
        val segments = 78
        val gap = usableWidth / segments

        for (index in 0 until segments) {
            val progress = index / max(1f, (segments - 1).toFloat())
            val bandIndex = railBandIndex(progress)
            val level = max(if (snapshot.analyzerRunning) .035f else .012f, displayLevels[bandIndex].pow(.60f))
            val flow = flowCoordinate(progress, flowMode)
            val hue = colorPhase + flow * 610f + progress * 140f
            val alpha = (.23f + level * .72f + beatPulse * .13f).coerceIn(.18f, 1f)
            val x = startX + index * gap
            val beatWave = abs(sin(progress * PI.toFloat() * 4f + colorPhase * .025f)) * beatPulse
            val length = dp(1.8f) + level * dp(10f) * neonIntensity + beatWave * dp(8f)

            strokePaint.color = hsv(hue, .97f, 1f, alpha)
            strokePaint.strokeWidth = dp(.82f) + level * dp(1.55f)
            canvas.drawLine(x, yTop, x, yTop + length, strokePaint)
            canvas.drawLine(x, yBottom, x, yBottom - length, strokePaint)
        }
    }

    private fun drawEdge(
        canvas: Canvas,
        left: Boolean,
        flowMode: ReactiveFlowMode,
    ) {
        val segments = 76
        val top = dp(7f)
        val bottom = height - dp(7f)
        val usable = max(1f, bottom - top)
        val outerX = if (left) dp(4.5f) else width - dp(4.5f)
        val direction = if (left) 1f else -1f
        val time = SystemClock.uptimeMillis() / 1000f
        val travel = flowTravel(time, flowMode)
        val panelBody = dp(edgeBudget * .74f)

        linePath.reset()
        for (step in 0..104) {
            val progress = step / 104f
            val y = top + usable * progress
            val wave = sin(progress * PI.toFloat() * 5.15f + travel) * dp(3.6f)
            val secondary = sin(progress * PI.toFloat() * 13.1f - travel * 1.35f) * dp(1.45f)
            val body = sin(progress * PI.toFloat()) * panelBody
            val zone = zoneEnergy(progress)
            val bassPush = displayBass * beatPulse * sin(progress * PI.toFloat()) * dp(11f)
            val x = outerX + direction * (
                dp(2f) + body + (wave + secondary) * (.22f + displayMid * .92f) + bassPush + zone * dp(2.2f)
                )
            if (step == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        val coreHue = if (left) colorPhase + 184f else colorPhase + 312f
        strokePaint.strokeWidth = dp(13f) * neonIntensity * (1f + beatPulse * .18f)
        strokePaint.color = hsv(coreHue, .98f, 1f, .040f + displayBass * .065f + beatPulse * .055f)
        canvas.drawPath(linePath, strokePaint)
        strokePaint.strokeWidth = dp(5.0f) * neonIntensity.coerceAtMost(1.38f)
        strokePaint.color = hsv(coreHue + 60f, .98f, 1f, .17f + displayMid * .17f + beatPulse * .12f)
        canvas.drawPath(linePath, strokePaint)
        strokePaint.strokeWidth = dp(1.45f + beatPulse * .35f)
        strokePaint.color = hsv(coreHue + 118f, .93f, 1f, .96f)
        canvas.drawPath(linePath, strokePaint)

        val barGap = usable / segments
        for (segment in 0 until segments) {
            val progress = segment / max(1f, (segments - 1).toFloat())
            val y = top + progress * usable
            val bandIndex = edgeBandIndex(progress)
            val level = displayLevels[bandIndex]
            val shaped = level.pow(.59f)
            val breathing = if (snapshot.analyzerRunning) .052f else .018f
            val amount = max(breathing, shaped)
            val wave = sin(progress * PI.toFloat() * 5.15f + travel) * dp(3.6f)
            val secondary = sin(progress * PI.toFloat() * 13.1f - travel * 1.35f) * dp(1.45f)
            val body = sin(progress * PI.toFloat()) * panelBody
            val bassPush = displayBass * beatPulse * sin(progress * PI.toFloat()) * dp(11f)
            val baseX = outerX + direction * (
                dp(2f) + body + (wave + secondary) * (.22f + displayMid * .92f) + bassPush
                )
            val zoneBoost = zoneEnergy(progress)
            val flowBeat = flowPulse(progress, flowMode)
            val length = dp(4.5f) +
                amount * dp(30f) * neonIntensity +
                zoneBoost * dp(5f) +
                flowBeat * beatPulse * dp(15f)
            val hue = flowingHue(progress, left, flowMode)
            val alpha = (.32f + amount * .65f + beatPulse * .12f).coerceIn(0f, 1f)

            strokePaint.strokeWidth = dp(5.5f) + amount * dp(6.4f)
            strokePaint.color = hsv(hue, .99f, 1f, alpha * .14f)
            canvas.drawLine(baseX, y, baseX + direction * length, y, strokePaint)

            strokePaint.strokeWidth = dp(1.05f) + amount * dp(2.45f) + beatPulse * dp(.35f)
            strokePaint.color = hsv(hue, .97f, 1f, alpha)
            canvas.drawLine(baseX, y, baseX + direction * length, y, strokePaint)

            if (segment % 2 == 0) {
                fillPaint.color = hsv(hue + 43f, .90f, 1f, .24f + amount * .60f)
                canvas.drawCircle(
                    baseX + direction * (length + dp(4.5f)),
                    y,
                    dp(.66f) + amount * dp(.72f) + displayTreble * dp(.28f),
                    fillPaint,
                )
            }
        }
    }

    private fun drawShockwaves(canvas: Canvas) {
        val minSide = min(width, height).toFloat()
        shockwaves.forEach { wave ->
            if (!wave.active) return@forEach
            val alpha = ((1f - wave.progress).pow(1.5f) * wave.strength).coerceIn(0f, 1f)
            strokePaint.shader = null
            strokePaint.color = hsv(wave.hue + wave.progress * 90f, .92f, 1f, alpha)
            strokePaint.strokeWidth = dp(.8f) + (1f - wave.progress) * dp(3.8f) * wave.strength

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
            val hueLeft = flowingHue(progress, true, flowMode)
            val hueRight = flowingHue(progress, false, flowMode)
            val size = dp(2.9f) + zone * dp(2.7f) + beatPulse * dp(1.8f)
            when (index % 4) {
                0 -> {
                    drawDiamond(canvas, dp(19f), y, size, hueLeft)
                    drawDiamond(canvas, width - dp(19f), y, size, hueRight)
                }

                1 -> {
                    drawSpark(canvas, dp(25f), y, size * (1.1f + displayTreble), hueLeft)
                    drawMusicNote(canvas, width - dp(23f), y, size * (1f + displayMid * .45f), hueRight)
                }

                2 -> {
                    drawPulseGlyph(canvas, dp(22f), y, size * (1.1f + displayBass), hueLeft)
                    drawPulseGlyph(canvas, width - dp(22f), y, size * (1.1f + displayBass), hueRight, mirror = true)
                }

                else -> {
                    drawMusicNote(canvas, dp(23f), y, size, hueLeft)
                    drawSpark(canvas, width - dp(25f), y, size * (1.1f + displayTreble), hueRight)
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
        strokePaint.strokeWidth = dp(1.05f)
        strokePaint.color = hsv(hue, .94f, 1f, .90f)
        canvas.drawPath(symbolPath, strokePaint)
        fillPaint.color = hsv(hue + 42f, .86f, 1f, .16f + beatPulse * .16f)
        canvas.drawPath(symbolPath, fillPaint)
    }

    private fun drawSpark(canvas: Canvas, x: Float, y: Float, size: Float, hue: Float) {
        strokePaint.strokeWidth = dp(1.0f)
        strokePaint.color = hsv(hue, .93f, 1f, .86f)
        canvas.drawLine(x - size, y, x + size, y, strokePaint)
        canvas.drawLine(x, y - size, x, y + size, strokePaint)
        canvas.drawLine(x - size * .55f, y - size * .55f, x + size * .55f, y + size * .55f, strokePaint)
        canvas.drawLine(x + size * .55f, y - size * .55f, x - size * .55f, y + size * .55f, strokePaint)
    }

    private fun drawMusicNote(canvas: Canvas, x: Float, y: Float, size: Float, hue: Float) {
        strokePaint.strokeWidth = dp(1.2f)
        strokePaint.color = hsv(hue, .96f, 1f, .90f)
        val stemTop = y - size * 1.25f
        val stemBottom = y + size * .35f
        canvas.drawLine(x, stemTop, x, stemBottom, strokePaint)
        canvas.drawLine(x, stemTop, x + size * .92f, stemTop + size * .28f, strokePaint)
        fillPaint.color = hsv(hue + 35f, .92f, 1f, .90f)
        canvas.drawCircle(x - size * .28f, stemBottom + size * .30f, size * .42f, fillPaint)
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
        strokePaint.color = hsv(hue, .94f, 1f, .88f)
        canvas.drawPath(symbolPath, strokePaint)
    }

    private fun drawAmbientParticles(canvas: Canvas) {
        val now = SystemClock.uptimeMillis() / 1000f
        val count = 34
        for (index in 0 until count) {
            val leftSide = index % 2 == 0
            val seed = index * 1.731f
            val speed = .014f + (index % 6) * .0032f + displayTreble * .018f
            val progress = ((now * speed + seed) % 1f)
            val y = dp(11f) + progress * (height - dp(22f))
            val distance = dp(10f) + abs(sin(seed + now * .37f)) * dp(31f)
            val x = if (leftSide) distance else width - distance
            val hue = colorPhase + progress * 440f + index * 13f
            val alpha = (.045f + displayTreble * .34f + displayFlux * .18f) *
                (.42f + abs(sin(seed + now * 1.3f)) * .58f)
            fillPaint.color = hsv(hue, .92f, 1f, alpha)
            canvas.drawCircle(x, y, dp(.52f) + displayTreble * dp(1.15f), fillPaint)
        }
    }

    private fun spawnStars(strength: Float) {
        val count = when (beatFxMode) {
            BeatFxMode.SMOOTH -> 0
            BeatFxMode.REACTIVE -> 5 + (strength * 9f).toInt()
            BeatFxMode.BRUTAL -> 10 + (strength * 24f).toInt()
        }
        repeat(count) {
            val star = stars.firstOrNull { !it.active } ?: stars.minByOrNull { it.life - it.age } ?: return@repeat
            val edgeBiased = random.nextFloat() < .72f
            val left = random.nextBoolean()
            val x = if (edgeBiased) {
                val edgeRange = width * (.07f + random.nextFloat() * .17f)
                if (left) edgeRange else width - edgeRange
            } else {
                width * (.12f + random.nextFloat() * .76f)
            }
            star.active = true
            star.x = x
            star.y = -random.nextFloat() * height * .08f - dp(4f)
            star.vx = (random.nextFloat() - .5f) * width * (.018f + strength * .035f)
            star.vy = height * (.17f + random.nextFloat() * .09f + strength * .14f)
            star.gravity = height * (.026f + random.nextFloat() * .020f)
            star.age = 0f
            star.life = 1.25f + random.nextFloat() * .85f
            star.size = dp(.95f + random.nextFloat() * 1.55f + strength * 1.5f)
            star.hue = (colorPhase + random.nextFloat() * 230f + strength * 90f) % 360f
            star.spin = (random.nextFloat() - .5f) * 8f
            star.rotation = random.nextFloat() * PI.toFloat() * 2f
            star.strength = strength
        }
    }

    private fun updateParticles(deltaSeconds: Float) {
        stars.forEach { star ->
            if (!star.active) return@forEach
            star.age += deltaSeconds
            star.vy += star.gravity * deltaSeconds
            star.x += star.vx * deltaSeconds
            star.y += star.vy * deltaSeconds
            star.rotation += star.spin * deltaSeconds
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
            val lifeProgress = (star.age / star.life).coerceIn(0f, 1f)
            val alpha = (sin(lifeProgress * PI).toFloat().pow(.72f) * (.45f + star.strength * .55f))
                .coerceIn(0f, 1f)
            val hue = star.hue + lifeProgress * 85f
            strokePaint.color = hsv(hue, .90f, 1f, alpha * .46f)
            strokePaint.strokeWidth = max(dp(.55f), star.size * .28f)
            canvas.drawLine(
                star.x,
                star.y - star.size * (2.8f + star.strength * 2.4f),
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
        val points = 8
        for (index in 0 until points) {
            val radius = if (index % 2 == 0) size else size * .34f
            val angle = rotation + index * (PI.toFloat() * 2f / points) - PI.toFloat() / 2f
            val px = x + cos(angle) * radius
            val py = y + sin(angle) * radius
            if (index == 0) symbolPath.moveTo(px, py) else symbolPath.lineTo(px, py)
        }
        symbolPath.close()
        fillPaint.color = hsv(hue, .78f, 1f, alpha)
        canvas.drawPath(symbolPath, fillPaint)
        strokePaint.color = hsv(hue + 35f, .52f, 1f, alpha)
        strokePaint.strokeWidth = dp(.42f)
        canvas.drawPath(symbolPath, strokePaint)
    }

    private fun spawnShockwave(strength: Float, flowMode: ReactiveFlowMode) {
        val wave = shockwaves.firstOrNull { !it.active } ?: shockwaves.minByOrNull { 1f - it.progress } ?: return
        wave.active = true
        wave.progress = 0f
        wave.speed = .78f + strength * .88f
        wave.strength = strength
        wave.hue = colorPhase + strength * 120f
        wave.mode = flowMode
    }

    private fun updateShockwaves(deltaSeconds: Float) {
        shockwaves.forEach { wave ->
            if (!wave.active) return@forEach
            wave.progress += deltaSeconds * wave.speed
            if (wave.progress >= 1f) wave.active = false
        }
    }

    private fun drawBottomBassFlash(canvas: Canvas) {
        if (bottomFlash <= .01f) return
        val centerX = width / 2f
        val y = height - dp(4f)
        val halfWidth = width * (.12f + bottomFlash * .37f)
        val shader = LinearGradient(
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
        strokePaint.shader = shader
        strokePaint.strokeWidth = dp(2f) + bottomFlash * dp(6f)
        canvas.drawLine(centerX - halfWidth, y, centerX + halfWidth, y, strokePaint)
        strokePaint.shader = null
    }

    private fun railBandIndex(progress: Float): Int {
        val mirrored = if (progress <= .5f) progress * 2f else (1f - progress) * 2f
        return (mirrored * displayLevels.lastIndex).toInt().coerceIn(0, displayLevels.lastIndex)
    }

    private fun edgeBandIndex(progress: Float): Int {
        return when {
            progress < .32f -> {
                val local = progress / .32f
                (15f - local * 5f).toInt().coerceIn(10, 15)
            }

            progress < .72f -> {
                val local = (progress - .32f) / .40f
                (10f - local * 6f).toInt().coerceIn(4, 10)
            }

            else -> {
                val local = (progress - .72f) / .28f
                (4f - local * 4f).toInt().coerceIn(0, 4)
            }
        }
    }

    private fun zoneEnergy(progress: Float): Float = when {
        progress < .32f -> displayTreble
        progress < .72f -> displayMid
        else -> displayBass
    }

    private fun flowCoordinate(progress: Float, mode: ReactiveFlowMode): Float {
        return when (mode) {
            ReactiveFlowMode.UP -> 1f - progress
            ReactiveFlowMode.DOWN -> progress
            ReactiveFlowMode.OUTWARD -> 1f - abs(progress - .5f) * 2f
            ReactiveFlowMode.AUTO,
            ReactiveFlowMode.INWARD,
            -> abs(progress - .5f) * 2f
        }
    }

    private fun flowingHue(
        progress: Float,
        left: Boolean,
        mode: ReactiveFlowMode,
    ): Float {
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
        val speed = .62f + displayMid * 1.45f + beatPulse * 1.35f
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
        return ((sin(coordinate * PI.toFloat() * 5f - time * (4f + beatPulse * 6f)) + 1f) * .5f)
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
        const val MAX_STARS = 96
        const val MAX_SHOCKWAVES = 5
    }
}
