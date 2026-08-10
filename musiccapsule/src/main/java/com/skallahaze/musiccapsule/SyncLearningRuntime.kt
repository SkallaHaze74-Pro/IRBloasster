package com.skallahaze.musiccapsule

import android.content.Context
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One shared timing source for stripes, centre patterns and RGB travel.
 *
 * The previous renderers each estimated their own speed from bass/treble/BPM,
 * which could make them individually look plausible but drift apart. This
 * runtime learns the beat interval once and publishes one tempo profile to all
 * visual layers.
 */
data class SyncLearningSnapshot(
    val bpm: Float = 0f,
    val beatIntervalMs: Float = 500f,
    val confidence: Float = 0f,
    val tempoFactor: Float = .45f,
    val attackRate: Float = 48f,
    val releaseRate: Float = 13f,
    val colorRateDegreesPerSecond: Float = 10f,
    val patternSpeed: Float = 2.1f,
    val patternGapMs: Long = 170L,
    val beatSequence: Long = 0L,
    val beatStrength: Float = 0f,
    val beatReliable: Boolean = true,
    val learnedBeats: Int = 0,
    val label: String = "lernt Takt …",
    val huePhase: Float = 0f,
    val updatedAt: Long = 0L,
)

object SyncLearningRuntime {
    private val lock = Any()
    private val intervals = ArrayList<Long>(14)

    private var state = SyncLearningSnapshot()
    private var lastAudioBeatSequence = Long.MIN_VALUE
    private var sharedBeatSequence = 0L
    private var lastBeatAt = 0L
    private var lastReliableBeatAt = 0L
    private var learnedBeatCount = 0
    private var fallbackBeatCount = 0

    private var previousBass = 0f
    private var previousFlux = 0f
    private var bassAverage = .10f
    private var fluxAverage = .05f
    private var energyAverage = .14f

    private var lastTrackKey = ""
    private var huePhase = 0f
    private var lastHueAt = 0L

    fun observe(context: Context, snapshot: CapsuleSnapshot): SyncLearningSnapshot = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        advanceHue(now)

        val trackKey = buildTrackKey(snapshot)
        if (trackKey.isNotBlank() && trackKey != lastTrackKey) {
            resetTrackLearning(trackKey, snapshot.beatSequence, now)
        }

        if (lastAudioBeatSequence == Long.MIN_VALUE || snapshot.beatSequence < lastAudioBeatSequence) {
            lastAudioBeatSequence = snapshot.beatSequence
        }

        val energyNow = (
            snapshot.bass * .43f +
                snapshot.mid * .29f +
                snapshot.treble * .18f +
                snapshot.spectralFlux * .10f
            ).coerceIn(0f, 1f)
        energyAverage += (energyNow - energyAverage) * .035f
        bassAverage += (snapshot.bass - bassAverage) * .030f
        fluxAverage += (snapshot.spectralFlux - fluxAverage) * .040f

        var beatStrength = state.beatStrength * .90f
        var beatReliable = state.beatReliable

        if (snapshot.beatSequence > lastAudioBeatSequence) {
            val strength = max(.32f, max(snapshot.beat, snapshot.bass * .88f))
            registerBeat(now, strength, reliable = true)
            beatStrength = strength
            beatReliable = true
            lastAudioBeatSequence = snapshot.beatSequence
        } else if (shouldAddFallbackBeat(snapshot, now)) {
            val bassRise = max(0f, snapshot.bass - previousBass)
            val fluxRise = max(0f, snapshot.spectralFlux - previousFlux)
            val score =
                bassRise * 3.8f +
                    max(0f, snapshot.bass / max(.035f, bassAverage) - 1f) * .48f +
                    fluxRise * 2.0f +
                    max(0f, snapshot.spectralFlux / max(.025f, fluxAverage) - 1f) * .20f
            val threshold = if (state.confidence >= .55f) .22f else .30f
            if (score >= threshold) {
                val strength = (.28f + score * .48f).coerceIn(.28f, .72f)
                registerBeat(now, strength, reliable = false)
                beatStrength = strength
                beatReliable = false
            }
        }

        previousBass = snapshot.bass
        previousFlux = snapshot.spectralFlux

        val tempo = resolveTempo()
        val autoMode = CapsulePreferences.autoTuneMode(context)
        val styleScale = when (autoMode) {
            AutoTuneMode.OFF -> 1f
            AutoTuneMode.SOFT -> .84f
            AutoTuneMode.BALANCED -> 1f // "Auto Lernen" – no fixed style bias.
            AutoTuneMode.BRUTAL -> 1.12f
        }
        val attackStyle = when (autoMode) {
            AutoTuneMode.SOFT -> .90f
            AutoTuneMode.BRUTAL -> 1.08f
            else -> 1f
        }
        val releaseStyle = when (autoMode) {
            AutoTuneMode.SOFT -> .87f
            AutoTuneMode.BRUTAL -> 1.10f
            else -> 1f
        }

