package com.skallahaze.irbloasster.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JblSub125ProfileTest {
    @Test
    fun photographedSubwooferProfileIsPreserved() {
        assertEquals("SUB125", JBL_SUB125.MODEL)
        assertEquals("SCS125", JBL_SUB125.SYSTEM)
        assertEquals("AC 230 V · 50 Hz", JBL_SUB125.POWER_INPUT)
        assertEquals(160, JBL_SUB125.MAX_POWER_CONSUMPTION_W)
        assertEquals("1 A · 250 V", JBL_SUB125.FUSE)
        assertFalse(JBL_SUB125.HAS_IR_RECEIVER)
    }

    @Test
    fun smartIrOnlyOffersIndirectSonyBassControls() {
        assertTrue(JBL_SUB125.SONY_INDIRECT_CONTROLS.contains(Sony_STR_DB870.SUBWOOFER_UP))
        assertTrue(JBL_SUB125.SONY_INDIRECT_CONTROLS.contains(Sony_STR_DB870.SUBWOOFER_DOWN))
        assertTrue(JBL_SUB125.SONY_INDIRECT_CONTROLS.contains(Sony_STR_DB870.TEST_TONE))
        assertEquals(5, JBL_SUB125.SONY_INDIRECT_CONTROLS.size)
    }
}
