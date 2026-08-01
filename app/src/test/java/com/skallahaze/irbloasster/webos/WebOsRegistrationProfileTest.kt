package com.skallahaze.irbloasster.webos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebOsRegistrationProfileTest {
    @Test
    fun profileRequestsPermissionsUsedBySmartIr() {
        assertTrue(WebOsRegistrationProfile.permissions.contains("READ_POWER_STATE"))
        assertTrue(WebOsRegistrationProfile.permissions.contains("CONTROL_AUDIO"))
        assertTrue(WebOsRegistrationProfile.permissions.contains("READ_INSTALLED_APPS"))
        assertTrue(WebOsRegistrationProfile.permissions.contains("READ_INPUT_DEVICE_LIST"))
        assertTrue(WebOsRegistrationProfile.permissions.contains("CONTROL_INPUT_TEXT"))
        assertTrue(WebOsRegistrationProfile.permissions.contains("CONTROL_MOUSE_AND_KEYBOARD"))
    }

    @Test
    fun blankKeyCreatesPromptRegistrationWithoutSecret() {
        val message = WebOsRegistrationProfile.registrationMessage(
            id = "register_1",
            appVersion = "1.1.7",
            clientKey = "",
            forcePairing = true,
        )
        val payload = message.getJSONObject("payload")

        assertEquals("register", message.getString("type"))
        assertEquals("PROMPT", payload.getString("pairingType"))
        assertTrue(payload.getBoolean("forcePairing"))
        assertFalse(payload.has("client-key"))
        assertTrue(
            payload.getJSONObject("manifest")
                .getJSONArray("permissions")
                .toString()
                .contains("READ_POWER_STATE"),
        )
    }

    @Test
    fun existingKeyIsIncludedForReconnect() {
        val message = WebOsRegistrationProfile.registrationMessage(
            id = "register_2",
            appVersion = "1.1.7",
            clientKey = "local-key",
            forcePairing = false,
        )

        assertEquals(
            "local-key",
            message.getJSONObject("payload").getString("client-key"),
        )
    }
}