        val attackRate = (46f + tempo.tempoFactor * 31f + tempo.confidence * 7f) * attackStyle
        val releaseRate = (9.5f + tempo.tempoFactor * 10.5f) * releaseStyle

        // Base RGB travel is intentionally much slower than the old renderer.
        // Beats add only a small phase nudge, so color never races ahead of bars.
        val colorRate = (
            4.8f +
                tempo.tempoFactor * 12.5f +
                energyAverage * 4.2f
            ) * styleScale

        val patternSpeed = (
            1000f / max(300f, tempo.intervalMs * .80f)
            ).coerceIn(1.35f, 3.45f) *
            when (autoMode) {
                AutoTuneMode.SOFT -> .90f
                AutoTuneMode.BRUTAL -> 1.08f
                else -> 1f
            }
        val gap = (tempo.intervalMs * .36f)
            .toLong()
            .coerceIn(105L, 235L)

        val label = when {
            !snapshot.analyzerRunning || snapshot.signal <= .003f -> "wartet auf Musik …"
            tempo.confidence < .24f -> "lernt Takt …"
            tempo.confidence < .55f -> "Takt gefunden · ${tempo.bpm.toInt()} BPM"
            else -> "gelernt · ${tempo.bpm.toInt()} BPM · ${(tempo.confidence * 100).toInt()}%"
        }

        state = SyncLearningSnapshot(
            bpm = tempo.bpm,
            beatIntervalMs = tempo.intervalMs,
            confidence = tempo.confidence,
            tempoFactor = tempo.tempoFactor,
            attackRate = attackRate.coerceIn(34f, 88f),
            releaseRate = releaseRate.coerceIn(7.5f, 24f),
            colorRateDegreesPerSecond = colorRate.coerceIn(3.8f, 25f),
            patternSpeed = patternSpeed,
            patternGapMs = gap,
            beatSequence = sharedBeatSequence,
            beatStrength = beatStrength.coerceIn(0f, 1f),
            beatReliable = beatReliable,
            learnedBeats = learnedBeatCount,
            label = label,
            huePhase = huePhase,
            updatedAt = now,
        )

        // The neutral BALANCED slot is now the user's "Auto Lernen" mode.
        // Publish its learned choices so the existing app status immediately
        // shows what the learner decided, even in the Nur-Striche view where
        // EdgePanelView itself is intentionally hidden.
        if (autoMode == AutoTuneMode.BALANCED) {
            val learnedMotion = when {
                tempo.tempoFactor > .68f || energyAverage > .60f -> MotionProfile.DIRECT
                tempo.tempoFactor > .30f || energyAverage > .34f -> MotionProfile.BALANCED
                else -> MotionProfile.SILKY
            }
            val learnedTrail = if (tempo.tempoFactor > .38f || energyAverage > .34f) {
                TrailMode.SHORT
            } else {
                TrailMode.MEDIUM
            }
            val learnedBeatFx = when {
                energyAverage > .62f || bassAverage > .58f -> BeatFxMode.BRUTAL
                tempo.confidence > .28f -> BeatFxMode.REACTIVE
                else -> BeatFxMode.SMOOTH
            }
            val learnedStars = when {
                bassAverage > .58f -> StarMode.BEAT_PLUS
                energyAverage > .34f -> StarMode.BALANCED
                else -> StarMode.SUBTLE
            }
            AutoTuneRuntime.publish(
                autoMode = autoMode,
                motion = learnedMotion,
                trail = learnedTrail,
                beatFx = learnedBeatFx,
                stars = learnedStars,
                energy = energyAverage,
                bassBias = (bassAverage / max(.08f, energyAverage) / 2.2f).coerceIn(0f, 1f),
                rhythmConfidence = tempo.confidence,
                bpm = tempo.bpm,
                label = "Auto Lernen · $label",
            )
        }

