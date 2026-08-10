package com.skallahaze.musiccapsule

import android.content.Context

enum class CapsuleDisplayMode(val storedValue: String, val label: String) {
    MINI("mini", "Kleine Kapsel"),
    RIM("rim", "Mini-Balken"),
    HIDDEN("hidden", "Ausgeblendet"),
    ;

    fun next(): CapsuleDisplayMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): CapsuleDisplayMode =
            entries.firstOrNull { it.storedValue == value } ?: MINI
    }
}

enum class MediaSourceLock(
    val storedValue: String,
    val label: String,
    val packageNames: Set<String>,
) {
    AUTO("auto", "Automatisch", emptySet()),
    YOUTUBE("youtube", "YouTube", setOf("com.google.android.youtube")),
    YOUTUBE_MUSIC(
        "youtube_music",
        "YouTube Music",
        setOf("com.google.android.apps.youtube.music"),
    ),
    SOUNDCLOUD("soundcloud", "SoundCloud", setOf("com.soundcloud.android")),
    SPOTIFY("spotify", "Spotify", setOf("com.spotify.music")),
    TWITCH(
        "twitch",
        "Twitch",
        setOf("tv.twitch.android.app", "tv.twitch.android.viewer"),
    ),
    ;

    fun next(): MediaSourceLock {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    fun matches(packageName: String): Boolean {
        if (this == AUTO) return true
        return packageNames.any { expected ->
            packageName == expected || packageName.startsWith("$expected.")
        }
    }

    companion object {
        fun from(value: String?): MediaSourceLock =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

enum class ReactiveFlowMode(val storedValue: String, val label: String) {
    AUTO("auto", "Beat Auto"),
    INWARD("inward", "Nach innen"),
    OUTWARD("outward", "Nach außen"),
    UP("up", "Nach oben"),
    DOWN("down", "Nach unten"),
    ;

    fun next(): ReactiveFlowMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): ReactiveFlowMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

enum class BeatFxMode(
    val storedValue: String,
    val label: String,
    val multiplier: Float,
) {
    // Sync Fix: slower base hue travel. Beat/BPM can still accelerate it,
    // but colors no longer outrun spectrum and pattern motion.
    SMOOTH("smooth", "Smooth", .40f),
    REACTIVE("reactive", "Reactive", .67f),
    BRUTAL("brutal", "Brutal", .92f),
    ;

    fun next(): BeatFxMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): BeatFxMode =
            entries.firstOrNull { it.storedValue == value } ?: BRUTAL
    }
}

enum class VisualLayerMode(val storedValue: String, val label: String) {
    FULL("full", "Voll"),
    CLEAN("clean", "Nur Striche"),
    BORDER_ONLY("border", "Nur Rand"),
    BORDER_DROP("border_drop", "Rand + Drop"),
    ;

    fun next(): VisualLayerMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): VisualLayerMode =
            entries.firstOrNull { it.storedValue == value } ?: FULL
    }
}

enum class MotionProfile(
    val storedValue: String,
    val label: String,
    val attackRate: Float,
    val releaseRate: Float,
) {
    // Faster attack lets bars reach a kick in the same visual moment; release
    // remains lower so the motion stays smooth instead of flickering.
    SILKY("silky", "Seidig", 29f, 10.5f),
    BALANCED("balanced", "Balance", 42f, 13.5f),
    DIRECT("direct", "Direkt", 58f, 17f),
    ;

    fun next(): MotionProfile {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): MotionProfile =
            entries.firstOrNull { it.storedValue == value } ?: SILKY
    }
}

enum class TrailMode(
    val storedValue: String,
    val label: String,
    val beatDecayRate: Float,
    val particleLifeScale: Float,
    val silenceFadeRate: Float,
) {
    SHORT("short", "Kurz", 6.2f, .76f, 27f),
    MEDIUM("medium", "Mittel", 4.6f, 1f, 17f),
    LONG("long", "Lang", 3.3f, 1.24f, 10f),
    ;

    fun next(): TrailMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): TrailMode =
            entries.firstOrNull { it.storedValue == value } ?: SHORT
    }
}

enum class AutoTuneMode(val storedValue: String, val label: String) {
    OFF("off", "Aus / Manuell"),
    SOFT("soft", "Auto Soft"),
    // The old balanced slot keeps its stored value for update compatibility,
    // but is now the neutral adaptive learner requested by the user.
    BALANCED("balanced", "Auto Lernen"),
    BRUTAL("brutal", "Auto Brutal"),
    ;

    fun next(): AutoTuneMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): AutoTuneMode =
            entries.firstOrNull { it.storedValue == value } ?: BALANCED
    }
}

enum class StarMode(val storedValue: String, val label: String) {
    OFF("off", "Aus"),
    SUBTLE("subtle", "Wenig"),
    BALANCED("balanced", "Normal"),
    BEAT_PLUS("beat_plus", "Mehr Beat"),
    DROP_ONLY("drop_only", "Nur Drops"),
    ;

    fun next(): StarMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): StarMode =
            entries.firstOrNull { it.storedValue == value } ?: BEAT_PLUS
    }
}

enum class StageStyle(val storedValue: String, val label: String) {
    AMOLED_BLACK("black", "AMOLED Schwarz"),
    NEON_AURA("aura", "Neon Aura"),
    LEAF_AURA("leaf", "Hanf Aura"),
    ;

    fun next(): StageStyle {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): StageStyle =
            entries.firstOrNull { it.storedValue == value } ?: NEON_AURA
    }
}

