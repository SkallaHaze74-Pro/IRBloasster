package com.skallahaze.irbloasster

/**
 * Very small NEC helper – converts an 8‑byte hex string (e.g. "20DF10EF")
 * into the carrier‑frequency pattern array required by ConsumerIrManager.transmit().
 * Works for standard 38 kHz NEC protocol (header 9 ms / 4.5 ms).
 */
object Nec {
    fun hexToPattern(hex: String): IntArray {
        require(hex.length == 8 || hex.length == 16) { "Hex string must be 8 or 16 chars" }
        val bits = hex.chunked(2)
            .joinToString(separator = "") { it.toInt(16).toString(2).padStart(8, '0') }
        val pattern = mutableListOf<Int>()
        // Leader
        pattern += 9000; pattern += 4500
        // Data bits
        bits.forEach { c ->
            pattern += 560
            pattern += if (c == '1') 1690 else 560
        }
        // Stop bit (560us high)
        pattern += 560
        return pattern.toIntArray()
    }
}
