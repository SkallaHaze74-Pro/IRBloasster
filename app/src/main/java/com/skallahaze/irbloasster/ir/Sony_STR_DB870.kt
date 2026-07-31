package com.skallahaze.irbloasster.ir

object Sony_STR_DB870 {
    const val FREQ = 40000
    val POWER_ON = Sirc.encode15(0x0A.toUByte(), 0x12.toUByte()) // example
    val POWER_OFF = Sirc.encode15(0x0A.toUByte(), 0x12.toUByte())
}
