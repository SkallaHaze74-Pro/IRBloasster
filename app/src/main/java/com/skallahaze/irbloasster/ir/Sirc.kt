package com.skallahaze.irbloasster.ir

import kotlin.math.max

object Sirc {
    private const val START_MARK = 2_400
    private const val START_SPACE = 600
    private const val BIT_MARK = 600
    private const val ZERO_SPACE = 600
    private const val ONE_SPACE = 1_200
    private const val FRAME_PERIOD = 45_000

    /**
     * Sony SIRC frame. The 7 command bits are followed by the address bits,
     * all least-significant bit first. Three frames make a reliable key press.
     */
    fun encode(
        command: Int,
        address: Int,
        bits: Int = if (address > 31) 15 else 12,
        frames: Int = 3,
    ): IntArray {
        require(command in 0..127) { "SIRC command must fit 7 bits" }
        require(bits == 12 || bits == 15 || bits == 20) { "SIRC uses 12, 15 or 20 bits" }
        require(address >= 0) { "SIRC address must be positive" }
        require(frames >= 1) { "At least one SIRC frame is required" }

        val addressBits = bits - 7
        require(address < (1 shl addressBits)) { "Address does not fit selected SIRC frame" }

        val data = command or (address shl 7)
        val pattern = mutableListOf<Int>()

        repeat(frames) { frameIndex ->
            val frameStart = pattern.size
            pattern += START_MARK
            pattern += START_SPACE

            repeat(bits) { bitIndex ->
                pattern += BIT_MARK
                pattern += if (((data shr bitIndex) and 1) == 1) ONE_SPACE else ZERO_SPACE
            }

            if (frameIndex < frames - 1) {
                val frameDuration = pattern.subList(frameStart, pattern.size).sum()
                pattern[pattern.lastIndex] += max(10_000, FRAME_PERIOD - frameDuration)
            }
        }

        return pattern.toIntArray()
    }
}
