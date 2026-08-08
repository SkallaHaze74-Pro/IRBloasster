package com.skallahaze.irbloasster.capsule

import android.graphics.Bitmap

data class MusicCapsuleSnapshot(
    val running: Boolean = false,
    val expanded: Boolean = false,
    val analyserActive: Boolean = false,
    val message: String = "Music Capsule aus",
    val title: String = "Keine Wiedergabe",
    val artist: String = "",
    val packageName: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    val signal: Float = 0f,
    val levels: FloatArray = FloatArray(MusicCapsuleRuntime.BAND_COUNT),
)

object MusicCapsuleRuntime {
    const val BAND_COUNT = 16

    private val lock = Any()
    private var state = MusicCapsuleSnapshot()

    fun snapshot(): MusicCapsuleSnapshot = synchronized(lock) {
        state.copy(levels = state.levels.copyOf())
    }

    fun updateService(
        running: Boolean,
        expanded: Boolean = state.expanded,
        analyserActive: Boolean = state.analyserActive,
        message: String = state.message,
    ) = synchronized(lock) {
        state = state.copy(
            running = running,
            expanded = expanded,
            analyserActive = analyserActive,
            message = message,
        )
    }

    fun updateExpanded(expanded: Boolean) = synchronized(lock) {
        state = state.copy(expanded = expanded)
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

    fun updateLevels(levels: FloatArray, signal: Float, message: String? = null) = synchronized(lock) {
        val safe = FloatArray(BAND_COUNT)
        for (index in safe.indices) {
            safe[index] = (levels.getOrNull(index) ?: 0f).coerceIn(0f, 1f)
        }
        state = state.copy(
            analyserActive = true,
            signal = signal.coerceIn(0f, 1f),
            levels = safe,
            message = message ?: state.message,
        )
    }

    fun markAnalyserUnavailable(message: String) = synchronized(lock) {
        state = state.copy(
            analyserActive = false,
            signal = 0f,
            levels = FloatArray(BAND_COUNT),
            message = message,
        )
    }
}
