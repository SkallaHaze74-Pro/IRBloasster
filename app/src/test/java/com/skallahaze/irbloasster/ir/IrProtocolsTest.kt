package com.skallahaze.irbloasster.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolsTest {
    @Test
    fun necFrameHasExpectedHeaderAndTrailer() {
        val pattern = NecProtocol.encode(0x20DF10EFu)
        assertEquals(9_000, pattern[0])
        assertEquals(4_500, pattern[1])
        assertEquals(67, pattern.size)
        assertEquals(560, pattern.last())
    }

    @Test
    fun sircTwelveBitUsesThreeFramesByDefault() {
        val pattern = SonySircProtocol.encode(command = 21, address = 16)
        assertEquals(2_400, pattern[0])
        assertEquals(600, pattern[1])
        assertTrue(pattern.size > 70)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sircRejectsOversizedAddress() {
        SonySircProtocol.encode(command = 1, address = 32)
    }
}
