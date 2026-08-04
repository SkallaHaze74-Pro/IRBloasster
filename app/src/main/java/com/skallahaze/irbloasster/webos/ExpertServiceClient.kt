package com.skallahaze.irbloasster.webos

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skallahaze.irbloasster.BuildConfig
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

enum class ExpertServiceConnection {
    DISCONNECTED,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR,
}

enum class ExpertFactoryMenu(
    val irKey: String,
    val displayName: String,
    val description: String,
) {
    EZ_ADJUST(
        irKey = "ezAdjust",
        displayName = "EZ-ADJUST",
        description = "Tool Options, White Balance, 22 Point WB und weitere Werksabgleiche",
    ),
    IN_START(
        irKey = "inStart",
        displayName = "IN-START",
        description = "System-, OLED-, HDMI- und Diagnoseinformationen",
    ),
}

data class ExpertServiceState(
    val connection: ExpertServiceConnection = ExpertServiceConnection.DISCONNECTED,
    val message: String = "Expert Service bereit",
    val host: String = "",
    val secureTransport: Boolean = false,
    val preflightRunning: Boolean = false,
    val preflightCompleted: Int = 0,
    val preflightTotal: Int = 0,
    val preflightValues: Map<String, String> = emptyMap(),
    val lastPreflightEpochMillis: Long = 0L,
    val lastLaunch: String = "",
    val errors: List<String> = emptyList(),
)

/**
 * Opens LG's stock, trusted Factorywin menus through the normal authorised
 * Second Screen launcher. It never calls factorymanager/pqcontroller write
 * methods and never bypasses LG's on-TV password prompt.
 */
