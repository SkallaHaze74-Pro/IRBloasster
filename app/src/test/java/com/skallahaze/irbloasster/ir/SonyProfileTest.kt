package com.skallahaze.irbloasster.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyProfileTest {
    @Test
    fun coreReceiverCommandsUseDevice16InAv1() {
        assertEquals(16, Sony_STR_DB870.POWER.addressFor(SonyCommandMode.AV1))
        assertEquals(12, Sony_STR_DB870.POWER.bitsFor(SonyCommandMode.AV1))
        assertEquals(18, Sony_STR_DB870.VOLUME_UP.command)
        assertEquals(20, Sony_STR_DB870.MUTE.command)
    }

    @Test
    fun av2Adds32AndUses15BitFrames() {
        assertEquals(48, Sony_STR_DB870.POWER.addressFor(SonyCommandMode.AV2))
        assertEquals(15, Sony_STR_DB870.POWER.bitsFor(SonyCommandMode.AV2))
        assertEquals(45, Sony_STR_DB870.TUNER_PRESET_UP.addressFor(SonyCommandMode.AV2))
        assertEquals(15, Sony_STR_DB870.TUNER_PRESET_UP.bitsFor(SonyCommandMode.AV2))
    }

    @Test
    fun modernDspAndMenuCommandsAre15BitAlreadyInAv1() {
        assertEquals(144, Sony_STR_DB870.AFD.addressFor(SonyCommandMode.AV1))
        assertEquals(15, Sony_STR_DB870.AFD.bitsFor(SonyCommandMode.AV1))
        assertEquals(144, Sony_STR_DB870.MAIN_MENU.addressFor(SonyCommandMode.AV1))
        assertEquals(15, Sony_STR_DB870.MENU_UP.bitsFor(SonyCommandMode.AV1))
        assertEquals(176, Sony_STR_DB870.MAIN_MENU.addressFor(SonyCommandMode.AV2))
    }

    @Test
    fun rmPp505GenerationInputsUseCorrectedCommands() {
        assertEquals(125, Sony_STR_DB870.INPUT_DVD_LD.command)
        assertEquals(105, Sony_STR_DB870.INPUT_TAPE_MD.command)
        assertEquals(29, Sony_STR_DB870.INPUT_AUX.command)
        assertEquals(73, Sony_STR_DB870.INPUT_MULTI_2CH_DIRECT.command)
    }

    @Test
    fun profileProducesThreeRepeatedSircFrames() {
        val pattern = Sony_STR_DB870.pattern(Sony_STR_DB870.POWER, SonyCommandMode.AV1)
        assertEquals(3, pattern.count { it == 2_400 })
        assertTrue(pattern.all { it > 0 })
    }
}
