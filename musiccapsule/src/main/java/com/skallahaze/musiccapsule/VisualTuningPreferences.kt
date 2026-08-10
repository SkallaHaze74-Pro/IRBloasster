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

enum class LivePatternMode(
    val storedValue: String,
    val label: String,
    val maxConcurrent: Int,
) {
    OFF("off", "Aus", 0),
    SUBTLE("subtle", "Wenig", 1),
    BALANCED("balanced", "Normal", 2),
    STRONG("strong", "Stark", 2),
    BEAT_ONLY("beat_only", "Nur echter Beat", 2),
    AUTO("auto", "Auto", 2),
    ;

    fun next(): LivePatternMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): LivePatternMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

enum class EndpointMode(
    val storedValue: String,
    val label: String,
    val strength: Float,
) {
    OFF("off", "Aus", 0f),
    SMALL("small", "Klein", .66f),
    NORMAL("normal", "Normal", 1f),
    STRONG("strong", "Stark", 1.38f),
    ;

    fun next(): EndpointMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): EndpointMode =
            entries.firstOrNull { it.storedValue == value } ?: STRONG
    }
}

enum class StageContentMode(val storedValue: String, val label: String) {
    FRAME_ONLY("frame", "Nur Rahmen"),
    FRAME_PATTERNS("patterns", "Rahmen + Muster"),
    FUSION("fusion", "Fusion mit Seiten"),
    ;

    fun next(): StageContentMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun from(value: String?): StageContentMode =
            entries.firstOrNull { it.storedValue == value } ?: FUSION
    }
}

object VisualTuningPreferences {
    private const val PREFS = "music_capsule_visual_tuning"
    private const val KEY_OPACITY = "visual_opacity"
    private const val KEY_PATTERN_MODE = "beat_pattern_mode"
    private const val KEY_LIVE_PATTERNS = "live_pattern_mode"
    private const val KEY_ENDPOINT_MODE = "endpoint_mode"
    private const val KEY_STAGE_CONTENT = "stage_content_mode"

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

    fun livePatternMode(context: Context): LivePatternMode = LivePatternMode.from(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIVE_PATTERNS, LivePatternMode.AUTO.storedValue),
    )

    fun setLivePatternMode(context: Context, mode: LivePatternMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIVE_PATTERNS, mode.storedValue)
            .apply()
    }

    fun endpointMode(context: Context): EndpointMode = EndpointMode.from(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT_MODE, EndpointMode.STRONG.storedValue),
    )

    fun setEndpointMode(context: Context, mode: EndpointMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENDPOINT_MODE, mode.storedValue)
            .apply()
    }

    fun stageContentMode(context: Context): StageContentMode = StageContentMode.from(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STAGE_CONTENT, StageContentMode.FUSION.storedValue),
    )

    fun setStageContentMode(context: Context, mode: StageContentMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STAGE_CONTENT, mode.storedValue)
            .apply()
    }
}