class ExpertServiceClient(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private data class PendingRequest(
        val label: String,
        val countsForPreflight: Boolean,
        val callback: (JSONObject) -> Unit,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestCounter = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, PendingRequest>()

    private var socket: WebSocket? = null
    private var activeClient: OkHttpClient? = null
    private var trustManager: ExpertTrustManager? = null
    private var registered = false
    private var registrationSent = false
    private var fallbackTried = false
    private var activeSecureTransport = false

    var state by mutableStateOf(
        ExpertServiceState(host = settingsRepository.webOsHost),
    )
        private set

    fun connect(): Boolean {
        val host = normalizeHost(settingsRepository.webOsHost)
        if (host.isBlank() || !isSafeHost(host)) {
            postState {
                it.copy(
                    connection = ExpertServiceConnection.ERROR,
                    message = "Zuerst im normalen SmartIR-Setup eine gültige TV-IP speichern",
                )
            }
            return false
        }

        closeSocket(updateUi = false)
        fallbackTried = false
        postState {
            it.copy(
                connection = ExpertServiceConnection.CONNECTING,
                message = "Expert Service verbindet sich mit dem LG-TV …",
                host = host,
                errors = emptyList(),
            )
        }
        openSecure(host)
        return true
    }

    fun runPreflight(): Boolean {
        if (!registered) return connect()

        val probes = listOf(
            Triple(
                "system",
                "ssap://system/getSystemInfo",
                JSONObject(),
            ),
            Triple(
                "capabilities",
                "ssap://config/getConfigs",
                JSONObject().put("configNames", JSONArray(PREFLIGHT_CONFIGS)),
            ),
            Triple(
                "picture",
                "ssap://settings/getSystemSettings",
                JSONObject()
                    .put("category", "picture")
                    .put("keys", JSONArray(PREFLIGHT_PICTURE_KEYS)),
            ),
            Triple(
                "foregroundApp",
                "ssap://com.webos.applicationManager/getForegroundAppInfo",
                JSONObject(),
            ),
        )

        postState {
            it.copy(
                preflightRunning = true,
                preflightCompleted = 0,
                preflightTotal = probes.size,
                preflightValues = emptyMap(),
                lastPreflightEpochMillis = 0L,
                message = "Read-only Vorprüfung läuft …",
                errors = emptyList(),
            )
        }

        probes.forEach { (label, uri, payload) ->
            val requestId = sendRequest(
                label = label,
                uri = uri,
                payload = payload,
                countsForPreflight = true,
            ) { response -> mergePreflight(label, flatten(response)) }

            if (requestId == null) {
                addError("$label: Anfrage konnte nicht gesendet werden")
                finishPreflightProbe()
            }
        }
        return true
    }

    fun launchFactoryMenu(menu: ExpertFactoryMenu): Boolean {
        if (!registered) {
            postState { it.copy(message = "Zuerst mit dem TV verbinden") }
            return false
        }
        if (state.preflightRunning || state.lastPreflightEpochMillis <= 0L) {
            postState { it.copy(message = "Vor dem Öffnen zuerst die read-only Vorprüfung abschließen") }
            return false
        }

        val payload = JSONObject()
            .put("id", FACTORY_APP_ID)
            .put(
                "params",
                JSONObject().put("irKey", menu.irKey),
            )

        return sendRequest(
            label = "${menu.displayName} öffnen",
            uri = "ssap://system.launcher/launch",
            payload = payload,
            countsForPreflight = false,
        ) {
            postState {
                it.copy(
                    message = "${menu.displayName} wurde über LG Factorywin geöffnet",
                    lastLaunch = menu.displayName,
                )
            }
        } != null
    }

    fun exportPreflightReport(): String {
        val snapshot = state
        return JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAtEpochMillis", System.currentTimeMillis())
            .put("mode", "expert-service-preflight-read-only")
            .put("factoryAppId", FACTORY_APP_ID)
            .put("stockFirmwarePath", true)
            .put("rootRequiredForMenuLaunch", false)
            .put("passwordBypass", false)
            .put("automaticWrites", false)
            .put("lastLaunch", snapshot.lastLaunch)
            .put("preflight", JSONObject(snapshot.preflightValues))
            .put(
                "availableMenus",
                JSONArray().apply {
                    ExpertFactoryMenu.entries.forEach { menu ->
                        put(
                            JSONObject()
                                .put("name", menu.displayName)
                                .put("irKey", menu.irKey)
                                .put("description", menu.description),
                        )
                    }
                },
            )
            .put("blockedWriteFamilies", JSONArray(BLOCKED_WRITE_FAMILIES))
            .put("warnings", JSONArray(SAFETY_WARNINGS))
            .put("errors", JSONArray(snapshot.errors))
            .toString(2)
    }

    fun close() {
        closeSocket(updateUi = true)
    }

    private fun openSecure(host: String) {
        val localTrustManager = ExpertTrustManager(
            settingsRepository.getWebOsCertificateFingerprint(),
        )
        trustManager = localTrustManager
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(localTrustManager), SecureRandom())
        }
        val client = baseClientBuilder()
            .sslSocketFactory(sslContext.socketFactory, localTrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
        openSocket(client, "wss://$host:3001/", secure = true, host = host)
    }

    private fun openLegacy(host: String) {
        fallbackTried = true
        postState {
            it.copy(
                connection = ExpertServiceConnection.CONNECTING,
                message = "WSS 3001 nicht erreichbar – Port 3000 wird getestet",
            )
        }
        openSocket(
            client = baseClientBuilder().build(),
            url = "ws://$host:3000/",
            secure = false,
            host = host,
        )
    }

    private fun openSocket(
        client: OkHttpClient,
        url: String,
        secure: Boolean,
        host: String,
    ) {
        closeSocket(updateUi = false)
        activeClient = client
        activeSecureTransport = secure
        registered = false
        registrationSent = false
        pending.clear()

        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    postState {
                        it.copy(
                            connection = ExpertServiceConnection.PAIRING,
                            message = "LG-Autorisierung wird geprüft …",
                            secureTransport = secure,
                        )
                    }
                    webSocket.send(helloMessage().toString())
                    mainHandler.postDelayed({ sendRegistration(webSocket) }, 350L)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text, webSocket)
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    throwable: Throwable,
                    response: Response?,
                ) {
                    val mismatch = trustManager?.fingerprintMismatch == true
                    if (secure && !fallbackTried && !mismatch) {
                        openLegacy(host)
                    } else {
                        postState {
                            it.copy(
                                connection = ExpertServiceConnection.ERROR,
                                message = if (mismatch) {
                                    "TV-Zertifikat hat sich geändert. Kopplung im normalen SmartIR erneuern."
                                } else {
                                    throwable.message ?: "Expert Service konnte keine Verbindung aufbauen"
                                },
                                preflightRunning = false,
                            )
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    registered = false
                    postState {
                        it.copy(
                            connection = ExpertServiceConnection.DISCONNECTED,
                            message = reason.ifBlank { "Expert Service getrennt" },
                            preflightRunning = false,
                        )
                    }
                }
            },
        )
    }

    private fun helloMessage(): JSONObject = JSONObject()
        .put("id", "expert_hello_${requestCounter.incrementAndGet()}")
        .put("type", "hello")
        .put(
            "payload",
            JSONObject()
                .put("sdkVersion", BuildConfig.VERSION_NAME)
                .put("deviceModel", Build.MODEL)
                .put("OSVersion", Build.VERSION.SDK_INT.toString())
                .put("appId", appContext.packageName)
                .put("appName", "SmartIR Expert Service")
                .put("appRegion", Locale.getDefault().country),
        )

    private fun sendRegistration(webSocket: WebSocket) {
        if (registrationSent) return
        registrationSent = true
        webSocket.send(
            WebOsRegistrationProfile.registrationMessage(
                id = "expert_register_${requestCounter.incrementAndGet()}",
                appVersion = BuildConfig.VERSION_NAME,
                clientKey = settingsRepository.getWebOsClientKey(),
                forcePairing = false,
            ).toString(),
        )
    }

    private fun handleMessage(text: String, webSocket: WebSocket) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        val type = message.optString("type")
        val id = message.optString("id")
        val payload = message.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "hello" -> sendRegistration(webSocket)

            "registered" -> {
                registered = true
                payload.optString("client-key")
                    .takeIf { it.isNotBlank() }
                    ?.let(settingsRepository::setWebOsClientKey)

                if (activeSecureTransport) {
                    trustManager?.lastFingerprint
                        ?.takeIf { it.isNotBlank() }
                        ?.let(settingsRepository::setWebOsCertificateFingerprint)
                }

                postState {
                    it.copy(
                        connection = ExpertServiceConnection.CONNECTED,
                        message = "Verbunden – read-only Vorprüfung startet",
                        secureTransport = activeSecureTransport,
                    )
                }
                runPreflight()
            }

            "response" -> {
                val request = pending.remove(id) ?: return
                val returnValue = if (payload.has("returnValue")) {
                    payload.optBoolean("returnValue", true)
                } else {
                    true
                }

                if (returnValue) {
                    runCatching { request.callback(payload) }
                        .onFailure { error ->
                            addError("${request.label}: ${error.message ?: "Antwort konnte nicht verarbeitet werden"}")
                        }
                } else {
                    addError(
                        "${request.label}: ${payload.optString("errorText", "Application error")}",
                    )
                }
                if (request.countsForPreflight) finishPreflightProbe()
            }

            "error" -> {
                val request = pending.remove(id)
                addError("${request?.label ?: id}: ${message.optString("error", "webOS-Anfrage fehlgeschlagen")}")
                if (request?.countsForPreflight == true) finishPreflightProbe()
            }
        }
    }

    private fun sendRequest(
        label: String,
        uri: String,
        payload: JSONObject,
        countsForPreflight: Boolean,
        callback: (JSONObject) -> Unit,
    ): String? {
        val webSocket = socket ?: return null
        if (!registered) return null

        val id = "expert_${requestCounter.incrementAndGet()}"
        pending[id] = PendingRequest(label, countsForPreflight, callback)
        val sent = webSocket.send(
            JSONObject()
                .put("id", id)
                .put("type", "request")
                .put("uri", uri)
                .put("payload", payload)
                .toString(),
        )
        if (!sent) {
            pending.remove(id)
            return null
        }

        mainHandler.postDelayed({
            val timedOut = pending.remove(id) ?: return@postDelayed
            addError("${timedOut.label}: Zeitüberschreitung")
            if (timedOut.countsForPreflight) finishPreflightProbe()
        }, REQUEST_TIMEOUT_MS)
        return id
    }

    private fun mergePreflight(prefix: String, values: Map<String, String>) {
        postState { current ->
            val prefixed = values.mapKeys { (key, _) ->
                if (key.isBlank()) prefix else "$prefix.$key"
            }
            current.copy(preflightValues = current.preflightValues + prefixed)
        }
    }

    private fun finishPreflightProbe() {
        postState { current ->
            if (current.preflightTotal <= 0) return@postState current
            val completed = (current.preflightCompleted + 1)
                .coerceAtMost(current.preflightTotal)
            val finished = completed >= current.preflightTotal
            current.copy(
                preflightCompleted = completed,
                preflightRunning = !finished,
                lastPreflightEpochMillis = if (finished) {
                    System.currentTimeMillis()
                } else {
                    current.lastPreflightEpochMillis
                },
                message = if (finished) {
                    "Vorprüfung abgeschlossen – LG-Menüs können bewusst geöffnet werden"
                } else {
                    "Read-only Vorprüfung läuft … $completed/${current.preflightTotal}"
                },
            )
        }
    }

    private fun flatten(source: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()

        fun visit(prefix: String, value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val path = if (prefix.isBlank()) key else "$prefix.$key"
                        if (!isSensitiveKey(path)) visit(path, value.opt(key))
                    }
                }

                is JSONArray -> if (prefix.isNotBlank()) {
                    result[prefix] = value.toString().take(MAX_VALUE_LENGTH)
                }

                null, JSONObject.NULL -> Unit
                else -> if (prefix.isNotBlank()) {
                    result[prefix] = value.toString().take(MAX_VALUE_LENGTH)
                }
            }
        }

        visit("", source)
        return result
    }

    private fun addError(message: String) {
        postState { current ->
            current.copy(errors = (current.errors + message).distinct().takeLast(20))
        }
    }

    private fun postState(transform: (ExpertServiceState) -> ExpertServiceState) {
        mainHandler.post { state = transform(state) }
    }

    private fun closeSocket(updateUi: Boolean) {
        registered = false
        registrationSent = false
        pending.clear()
        socket?.close(1000, "SmartIR Expert Service beendet")
        socket = null
        activeClient?.dispatcher?.executorService?.shutdown()
        activeClient?.connectionPool?.evictAll()
        activeClient = null

        if (updateUi) {
            postState {
                it.copy(
                    connection = ExpertServiceConnection.DISCONNECTED,
                    message = "Expert Service getrennt",
                    preflightRunning = false,
                )
            }
        }
    }

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    private fun normalizeHost(rawHost: String): String = rawHost.trim()
        .removePrefix("ws://")
        .removePrefix("wss://")
        .substringBefore('/')
        .removeSuffix(":3000")
        .removeSuffix(":3001")

    private fun isSafeHost(host: String): Boolean =
        host.isNotBlank() && host.none { it.isWhitespace() || it == '/' || it == '\\' }

    private fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase(Locale.ROOT)
        return SENSITIVE_KEY_PARTS.any(lower::contains)
    }

    private class ExpertTrustManager(expectedFingerprint: String) : X509TrustManager {
        private val expected = normalizeFingerprint(expectedFingerprint)

        @Volatile var lastFingerprint: String = ""
            private set
        @Volatile var fingerprintMismatch: Boolean = false
            private set

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) {
            val certificate = chain?.firstOrNull()
                ?: throw CertificateException("TV-Zertifikat fehlt")
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString(":") { byte -> "%02X".format(byte) }
            lastFingerprint = fingerprint
            if (expected.isNotBlank() && normalizeFingerprint(fingerprint) != expected) {
                fingerprintMismatch = true
                throw CertificateException("TV-Zertifikat stimmt nicht mit SmartIR überein")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val FACTORY_APP_ID = "com.webos.app.factorywin"

        val SAFETY_WARNINGS = listOf(
            "EZ-ADJUST ist die originale LG-Werksoberfläche und kann dauerhafte Änderungen vornehmen.",
            "ToolOPT1/2/4/5, Paneltyp, PMIC, VCOM sowie Schlüssel- und Modellflags nicht verändern.",
            "White Balance und 22 Point WB nur mit Messgerät und vollständigem Vorher-Backup ändern.",
            "SmartIR öffnet lediglich das Stock-Menü und umgeht weder Passwort noch LG-Berechtigungen.",
        )

        val BLOCKED_WRITE_FAMILIES = listOf(
            "factorymanager/setFactoryOpt",
            "config/setConfigs",
            "pqcontroller/setWhiteBalance",
            "pqcontroller/setNpointWB",
            "pqcontroller/setWbPattern",
            "factorymanager/resetWhiteBalance",
            "External-PQ write",
            "NVRAM/EDID/DRM/Panel writes",
        )

        private val PREFLIGHT_CONFIGS = listOf(
            "tv.model.modelname",
            "tv.hw.SoCChipType",
            "tv.hw.displayType",
            "tv.hw.panelResolution",
            "tv.hw.SoCOutputFrameRate",
            "tv.model.supportHDR",
            "tv.model.supportDolbyVisionHDR",
            "tv.model.supportVRR",
            "tv.model.supportIsf",
            "tv.model.supportOledTconOrbit",
            "tv.nyx.firmwareVersion",
            "tv.nyx.platformVersion",
        )

        private val PREFLIGHT_PICTURE_KEYS = listOf(
            "pictureMode",
            "backlight",
            "brightness",
            "contrast",
            "color",
            "energySaving",
        )

        private val SENSITIVE_KEY_PARTS = listOf(
            "client-key",
            "serial",
            "mac",
            "ipaddress",
            "esn",
            "vsn",
            "widevine",
            "drm",
            "certificate",
            "token",
            "password",
        )

        private const val REQUEST_TIMEOUT_MS = 7_000L
        private const val MAX_VALUE_LENGTH = 4_000

        private fun normalizeFingerprint(value: String): String =
            value.replace(":", "").trim().uppercase(Locale.ROOT)
    }
}
