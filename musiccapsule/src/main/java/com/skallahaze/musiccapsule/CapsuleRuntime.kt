package com.skallahaze.musiccapsule

import android.graphics.Bitmap

data class CapsuleSnapshot(
    val overlayRunning: Boolean = false,
    val analyzerRunning: Boolean = false,
    val expanded: Boolean = false,
    val message: String = "Music Capsule aus",
    val title: String = "Keine Wiedergabe",
    val artist: String = "",
    val packageName: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    val signal: Float = 0f,
    val levels: FloatArray = FloatArray(CapsuleRuntime.BAND_COUNT),
    val source: String = "idle",
)

object CapsuleRuntime {
    const val BAND_COUNT = 16

    private val lock = Any()
    private var state = CapsuleSnapshot()

    fun snapshot(): CapsuleSnapshot = synchronized(lock) {
        state.copy(levels = state.levels.copyOf())
    }

    fun updateOverlay(
        running: Boolean,
        expanded: Boolean = state.expanded,
        message: String = state.message,
    ) = synchronized(lock) {
        state = state.copy(
            overlayRunning = running,
            expanded = expanded,
            message = message,
        )
    }

    fun updateExpanded(expanded: Boolean) = synchronized(lock) {
        state = state.copy(expanded = expanded)
    }

    fun updateAnalyzer(
        running: Boolean,
        message: String = state.message,
        source: String = state.source,
    ) = synchronized(lock) {
        state = state.copy(
            analyzerRunning = running,
            message = message,
            source = source,
        )
    }

    fun updateMedia(
        title: String,
        artist: String,
        packageName: String,
        artwork: Bitmap?,
        isPlaying: Boolean,
    ) = synchronized(lock) {
        state = state.copy(
            title = title.ifBlank { "Unbekannter Titel" },
            artist = artist,
            packageName = packageName,
            artwork = artwork,
            isPlaying = isPlaying,
        )
    }

    fun clearMedia() = synchronized(lock) {
        state = state.copy(
            title = "Keine Wiedergabe",
            artist = "",
            packageName = "",
            artwork = null,
            isPlaying = false,
        )
    }

    fun updateLevels(
        levels: FloatArray,
        signal: Float,
        message: String,
        source: String = "playback-capture",
    ) = synchronized(lock) {
        val safe = FloatArray(BAND_COUNT)
        for (index in safe.indices) {
            safe[index] = (levels.getOrNull(index) ?: 0f).coerceIn(0f, 1f)
        }
        state = state.copy(
            analyzerRunning = true,
            levels = safe,
            signal = signal.coerceIn(0f, 1f),
            message = message,
            source = source,
        )
    }

    fun markAnalyzerStopped(message: String = "Audioanalyse aus") = synchronized(lock) {
        state = state.copy(
            analyzerRunning = false,
            signal = 0f,
            levels = FloatArray(BAND_COUNT),
            message = message,
            source = "idle",
        )
    }
}
