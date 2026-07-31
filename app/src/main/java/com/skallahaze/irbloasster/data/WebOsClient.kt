package com.skallahaze.irbloasster.data

import android.content.Context
import com.skallahaze.irbloasster.model.TvApp
import com.skallahaze.irbloasster.model.TvInput
import com.skallahaze.irbloasster.model.TvStatus
import com.skallahaze.irbloasster.model.WebOsConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebOsClient(
    context: Context,
    private val preferences: SecurePreferences,
    private val log: DiagnosticsLog
) {
    private data class PendingRequest(
        val uri: String,
        val subscription: Boolean,
        val callback: (JSONObject) -> Unit
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requestCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()

    private val clearClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val secureClient = LocalTls.localSelfSignedClient(
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
    )

    private val pointerClient = WebOsPointerClient(log)

    private val _connectionState = MutableStateFlow(WebOsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebOsConnectionState> = _connectionState.asStateFlow()

    private val _status = MutableStateFlow(TvStatus())
    val status: StateFlow<TvStatus> = _status.asStateFlow()

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    private val _inputs = MutableStateFlow<List<TvInput>>(emptyList())
    val inputs: StateFlow<List<TvInput>> = _inputs.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var socket: WebSocket? = null
    private var currentIp: String? = null
    private var connectionGeneration: Int = 0
    private var activeSecureConnection = false
    private var retriedWithoutClientKey = false
    private var pendingPointerAction: (() -> Unit)? = null

    init {
        scope.launch {
            pointerClient.connected.collect { connected ->
                _status.value = _status.value.copy(pointerConnected = connected)
                if (connected) {
                    pendingPointerAction?.invoke()
                    pendingPointerAction = null
                }
            }
        }
    }

    fun connect(ipAddress: String) {
        val normalizedIp = ipAddress.trim()
        if (normalizedIp.isBlank()) {
            setError("No TV IP address configured")
            return
        }

        connectionGeneration += 1
        val generation = connectionGeneration
        closeSocketOnly()
        pendingRequests.clear()
        retriedWithoutClientKey = false
        currentIp = normalizedIp
        preferences.putString(SecurePreferences.KEY_TV_IP, normalizedIp)
        _lastError.value = null
        _connectionState.value = WebOsConnectionState.CONNECTING
        log.info("webOS", "Connecting to $normalizedIp")
        openSocket(normalizedIp, secure = false, generation = generation)
    }

    fun disconnect() {
        connectionGeneration += 1
        pointerClient.disconnect()
        closeSocketOnly()
        pendingRequests.clear()
        _connectionState.value = WebOsConnectionState.DISCONNECTED
        log.info("webOS", "Disconnected")
    }

    fun close() {
        disconnect()
        scope.cancel()
        clearClient.dispatcher.executorService.shutdown()
        secureClient.dispatcher.executorService.shutdown()
    }

    fun refresh() {
        if (!isConnected()) return
        subscribeStatus()
        loadInputs()
        loadApps()
        loadSystemInfo()
        requestPointerSocket()
    }

    fun volumeUp() = sendRequest(WebOsProtocol.VOLUME_UP)

    fun volumeDown() = sendRequest(WebOsProtocol.VOLUME_DOWN)

    fun setVolume(volume: Int) = sendRequest(
        WebOsProtocol.SET_VOLUME,
        JSONObject().put("volume", volume.coerceIn(0, 100))
    )

    fun toggleMute() {
        val newValue = !(_status.value.muted ?: false)
        sendRequest(WebOsProtocol.SET_MUTE, JSONObject().put("mute", newValue))
    }

    fun channelUp() = sendRequest(WebOsProtocol.CHANNEL_UP)

    fun channelDown() = sendRequest(WebOsProtocol.CHANNEL_DOWN)

    fun mediaPlay() = sendRequest(WebOsProtocol.MEDIA_PLAY)

    fun mediaPause() = sendRequest(WebOsProtocol.MEDIA_PAUSE)

    fun mediaStop() = sendRequest(WebOsProtocol.MEDIA_STOP)

    fun mediaRewind() = sendRequest(WebOsProtocol.MEDIA_REWIND)

    fun mediaFastForward() = sendRequest(WebOsProtocol.MEDIA_FAST_FORWARD)

    fun powerOff() = sendRequest(WebOsProtocol.TURN_OFF)

    fun switchInput(inputId: String) = sendRequest(
        WebOsProtocol.SWITCH_INPUT,
        JSONObject().put("inputId", inputId)
    )

    fun launchApp(appId: String) = sendRequest(
        WebOsProtocol.LAUNCH_APP,
        JSONObject().put("id", appId)
    )

    fun requestPointerSocket() {
        sendRequest(WebOsProtocol.GET_POINTER_SOCKET) { payload ->
            val socketPath = payload.optString("socketPath")
            if (socketPath.isNotBlank()) {
                pointerClient.connect(
                    socketPath = socketPath,
                    client = if (socketPath.startsWith("wss://")) secureClient else clearClient
                )
            } else {
                log.warn("Pointer", "TV did not return a pointer socket path")
            }
        }
    }

    fun pointerClick() {
        if (pointerClient.connected.value) pointerClient.click()
        else {
            pendingPointerAction = pointerClient::click
            requestPointerSocket()
        }
    }

    fun pointerButton(name: String) {
        if (pointerClient.connected.value) pointerClient.button(name)
        else {
            pendingPointerAction = { pointerClient.button(name) }
            requestPointerSocket()
        }
    }

    fun pointerMove(dx: Float, dy: Float, drag: Boolean = false) {
        if (pointerClient.connected.value) pointerClient.move(dx, dy, drag)
        else requestPointerSocket()
    }

    fun pointerScroll(dx: Float, dy: Float) {
        if (pointerClient.connected.value) pointerClient.scroll(dx, dy)
        else requestPointerSocket()
    }

    fun registerKeyboard() = sendRequest(WebOsProtocol.REGISTER_KEYBOARD, subscribe = true)

    fun insertText(text: String, replace: Boolean = false) {
        if (text.isEmpty()) return
        sendRequest(
            WebOsProtocol.INSERT_TEXT,
            JSONObject()
                .put("text", text)
                .put("replace", replace)
        )
    }

    fun deleteText(count: Int = 1) = sendRequest(
        WebOsProtocol.DELETE_TEXT,
        JSONObject().put("count", count.coerceAtLeast(1))
    )

    fun sendEnter() = sendRequest(WebOsProtocol.SEND_ENTER)

    private fun openSocket(ipAddress: String, secure: Boolean, generation: Int) {
        activeSecureConnection = secure
        val url = if (secure) "wss://$ipAddress:3001" else "ws://$ipAddress:3000"
        val client = if (secure) secureClient else clearClient
        log.info("webOS", "Opening ${if (secure) "secure" else "local"} WebSocket")

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != connectionGeneration) {
                    webSocket.close(1000, "Superseded connection")
                    return
                }

                if (secure && !verifyOrStoreCertificate(ipAddress, response)) {
                    webSocket.close(1008, "TV certificate changed")
                    setError("TV certificate changed. Remove the stored TV and pair again.")
                    return
                }

                socket = webSocket
                _connectionState.value = WebOsConnectionState.CONNECTING
                val hello = WebOsProtocol.hello(nextId("hello"), appContext.getString(com.skallahaze.irbloasster.R.string.app_name))
                sendRaw(hello)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != connectionGeneration) return
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (generation != connectionGeneration) return
                log.warn("webOS", "Socket failure on $url: ${t.message ?: t.javaClass.simpleName}")
                if (!secure) {
                    _connectionState.value = WebOsConnectionState.CONNECTING
                    openSocket(ipAddress, secure = true, generation = generation)
                } else {
                    setError(t.message ?: "Unable to connect to the TV")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation != connectionGeneration) return
                pointerClient.disconnect()
                if (_connectionState.value != WebOsConnectionState.ERROR) {
                    _connectionState.value = WebOsConnectionState.DISCONNECTED
                }
                log.info("webOS", "Socket closed: $code $reason")
            }
        }

        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun handleMessage(text: String) {
        log.incoming("webOS", text)
        val message = runCatching { JSONObject(text) }.getOrElse {
            log.warn("webOS", "Ignored non-JSON response")
            return
        }

        val type = message.optString("type")
        when (type) {
            "hello" -> {
                val clientKey = preferences.getEncryptedString(SecurePreferences.KEY_TV_CLIENT_KEY)
                _connectionState.value = if (clientKey.isNullOrBlank()) {
                    WebOsConnectionState.PAIRING
                } else {
                    WebOsConnectionState.CONNECTING
                }
                sendRaw(WebOsProtocol.register(nextId("register"), clientKey))
            }

            "registered" -> {
                val clientKey = message.optJSONObject("payload")?.optString("client-key")
                if (!clientKey.isNullOrBlank()) {
                    preferences.putEncryptedString(SecurePreferences.KEY_TV_CLIENT_KEY, clientKey)
                }
                _connectionState.value = WebOsConnectionState.CONNECTED
                _lastError.value = null
                log.info("webOS", "TV paired and connected")
                refresh()
            }

            "response" -> dispatchResponse(message)

            "error" -> handleProtocolError(message)

            else -> log.info("webOS", "Unhandled message type: $type")
        }
    }

    private fun dispatchResponse(message: JSONObject) {
        val id = message.optString("id")
        val pending = pendingRequests[id]
        val payload = when (val rawPayload = message.opt("payload")) {
            is JSONObject -> rawPayload
            is JSONArray -> JSONObject().put("items", rawPayload)
            null -> JSONObject()
            else -> JSONObject().put("value", rawPayload)
        }

        pending?.callback?.invoke(payload)
        if (pending != null && !pending.subscription) {
            pendingRequests.remove(id)
        }
    }

    private fun handleProtocolError(message: JSONObject) {
        val id = message.optString("id")
        val description = message.optString("error", "Unknown webOS error")
        log.error("webOS", "$id: $description")

        if (id.startsWith("register") && !retriedWithoutClientKey) {
            val existingKey = preferences.getEncryptedString(SecurePreferences.KEY_TV_CLIENT_KEY)
            if (!existingKey.isNullOrBlank()) {
                retriedWithoutClientKey = true
                preferences.putEncryptedString(SecurePreferences.KEY_TV_CLIENT_KEY, null)
                _connectionState.value = WebOsConnectionState.PAIRING
                sendRaw(WebOsProtocol.register(nextId("register"), null))
                return
            }
        }

        _lastError.value = description
        if (id.startsWith("register")) {
            _connectionState.value = WebOsConnectionState.ERROR
        }
    }

    private fun subscribeStatus() {
        sendRequest(WebOsProtocol.GET_VOLUME_STATUS, subscribe = true) { parseVolumeStatus(it) }
        sendRequest(WebOsProtocol.GET_FOREGROUND_APP, subscribe = true) { parseForegroundApp(it) }
        sendRequest(WebOsProtocol.GET_CURRENT_CHANNEL, subscribe = true) { parseChannel(it) }
        sendRequest(WebOsProtocol.GET_POWER_STATE, subscribe = true) { parsePowerState(it) }
    }

    private fun loadInputs() {
        sendRequest(WebOsProtocol.GET_INPUTS) { payload ->
            val devices = payload.optJSONArray("devices") ?: payload.optJSONArray("inputs") ?: JSONArray()
            val parsed = buildList {
                for (index in 0 until devices.length()) {
                    val item = devices.optJSONObject(index) ?: continue
                    val id = item.optString("id", item.optString("inputId"))
                    if (id.isBlank()) continue
                    add(
                        TvInput(
                            id = id,
                            label = item.optString("label", item.optString("name", id)),
                            connected = item.optBoolean("connected", false),
                            icon = item.optString("icon").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            _inputs.value = parsed
        }
    }

    private fun loadApps() {
        sendRequest(WebOsProtocol.LIST_APPS) { payload ->
            val list = payload.optJSONArray("apps")
                ?: payload.optJSONArray("launchPoints")
                ?: payload.optJSONArray("items")
                ?: JSONArray()
            val parsed = buildList {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val id = item.optString("id", item.optString("appId"))
                    if (id.isBlank()) continue
                    add(
                        TvApp(
                            id = id,
                            title = item.optString("title", item.optString("name", id)),
                            visible = !item.has("visible") || item.optBoolean("visible", true)
                        )
                    )
                }
            }.filter { it.visible }.sortedBy { it.title.lowercase() }
            _apps.value = parsed
        }
    }

    private fun loadSystemInfo() {
        sendRequest(WebOsProtocol.GET_SYSTEM_INFO) { payload ->
            val model = payload.optString("modelName", payload.optString("model"))
            _status.value = _status.value.copy(
                systemModelName = model.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun parseVolumeStatus(payload: JSONObject) {
        val nested = payload.optJSONObject("volumeStatus")
        val volume = when {
            payload.has("volume") -> payload.optInt("volume")
            nested?.has("volume") == true -> nested.optInt("volume")
            else -> _status.value.volume
        }
        val muted = when {
            payload.has("mute") -> payload.optBoolean("mute")
            nested?.has("mute") == true -> nested.optBoolean("mute")
            else -> _status.value.muted
        }
        _status.value = _status.value.copy(volume = volume, muted = muted)
    }

    private fun parseForegroundApp(payload: JSONObject) {
        val appId = payload.optString("appId", payload.optString("id"))
        val title = _apps.value.firstOrNull { it.id == appId }?.title
        _status.value = _status.value.copy(
            foregroundAppId = appId.takeIf { it.isNotBlank() },
            foregroundAppTitle = title
        )
    }

    private fun parseChannel(payload: JSONObject) {
        val name = payload.optString("channelName", payload.optString("channelNumber"))
        _status.value = _status.value.copy(channelName = name.takeIf { it.isNotBlank() })
    }

    private fun parsePowerState(payload: JSONObject) {
        val state = payload.optString("state", payload.optString("powerState"))
        _status.value = _status.value.copy(powerState = state.takeIf { it.isNotBlank() })
    }

    private fun sendRequest(
        uri: String,
        payload: JSONObject? = null,
        subscribe: Boolean = false,
        callback: (JSONObject) -> Unit = {}
    ) {
        if (!isConnected()) {
            log.warn("webOS", "Command skipped while TV is not connected: $uri")
            return
        }
        val id = nextId(if (subscribe) "sub" else "req")
        pendingRequests[id] = PendingRequest(uri, subscribe, callback)
        sendRaw(WebOsProtocol.request(id, uri, payload, subscribe))
    }

    private fun sendRaw(message: JSONObject) {
        val text = message.toString()
        if (socket?.send(text) == true) {
            log.out("webOS", text)
        } else {
            log.warn("webOS", "Unable to send message because the socket is closed")
        }
    }

    private fun isConnected(): Boolean =
        _connectionState.value == WebOsConnectionState.CONNECTED && socket != null

    private fun nextId(prefix: String): String = "$prefix-${requestCounter.getAndIncrement()}"

    private fun closeSocketOnly() {
        pointerClient.disconnect()
        socket?.close(1000, "Client disconnect")
        socket = null
    }

    private fun verifyOrStoreCertificate(ipAddress: String, response: Response): Boolean {
        val fingerprint = LocalTls.sha256Fingerprint(response) ?: return true
        val key = SecurePreferences.KEY_TV_CERT_FINGERPRINT_PREFIX + ipAddress
        val stored = preferences.getString(key)
        return when {
            stored.isBlank() -> {
                preferences.putString(key, fingerprint)
                log.info("TLS", "Stored TV certificate fingerprint: $fingerprint")
                true
            }

            stored == fingerprint -> true

            else -> {
                log.error("TLS", "TV certificate fingerprint changed")
                false
            }
        }
    }

    private fun setError(message: String) {
        _lastError.value = message
        _connectionState.value = WebOsConnectionState.ERROR
        log.error("webOS", message)
    }
}
