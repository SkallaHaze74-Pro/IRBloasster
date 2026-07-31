package com.skallahaze.irbloasster.ir

import kotlin.math.max

object NecProtocol {
    const val FREQUENCY_HZ = 38_000

    /**
     * Encodes the common NEC hexadecimal notation (for example 20DF10EF).
     * Bytes are transmitted from most-significant to least-significant byte,
     * while every byte is transmitted least-significant bit first.
     */
    fun encode(code: UInt, repeats: Int = 1): IntArray {
        require(repeats in 1..5) { "repeats must be between 1 and 5" }
        val result = mutableListOf<Int>()

        repeat(repeats) { repeatIndex ->
            val frameStart = result.size
            result += 9_000
            result += 4_500

            for (byteShift in intArrayOf(24, 16, 8, 0)) {
                val value = ((code shr byteShift) and 0xFFu).toInt()
                repeat(8) { bitIndex ->
                    result += 560
                    result += if (((value shr bitIndex) and 1) == 1) 1_690 else 560
                }
            }

            result += 560
            if (repeatIndex < repeats - 1) {
                val frameDuration = result.subList(frameStart, result.size).sum()
                result += max(10_000, 110_000 - frameDuration)
            }
        }

        return result.toIntArray()
    }
}

object SonySircProtocol {
    const val FREQUENCY_HZ = 40_000

    enum class FrameBits(val count: Int) {
        BITS_12(12),
        BITS_15(15),
        BITS_20(20);

        companion object {
            fun from(value: Int): FrameBits = entries.firstOrNull { it.count == value } ?: BITS_12
        }
    }

    /**
     * Sony SIRC: 7 command bits, then address and optional extended bits, LSB first.
     * Three repeated frames are the most compatible choice for Sony equipment.
     */
    fun encode(
        command: Int,
        address: Int,
        bits: FrameBits = FrameBits.BITS_12,
        extended: Int = 0,
        repeats: Int = 3
    ): IntArray {
        require(command in 0..0x7F) { "command must fit in 7 bits" }
        require(repeats in 1..5) { "repeats must be between 1 and 5" }

        val addressBits = when (bits) {
            FrameBits.BITS_12 -> 5
            FrameBits.BITS_15 -> 8
            FrameBits.BITS_20 -> 5
        }
        require(address in 0 until (1 shl addressBits)) { "address does not fit selected frame" }
        if (bits == FrameBits.BITS_20) {
            require(extended in 0..0xFF) { "extended must fit in 8 bits" }
        }

        var frame = command.toLong() or (address.toLong() shl 7)
        if (bits == FrameBits.BITS_20) {
            frame = frame or (extended.toLong() shl 12)
        }

        val result = mutableListOf<Int>()
        repeat(repeats) { repeatIndex ->
            val frameStart = result.size
            result += 2_400
            result += 600

            repeat(bits.count) { bitIndex ->
                val one = ((frame shr bitIndex) and 1L) == 1L
                result += if (one) 1_200 else 600
                result += 600
            }

            if (repeatIndex < repeats - 1) {
                val frameDuration = result.subList(frameStart, result.size).sum()
                val gap = max(10_000, 45_000 - frameDuration)
                result[result.lastIndex] = result.last() + gap
            }
        }

        return result.toIntArray()
    }
}
