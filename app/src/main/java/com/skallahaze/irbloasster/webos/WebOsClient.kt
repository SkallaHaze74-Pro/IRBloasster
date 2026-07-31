package com.skallahaze.irbloasster.webos

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skallahaze.irbloasster.data.SettingsRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

enum class WebOsConnection {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR,
}

data class WebOsApp(val id: String, val title: String)

data class WebOsInput(
    val id: String,
    val label: String,
    val connected: Boolean = true,
)

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
    val apps: List<WebOsApp> = emptyList(),
    val inputs: List<WebOsInput> = emptyList(),
    val discoveredTvs: List<DiscoveredWebOsTv> = emptyList(),
    val pointerReady: Boolean = false,
    val secureTransport: Boolean = false,
    val certificateFingerprint: String = "",
    val reconnectAttempt: Int = 0,
)

class WebOsClient(
    context: Context,
    private val settings: SettingsRepository,
) {
    private data class PendingRequest(
        val subscription: Boolean,
        val callback: ((JSONObject) -> Unit)?,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovery = SsdpDiscovery(context)
    private val requestCounter = AtomicInteger(0)
    private val connectionGeneration = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, PendingRequest>()

    private var socket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private var activeClient: OkHttpClient? = null
    private var trustManager: LocalTvTrustManager? = null
    private var pendingPointerButton: String? = null
    private var manualDisconnect = false
    private var registrationSent = false
    private var fallbackTried = false
    private var activeSecureTransport = false
    private var reconnectAttempt = 0
    @Volatile private var registered = false

    var state by mutableStateOf(
        WebOsState(
            host = settings.webOsHost,
            certificateFingerprint = settings.getWebOsCertificateFingerprint(),
        ),
    )
        private set

    fun connect(rawHost: String = settings.webOsHost) {
        val host = normalizeHost(rawHost)
        if (host.isBlank() || !isSafeHost(host)) {
            updateState {
                it.copy(
                    connection = WebOsConnection.ERROR,
                    message = "Bitte eine gültige lokale TV-IP oder einen Hostnamen eintragen",
                )
            }
            return
        }

        settings.setWebOsHost(host)
        manualDisconnect = false
        fallbackTried = false
        reconnectAttempt = 0
        val generation = connectionGeneration.incrementAndGet()
        disconnectSockets(updateUi = false)
        openSecure(host, generation)
    }

    fun disconnect() {
        manualDisconnect = true
        connectionGeneration.incrementAndGet()
        disconnectSockets(updateUi = true)
    }

    fun close() {
        manualDisconnect = true
        connectionGeneration.incrementAndGet()
        disconnectSockets(updateUi = false)
        ioScope.cancel()
    }

    fun forgetPairing() {
        settings.clearWebOsPairing()
        disconnect()
        updateState {
            it.copy(
                message = "Kopplung und Zertifikat-Fingerabdruck gelöscht",
                certificateFingerprint = "",
            )
        }
    }

    fun discoverTvs(): Boolean {
        updateState {
            it.copy(
                connection = if (registered) it.connection else WebOsConnection.DISCOVERING,
                message = "Suche LG webOS TVs im lokalen Netzwerk …",
            )
        }
        ioScope.launch {
            discovery.discover()
                .onSuccess { devices ->
                    updateState {
                        it.copy(
                            connection = if (registered) WebOsConnection.CONNECTED else WebOsConnection.DISCONNECTED,
                            discoveredTvs = devices,
                            message = if (devices.isEmpty()) {
                                "Kein LG webOS TV gefunden – IP kann manuell eingetragen werden"
                            } else {
                                "${devices.size} LG webOS TV${if (devices.size == 1) "" else "s"} gefunden"
                            },
                        )
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            connection = if (registered) WebOsConnection.CONNECTED else WebOsConnection.ERROR,
                            message = error.message ?: "TV-Suche fehlgeschlagen",
                        )
                    }
                }
        }
        return true
    }

    fun selectDiscoveredTv(tv: DiscoveredWebOsTv) {
        settings.setWebOsHost(tv.host)
        updateState {
            it.copy(
                host = tv.host,
                message = "${tv.name} (${tv.host}) ausgewählt",
            )
        }
    }

    fun wakeTv(): Boolean {
        val mac = settings.webOsMac
        if (!WakeOnLan.isValidMac(mac)) {
            updateState { it.copy(message = "Bitte zuerst eine gültige TV-MAC-Adresse speichern") }
            return false
        }

        ioScope.launch {
            updateState { it.copy(message = "Wake-on-LAN wird gesendet …") }
            WakeOnLan.send(mac)
                .onSuccess {
                    updateState { it.copy(message = "Wake-on-LAN gesendet") }
                    if (settings.autoConnect && settings.webOsHost.isNotBlank()) {
                        delay(1_800L)
                        mainHandler.post { connect(settings.webOsHost) }
                    }
                }
                .onFailure { error ->
                    updateState { it.copy(message = error.message ?: "Wake-on-LAN fehlgeschlagen") }
                }
        }
        return true
    }

    fun refreshStatus() {
        subscribe("ssap://audio/getVolume") { parseVolume(it) }
        subscribe("ssap://com.webos.applicationManager/getForegroundAppInfo") { payload ->
            updateState { current ->
                current.copy(currentApp = payload.optString("appId").ifBlank { current.currentApp.orEmpty() })
            }
        }
        subscribe("ssap://com.webos.service.tvpower/power/getPowerState") { payload ->
            updateState { current ->
                current.copy(powerState = payload.optString("state").ifBlank { payload.optString("powerState", current.powerState.orEmpty()) })
            }
        }
        request("ssap://system/getSystemInfo") { payload -> parseGenericStatus(payload) }
        loadApps()
        loadInputs()
        requestPointerSocket()
    }

    fun volumeUp(): Boolean = request("ssap://audio/volumeUp") != null
    fun volumeDown(): Boolean = request("ssap://audio/volumeDown") != null

    fun setVolume(volume: Int): Boolean =
        request("ssap://audio/setVolume", JSONObject().put("volume", volume.coerceIn(0, 100))) != null

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
            JSONObject().put("text", text).put("replace", false),
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
        request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", count.coerceAtLeast(1))) != null

    fun sendButton(name: String): Boolean {
        val pointer = pointerSocket
        if (pointer != null && state.pointerReady) {
            return pointer.send("type:button\nname:${name.uppercase(Locale.ROOT)}\n\n")
        }

        pendingPointerButton = name
        return requestPointerSocket()
    }

    fun movePointer(dx: Int, dy: Int, down: Boolean = false): Boolean =
        pointerSocket?.send(
            "type:move\ndx:$dx\ndy:$dy\ndown:${if (down) 1 else 0}\n\n",
        ) == true

    fun clickPointer(): Boolean = pointerSocket?.send("type:click\n\n") == true

    fun scrollPointer(dy: Int): Boolean =
        pointerSocket?.send("type:scroll\ndx:0\ndy:$dy\n\n") == true

    private fun openSecure(host: String, generation: Int) {
        updateState {
            it.copy(
                connection = WebOsConnection.CONNECTING,
                message = "Sichere Verbindung zu $host:3001 wird aufgebaut …",
                host = host,
                reconnectAttempt = reconnectAttempt,
                pointerReady = false,
            )
        }

        val localTrustManager = LocalTvTrustManager(settings.getWebOsCertificateFingerprint())
        trustManager = localTrustManager
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(localTrustManager), SecureRandom())
        }
        val client = baseClientBuilder()
            .sslSocketFactory(sslContext.socketFactory, localTrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
        openSocket(client, "wss://$host:3001/", secure = true, host = host, generation = generation)
    }

    private fun openLegacy(host: String, generation: Int) {
        fallbackTried = true
        updateState {
            it.copy(
                connection = WebOsConnection.CONNECTING,
                message = "Port 3001 nicht erreichbar – teste lokalen Port 3000 …",
                host = host,
            )
        }
        val client = baseClientBuilder().build()
        openSocket(client, "ws://$host:3000/", secure = false, host = host, generation = generation)
    }

    private fun openSocket(
        client: OkHttpClient,
        url: String,
        secure: Boolean,
        host: String,
        generation: Int,
    ) {
        disconnectSockets(updateUi = false)
        activeClient = client
        activeSecureTransport = secure
        registrationSent = false
        registered = false
        pending.clear()
        val request = Request.Builder().url(url).build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != connectionGeneration.get()) return
                updateState {
                    it.copy(
                        connection = WebOsConnection.PAIRING,
                        message = "webOS-Anmeldung läuft – Abfrage am Fernseher bestätigen",
                        secureTransport = secure,
                    )
                }
                webSocket.send(helloMessage().toString())
                mainHandler.postDelayed({
                    if (!registrationSent && socket === webSocket && generation == connectionGeneration.get()) {
                        sendRegistration(webSocket)
                    }
                }, 650L)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation == connectionGeneration.get()) handleMessage(text, webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (generation == connectionGeneration.get()) handleMessage(bytes.utf8(), webSocket)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation != connectionGeneration.get() || manualDisconnect) return
                registered = false
                updateState { it.copy(pointerReady = false) }
                scheduleReconnect(host, generation, reason.ifBlank { "TV-Verbindung beendet" })
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                if (generation != connectionGeneration.get() || manualDisconnect) return
                registered = false
                updateState { it.copy(pointerReady = false) }

                val certificateMismatch = trustManager?.fingerprintMismatch == true
                if (secure && !fallbackTried && !certificateMismatch) {
                    openLegacy(host, generation)
                } else if (certificateMismatch) {
                    updateState {
                        it.copy(
                            connection = WebOsConnection.ERROR,
                            message = "TV-Zertifikat hat sich geändert. Kopplung im Setup löschen und bewusst neu verbinden.",
                        )
                    }
                } else {
                    scheduleReconnect(host, generation, throwable.message ?: "TV nicht erreichbar")
                }
            }
        })
    }

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    private fun scheduleReconnect(host: String, generation: Int, reason: String) {
        if (!settings.autoConnect || manualDisconnect || reconnectAttempt >= MAX_RECONNECTS) {
            updateState {
                it.copy(
                    connection = WebOsConnection.ERROR,
                    message = reason,
                    reconnectAttempt = reconnectAttempt,
                )
            }
            return
        }

        reconnectAttempt += 1
        val delayMs = (1_500L * reconnectAttempt).coerceAtMost(7_500L)
        updateState {
            it.copy(
                connection = WebOsConnection.CONNECTING,
                message = "$reason · neuer Versuch in ${delayMs / 1_000}s",
                reconnectAttempt = reconnectAttempt,
            )
        }
        mainHandler.postDelayed({
            if (!manualDisconnect && generation == connectionGeneration.get()) {
                fallbackTried = false
                openSecure(host, generation)
            }
        }, delayMs)
    }

    private fun helloMessage(): JSONObject = JSONObject()
        .put("id", "hello_${requestCounter.incrementAndGet()}")
        .put("type", "hello")
        .put(
            "payload",
            JSONObject()
                .put("sdkVersion", "1.1")
                .put("deviceModel", Build.MODEL)
                .put("OSVersion", Build.VERSION.SDK_INT.toString())
                .put("resolution", "phone")
                .put("appId", "com.skallahaze.irbloasster")
                .put("appName", "SmartIR")
                .put("appRegion", Locale.getDefault().country),
        )

    private fun sendRegistration(webSocket: WebSocket) {
        if (registrationSent) return
        registrationSent = true
        webSocket.send(registrationMessage().toString())
    }

    private fun request(
        uri: String,
        payload: JSONObject? = null,
        subscribe: Boolean = false,
        callback: ((JSONObject) -> Unit)? = null,
    ): String? {
        val webSocket = socket ?: return null
        if (!registered) return null

        val id = "smartir_${requestCounter.incrementAndGet()}"
        pending[id] = PendingRequest(subscribe, callback)
        val message = JSONObject()
            .put("id", id)
            .put("type", if (subscribe) "subscribe" else "request")
            .put("uri", uri)
            .put("payload", payload ?: JSONObject())

        return if (webSocket.send(message.toString())) id else {
            pending.remove(id)
            null
        }
    }

    private fun request(uri: String, callback: (JSONObject) -> Unit): String? =
        request(uri, payload = null, subscribe = false, callback = callback)

    private fun subscribe(uri: String, callback: (JSONObject) -> Unit): String? =
        request(uri, payload = null, subscribe = true, callback = callback)

    private fun handleMessage(text: String, webSocket: WebSocket) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        updateState { it.copy(lastResponse = redactSecrets(message).take(1_500)) }
        val type = message.optString("type")
        val id = message.optString("id")
        val payload = message.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "hello" -> sendRegistration(webSocket)
            "registered" -> {
                registered = true
                reconnectAttempt = 0
                payload.optString("client-key").takeIf { it.isNotBlank() }?.let(settings::setWebOsClientKey)
                val fingerprint = if (activeSecureTransport) trustManager?.lastFingerprint.orEmpty() else ""
                if (fingerprint.isNotBlank()) settings.setWebOsCertificateFingerprint(fingerprint)
                updateState {
                    it.copy(
                        connection = WebOsConnection.CONNECTED,
                        message = if (activeSecureTransport) "LG TV sicher verbunden" else "LG TV über Legacy-Port 3000 verbunden",
                        secureTransport = activeSecureTransport,
                        certificateFingerprint = fingerprint.ifBlank { settings.getWebOsCertificateFingerprint() },
                        reconnectAttempt = 0,
                    )
                }
                refreshStatus()
            }
            "response" -> {
                dispatchPending(id, payload)
                parseGenericStatus(payload)
            }
            "error" -> {
                pending.remove(id)
                val errorText = message.optString("error", "webOS-Befehl fehlgeschlagen")
                updateState { it.copy(message = errorText) }
            }
        }
    }

    private fun dispatchPending(id: String, payload: JSONObject) {
        if (id.isBlank()) return
        val entry = pending[id] ?: return
        entry.callback?.invoke(payload)
        if (!entry.subscription) pending.remove(id)
    }

    private fun parseGenericStatus(payload: JSONObject) {
        parseVolume(payload)
        updateState { current ->
            current.copy(
                currentApp = payload.optString("appId").takeIf { it.isNotBlank() } ?: current.currentApp,
                modelName = payload.optString("modelName").takeIf { it.isNotBlank() }
                    ?: payload.optString("model_name").takeIf { it.isNotBlank() }
                    ?: current.modelName,
                powerState = payload.optString("state").takeIf { it.isNotBlank() }
                    ?: payload.optString("powerState").takeIf { it.isNotBlank() }
                    ?: current.powerState,
            )
        }
    }

    private fun parseVolume(payload: JSONObject) {
        val nested = payload.optJSONObject("volumeStatus")
        val volume = when {
            payload.has("volume") -> payload.optInt("volume")
            nested?.has("volume") == true -> nested.optInt("volume")
            else -> null
        }
        val muted = when {
            payload.has("muted") -> payload.optBoolean("muted")
            payload.has("mute") -> payload.optBoolean("mute")
            nested?.has("muteStatus") == true -> nested.optBoolean("muteStatus")
            else -> null
        }
        if (volume != null || muted != null) {
            updateState { current ->
                current.copy(
                    volume = volume ?: current.volume,
                    muted = muted ?: current.muted,
                )
            }
        }
    }

    private fun loadApps() {
        request("ssap://com.webos.applicationManager/listApps") { payload ->
            val array = payload.optJSONArray("apps") ?: JSONArray()
            val apps = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isNotBlank()) add(WebOsApp(id, item.optString("title", id)))
                }
            }.sortedBy { it.title.lowercase(Locale.ROOT) }
            updateState { it.copy(apps = apps) }
        }
    }

    private fun loadInputs() {
        request("ssap://tv/getExternalInputList") { payload ->
            val array = payload.optJSONArray("devices") ?: JSONArray()
            val inputs = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id", item.optString("inputId"))
                    if (id.isBlank()) continue
                    add(
                        WebOsInput(
                            id = id,
                            label = item.optString("label", item.optString("name", id)),
                            connected = if (item.has("connected")) item.optBoolean("connected") else true,
                        ),
                    )
                }
            }
            updateState { it.copy(inputs = inputs) }
        }
    }

    private fun requestPointerSocket(): Boolean =
        request("ssap://com.webos.service.networkinput/getPointerInputSocket") { payload ->
            val socketPath = payload.optString("socketPath")
            if (socketPath.isNotBlank()) openPointerSocket(socketPath)
        } != null

    private fun openPointerSocket(socketPath: String) {
        val client = activeClient ?: return
        pointerSocket?.close(1000, "Neue Pointer-Verbindung")
        pointerSocket = client.newWebSocket(
            Request.Builder().url(socketPath).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    updateState { it.copy(pointerReady = true) }
                    pendingPointerButton?.let { button ->
                        webSocket.send("type:button\nname:${button.uppercase(Locale.ROOT)}\n\n")
                        pendingPointerButton = null
                    }
                }

                override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                    pointerSocket = null
                    updateState {
                        it.copy(
                            pointerReady = false,
                            message = throwable.message ?: "Magic-Remote-Verbindung fehlgeschlagen",
                        )
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    pointerSocket = null
                    updateState { it.copy(pointerReady = false) }
                }
            },
        )
    }

    private fun registrationMessage(): JSONObject {
        val permissionArray = JSONArray().apply { WEBOS_PERMISSIONS.forEach(::put) }
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.1.0")
            .put("permissions", permissionArray)

        val payload = JSONObject()
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)

        settings.getWebOsClientKey().takeIf { it.isNotBlank() }?.let {
            payload.put("client-key", it)
        }

        return JSONObject()
            .put("id", "register_${requestCounter.incrementAndGet()}")
            .put("type", "register")
            .put("payload", payload)
    }

    private fun disconnectSockets(updateUi: Boolean) {
        registered = false
        registrationSent = false
        pending.clear()
        pointerSocket?.close(1000, "SmartIR beendet")
        pointerSocket = null
        socket?.close(1000, "SmartIR beendet")
        socket = null
        pendingPointerButton = null
        activeClient?.dispatcher?.executorService?.shutdown()
        activeClient?.connectionPool?.evictAll()
        activeClient = null

        if (updateUi) {
            updateState {
                it.copy(
                    connection = WebOsConnection.DISCONNECTED,
                    message = "Nicht verbunden",
                    pointerReady = false,
                    reconnectAttempt = 0,
                )
            }
        }
    }

    private fun updateState(transform: (WebOsState) -> WebOsState) {
        mainHandler.post { state = transform(state) }
    }

    private fun normalizeHost(rawHost: String): String {
        var host = rawHost.trim()
            .removePrefix("ws://")
            .removePrefix("wss://")
            .substringBefore('/')
        host = host.removeSuffix(":3000").removeSuffix(":3001")
        return host
    }

    private fun isSafeHost(host: String): Boolean =
        host.isNotBlank() && host.none { it.isWhitespace() || it == '/' || it == '\\' }

    private fun redactSecrets(message: JSONObject): String {
        fun redact(value: Any?): Any? = when (value) {
            is JSONObject -> JSONObject().also { clean ->
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    clean.put(key, if (key.equals("client-key", true)) "***" else redact(value.opt(key)))
                }
            }
            is JSONArray -> JSONArray().also { clean ->
                for (index in 0 until value.length()) clean.put(redact(value.opt(index)))
            }
            else -> value
        }
        return (redact(message) as JSONObject).toString(2)
    }

    private class LocalTvTrustManager(expectedFingerprint: String) : X509TrustManager {
        private val expected = normalizeFingerprint(expectedFingerprint)

        @Volatile var lastFingerprint: String = ""
            private set
        @Volatile var fingerprintMismatch: Boolean = false
            private set

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val certificate = chain?.firstOrNull() ?: throw CertificateException("TV-Zertifikat fehlt")
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString(":") { byte -> "%02X".format(byte) }
            lastFingerprint = fingerprint
            if (expected.isNotBlank() && normalizeFingerprint(fingerprint) != expected) {
                fingerprintMismatch = true
                throw CertificateException("Gespeicherter TV-Fingerabdruck stimmt nicht überein")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val MAX_RECONNECTS = 5
        val WEBOS_PERMISSIONS = listOf(
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
            "READ_INSTALLED_APPS",
            "READ_NETWORK_STATE",
            "READ_RUNNING_APPS",
            "READ_TV_CHANNEL_LIST",
            "READ_TV_PROGRAM_INFO",
            "WRITE_NOTIFICATION_TOAST",
            "WRITE_SETTINGS",
            "WRITE_TV_CHANNEL",
        )

        fun normalizeFingerprint(value: String): String =
            value.replace(":", "").trim().uppercase(Locale.ROOT)
    }
}
