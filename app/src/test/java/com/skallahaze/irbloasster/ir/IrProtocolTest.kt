package com.skallahaze.irbloasster.ir

import com.skallahaze.irbloasster.model.LgIrCommand
import com.skallahaze.irbloasster.model.SonyCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolTest {
    @Test
    fun necFrameHasExpectedShape() {
        val signal = NecProtocol.encode(0x20DF10EFL)
        assertEquals(38_000, signal.carrierFrequency)
        assertEquals(67, signal.pattern.size)
        assertEquals(9_000, signal.pattern.first())
        assertTrue(signal.pattern.all { it > 0 })
    }

    @Test
    fun sonyFramesUseCorrectBitLengthsAndRepeats() {
        val twelve = SonySircProtocol.encodeRaw(0x10A, 12)
        val fifteen = SonySircProtocol.encodeRaw(0x540C, 15)
        val twenty = SonySircProtocol.encodeRaw(0xABCDE, 20)

        assertEquals(26, twelve.pattern.size)
        assertEquals(32, fifteen.pattern.size)
        assertEquals(42, twenty.pattern.size)
        assertEquals(3, fifteen.repeats)
        assertEquals(40_000, fifteen.carrierFrequency)
    }

    @Test
    fun deviceProfilesContainEssentialCommands() {
        assertEquals("20DF10EF", LgOledB1IrProfile.codeHex(LgIrCommand.POWER))
        val sony = SonyHtRt3Profiles.all.first()
        assertNotNull(sony.signal(SonyCommand.POWER))
        assertNotNull(sony.signal(SonyCommand.VOLUME_UP))
        assertNotNull(sony.signal(SonyCommand.MUTE))
    }
}
