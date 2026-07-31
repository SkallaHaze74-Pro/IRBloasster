package com.skallahaze.irbloasster.ir

object NecEncoder {
    private const val HEADER_MARK = 9_000
    private const val HEADER_SPACE = 4_500
    private const val BIT_MARK = 560
    private const val ZERO_SPACE = 560
    private const val ONE_SPACE = 1_690

    /**
     * Encodes the commonly written NEC byte sequence (for example 20 DF 10 EF).
     * Each byte is transmitted in display order, least-significant bit first.
     */
    fun encode(code: Long): IntArray {
        require(code in 0..0xFFFF_FFFFL) { "NEC code must be 32 bit" }
        val pattern = ArrayList<Int>(67)
        pattern += HEADER_MARK
        pattern += HEADER_SPACE

        for (byteShift in intArrayOf(24, 16, 8, 0)) {
            val value = ((code shr byteShift) and 0xFF).toInt()
            repeat(8) { bitIndex ->
                pattern += BIT_MARK
                pattern += if (((value shr bitIndex) and 1) == 1) ONE_SPACE else ZERO_SPACE
            }
        }
        pattern += BIT_MARK
        return pattern.toIntArray()
    }
}
