package com.skallahaze.irbloasster.ir

object Sirc {
    fun encode15(command: UByte, address: UByte): IntArray {
        val frame = (command.toInt() or (address.toInt() shl 7))
        val pulses = mutableListOf<Int>()
        pulses += listOf(2400, 600)
        repeat(12) { i ->
            val bit = (frame shr i) and 1
            pulses += 600
            pulses += if (bit == 1) 1200 else 600
        }
        return pulses.toIntArray()
    }
}
