package com.skallahaze.irbloasster.ir

object SonySircEncoder {
    private const val HEADER_MARK = 2_400
    private const val SPACE = 600
    private const val ZERO_MARK = 600
    private const val ONE_MARK = 1_200

    fun encode(
        command: Int,
        address: Int,
        bits: Int = 12,
        extended: Int = 0
    ): IntArray {
        require(bits == 12 || bits == 15 || bits == 20) { "SIRC supports 12, 15 or 20 bits" }
        require(command in 0..0x7F) { "SIRC command must fit in 7 bits" }

        val addressBits = if (bits == 15) 8 else 5
        require(address in 0 until (1 shl addressBits)) { "Address does not fit selected SIRC length" }
        if (bits == 20) require(extended in 0..0xFF) { "Extended value must fit in 8 bits" }

        val frame = command or (address shl 7) or if (bits == 20) (extended shl 12) else 0
        val pattern = ArrayList<Int>(2 + bits * 2)
        pattern += HEADER_MARK
        pattern += SPACE
        repeat(bits) { bitIndex ->
            pattern += if (((frame shr bitIndex) and 1) == 1) ONE_MARK else ZERO_MARK
            pattern += SPACE
        }
        return pattern.toIntArray()
    }
}
