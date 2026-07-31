package com.skallahaze.irbloasster.ir

data class IrSignal(
    val label: String,
    val carrierFrequencyHz: Int,
    val patternMicros: IntArray,
    val repeatCount: Int = 1,
    val repeatDelayMillis: Long = 45
)