        state
    }

    fun snapshot(): SyncLearningSnapshot = synchronized(lock) { state }

    /** Smooth shared hue at render-frame time, even though audio snapshots arrive ~28 ms apart. */
    fun hueAt(nowMs: Long = SystemClock.elapsedRealtime()): Float = synchronized(lock) {
        val elapsed = ((nowMs - state.updatedAt).coerceAtLeast(0L) / 1000f)
        (state.huePhase + elapsed * state.colorRateDegreesPerSecond) % 360f
    }

    fun clear() = synchronized(lock) {
        intervals.clear()
        state = SyncLearningSnapshot()
        lastAudioBeatSequence = Long.MIN_VALUE
        sharedBeatSequence = 0L
        lastBeatAt = 0L
        lastReliableBeatAt = 0L
        learnedBeatCount = 0
        fallbackBeatCount = 0
        previousBass = 0f
        previousFlux = 0f
        bassAverage = .10f
        fluxAverage = .05f
        energyAverage = .14f
        lastTrackKey = ""
        huePhase = 0f
        lastHueAt = 0L
    }

    private fun advanceHue(now: Long) {
        if (lastHueAt == 0L) {
            lastHueAt = now
            return
        }
        val dt = ((now - lastHueAt).coerceIn(0L, 250L)) / 1000f
        huePhase = (huePhase + dt * state.colorRateDegreesPerSecond) % 360f
        lastHueAt = now
    }

    private fun buildTrackKey(snapshot: CapsuleSnapshot): String {
        if (snapshot.title.isBlank() || snapshot.title == "Keine Wiedergabe") return ""
        return "${snapshot.packageName}|${snapshot.title}|${snapshot.artist}"
    }

    private fun resetTrackLearning(trackKey: String, audioSequence: Long, now: Long) {
        intervals.clear()
        lastTrackKey = trackKey
        lastAudioBeatSequence = audioSequence
        lastBeatAt = 0L
        lastReliableBeatAt = 0L
        learnedBeatCount = 0
        fallbackBeatCount = 0
        bassAverage = .10f
        fluxAverage = .05f
        energyAverage = .14f
        state = state.copy(
            bpm = 0f,
            confidence = 0f,
            label = "lernt neuen Track …",
            updatedAt = now,
        )
    }

    private fun registerBeat(now: Long, strength: Float, reliable: Boolean) {
        val interval = if (lastBeatAt > 0L) now - lastBeatAt else 0L
        val existing = resolveTempo()
        val expected = existing.intervalMs

        if (interval in 230L..1_350L) {
            val accept = if (reliable || intervals.size < 3) {
                true
            } else {
                // A fallback may fill a missed kick, but it is not allowed to
                // rewrite an already stable tempo with a random transient.
                interval in (expected * .67f).toLong()..(expected * 1.34f).toLong()
            }
            if (accept) {
                intervals += interval
                while (intervals.size > 14) intervals.removeAt(0)
            }
        }

        lastBeatAt = now
        if (reliable) {
            lastReliableBeatAt = now
            learnedBeatCount += 1
        } else {
            fallbackBeatCount += 1
        }
        sharedBeatSequence += 1L

        // Small synchronized color accent only; old values were tens of degrees
        // per kick and visually ran ahead of the actual spectrum response.
        huePhase = (huePhase + 2.2f + strength.coerceIn(0f, 1f) * 3.8f) % 360f
    }

    private fun shouldAddFallbackBeat(snapshot: CapsuleSnapshot, now: Long): Boolean {
        if (!snapshot.analyzerRunning || snapshot.signal <= .0045f) return false
        if (lastBeatAt == 0L) return false
        val tempo = resolveTempo()
        val expected = tempo.intervalMs
        val minimumGap = max(150L, (expected * .66f).toLong())
        if (now - lastBeatAt < minimumGap) return false
        // If the reliable detector is still firing normally, do not invent an
        // extra beat between two genuine events.
        if (
            lastReliableBeatAt > 0L &&
            now - lastReliableBeatAt < (expected * 1.40f).toLong()
        ) {
            return false
        }
        return true
    }

    private data class TempoResult(
        val bpm: Float,
        val intervalMs: Float,
        val confidence: Float,
        val tempoFactor: Float,
    )

    private fun resolveTempo(): TempoResult {
        if (intervals.isEmpty()) {
            return TempoResult(0f, 500f, 0f, .45f)
        }

        val sorted = intervals.sorted()
        val rawMedian = median(sorted.map { it.toFloat() })
        var bpm = 60_000f / max(250f, rawMedian)
        // Keep animation tempo in a useful musical range while naturally
        // handling half-time / double-time detector interpretations.
        while (bpm < 70f) bpm *= 2f
        while (bpm > 190f) bpm /= 2f
        val normalizedInterval = 60_000f / bpm

        val normalized = intervals.map { interval ->
            var value = interval.toFloat()
            while (value > normalizedInterval * 1.65f) value /= 2f
            while (value < normalizedInterval * .58f) value *= 2f
            value
        }
        val med = median(normalized)
        val deviations = normalized.map { abs(it - med) }
        val mad = median(deviations)
        val stability = (1f - (mad / max(1f, med)) * 3.2f).coerceIn(0f, 1f)
        val sampleConfidence = min(1f, intervals.size / 8f)
        val fallbackPenalty = if (learnedBeatCount + fallbackBeatCount == 0) {
            1f
        } else {
            (1f - fallbackBeatCount.toFloat() / (learnedBeatCount + fallbackBeatCount) * .20f)
                .coerceIn(.75f, 1f)
        }
        val confidence = (sampleConfidence * stability * fallbackPenalty).coerceIn(0f, 1f)
        val factor = ((bpm - 70f) / 110f).coerceIn(0f, 1f)
        return TempoResult(bpm, normalizedInterval, confidence, factor)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) * .5f
        } else {
            sorted[middle]
        }
    }
}
