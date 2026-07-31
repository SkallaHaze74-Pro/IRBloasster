package com.skallahaze.irbloasster.network

import com.skallahaze.irbloasster.model.TvApp
import com.skallahaze.irbloasster.model.TvInput
import com.skallahaze.irbloasster.model.WebOsConnectionStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebOsClient(
    private val host: String,
    private val initialClientKey: String?,
    private val onClientKey: (String) -> Unit,
    private val onStatus: (WebOsConnectionStatus, String) -> Unit,
    private val onLog: (String, Boolean, String) -> Unit,
    private val onVolume: (Int?, Boolean?) -> Unit,
    private val onInputs: (List<TvInput>) -> Unit,
    private val onApps: (List<TvApp>) -> Unit,
    private val onForegroundApp: (String?) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val ids = AtomicInteger(1)
    private val oneShotCallbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()
    private val subscriptionCallbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()
    private val pointerQueue = ArrayDeque<String>()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var pointerSocket: WebSocket? = null

    @Volatile
    private var pointerConnecting = false

    @Volatile
    private var registered = false

    fun connect() {
        if (host.isBlank() || host.any { it.isWhitespace() || it == '/' }) {
            onStatus(WebOsConnectionStatus.ERROR, "Ungültige TV-Adresse")
            return
        }
        disconnect(silent = true)
        registered = false
        onStatus(WebOsConnectionStatus.CONNECTING, "Verbindung zu $host …")
        val request = Request.Builder().url("ws://$host:3000/").build()
        webSocket = client.newWebSocket(request, MainSocketListener())
    }

    fun disconnect(silent: Boolean = false) {
        registered = false
        pointerConnecting = false
        synchronized(pointerQueue) { pointerQueue.clear() }
        pointerSocket?.close(1000, "App disconnected")
        pointerSocket = null
        webSocket?.close(1000, "App disconnected")
        webSocket = null
        oneShotCallbacks.clear()
        subscriptionCallbacks.clear()
        if (!silent) onStatus(WebOsConnectionStatus.DISCONNECTED, "Nicht verbunden")
    }

    fun refresh() {
        if (!registered) return
        subscribeVolume()
        subscribeForegroundApp()
        loadInputs()
        loadApps()
    }

    fun volumeUp(): Boolean = request("ssap://audio/volumeUp")
    fun volumeDown(): Boolean = request("ssap://audio/volumeDown")
    fun channelUp(): Boolean = request("ssap://tv/channelUp")
    fun channelDown(): Boolean = request("ssap://tv/channelDown")
    fun turnOff(): Boolean = request("ssap://system/turnOff")

    fun setMute(mute: Boolean): Boolean = request(
        uri = "ssap://audio/setMute",
        payload = JSONObject().put("mute", mute)
    )

    fun switchInput(inputId: String): Boolean = request(
        uri = "ssap://tv/switchInput",
        payload = JSONObject().put("inputId", inputId)
    )

    fun launchApp(appId: String): Boolean = request(
        uri = "ssap://system.launcher/launch",
        payload = JSONObject().put("id", appId)
    )

    fun insertText(text: String): Boolean = request(
        uri = "ssap://com.webos.service.ime/insertText",
        payload = JSONObject()
            .put("text", text)
            .put("replace", 0)
    )

    fun sendRemoteButton(name: String): Boolean = sendPointerMessage(
        "type:button\nname:${name.uppercase()}\n\n"
    )

    fun pointerClick(): Boolean = sendPointerMessage("type:click\n\n")

    fun pointerMove(dx: Float, dy: Float): Boolean {
        val safeDx = dx.toInt().coerceIn(-250, 250)
        val safeDy = dy.toInt().coerceIn(-250, 250)
        if (safeDx == 0 && safeDy == 0) return true
        return sendPointerMessage("type:move\ndx:$safeDx\ndy:$safeDy\ndown:0\n\n")
    }

    fun pointerScroll(dy: Float): Boolean {
        val safeDy = dy.toInt().coerceIn(-20, 20)
        if (safeDy == 0) return true
        return sendPointerMessage("type:scroll\ndx:0\ndy:$safeDy\n\n")
    }

    private fun request(
        uri: String,
        payload: JSONObject = JSONObject(),
        subscribe: Boolean = false,
        callback: ((JSONObject) -> Unit)? = null
    ): Boolean {
        val socket = webSocket ?: return false
        if (!registered && !uri.startsWith("ssap://pairing")) return false
        val id = "livingroom_${ids.getAndIncrement()}"
        if (callback != null) {
            if (subscribe) subscriptionCallbacks[id] = callback else oneShotCallbacks[id] = callback
        }
        val message = JSONObject()
            .put("id", id)
            .put("type", if (subscribe) "subscribe" else "request")
            .put("uri", uri)
            .put("payload", payload)
        val accepted = socket.send(message.toString())
        if (!accepted) {
            oneShotCallbacks.remove(id)
            subscriptionCallbacks.remove(id)
        }
        return accepted
    }

    private fun loadInputs() {
        request("ssap://tv/getExternalInputList", callback = { payload ->
            val devices = payload.optJSONArray("devices") ?: JSONArray()
            val result = buildList {
                for (index in 0 until devices.length()) {
                    val item = devices.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("inputId") }
                    if (id.isBlank()) continue
                    val label = item.optString("label").ifBlank { id }
                    val connected = item.optBoolean("connected", false) ||
                        item.optString("connectionStatus").equals("connected", ignoreCase = true)
                    add(TvInput(id = id, label = label, connected = connected))
                }
            }
            onInputs(result)
        })
    }

    private fun loadApps() {
        request("ssap://com.webos.applicationManager/listApps", callback = { payload ->
            val apps = payload.optJSONArray("apps") ?: JSONArray()
            val result = buildList {
                for (index in 0 until apps.length()) {
                    val item = apps.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title").ifBlank { id }
                    if (id.isBlank() || title.isBlank()) continue
                    add(TvApp(id = id, title = title, visible = item.optBoolean("visible", true)))
                }
            }.filter { it.visible }
                .sortedBy { it.title.lowercase() }
            onApps(result)
        })
    }

    private fun subscribeVolume() {
        request(
            uri = "ssap://audio/getStatus",
            subscribe = true,
            callback = { payload ->
                val nested = payload.optJSONObject("volumeStatus")
                val source = nested ?: payload
                val volume = when {
                    source.has("volume") -> source.optInt("volume")
                    payload.has("volume") -> payload.optInt("volume")
                    else -> null
                }
                val mute = when {
                    source.has("mute") -> source.optBoolean("mute")
                    payload.has("mute") -> payload.optBoolean("mute")
                    else -> null
                }
                onVolume(volume, mute)
            }
        )
    }

    private fun subscribeForegroundApp() {
        request(
            uri = "ssap://com.webos.applicationManager/getForegroundAppInfo",
            subscribe = true,
            callback = { payload ->
                onForegroundApp(payload.optString("appId").takeIf { it.isNotBlank() })
            }
        )
    }

    private fun sendRegistration(socket: WebSocket) {
        val permissions = JSONArray(
            listOf(
                "LAUNCH",
                "LAUNCH_WEBAPP",
                "APP_TO_APP",
                "CLOSE",
                "TEST_OPEN",
                "TEST_PROTECTED",
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
                "READ_POWER_STATE",
                "WRITE_NOTIFICATION_TOAST"
            )
        )
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.0.0")
            .put("permissions", permissions)
            .put("localizedAppNames", JSONObject().put("", "Living Room Controller"))
            .put("localizedVendorNames", JSONObject().put("", "SkallaHaze"))
        val payload = JSONObject()
            .put("pairingType", "PROMPT")
            .put("forcePairing", false)
            .put("manifest", manifest)
        initialClientKey?.takeIf { it.isNotBlank() }?.let { payload.put("client-key", it) }

        val message = JSONObject()
            .put("id", "register_0")
            .put("type", "register")
            .put("payload", payload)
        socket.send(message.toString())
        onStatus(WebOsConnectionStatus.PAIRING, "Kopplung am Fernseher bestätigen")
    }

    private fun handleMessage(text: String) {
        val message = runCatching { JSONObject(text) }.getOrElse {
            onLog("webOS Antwort", false, "Ungültiges JSON")
            return
        }
        val type = message.optString("type")
        val id = message.optString("id")
        val payload = message.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "registered" -> {
                registered = true
                payload.optString("client-key")
                    .takeIf { it.isNotBlank() }
                    ?.let(onClientKey)
                onStatus(WebOsConnectionStatus.CONNECTED, "LG webOS verbunden")
                onLog("webOS Pairing", true, "Client registriert")
                refresh()
            }

            "response" -> {
                val callback = oneShotCallbacks.remove(id) ?: subscriptionCallbacks[id]
                callback?.invoke(payload)
                if (payload.has("returnValue") && !payload.optBoolean("returnValue", true)) {
                    onLog("webOS Antwort", false, payload.toString())
                }
            }

            "error" -> {
                oneShotCallbacks.remove(id)
                val error = message.optString("error").ifBlank { payload.toString() }
                onLog("webOS Fehler", false, error)
                if (!registered) onStatus(WebOsConnectionStatus.ERROR, error)
            }
        }
    }

    private fun sendPointerMessage(message: String): Boolean {
        pointerSocket?.let { return it.send(message) }
        synchronized(pointerQueue) {
            if (pointerQueue.size >= 24) pointerQueue.removeFirst()
            pointerQueue.addLast(message)
        }
        ensurePointerSocket()
        return true
    }

    private fun ensurePointerSocket() {
        if (pointerSocket != null || pointerConnecting || !registered) return
        pointerConnecting = true
        val requested = request(
            uri = "ssap://com.webos.service.networkinput/getPointerInputSocket",
            callback = pointerCallback@{ payload ->
                val socketPath = payload.optString("socketPath")
                if (socketPath.isBlank()) {
                    pointerConnecting = false
                    onLog("Magic Remote", false, "Kein Pointer-Socket erhalten")
                    return@pointerCallback
                }
                val pointerRequest = runCatching { Request.Builder().url(socketPath).build() }
                    .getOrElse {
                        pointerConnecting = false
                        onLog("Magic Remote", false, it.message.orEmpty())
                        return@pointerCallback
                    }
                client.newWebSocket(pointerRequest, PointerSocketListener())
            }
        )
        if (!requested) pointerConnecting = false
    }

    private inner class MainSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            this@WebOsClient.webSocket = webSocket
            sendRegistration(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            registered = false
            this@WebOsClient.webSocket = null
            onStatus(WebOsConnectionStatus.DISCONNECTED, "Verbindung beendet")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            registered = false
            this@WebOsClient.webSocket = null
            onStatus(WebOsConnectionStatus.ERROR, t.message ?: "webOS-Verbindung fehlgeschlagen")
            onLog("webOS Verbindung", false, t.message.orEmpty())
        }
    }

    private inner class PointerSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            pointerSocket = webSocket
            pointerConnecting = false
            val pending = mutableListOf<String>()
            synchronized(pointerQueue) {
                while (pointerQueue.isNotEmpty()) pending += pointerQueue.removeFirst()
            }
            pending.forEach { message -> webSocket.send(message) }
            onLog("Magic Remote", true, "Pointer-Socket verbunden")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            pointerSocket = null
            pointerConnecting = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            pointerSocket = null
            pointerConnecting = false
            onLog("Magic Remote", false, t.message.orEmpty())
        }
    }
}
