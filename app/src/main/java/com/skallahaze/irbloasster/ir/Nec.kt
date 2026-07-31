package com.skallahaze.irbloasster.ir

object Nec {
    private const val HEADER_MARK = 9_000
    private const val HEADER_SPACE = 4_500
    private const val BIT_MARK = 560
    private const val ZERO_SPACE = 560
    private const val ONE_SPACE = 1_690
    private const val FRAME_GAP = 40_000

    /**
     * Encodes a conventional 32-bit NEC value such as 0x20DF10EF.
     * NEC sends every byte least-significant bit first.
     */
    fun encode(code: Long, repeatFrames: Int = 0): IntArray {
        require(code in 0..0xFFFF_FFFFL) { "NEC code must be 32-bit" }
        require(repeatFrames >= 0) { "repeatFrames must be positive" }

        val pattern = mutableListOf<Int>()

        fun appendFullFrame() {
            pattern += HEADER_MARK
            pattern += HEADER_SPACE

            for (byteShift in intArrayOf(24, 16, 8, 0)) {
                val byte = ((code shr byteShift) and 0xFF).toInt()
                repeat(8) { bitIndex ->
                    pattern += BIT_MARK
                    pattern += if (((byte shr bitIndex) and 1) == 1) ONE_SPACE else ZERO_SPACE
                }
            }

            pattern += BIT_MARK
        }

        appendFullFrame()
        repeat(repeatFrames) {
            pattern += FRAME_GAP
            appendFullFrame()
        }

        return pattern.toIntArray()
    }
}
