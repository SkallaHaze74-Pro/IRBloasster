package com.skallahaze.irbloasster.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LgTvProfileTest {
    @Test
    fun exactPhotographedProductVariantIsPreserved() {
        assertEquals("OLED55B19LA", LG_OLED55B1.MODEL)
        assertEquals("OLED55B19LA.DEUQJP", LG_OLED55B1.PRODUCT_CODE)
        assertEquals("B1", LG_OLED55B1.SERIES)
        assertEquals("09/2021", LG_OLED55B1.MANUFACTURED)
        assertEquals("Poland", LG_OLED55B1.ASSEMBLED_IN)
    }

    @Test
    fun hdmiThreeIsEarCAndHdmiThreeAndFourAre120HzHdmi21() {
        val hdmi3 = LG_OLED55B1.HDMI_PORTS.single { it.number == 3 }
        val hdmi4 = LG_OLED55B1.HDMI_PORTS.single { it.number == 4 }

        assertEquals("HDMI_3", hdmi3.inputId)
        assertTrue(hdmi3.eArc)
        assertTrue(hdmi3.hdmi21)
        assertEquals(120, hdmi3.maxRefreshHz)

        assertEquals("HDMI_4", hdmi4.inputId)
        assertFalse(hdmi4.eArc)
        assertTrue(hdmi4.hdmi21)
        assertEquals(120, hdmi4.maxRefreshHz)
    }

    @Test
    fun profileContainsFourUniqueWebOsHdmiIds() {
        assertEquals(4, LG_OLED55B1.HDMI_PORTS.size)
        assertEquals(4, LG_OLED55B1.HDMI_PORTS.map { it.inputId }.toSet().size)
        assertEquals(2, LG_OLED55B1.HDMI_PORTS.count { it.hdmi21 })
        assertEquals(3, LG_OLED55B1.EARC_HDMI_PORT)
    }

    @Test
    fun capabilityFlagsMatchOfficialB1Profile() {
        assertEquals(120, LG_OLED55B1.NATIVE_REFRESH_HZ)
        assertEquals("webOS 6.0", LG_OLED55B1.WEB_OS_VERSION)
        assertTrue(LG_OLED55B1.SUPPORTS_VRR)
        assertTrue(LG_OLED55B1.SUPPORTS_ALLM)
        assertTrue(LG_OLED55B1.SUPPORTS_GSYNC)
        assertTrue(LG_OLED55B1.SUPPORTS_FREESYNC)
        assertTrue(LG_OLED55B1.SUPPORTS_HGIG)
        assertTrue(LG_OLED55B1.SUPPORTS_WAKE_ON_WIFI)
    }
}
