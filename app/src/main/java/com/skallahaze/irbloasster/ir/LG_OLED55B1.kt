package com.skallahaze.irbloasster.ir

data class LgCommand(
    val id: String,
    val label: String,
    val code: Long,
    val repeatFrames: Int = 0,
)

object LG_OLED55B1 {
    const val FREQUENCY = 38_000

    val POWER = LgCommand("power", "Power", 0x20DF10EFL)
    val POWER_ON = LgCommand("power_on", "Einschalten", 0x20DF23DCL)
    val POWER_OFF = LgCommand("power_off", "Ausschalten", 0x20DFA35CL)
    val VOLUME_UP = LgCommand("volume_up", "Lauter", 0x20DF40BFL, repeatFrames = 2)
    val VOLUME_DOWN = LgCommand("volume_down", "Leiser", 0x20DFC03FL, repeatFrames = 2)
    val MUTE = LgCommand("mute", "Stumm", 0x20DF906FL)
    val CHANNEL_UP = LgCommand("channel_up", "Kanal +", 0x20DF00FFL, repeatFrames = 2)
    val CHANNEL_DOWN = LgCommand("channel_down", "Kanal −", 0x20DF807FL, repeatFrames = 2)
    val INPUT = LgCommand("input", "Eingang", 0x20DFD02FL)

    val UP = LgCommand("up", "Hoch", 0x20DF02FDL)
    val DOWN = LgCommand("down", "Runter", 0x20DF827DL)
    val LEFT = LgCommand("left", "Links", 0x20DFE01FL)
    val RIGHT = LgCommand("right", "Rechts", 0x20DF609FL)
    val OK = LgCommand("ok", "OK", 0x20DF22DDL)
    val HOME = LgCommand("home", "Home", 0x20DF3EC1L)
    val BACK = LgCommand("back", "Zurück", 0x20DF14EBL)
    val SETTINGS = LgCommand("settings", "Einstellungen", 0x20DFC23DL)
    val INFO = LgCommand("info", "Info", 0x20DF55AAL)
    val GUIDE = LgCommand("guide", "Guide", 0x20DFD52AL)
    val EXIT = LgCommand("exit", "Exit", 0x20DFDA25L)

    val PLAY = LgCommand("play", "Play", 0x20DF0DF2L)
    val PAUSE = LgCommand("pause", "Pause", 0x20DF5DA2L)
    val STOP = LgCommand("stop", "Stop", 0x20DF8D72L)
    val REWIND = LgCommand("rewind", "Zurückspulen", 0x20DFF10EL)
    val FAST_FORWARD = LgCommand("fast_forward", "Vorspulen", 0x20DF718EL)

    val RED = LgCommand("red", "Rot", 0x20DF4EB1L)
    val GREEN = LgCommand("green", "Grün", 0x20DF8E71L)
    val YELLOW = LgCommand("yellow", "Gelb", 0x20DFC639L)
    val BLUE = LgCommand("blue", "Blau", 0x20DF8679L)

    val DIGITS = listOf(
        LgCommand("digit_0", "0", 0x20DF08F7L),
        LgCommand("digit_1", "1", 0x20DF8877L),
        LgCommand("digit_2", "2", 0x20DF48B7L),
        LgCommand("digit_3", "3", 0x20DFC837L),
        LgCommand("digit_4", "4", 0x20DF28D7L),
        LgCommand("digit_5", "5", 0x20DFA857L),
        LgCommand("digit_6", "6", 0x20DF6897L),
        LgCommand("digit_7", "7", 0x20DFE817L),
        LgCommand("digit_8", "8", 0x20DF18E7L),
        LgCommand("digit_9", "9", 0x20DF9867L),
    )
}
