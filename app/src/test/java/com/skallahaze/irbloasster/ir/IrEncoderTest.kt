package com.skallahaze.irbloasster.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IrEncoderTest {
    @Test
    fun nec_hasExpectedHeaderAndLength() {
        val pattern = NecEncoder.encode(0x20DF10EFL)
        assertEquals(67, pattern.size)
        assertEquals(9_000, pattern[0])
        assertEquals(4_500, pattern[1])
        assertEquals(560, pattern.last())
    }

    @Test
    fun nec_transmitsFirstDisplayedByteLeastSignificantBitFirst() {
        val pattern = NecEncoder.encode(0x20DF10EFL)
        val firstByteSpaces = IntArray(8) { bit -> pattern[3 + bit * 2] }
        assertArrayEquals(
            intArrayOf(560, 560, 560, 560, 560, 1_690, 560, 560),
            firstByteSpaces
        )
    }

    @Test
    fun sirc_supportsAllFrameLengths() {
        assertEquals(26, SonySircEncoder.encode(21, 16, 12).size)
        assertEquals(32, SonySircEncoder.encode(21, 16, 15).size)
        assertEquals(42, SonySircEncoder.encode(21, 16, 20, 1).size)
    }

    @Test
    fun sirc_rejectsUnsupportedLength() {
        assertThrows(IllegalArgumentException::class.java) {
            SonySircEncoder.encode(21, 16, 13)
        }
    }
}