object CapsulePreferences {
    private const val PREFS = "music_capsule_design"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_SOURCE_LOCK = "source_lock"
    private const val KEY_EDGE_PANELS = "edge_panels"
    private const val KEY_NEON_INTENSITY = "neon_intensity"
    private const val KEY_LOCK_SCREEN = "lock_screen"
    private const val KEY_FLOW_MODE = "flow_mode"
    private const val KEY_BEAT_FX = "beat_fx"
    private const val KEY_VISUAL_LAYER = "visual_layer"
    private const val KEY_MOTION_PROFILE = "motion_profile"
    private const val KEY_TRAIL_MODE = "trail_mode"
    private const val KEY_AUTO_TUNE = "auto_tune"
    private const val KEY_STAR_MODE = "star_mode"
    private const val KEY_STAGE_STYLE = "stage_style"
    private const val KEY_STAGE_KEEP_AWAKE = "stage_keep_awake"

    fun displayMode(context: Context): CapsuleDisplayMode = CapsuleDisplayMode.from(
        prefs(context).getString(KEY_DISPLAY_MODE, CapsuleDisplayMode.MINI.storedValue),
    )

    fun setDisplayMode(context: Context, mode: CapsuleDisplayMode) {
        prefs(context).edit().putString(KEY_DISPLAY_MODE, mode.storedValue).apply()
    }

    fun sourceLock(context: Context): MediaSourceLock = MediaSourceLock.from(
        prefs(context).getString(KEY_SOURCE_LOCK, MediaSourceLock.AUTO.storedValue),
    )

    fun setSourceLock(context: Context, source: MediaSourceLock) {
        prefs(context).edit().putString(KEY_SOURCE_LOCK, source.storedValue).apply()
    }

    fun edgePanelsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EDGE_PANELS, true)

    fun setEdgePanelsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EDGE_PANELS, enabled).apply()
    }

    fun neonIntensity(context: Context): Float = prefs(context)
        .getFloat(KEY_NEON_INTENSITY, 1.35f)
        .coerceIn(.75f, 1.8f)

    fun setNeonIntensity(context: Context, intensity: Float) {
        prefs(context).edit()
            .putFloat(KEY_NEON_INTENSITY, intensity.coerceIn(.75f, 1.8f))
            .apply()
    }

    fun nextIntensity(context: Context): Float {
        val current = neonIntensity(context)
        val next = when {
            current < 1.15f -> 1.35f
            current < 1.55f -> 1.70f
            else -> 1.0f
        }
        setNeonIntensity(context, next)
        return next
    }

    fun lockScreenEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_SCREEN, true)

    fun setLockScreenEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN, enabled).apply()
    }

    fun flowMode(context: Context): ReactiveFlowMode = ReactiveFlowMode.from(
        prefs(context).getString(KEY_FLOW_MODE, ReactiveFlowMode.AUTO.storedValue),
    )

    fun setFlowMode(context: Context, mode: ReactiveFlowMode) {
        prefs(context).edit().putString(KEY_FLOW_MODE, mode.storedValue).apply()
    }

    fun beatFxMode(context: Context): BeatFxMode = BeatFxMode.from(
        prefs(context).getString(KEY_BEAT_FX, BeatFxMode.BRUTAL.storedValue),
    )

    fun setBeatFxMode(context: Context, mode: BeatFxMode) {
        prefs(context).edit().putString(KEY_BEAT_FX, mode.storedValue).apply()
    }

    fun visualLayerMode(context: Context): VisualLayerMode = VisualLayerMode.from(
        prefs(context).getString(KEY_VISUAL_LAYER, VisualLayerMode.FULL.storedValue),
    )

    fun setVisualLayerMode(context: Context, mode: VisualLayerMode) {
        prefs(context).edit().putString(KEY_VISUAL_LAYER, mode.storedValue).apply()
    }

    fun motionProfile(context: Context): MotionProfile = MotionProfile.from(
        prefs(context).getString(KEY_MOTION_PROFILE, MotionProfile.SILKY.storedValue),
    )

    fun setMotionProfile(context: Context, profile: MotionProfile) {
        prefs(context).edit().putString(KEY_MOTION_PROFILE, profile.storedValue).apply()
    }

    fun trailMode(context: Context): TrailMode = TrailMode.from(
        prefs(context).getString(KEY_TRAIL_MODE, TrailMode.SHORT.storedValue),
    )

    fun setTrailMode(context: Context, mode: TrailMode) {
        prefs(context).edit().putString(KEY_TRAIL_MODE, mode.storedValue).apply()
    }

    fun autoTuneMode(context: Context): AutoTuneMode = AutoTuneMode.from(
        prefs(context).getString(KEY_AUTO_TUNE, AutoTuneMode.BALANCED.storedValue),
    )

    fun setAutoTuneMode(context: Context, mode: AutoTuneMode) {
        prefs(context).edit().putString(KEY_AUTO_TUNE, mode.storedValue).apply()
    }

    fun starMode(context: Context): StarMode = StarMode.from(
        prefs(context).getString(KEY_STAR_MODE, StarMode.BEAT_PLUS.storedValue),
    )

    fun setStarMode(context: Context, mode: StarMode) {
        prefs(context).edit().putString(KEY_STAR_MODE, mode.storedValue).apply()
    }

    fun stageStyle(context: Context): StageStyle = StageStyle.from(
        prefs(context).getString(KEY_STAGE_STYLE, StageStyle.NEON_AURA.storedValue),
    )

    fun setStageStyle(context: Context, style: StageStyle) {
        prefs(context).edit().putString(KEY_STAGE_STYLE, style.storedValue).apply()
    }

    fun stageKeepAwake(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STAGE_KEEP_AWAKE, true)

    fun setStageKeepAwake(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_STAGE_KEEP_AWAKE, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
