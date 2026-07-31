package com.skallahaze.irbloasster.ir

object Nec {
    fun encode(code: UInt): IntArray {
        val pulses = mutableListOf<Int>()
        fun add(mark: Int, space: Int) { pulses += mark; pulses += space }
        add(9000, 4500)
        repeat(32) { i ->
            val bit = (code shr (31 - i)) and 1u
            add(560, if (bit == 1u) 1690 else 560)
        }
        add(560, 0)
        return pulses.toIntArray()
    }
}
