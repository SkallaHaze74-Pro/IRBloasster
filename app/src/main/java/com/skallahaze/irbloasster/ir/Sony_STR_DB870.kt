package com.skallahaze.irbloasster.ir

enum class SonyCommandMode(val title: String) {
    AV1("AV1"),
    AV2("AV2"),
}

data class SonyCommand(
    val id: String,
    val label: String,
    val command: Int,
    val baseAddress: Int = 16,
)

object Sony_STR_DB870 {
    const val FREQUENCY = 40_000

    val POWER = SonyCommand("power", "Power", 21)
    val POWER_ON = SonyCommand("power_on", "Einschalten", 46)
    val POWER_OFF = SonyCommand("power_off", "Ausschalten", 47)
    val VOLUME_UP = SonyCommand("volume_up", "Lauter", 18)
    val VOLUME_DOWN = SonyCommand("volume_down", "Leiser", 19)
    val MUTE = SonyCommand("mute", "Stumm", 20)

    val INPUT_PHONO = SonyCommand("phono", "PHONO", 32)
    val INPUT_TUNER = SonyCommand("tuner", "TUNER", 33)
    val INPUT_VIDEO_1 = SonyCommand("video_1", "VIDEO 1", 34)
    val INPUT_TAPE_MD = SonyCommand("tape_md", "TAPE / MD", 35)
    val INPUT_CD = SonyCommand("cd", "CD / SACD", 37)
    val INPUT_VIDEO_2 = SonyCommand("video_2", "VIDEO 2", 30)
    val INPUT_VIDEO_3 = SonyCommand("video_3", "VIDEO 3", 66)
    val INPUT_TV_SAT = SonyCommand("tv_sat", "TV / SAT", 106)
    val INPUT_DVD_LD = SonyCommand("dvd_ld", "DVD / LD", 107)
    val INPUT_MULTI = SonyCommand("multi", "MULTI CH", 114)

    val MODE_2CH = SonyCommand("mode_2ch", "2CH Stereo", 8, baseAddress = 18)
    val SOUND_FIELD_NEXT = SonyCommand("sound_next", "Sound Field +", 54, baseAddress = 18)
    val SOUND_FIELD_PREVIOUS = SonyCommand("sound_previous", "Sound Field −", 55, baseAddress = 18)
    val TEST_TONE = SonyCommand("test_tone", "Test Tone", 74, baseAddress = 18)
    val EFFECT_OFF = SonyCommand("effect_off", "Effect Off", 93, baseAddress = 18)
    val SUBWOOFER_UP = SonyCommand("sub_up", "Subwoofer +", 86, baseAddress = 18)
    val SUBWOOFER_DOWN = SonyCommand("sub_down", "Subwoofer −", 87, baseAddress = 18)

    val MENU_UP = SonyCommand("menu_up", "Hoch", 120, baseAddress = 12)
    val MENU_DOWN = SonyCommand("menu_down", "Runter", 121, baseAddress = 12)
    val MENU_LEFT = SonyCommand("menu_left", "Links", 122, baseAddress = 12)
    val MENU_RIGHT = SonyCommand("menu_right", "Rechts", 123, baseAddress = 12)
    val MENU_SELECT = SonyCommand("menu_select", "Select", 119, baseAddress = 12)
    val MENU_ENTER = SonyCommand("menu_enter", "Enter", 12, baseAddress = 12)

    val TUNER_PRESET_UP = SonyCommand("preset_up", "Preset +", 16, baseAddress = 13)
    val TUNER_PRESET_DOWN = SonyCommand("preset_down", "Preset −", 17, baseAddress = 13)
    val TUNING_UP = SonyCommand("tuning_up", "Tuning +", 18, baseAddress = 13)
    val TUNING_DOWN = SonyCommand("tuning_down", "Tuning −", 19, baseAddress = 13)
    val FM_MODE = SonyCommand("fm_mode", "FM Mode", 33, baseAddress = 13)

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
        INPUT_MULTI,
    )

    fun pattern(command: SonyCommand, mode: SonyCommandMode): IntArray {
        val address = command.baseAddress + if (mode == SonyCommandMode.AV2) 32 else 0
        val bits = if (mode == SonyCommandMode.AV2) 15 else 12
        return Sirc.encode(command.command, address, bits = bits, frames = 3)
    }
}
