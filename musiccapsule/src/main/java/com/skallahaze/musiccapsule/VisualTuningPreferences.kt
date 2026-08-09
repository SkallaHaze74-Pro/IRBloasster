package com.skallahaze.musiccapsule

import android.content.Context

enum class BeatPatternMode(val storedValue: String, val label: String) {
    AUTO("auto", "Beat Auto"),
    RECTANGLE("rectangle", "Viereck"),
    INFINITY("infinity", "Unendlich ∞"),
    UP_DOWN("up_down", "Hoch / Runter"),
    HORIZONTAL("horizontal", "Quer"),
    DIAMOND("diamond", "Diamant"),
    OFF("off", "Aus"),
    ;

    fun next(): BeatPatternMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): BeatPatternMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

object VisualTuningPreferences {
    private const val PREFS = "music_capsule_visual_tuning"
    private const val KEY_OPACITY = "visual_opacity"
    private const val KEY_PATTERN_MODE = "beat_pattern_mode"

    /** Visual alpha inside the existing touch-through overlay window. */
    fun opacity(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_OPACITY, .88f)
            .coerceIn(.25f, 1f)

    fun setOpacity(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_OPACITY, value.coerceIn(.25f, 1f))
            .apply()
    }

    fun nextOpacity(context: Context): Float {
        val current = opacity(context)
        val next = when {
            current < .43f -> .55f
            current < .63f -> .72f
            current < .82f -> .90f
            current < .96f -> 1f
            else -> .35f
        }
        setOpacity(context, next)
        return next
    }

    fun patternMode(context: Context): BeatPatternMode = BeatPatternMode.from(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PATTERN_MODE, BeatPatternMode.AUTO.storedValue),
    )

    fun setPatternMode(context: Context, mode: BeatPatternMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATTERN_MODE, mode.storedValue)
            .apply()
    }
}
