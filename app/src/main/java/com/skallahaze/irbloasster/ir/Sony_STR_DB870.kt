package com.skallahaze.irbloasster.ir

enum class SonyCommandMode(val title: String) {
    AV1("AV1"),
    AV2("AV2"),
}

data class SonyCommand(
    val id: String,
    val label: String,
    val command: Int,
    val av1Address: Int = 16,
    val av1Bits: Int = if (av1Address > 31) 15 else 12,
    val note: String? = null,
) {
    init {
        require(command in 0..127) { "Sony SIRC command must fit 7 bits" }
        require(av1Address in 0..255) { "Sony SIRC address must fit 8 bits" }
        require(av1Bits == 12 || av1Bits == 15 || av1Bits == 20) {
            "Sony SIRC uses 12, 15 or 20 bits"
        }
    }

    fun addressFor(mode: SonyCommandMode): Int =
        av1Address + if (mode == SonyCommandMode.AV2) 32 else 0

    fun bitsFor(mode: SonyCommandMode): Int =
        if (mode == SonyCommandMode.AV2) 15 else av1Bits
}

/**
 * Sony STR-DB870 candidate profile.
 *
 * The receiver model is confirmed from the device label. Sony lists RM-U305A
 * and RM-PP505 as the supplied remotes depending on region. AV1 is the factory
 * command mode; AV2 shifts the Sony device address by 32 and uses a 15-bit
 * SIRC frame.
 *
 * The profile combines Sony's documented button set with learned RM-PP505
 * signals and known Sony receiver SIRC mappings. It remains a hardware-test
 * profile until every button has been confirmed on the user's receiver.
 */
object Sony_STR_DB870 {
    const val FREQUENCY = 40_000
    const val MODEL = "STR-DB870"
    const val REGIONAL_REMOTES = "RM-U305A / RM-PP505"

    // Core receiver controls — Sony device 16 in AV1, device 48 in AV2.
    val POWER = SonyCommand("power", "Power", 21)
    val POWER_ON = SonyCommand("power_on", "Einschalten", 46)
    val POWER_OFF = SonyCommand("power_off", "Ausschalten", 47)
    val VOLUME_UP = SonyCommand("volume_up", "Lauter", 18)
    val VOLUME_DOWN = SonyCommand("volume_down", "Leiser", 19)
    val MUTE = SonyCommand("mute", "Stumm", 20)
    val SLEEP = SonyCommand("sleep", "Sleep", 96)

    // Input selection learned from the RM-PP505 generation.
    val INPUT_PHONO = SonyCommand("phono", "PHONO", 32)
    val INPUT_TUNER = SonyCommand("tuner", "TUNER", 33)
    val INPUT_VIDEO_1 = SonyCommand("video_1", "VIDEO 1", 34)
    val INPUT_VIDEO_2 = SonyCommand("video_2", "VIDEO 2", 30)
    val INPUT_VIDEO_3 = SonyCommand("video_3", "VIDEO 3", 66)
    val INPUT_TV_SAT = SonyCommand("tv_sat", "TV / SAT", 106)
    val INPUT_DVD_LD = SonyCommand(
        "dvd_ld",
        "DVD / LD",
        125,
        note = "RM-PP505 learned code; command 107 is retained as an older fallback",
    )
    val INPUT_TAPE_MD = SonyCommand(
        "tape_md",
        "MD / TAPE",
        105,
        note = "RM-PP505 learned code; command 35 is retained as a generic fallback",
    )
    val INPUT_CD = SonyCommand("cd", "CD / SACD", 37)
    val INPUT_AUX = SonyCommand("aux", "AUX", 29)
    val INPUT_MULTI_2CH_DIRECT = SonyCommand(
        "multi_2ch_direct",
        "MULTI / 2CH A.DIRECT",
        73,
    )
    val INPUT_MULTI = SonyCommand(
        "multi",
        "MULTI CH",
        114,
        note = "Discrete Multi/5.1 candidate",
    )

    // RM-PP505/DB870-era DSP and receiver-menu family — device 144/176.
    val AFD = SonyCommand("afd", "A.F.D.", 71, av1Address = 144)
    val MODE_2CH = SonyCommand("mode_2ch", "2CH / OFF", 65, av1Address = 144)
    val SOUND_FIELD_NEXT = SonyCommand("sound_next", "Mode +", 110, av1Address = 144)
    val SOUND_FIELD_PREVIOUS = SonyCommand("sound_previous", "Mode −", 111, av1Address = 144)
    val INPUT_MODE = SonyCommand("input_mode", "Input Mode", 48, av1Address = 144)
    val NIGHT_MODE = SonyCommand("night_mode", "Night Mode", 32, av1Address = 144)
    val EQ_TONE = SonyCommand("eq_tone", "EQ / Tone", 76, av1Address = 144)
    val AUDIO_SPLIT = SonyCommand("audio_split", "Audio Split", 100, av1Address = 144)
    val MAIN_MENU = SonyCommand("main_menu", "Main Menu", 119, av1Address = 144)
    val MENU_UP = SonyCommand("menu_up", "Hoch", 120, av1Address = 144)
    val MENU_DOWN = SonyCommand("menu_down", "Runter", 121, av1Address = 144)
    val MENU_LEFT = SonyCommand("menu_left", "Links", 122, av1Address = 144)
    val MENU_RIGHT = SonyCommand("menu_right", "Rechts", 123, av1Address = 144)

