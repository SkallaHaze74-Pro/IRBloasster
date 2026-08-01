package com.skallahaze.irbloasster.webos

import org.json.JSONArray
import org.json.JSONObject

/**
 * Permission profile requested from the user's own LG webOS TV.
 *
 * Profile 2 fixes the 401 regression seen after a fresh pairing by explicitly
 * requesting READ_POWER_STATE. It also requests the text and pointer permissions
 * used by the on-screen keyboard and Magic-Remote socket.
 */
internal object WebOsRegistrationProfile {
    const val PROFILE_VERSION = 2

    val permissions = listOf(
        "LAUNCH",
        "LAUNCH_WEBAPP",
        "APP_TO_APP",
        "CLOSE",
        "CONTROL_AUDIO",
        "CONTROL_DISPLAY",
        "CONTROL_INPUT_JOYSTICK",
        "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_MEDIA_PLAYBACK",
        "CONTROL_INPUT_TEXT",
        "CONTROL_INPUT_TV",
        "CONTROL_MOUSE_AND_KEYBOARD",
        "CONTROL_POWER",
        "READ_APP_STATUS",
        "READ_COUNTRY_INFO",
        "READ_CURRENT_CHANNEL",
        "READ_INPUT_DEVICE_LIST",
        "READ_INSTALLED_APPS",
        "READ_NETWORK_STATE",
        "READ_POWER_STATE",
        "READ_RUNNING_APPS",
        "READ_TV_CHANNEL_LIST",
        "WRITE_NOTIFICATION_TOAST",
    )

    fun registrationMessage(
        id: String,
        appVersion: String,
        clientKey: String,
        forcePairing: Boolean,
    ): JSONObject {
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", appVersion)
            .put("permissions", JSONArray().apply { permissions.forEach(::put) })

        val payload = JSONObject()
            .put("pairingType", "PROMPT")
            .put("forcePairing", forcePairing)
            .put("manifest", manifest)

        if (clientKey.isNotBlank()) payload.put("client-key", clientKey)

        return JSONObject()
            .put("id", id)
            .put("type", "register")
            .put("payload", payload)
    }
}
