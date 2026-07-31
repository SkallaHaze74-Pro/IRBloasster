package com.skallahaze.irbloasster.webos

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skallahaze.irbloasster.data.SettingsRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

enum class WebOsConnection {
    DISCONNECTED,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR,
}

data class WebOsState(
    val connection: WebOsConnection = WebOsConnection.DISCONNECTED,
    val message: String = "Nicht verbunden",
    val host: String = "",
    val volume: Int? = null,
    val muted: Boolean? = null,
    val currentApp: String? = null,
    val modelName: String? = null,
    val powerState: String? = null,
    val lastResponse: String? = null,
)

class WebOsClient(
    private val settings: SettingsRepository,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestCounter = AtomicInteger(0)
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private var pendingPointerButton: String? = null
    private var manualDisconnect = false
    @Volatile private var registered = false

    var state by mutableStateOf(WebOsState(host = settings.webOsHost))
        private set

    fun connect(rawHost: String = settings.webOsHost) {
        val host = normalizeHost(rawHost)
        if (host.isBlank()) {
            updateState {
                it.copy(
                    connection = WebOsConnection.ERROR,
                    message = "Bitte die IP-Adresse des LG TVs eintragen",
                )
            }
            return
        }

        settings.setWebOsHost(host)
        disconnectInternal(updateUi = false)
        manualDisconnect = false
        updateState {
            it.copy(
                connection = WebOsConnection.CONNECTING,
                message = "Verbindung zu $host wird aufgebaut …",
                host = host,
            )
        }

        val request = Request.Builder()
            .url("ws://$host:3000")
            .build()

        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                updateState {
                    it.copy(
                        connection = WebOsConnection.PAIRING,
                        message = "Am Fernseher die Verbindung bestätigen",
                    )
                }
                webSocket.send(registrationMessage().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!manualDisconnect) {
                    updateState {
                        it.copy(
                            connection = WebOsConnection.DISCONNECTED,
                            message = "TV-Verbindung wurde beendet",
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                if (!manualDisconnect) {
                    updateState {
                        it.copy(
                            connection = WebOsConnection.ERROR,
                            message = throwable.message ?: "TV nicht erreichbar",
                        )
                    }
                }
            }
        })
    }

    fun disconnect() {
        manualDisconnect = true
        disconnectInternal(updateUi = true)
    }

    fun close() {
        manualDisconnect = true
        disconnectInternal(updateUi = false)
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    fun forgetPairing() {
        settings.clearWebOsPairing()
        disconnect()
        updateState { it.copy(message = "Kopplung gelöscht") }
    }

    fun refreshStatus() {
        request("ssap://audio/getVolume", subscribe = true)
        request("ssap://audio/getMute", subscribe = true)
        request("ssap://com.webos.applicationManager/getForegroundAppInfo", subscribe = true)
        request("ssap://system/getSystemInfo")
        request("ssap://com.webos.service.tvpower/power/getPowerState", subscribe = true)
    }

    fun volumeUp(): Boolean = request("ssap://audio/volumeUp") != null
    fun volumeDown(): Boolean = request("ssap://audio/volumeDown") != null

    fun toggleMute(): Boolean {
        val muted = state.muted ?: false
        return request("ssap://audio/setMute", JSONObject().put("mute", !muted)) != null
    }

    fun powerOff(): Boolean = request("ssap://system/turnOff") != null
    fun channelUp(): Boolean = request("ssap://tv/channelUp") != null
    fun channelDown(): Boolean = request("ssap://tv/channelDown") != null
    fun play(): Boolean = request("ssap://media.controls/play") != null
    fun pause(): Boolean = request("ssap://media.controls/pause") != null
    fun stop(): Boolean = request("ssap://media.controls/stop") != null
    fun rewind(): Boolean = request("ssap://media.controls/rewind") != null
    fun fastForward(): Boolean = request("ssap://media.controls/fastForward") != null

    fun switchInput(inputId: String): Boolean =
        request("ssap://tv/switchInput", JSONObject().put("inputId", inputId)) != null

    fun launchApp(appId: String): Boolean =
        request("ssap://system.launcher/launch", JSONObject().put("id", appId)) != null

    fun insertText(text: String): Boolean =
        request(
            "ssap://com.webos.service.ime/insertText",
            JSONObject().put("text", text).put("replace", 0),
        ) != null

    fun sendEnter(): Boolean = request("ssap://com.webos.service.ime/sendEnterKey") != null

    fun sendCustom(uri: String, payloadJson: String = ""): Boolean {
        val cleanUri = uri.trim()
        if (!cleanUri.startsWith("ssap://")) return false
        val payload = payloadJson.trim().takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull() ?: return false
        }
        return request(cleanUri, payload) != null
    }

    fun deleteCharacters(count: Int = 1): Boolean =
        request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", count)) != null

    fun sendButton(name: String): Boolean {
        val pointer = pointerSocket
        if (pointer != null) {
            return pointer.send("type:button\nname:$name\n\n")
        }

        pendingPointerButton = name
        return request("ssap://com.webos.service.networkinput/getPointerInputSocket") != null
    }

    fun movePointer(dx: Int, dy: Int, down: Boolean = false): Boolean =
        pointerSocket?.send(
            "type:move\ndx:$dx\ndy:$dy\ndown:${if (down) 1 else 0}\n\n",
        ) == true

    fun clickPointer(): Boolean = pointerSocket?.send("type:click\n\n") == true

    fun scrollPointer(dy: Int): Boolean =
        pointerSocket?.send("type:scroll\ndx:0\ndy:$dy\n\n") == true

    private fun request(
        uri: String,
        payload: JSONObject? = null,
        subscribe: Boolean = false,
    ): String? {
        val webSocket = socket ?: return null
        if (!registered) return null

        val id = "smartir_${requestCounter.incrementAndGet()}"
        val message = JSONObject()
            .put("id", id)
            .put("type", if (subscribe) "subscribe" else "request")
            .put("uri", uri)

        if (payload != null) message.put("payload", payload)
        return if (webSocket.send(message.toString())) id else null
    }

    private fun handleMessage(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        updateState { it.copy(lastResponse = text.take(1_500)) }
        val type = message.optString("type")
        val payload = message.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "registered" -> {
                registered = true
                payload.optString("client-key").takeIf { it.isNotBlank() }?.let {
                    settings.setWebOsClientKey(it)
                }
                updateState {
                    it.copy(
                        connection = WebOsConnection.CONNECTED,
                        message = "LG TV verbunden",
                    )
                }
                refreshStatus()
            }

            "response" -> handleResponse(payload)

            "error" -> {
                val errorText = message.optString("error", "webOS-Befehl fehlgeschlagen")
                updateState { current ->
                    current.copy(message = errorText)
                }
            }
        }
    }

    private fun handleResponse(payload: JSONObject) {
        val socketPath = payload.optString("socketPath")
        if (socketPath.isNotBlank()) {
            openPointerSocket(socketPath)
        }

        updateState { current ->
            current.copy(
                volume = payload.intOrNull("volume") ?: current.volume,
                muted = payload.booleanOrNull("muted")
                    ?: payload.booleanOrNull("mute")
                    ?: current.muted,
                currentApp = payload.stringOrNull("appId") ?: current.currentApp,
                modelName = payload.stringOrNull("modelName")
                    ?: payload.stringOrNull("model_name")
                    ?: current.modelName,
                powerState = payload.stringOrNull("state") ?: current.powerState,
            )
        }
    }

    private fun openPointerSocket(socketPath: String) {
        pointerSocket?.close(1000, "Neue Pointer-Verbindung")
        val request = Request.Builder().url(socketPath).build()
        pointerSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                pendingPointerButton?.let { button ->
                    webSocket.send("type:button\nname:$button\n\n")
                    pendingPointerButton = null
                }
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                pointerSocket = null
                updateState { it.copy(message = throwable.message ?: "Magic-Remote-Verbindung fehlgeschlagen") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                pointerSocket = null
            }
        })
    }

    private fun registrationMessage(): JSONObject {
        val permissions = listOf(
            "LAUNCH",
            "LAUNCH_WEBAPP",
            "APP_TO_APP",
            "CLOSE",
            "CONTROL_AUDIO",
            "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_PLAYBACK",
            "CONTROL_INPUT_TV",
            "CONTROL_POWER",
            "READ_APP_STATUS",
            "READ_CURRENT_CHANNEL",
            "READ_INPUT_DEVICE_LIST",
            "READ_NETWORK_STATE",
            "READ_RUNNING_APPS",
            "READ_TV_CHANNEL_LIST",
            "WRITE_NOTIFICATION_TOAST",
            "READ_POWER_STATE",
        )
        val permissionArray = JSONArray().apply {
            permissions.forEach { put(it) }
        }

        val localizedAppNames = JSONObject().put("", "SmartIR")
        val localizedVendorNames = JSONObject().put("", "SkallaHaze")
        val signed = JSONObject()
            .put("created", "20260731")
            .put("appId", "com.skallahaze.irbloasster")
            .put("vendorId", "com.skallahaze")
            .put("localizedAppNames", localizedAppNames)
            .put("localizedVendorNames", localizedVendorNames)
            .put("permissions", permissionArray)

        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.0.0")
            .put("signed", signed)
            .put("permissions", permissionArray)

        val payload = JSONObject()
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)

        settings.getWebOsClientKey().takeIf { it.isNotBlank() }?.let {
            payload.put("client-key", it)
        }

        return JSONObject()
            .put("id", "register_0")
            .put("type", "register")
            .put("payload", payload)
    }

    private fun disconnectInternal(updateUi: Boolean) {
        registered = false
        pointerSocket?.close(1000, "SmartIR beendet")
        pointerSocket = null
        socket?.close(1000, "SmartIR beendet")
        socket = null
        pendingPointerButton = null

        if (updateUi) {
            updateState {
                it.copy(
                    connection = WebOsConnection.DISCONNECTED,
                    message = "Nicht verbunden",
                )
            }
        }
    }

    private fun updateState(transform: (WebOsState) -> WebOsState) {
        mainHandler.post {
            state = transform(state)
        }
    }

    private fun normalizeHost(rawHost: String): String {
        var host = rawHost.trim()
            .removePrefix("ws://")
            .removePrefix("wss://")
            .substringBefore('/')

        host = host.removeSuffix(":3000").removeSuffix(":3001")
        return host
    }

    private fun JSONObject.intOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.booleanOrNull(name: String): Boolean? =
        if (has(name) && !isNull(name)) optBoolean(name) else null

    private fun JSONObject.stringOrNull(name: String): String? =
        optString(name).takeIf { it.isNotBlank() }
}