    // Enter/Exec and test-tone were learned on the basic receiver address.
    val MENU_SELECT = SonyCommand("menu_select", "Enter / Exec", 12)
    val MENU_ENTER = MENU_SELECT
    val TEST_TONE = SonyCommand("test_tone", "Test Tone", 74)

    // Discrete subwoofer candidates on device 16.
    val SUBWOOFER_UP = SonyCommand("sub_up", "Subwoofer +", 92)
    val SUBWOOFER_DOWN = SonyCommand("sub_down", "Subwoofer −", 93)

    // Tuner family — Sony tuner device 13/45.
    val TUNER_PRESET_UP = SonyCommand("preset_up", "Preset +", 16, av1Address = 13)
    val TUNER_PRESET_DOWN = SonyCommand("preset_down", "Preset −", 17, av1Address = 13)
    val TUNING_UP = SonyCommand("tuning_up", "Tuning +", 18, av1Address = 13)
    val TUNING_DOWN = SonyCommand("tuning_down", "Tuning −", 19, av1Address = 13)
    val FM_MODE = SonyCommand("fm_mode", "FM Mode", 33, av1Address = 13)
    val DIRECT_TUNING = SonyCommand("direct_tuning", "Direct Tuning", 83, av1Address = 13)

    // Useful older Sony receiver mappings retained as explicit fallbacks.
    val INPUT_DVD_LD_OLD = SonyCommand(
        "dvd_ld_old",
        "DVD / LD (alt)",
        107,
        note = "Older Sony receiver mapping",
    )
    val INPUT_TAPE_MD_GENERIC = SonyCommand(
        "tape_md_generic",
        "TAPE / MD (alt)",
        35,
        note = "Generic older Sony receiver mapping",
    )
    val MODE_2CH_LEGACY = SonyCommand(
        "mode_2ch_legacy",
        "2CH (Legacy)",
        8,
        av1Address = 18,
    )
    val SOUND_FIELD_NEXT_LEGACY = SonyCommand(
        "sound_next_legacy",
        "Sound Field + (Legacy)",
        54,
        av1Address = 18,
    )
    val SOUND_FIELD_PREVIOUS_LEGACY = SonyCommand(
        "sound_previous_legacy",
        "Sound Field − (Legacy)",
        55,
        av1Address = 18,
    )
    val EFFECT_OFF = SonyCommand("effect_off", "Effect Off (Legacy)", 93, av1Address = 18)
    val SUBWOOFER_UP_LEGACY = SonyCommand(
        "sub_up_legacy",
        "Woofer + (Legacy)",
        86,
        av1Address = 18,
    )
    val SUBWOOFER_DOWN_LEGACY = SonyCommand(
        "sub_down_legacy",
        "Woofer − (Legacy)",
        87,
        av1Address = 18,
    )
    val TEST_TONE_MODERN = SonyCommand(
        "test_tone_modern",
        "Test Tone (Device 144)",
        74,
        av1Address = 144,
    )

    val INPUTS = listOf(
        INPUT_TV_SAT,
        INPUT_DVD_LD,
        INPUT_VIDEO_1,
        INPUT_VIDEO_2,
        INPUT_VIDEO_3,
        INPUT_CD,
        INPUT_TUNER,
        INPUT_TAPE_MD,
        INPUT_PHONO,
        INPUT_AUX,
        INPUT_MULTI_2CH_DIRECT,
        INPUT_MULTI,
    )

    val SOUND_CONTROLS = listOf(
        AFD,
        MODE_2CH,
        SOUND_FIELD_NEXT,
        SOUND_FIELD_PREVIOUS,
        INPUT_MODE,
        NIGHT_MODE,
        EQ_TONE,
        AUDIO_SPLIT,
    )

    val TUNER_CONTROLS = listOf(
        TUNER_PRESET_UP,
        TUNER_PRESET_DOWN,
        TUNING_UP,
        TUNING_DOWN,
        FM_MODE,
        DIRECT_TUNING,
    )

    val FALLBACK_CODES = listOf(
        INPUT_DVD_LD_OLD,
        INPUT_TAPE_MD_GENERIC,
        MODE_2CH_LEGACY,
        SOUND_FIELD_NEXT_LEGACY,
        SOUND_FIELD_PREVIOUS_LEGACY,
        EFFECT_OFF,
        SUBWOOFER_UP_LEGACY,
        SUBWOOFER_DOWN_LEGACY,
        TEST_TONE_MODERN,
    )

    fun pattern(command: SonyCommand, mode: SonyCommandMode): IntArray =
        Sirc.encode(
            command = command.command,
            address = command.addressFor(mode),
            bits = command.bitsFor(mode),
            frames = 3,
        )
}
