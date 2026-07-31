package com.skallahaze.irbloasster.ir

object LG_OLED55B1 {
    const val FREQ = 38000
    val POWER_ON = Nec.encode(0x20DF23DCu)
    val POWER_OFF = Nec.encode(0x20DFA35Cu)
    val VOLUME_UP = Nec.encode(0x20DF40BFu)
    val VOLUME_DOWN = Nec.encode(0x20DFC03Fu)
}
