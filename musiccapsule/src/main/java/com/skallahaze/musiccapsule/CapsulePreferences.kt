package com.skallahaze.musiccapsule

import android.content.Context

enum class CapsuleMode {
    RIM,
    COMPACT,
    EXPANDED,
}

enum class MediaSourceLock(
    val key: String,
    val label: String,
    val packagePrefixes: List<String>,
) {
    AUTO("auto", "Auto", emptyList()),
    YOUTUBE(
        "youtube",
        "YouTube",
        listOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
        ),
    ),
    SOUNDCLOUD("soundcloud", "SoundCloud", listOf("com.soundcloud.android")),
    SPOTIFY("spotify", "Spotify", listOf("com.spotify.music")),
    TWITCH("twitch", "Twitch", listOf("tv.twitch.android.app")),
    ;

    fun matches(packageName: String): Boolean {
        if (this == AUTO) return true
        return packagePrefixes.any { prefix -> packageName.startsWith(prefix, ignoreCase = true) }
    }

    companion object {
        fun fromKey(value: String?): MediaSourceLock {
            return entries.firstOrNull { it.key == value } ?: AUTO
        }
    }
}

object CapsulePreferences {
    private const val FILE_NAME = "music_capsule_preferences"
    private const val KEY_MODE = "overlay_mode"
    private const val KEY_EDGES = "edge_panels"
    private const val KEY_SOURCE_LOCK = "source_lock"
    private const val KEY_EDGE_INTENSITY = "edge_intensity"

    private fun preferences(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun overlayMode(context: Context): CapsuleMode {
        val value = preferences(context).getString(KEY_MODE, CapsuleMode.COMPACT.name)
        return runCatching { CapsuleMode.valueOf(value ?: CapsuleMode.COMPACT.name) }
            .getOrDefault(CapsuleMode.COMPACT)
    }

    fun setOverlayMode(context: Context, mode: CapsuleMode) {
        preferences(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun edgePanelsEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_EDGES, true)

    fun setEdgePanelsEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_EDGES, enabled).apply()
    }

    fun sourceLock(context: Context): MediaSourceLock =
        MediaSourceLock.fromKey(preferences(context).getString(KEY_SOURCE_LOCK, MediaSourceLock.AUTO.key))

    fun setSourceLock(context: Context, source: MediaSourceLock) {
        preferences(context).edit().putString(KEY_SOURCE_LOCK, source.key).apply()
    }

    fun edgeIntensity(context: Context): Float =
        preferences(context).getFloat(KEY_EDGE_INTENSITY, 1.12f).coerceIn(0.55f, 1.65f)

    fun setEdgeIntensity(context: Context, intensity: Float) {
        preferences(context).edit()
            .putFloat(KEY_EDGE_INTENSITY, intensity.coerceIn(0.55f, 1.65f))
            .apply()
    }
}
