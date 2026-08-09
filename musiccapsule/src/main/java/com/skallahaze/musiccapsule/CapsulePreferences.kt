package com.skallahaze.musiccapsule

import android.content.Context

enum class CapsuleDisplayMode(val storedValue: String, val label: String) {
    MINI("mini", "Kleine Kapsel"),
    RIM("rim", "Nur Neon-Rand"),
    ;

    fun next(): CapsuleDisplayMode = when (this) {
        MINI -> RIM
        RIM -> MINI
    }

    companion object {
        fun from(value: String?): CapsuleDisplayMode = entries.firstOrNull { it.storedValue == value } ?: MINI
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
        fun from(value: String?): MediaSourceLock = entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

object CapsulePreferences {
    private const val PREFS = "music_capsule_design"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_SOURCE_LOCK = "source_lock"
    private const val KEY_EDGE_PANELS = "edge_panels"
    private const val KEY_NEON_INTENSITY = "neon_intensity"
    private const val KEY_LOCK_SCREEN = "lock_screen"

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

    fun edgePanelsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_EDGE_PANELS, true)

    fun setEdgePanelsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EDGE_PANELS, enabled).apply()
    }

    fun neonIntensity(context: Context): Float = prefs(context)
        .getFloat(KEY_NEON_INTENSITY, 1.35f)
        .coerceIn(0.75f, 1.8f)

    fun setNeonIntensity(context: Context, intensity: Float) {
        prefs(context).edit().putFloat(KEY_NEON_INTENSITY, intensity.coerceIn(0.75f, 1.8f)).apply()
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

    fun lockScreenEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK_SCREEN, true)

    fun setLockScreenEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN, enabled).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
