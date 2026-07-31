package com.skallahaze.irbloasster.webos

import android.os.Build
import com.skallahaze.irbloasster.model.TvApp
import com.skallahaze.irbloasster.model.TvInput
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class WebOsClient(
    private val listener: Listener
) {
    data class Config(
        val ipAddress: String,
        val clientKey: String = "",
        val certificateFingerprint: String = ""
    )

    interface Listener {
        fun onStatus(message: String)
        fun onPairingRequired()
        fun onRegistered(clientKey: String, certificateFingerprint: String)
        fun onDisconnected(reason: String)
        fun onError(message: String)
        fun onVolume(volume: Int?, muted: Boolean)
        fun onForegroundApp(appId: String)
        fun onPowerState(state: String)
        fun onApps(apps: List<TvApp>)
        fun onInputs(inputs: List<TvInput>)
        fun onPointerReady(ready: Boolean)
    }

    private data class PendingRequest(
        val subscription: Boolean,
        val callback: ((JSONObject) -> Unit)?
    )

    private val nextRequestId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, PendingRequest>()

    @Volatile
    private var registered = false
    private var config: Config? = null
    private var client: OkHttpClient? = null
    private var socket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private var trustManager: LocalTvTrustManager? = null
    private var triedClearTextFallback = false
    private var registerRequestId: String? = null
    private var manuallyClosed = false

    val isConnected: Boolean
        get() = registered

    fun connect(config: Config) {
        val host = config.ipAddress.trim()
        require(host.isNotBlank()) { "TV-IP fehlt" }
        require(!host.contains('/') && !host.contains(' ')) { "Ungültige TV-IP" }

        disconnect(notify = false)
        manuallyClosed = false
        this.config = config.copy(ipAddress = host)
        triedClearTextFallback = false
        connectSecure()
    }

    fun disconnect(notify: Boolean = true) {
        manuallyClosed = true
        registered = false
        pending.clear()
        pointerSocket?.close(1000, "client disconnect")
        pointerSocket = null
        socket?.close(1000, "client disconnect")
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        listener.onPointerReady(false)
        if (notify) listener.onDisconnected("Verbindung getrennt")
    }

    fun volumeUp() = request("ssap://audio/volumeUp")
    fun volumeDown() = request("ssap://audio/volumeDown")
    fun setVolume(volume: Int) = request(
        "ssap://audio/setVolume",
        JSONObject().put("volume", volume.coerceIn(0, 100))
    )

    fun setMute(muted: Boolean) = request(
        "ssap://audio/setMute",
        JSONObject().put("mute", muted)
    )

    fun powerOff() = request("ssap://system/turnOff")
    fun channelUp() = request("ssap://tv/channelUp")
    fun channelDown() = request("ssap://tv/channelDown")
    fun play() = request("ssap://media.controls/play")
    fun pause() = request("ssap://media.controls/pause")
    fun stop() = request("ssap://media.controls/stop")
    fun fastForward() = request("ssap://media.controls/fastForward")
    fun rewind() = request("ssap://media.controls/rewind")

    fun switchInput(inputId: String) = request(
        "ssap://tv/switchInput",
        JSONObject().put("inputId", inputId)
    )

    fun launchApp(appId: String) = request(
        "ssap://system.launcher/launch",
        JSONObject().put("id", appId)
    )

    fun insertText(text: String) = request(
        "ssap://com.webos.service.ime/insertText",
        JSONObject().put("text", text).put("replace", false)
    )

    fun sendEnterKey() = request("ssap://com.webos.service.ime/sendEnterKey")

    fun refresh() {
        subscribeVolume()
        subscribeForegroundApp()
        subscribePowerState()
        loadApps()
        loadInputs()
        connectPointer()
    }

    fun pointerButton(name: String) {
        sendPointerFrame("type:button\nname:${name.uppercase(Locale.ROOT)}\n\n")
    }

    fun pointerClick() {
        sendPointerFrame("type:click\n\n")
    }

    fun pointerMove(dx: Float, dy: Float, drag: Boolean = false) {
        sendPointerFrame("type:move\ndx:$dx\ndy:$dy\ndown:${if (drag) 1 else 0}\n\n")
    }

    fun pointerScroll(dx: Float, dy: Float) {
        sendPointerFrame("type:scroll\ndx:$dx\ndy:$dy\n\n")
    }

    private fun connectSecure() {
        val currentConfig = config ?: return
        listener.onStatus("Verbinde verschlüsselt mit ${currentConfig.ipAddress}:3001 …")
        val localTrustManager = LocalTvTrustManager(currentConfig.certificateFingerprint)
        trustManager = localTrustManager
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(localTrustManager), SecureRandom())
        }
        val okHttp = baseClientBuilder()
            .sslSocketFactory(sslContext.socketFactory, localTrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
        client = okHttp
        openSocket(okHttp, "wss://${currentConfig.ipAddress}:3001/")
    }

    private fun connectClearTextFallback() {
        val currentConfig = config ?: return
        triedClearTextFallback = true
        listener.onStatus("Port 3001 nicht erreichbar – teste lokalen Port 3000 …")
        val okHttp = baseClientBuilder().build()
        client = okHttp
        openSocket(okHttp, "ws://${currentConfig.ipAddress}:3000/")
    }

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    private fun openSocket(okHttp: OkHttpClient, url: String) {
        val request = Request.Builder().url(url).build()
        socket = okHttp.newWebSocket(request, mainSocketListener)
    }

    private val mainSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            listener.onStatus("Socket offen – starte webOS-Anmeldung …")
            sendHello()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleMessage(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            registered = false
            listener.onPointerReady(false)
            if (!manuallyClosed) listener.onDisconnected(reason.ifBlank { "TV hat die Verbindung beendet" })
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            registered = false
            listener.onPointerReady(false)
            if (manuallyClosed) return

            val fingerprintMismatch = trustManager?.fingerprintMismatch == true
            if (!triedClearTextFallback && !fingerprintMismatch) {
                connectClearTextFallback()
            } else {
                val message = if (fingerprintMismatch) {
                    "Das TV-Zertifikat hat sich geändert. Pairing im Setup zurücksetzen und erneut verbinden."
                } else {
                    throwable.message ?: "WebSocket-Verbindung fehlgeschlagen"
                }
                listener.onError(message)
            }
        }
    }

    private fun sendHello() {
        val id = nextId()
        val payload = JSONObject()
            .put("sdkVersion", "1.0")
            .put("deviceModel", Build.MODEL)
            .put("OSVersion", Build.VERSION.SDK_INT.toString())
            .put("resolution", "phone")
            .put("appId", "com.skallahaze.irbloasster")
            .put("appName", "LivingRoom Controller")
            .put("appRegion", Locale.getDefault().country)

        sendJson(
            JSONObject()
                .put("id", id)
                .put("type", "hello")
                .put("payload", payload)
        )
    }

    private fun sendRegister() {
        val currentConfig = config ?: return
        val id = nextId()
        registerRequestId = id

        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("permissions", JSONArray(WEBOS_PERMISSIONS))

        val payload = JSONObject()
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)

        if (currentConfig.clientKey.isNotBlank()) {
            payload.put("client-key", currentConfig.clientKey)
        }

        sendJson(
            JSONObject()
                .put("id", id)
                .put("type", "register")
                .put("payload", payload)
        )
    }

    private fun handleMessage(text: String) {
        runCatching {
            val message = JSONObject(text)
            val type = message.optString("type")
            val id = message.optString("id")
            val payload = message.optJSONObject("payload") ?: JSONObject()

            when (type) {
                "hello" -> sendRegister()
                "registered" -> {
                    val clientKey = payload.optString("client-key")
                    if (clientKey.isBlank()) error("TV hat keinen Client-Key geliefert")
                    registered = true
                    val fingerprint = trustManager?.lastFingerprint.orEmpty()
                    listener.onRegistered(clientKey, fingerprint)
                }
                "response" -> {
                    if (id == registerRequestId && payload.has("pairingType")) {
                        listener.onPairingRequired()
                    }
                    dispatchResponse(id, payload)
                }
                "error" -> {
                    val errorText = message.optString("error").ifBlank { payload.toString() }
                    if (id.isNotBlank()) pending.remove(id)
                    listener.onError(errorText)
                }
            }
        }.onFailure { error ->
            listener.onError("Ungültige webOS-Antwort: ${error.message}")
        }
    }

    private fun dispatchResponse(id: String, payload: JSONObject) {
        if (id.isBlank()) return
        val request = pending[id] ?: return
        request.callback?.invoke(payload)
        if (!request.subscription) pending.remove(id)
    }

    private fun request(
        uri: String,
        payload: JSONObject = JSONObject(),
        callback: ((JSONObject) -> Unit)? = null
    ) {
        sendCommand("request", uri, payload, false, callback)
    }

    private fun subscribe(
        uri: String,
        callback: (JSONObject) -> Unit
    ) {
        sendCommand("subscribe", uri, JSONObject(), true, callback)
    }

    private fun sendCommand(
        type: String,
        uri: String,
        payload: JSONObject,
        subscription: Boolean,
        callback: ((JSONObject) -> Unit)?
    ) {
        if (!registered) {
            listener.onError("TV ist noch nicht verbunden")
            return
        }
        val id = nextId()
        pending[id] = PendingRequest(subscription, callback)
        sendJson(
            JSONObject()
                .put("id", id)
                .put("type", type)
                .put("uri", uri)
                .put("payload", payload)
        )
    }

    private fun subscribeVolume() {
        subscribe("ssap://audio/getVolume") { payload ->
            val volume = when {
                payload.has("volume") -> payload.optInt("volume")
                payload.optJSONObject("volumeStatus")?.has("volume") == true ->
                    payload.optJSONObject("volumeStatus")?.optInt("volume")
                else -> null
            }
            val muted = when {
                payload.has("muted") -> payload.optBoolean("muted")
                payload.has("mute") -> payload.optBoolean("mute")
                payload.optJSONObject("volumeStatus")?.has("muteStatus") == true ->
                    payload.optJSONObject("volumeStatus")?.optBoolean("muteStatus") == true
                else -> false
            }
            listener.onVolume(volume, muted)
        }
    }

    private fun subscribeForegroundApp() {
        subscribe("ssap://com.webos.applicationManager/getForegroundAppInfo") { payload ->
            listener.onForegroundApp(payload.optString("appId", payload.optString("id")))
        }
    }

    private fun subscribePowerState() {
        subscribe("ssap://com.webos.service.tvpower/power/getPowerState") { payload ->
            listener.onPowerState(payload.optString("state", payload.optString("powerState")))
        }
    }

    private fun loadApps() {
        request("ssap://com.webos.applicationManager/listApps") { payload ->
            val array = payload.optJSONArray("apps") ?: JSONArray()
            val apps = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title", id)
                    if (id.isNotBlank()) add(TvApp(id, title))
                }
            }.sortedBy { it.title.lowercase(Locale.ROOT) }
            listener.onApps(apps)
        }
    }

    private fun loadInputs() {
        request("ssap://tv/getExternalInputList") { payload ->
            val array = payload.optJSONArray("devices") ?: JSONArray()
            val inputs = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id", item.optString("inputId"))
                    val label = item.optString("label", item.optString("name", id))
                    val connected = if (item.has("connected")) item.optBoolean("connected") else true
                    if (id.isNotBlank()) add(TvInput(id, label, connected))
                }
            }
            listener.onInputs(inputs)
        }
    }

    private fun connectPointer() {
        request("ssap://com.webos.service.networkinput/getPointerInputSocket") { payload ->
            val socketPath = payload.optString("socketPath")
            if (socketPath.isBlank()) {
                listener.onPointerReady(false)
                return@request
            }
            val okHttp = client ?: return@request
            pointerSocket?.close(1000, "reconnect")
            pointerSocket = okHttp.newWebSocket(
                Request.Builder().url(socketPath).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        listener.onPointerReady(true)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        listener.onPointerReady(false)
                    }

                    override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                        listener.onPointerReady(false)
                        listener.onStatus("Touchpad nicht verfügbar: ${throwable.message.orEmpty()}")
                    }
                }
            )
        }
    }

    private fun sendPointerFrame(frame: String) {
        if (pointerSocket?.send(frame) != true) {
            listener.onError("Touchpad-Socket ist noch nicht bereit")
        }
    }

    private fun sendJson(json: JSONObject) {
        if (socket?.send(json.toString()) != true) {
            listener.onError("webOS-Socket ist nicht offen")
        }
    }

    private fun nextId(): String = nextRequestId.getAndIncrement().toString()

    private class LocalTvTrustManager(
        expectedFingerprint: String
    ) : X509TrustManager {
        private val expected = normalizeFingerprint(expectedFingerprint)

        @Volatile
        var lastFingerprint: String = ""
            private set

        @Volatile
        var fingerprintMismatch: Boolean = false
            private set

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val certificate = chain?.firstOrNull() ?: throw CertificateException("TV-Zertifikat fehlt")
            val fingerprint = sha256(certificate.encoded)
            lastFingerprint = fingerprint
            if (expected.isNotBlank() && normalizeFingerprint(fingerprint) != expected) {
                fingerprintMismatch = true
                throw CertificateException("TV-Zertifikat stimmt nicht mit dem gespeicherten Fingerabdruck überein")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        private companion object {
            fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(":") { byte -> "%02X".format(byte) }
        }
    }

    private companion object {
        val WEBOS_PERMISSIONS = listOf(
            "LAUNCH",
            "LAUNCH_WEBAPP",
            "APP_TO_APP",
            "CONTROL_AUDIO",
            "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_PLAYBACK",
            "CONTROL_INPUT_TV",
            "CONTROL_POWER",
            "READ_APP_STATUS",
            "READ_CURRENT_CHANNEL",
            "READ_INPUT_DEVICE_LIST",
            "READ_INSTALLED_APPS",
            "READ_LGE_TV_INPUT_EVENTS",
            "READ_NOTIFICATIONS",
            "READ_RUNNING_APPS",
            "READ_TV_CHANNEL_LIST",
            "READ_TV_PROGRAM_INFO",
            "WRITE_NOTIFICATION_TOAST",
            "WRITE_SETTINGS",
            "WRITE_TV_CHANNEL"
        )

        fun normalizeFingerprint(value: String): String = value.replace(":", "").trim().uppercase(Locale.ROOT)
    }
}
