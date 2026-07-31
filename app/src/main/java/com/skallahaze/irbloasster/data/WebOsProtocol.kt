package com.skallahaze.irbloasster.data

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

object WebOsProtocol {
    const val DISCOVERY_TARGET = "urn:lge-com:service:webos-second-screen:1"

    const val GET_VOLUME_STATUS = "ssap://audio/getStatus"
    const val GET_VOLUME = "ssap://audio/getVolume"
    const val SET_VOLUME = "ssap://audio/setVolume"
    const val SET_MUTE = "ssap://audio/setMute"
    const val VOLUME_UP = "ssap://audio/volumeUp"
    const val VOLUME_DOWN = "ssap://audio/volumeDown"

    const val CHANNEL_UP = "ssap://tv/channelUp"
    const val CHANNEL_DOWN = "ssap://tv/channelDown"
    const val GET_CURRENT_CHANNEL = "ssap://tv/getCurrentChannel"
    const val GET_INPUTS = "ssap://tv/getExternalInputList"
    const val SWITCH_INPUT = "ssap://tv/switchInput"

    const val LIST_APPS = "ssap://com.webos.applicationManager/listApps"
    const val LIST_LAUNCH_POINTS = "ssap://com.webos.applicationManager/listLaunchPoints"
    const val GET_FOREGROUND_APP = "ssap://com.webos.applicationManager/getForegroundAppInfo"
    const val LAUNCH_APP = "ssap://system.launcher/launch"

    const val GET_SYSTEM_INFO = "ssap://system/getSystemInfo"
    const val GET_POWER_STATE = "ssap://com.webos.service.tvpower/power/getPowerState"
    const val TURN_OFF = "ssap://system/turnOff"

    const val MEDIA_PLAY = "ssap://media.controls/play"
    const val MEDIA_PAUSE = "ssap://media.controls/pause"
    const val MEDIA_STOP = "ssap://media.controls/stop"
    const val MEDIA_REWIND = "ssap://media.controls/rewind"
    const val MEDIA_FAST_FORWARD = "ssap://media.controls/fastForward"

    const val GET_POINTER_SOCKET = "ssap://com.webos.service.networkinput/getPointerInputSocket"
    const val REGISTER_KEYBOARD = "ssap://com.webos.service.ime/registerRemoteKeyboard"
    const val INSERT_TEXT = "ssap://com.webos.service.ime/insertText"
    const val DELETE_TEXT = "ssap://com.webos.service.ime/deleteCharacters"
    const val SEND_ENTER = "ssap://com.webos.service.ime/sendEnterKey"

    val permissions: List<String> = listOf(
        "LAUNCH",
        "LAUNCH_WEBAPP",
        "APP_TO_APP",
        "CONTROL_AUDIO",
        "CONTROL_INPUT_MEDIA_PLAYBACK",
        "UPDATE_FROM_REMOTE_APP",
        "CONTROL_POWER",
        "READ_INSTALLED_APPS",
        "CONTROL_DISPLAY",
        "CONTROL_INPUT_JOYSTICK",
        "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_TV",
        "READ_INPUT_DEVICE_LIST",
        "READ_NETWORK_STATE",
        "READ_TV_CHANNEL_LIST",
        "WRITE_NOTIFICATION_TOAST",
        "CONTROL_BLUETOOTH",
        "CHECK_BLUETOOTH_DEVICE",
        "CONTROL_USER_INFO",
        "CONTROL_TIMER_INFO",
        "READ_SETTINGS",
        "CONTROL_TV_SCREEN",
        "CONTROL_INPUT_TEXT",
        "CONTROL_MOUSE_AND_KEYBOARD",
        "READ_CURRENT_CHANNEL",
        "READ_RUNNING_APPS"
    )

    fun hello(id: String, appName: String): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", "hello")
        put("payload", JSONObject().apply {
            put("sdkVersion", "LivingRoomController/0.2.0")
            put("deviceModel", Build.MODEL)
            put("OSVersion", Build.VERSION.SDK_INT.toString())
            put("appId", "com.skallahaze.irbloasster")
            put("appName", appName)
            put("appRegion", java.util.Locale.getDefault().country)
        })
    }

    fun register(id: String, clientKey: String?): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", "register")
        put("payload", JSONObject().apply {
            if (!clientKey.isNullOrBlank()) {
                put("client-key", clientKey)
            }
            put("manifest", JSONObject().apply {
                put("manifestVersion", 1)
                put("permissions", JSONArray(permissions))
            })
        })
    }

    fun request(
        id: String,
        uri: String,
        payload: JSONObject? = null,
        subscribe: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", if (subscribe) "subscribe" else "request")
        put("uri", uri)
        if (payload != null) put("payload", payload)
    }
}
