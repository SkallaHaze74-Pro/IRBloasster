package com.skallahaze.irbloasster.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolsTest {
    @Test
    fun necFrameHasExpectedHeaderAndLength() {
        val pattern = Nec.encode(0x20DF10EFL)

        assertEquals(9_000, pattern[0])
        assertEquals(4_500, pattern[1])
        assertEquals(67, pattern.size)
        assertEquals(560, pattern.last())
    }

    @Test
    fun necSendsEachByteLeastSignificantBitFirst() {
        val pattern = Nec.encode(0x20DF10EFL)

        // First byte is 0x20. Its first transmitted bit is 0 and bit 5 is 1.
        assertEquals(560, pattern[3])
        assertEquals(1_690, pattern[13])
    }

    @Test
    fun sircTwelveBitUsesThreeFramesByDefault() {
        val pattern = Sirc.encode(command = 21, address = 16)

        assertEquals(2_400, pattern[0])
        assertEquals(600, pattern[1])
        assertEquals(78, pattern.size)
        assertTrue(pattern.all { it > 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun sircRejectsAddressThatDoesNotFitTwelveBits() {
        Sirc.encode(command = 1, address = 32, bits = 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sircRejectsUnsupportedBitLength() {
        Sirc.encode(command = 1, address = 1, bits = 13)
    }
}
