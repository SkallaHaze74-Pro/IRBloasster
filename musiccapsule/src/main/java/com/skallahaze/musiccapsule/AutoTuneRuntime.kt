package com.skallahaze.musiccapsule

import android.os.SystemClock

data class AutoTuneSnapshot(
    val autoMode: AutoTuneMode = AutoTuneMode.BALANCED,
    val motion: MotionProfile = MotionProfile.SILKY,
    val trail: TrailMode = TrailMode.SHORT,
    val beatFx: BeatFxMode = BeatFxMode.REACTIVE,
    val stars: StarMode = StarMode.BEAT_PLUS,
    val energy: Float = 0f,
    val bassBias: Float = 0f,
    val rhythmConfidence: Float = 0f,
    val bpm: Float = 0f,
    val label: String = "lernt den Track …",
    val updatedAt: Long = 0L,
)

/** Lightweight cross-view diagnostics for the live Smart Auto profile. */
object AutoTuneRuntime {
    private val lock = Any()
    private var state = AutoTuneSnapshot()

    fun snapshot(): AutoTuneSnapshot = synchronized(lock) { state }

    fun publish(
        autoMode: AutoTuneMode,
        motion: MotionProfile,
        trail: TrailMode,
        beatFx: BeatFxMode,
        stars: StarMode,
        energy: Float,
        bassBias: Float,
        rhythmConfidence: Float,
        bpm: Float,
        label: String,
    ) = synchronized(lock) {
        state = AutoTuneSnapshot(
            autoMode = autoMode,
            motion = motion,
            trail = trail,
            beatFx = beatFx,
            stars = stars,
            energy = energy.coerceIn(0f, 1f),
            bassBias = bassBias.coerceIn(0f, 1f),
            rhythmConfidence = rhythmConfidence.coerceIn(0f, 1f),
            bpm = bpm.coerceIn(0f, 260f),
            label = label,
            updatedAt = SystemClock.elapsedRealtime(),
        )
    }

    fun clear() = synchronized(lock) {
        state = AutoTuneSnapshot()
    }
}
