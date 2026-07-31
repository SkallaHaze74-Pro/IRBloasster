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

    @Test
    fun sircEncodesDataAsPulseWidthAndLsbFirst() {
        val pattern = Sirc.encode(command = 21, address = 16, bits = 12, frames = 1)

        assertEquals(2_400, pattern[0])
        assertEquals(600, pattern[1])
        assertEquals(1_200, pattern[2]) // command bit 0 = 1
        assertEquals(600, pattern[3])   // fixed inter-bit space
        assertEquals(600, pattern[4])   // command bit 1 = 0
        assertEquals(600, pattern[5])
        assertEquals(26, pattern.size)
    }

    @Test
    fun sircRepeatsFramesOnFortyFiveMillisecondPeriod() {
        val pattern = Sirc.encode(command = 0, address = 0, bits = 12, frames = 2)

        assertEquals(52, pattern.size)
        assertEquals(45_000, pattern.take(26).sum())
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
