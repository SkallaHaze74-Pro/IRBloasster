package com.skallahaze.musiccapsule

import android.content.Context

enum class CapsuleDisplayMode(val storedValue: String, val label: String) {
    MINI("mini", "Kleine Kapsel"),
    BARS("bars", "Nur Balken"),
    RIM("rim", "Nur Neon-Rand"),
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
    YOUTUBE(
        "youtube",
        "YouTube",
        setOf("com.google.android.youtube"),
    ),
    YOUTUBE_MUSIC(
        "youtube_music",
        "YouTube Music",
        setOf("com.google.android.apps.youtube.music"),
    ),
    SOUNDCLOUD(
        "soundcloud",
        "SoundCloud",
        setOf("com.soundcloud.android"),
    ),
    SPOTIFY(
        "spotify",
        "Spotify",
        setOf("com.spotify.music"),
    ),
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

enum class ReactiveFlowMode(
    val storedValue: String,
    val label: String,
) {
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
    SMOOTH("smooth", "Smooth", .56f),
    REACTIVE("reactive", "Reactive", .90f),
    BRUTAL("brutal", "Brutal", 1.22f),
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

enum class VisualLayerMode(
    val storedValue: String,
    val label: String,
) {
    FULL("full", "Alles"),
    CLEAN("clean", "Clean"),
    BORDER_ONLY("border_only", "Nur Rand"),
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
    val silenceReleaseRate: Float,
    val starScale: Float,
) {
    DIRECT("direct", "Direkt", 34f, 18f, 34f, .82f),
    SMOOTH("smooth", "Smooth", 24f, 13f, 30f, .72f),
    FLOATING("floating", "Schwebend", 16f, 7.5f, 22f, .62f),
    ;

    fun next(): MotionProfile {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): MotionProfile =
            entries.firstOrNull { it.storedValue == value } ?: SMOOTH
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
        prefs(context).edit().putFloat(KEY_NEON_INTENSITY, intensity.coerceIn(.75f, 1.8f)).apply()
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
        prefs(context).getString(KEY_MOTION_PROFILE, MotionProfile.SMOOTH.storedValue),
    )

    fun setMotionProfile(context: Context, profile: MotionProfile) {
        prefs(context).edit().putString(KEY_MOTION_PROFILE, profile.storedValue).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
