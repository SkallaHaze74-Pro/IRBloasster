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

enum class DeepLabConnection {
    DISCONNECTED,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR,
}

data class DeepLabValue(
    val key: String,
    val value: String,
)

data class DeepLabState(
    val connection: DeepLabConnection = DeepLabConnection.DISCONNECTED,
    val message: String = "Deep Scan bereit",
    val host: String = "",
    val scanning: Boolean = false,
    val completedRequests: Int = 0,
    val totalRequests: Int = 0,
    val systemInfo: List<DeepLabValue> = emptyList(),
    val capabilities: List<DeepLabValue> = emptyList(),
    val settings: Map<String, List<DeepLabValue>> = emptyMap(),
    val liveStatus: List<DeepLabValue> = emptyList(),
    val installedApps: List<DeepLabValue> = emptyList(),
    val availableSafeHiddenApps: List<String> = emptyList(),
    val pqSnapshots: Map<String, String> = emptyMap(),
    val errors: List<String> = emptyList(),
    val secureTransport: Boolean = false,
    val lastScanEpochMillis: Long = 0L,
)

/**
 * More exhaustive, still read-only scanner for the user's own LG webOS TV.
 *
 * The scanner deliberately has no setSystemSettings, setConfigs, External-PQ
 * writer, White-Balance writer, panel writer, service-reset or NVRAM writer.
 * Unsupported endpoints are isolated so one bad key no longer cancels an
 * entire settings category.
 */
class DeepTvLabClient(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private data class PendingProbe(
        val label: String,
        val quiet: Boolean,
        val callback: (JSONObject) -> Unit,
    )

    private data class Probe(
        val label: String,
        val uri: String,
        val payload: JSONObject = JSONObject(),
        val quiet: Boolean = false,
        val callback: (JSONObject) -> Unit,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestCounter = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, PendingProbe>()

    private var socket: WebSocket? = null
    private var activeClient: OkHttpClient? = null
    private var trustManager: DeepLabTrustManager? = null
    private var registered = false
    private var registrationSent = false
    private var fallbackTried = false
    private var activeSecureTransport = false

    var state by mutableStateOf(
        DeepLabState(host = settingsRepository.webOsHost),
    )
        private set

    fun connectAndScan(): Boolean {
        val host = normalizeHost(settingsRepository.webOsHost)
        if (host.isBlank() || !isSafeHost(host)) {
            postState {
                it.copy(
                    connection = DeepLabConnection.ERROR,
                    message = "Im normalen SmartIR-Setup zuerst eine gültige TV-IP speichern",
                )
            }
            return false
        }

        closeSocket(updateUi = false)
        fallbackTried = false
        postState {
            it.copy(
                connection = DeepLabConnection.CONNECTING,
                message = "Deep Scan verbindet sich sicher mit $host …",
                host = host,
                errors = emptyList(),
            )
        }
        openSecure(host)
        return true
    }

    fun rescan(): Boolean {
        if (!registered) return connectAndScan()
        scanAll()
        return true
    }

    fun close() {
        closeSocket(updateUi = true)
    }

    fun launchSafeHiddenApp(appId: String): Boolean {
        if (appId !in SAFE_HIDDEN_APPS.keys) return false
        return sendRequest(
            label = "App starten: $appId",
            uri = "ssap://system.launcher/launch",
            payload = JSONObject().put("id", appId),
            quiet = false,
        ) { } != null
    }

    fun anonymizedReport(): String {
        val snapshot = state
        return JSONObject()
            .put("schemaVersion", 2)
            .put("generatedAtEpochMillis", System.currentTimeMillis())
            .put("mode", "deep-read-only")
            .put("systemInfo", valuesToJson(snapshot.systemInfo))
            .put("capabilities", valuesToJson(snapshot.capabilities))
            .put("liveStatus", valuesToJson(snapshot.liveStatus))
            .put(
                "settings",
                JSONObject().also { categories ->
                    snapshot.settings.forEach { (category, values) ->
                        categories.put(category, valuesToJson(values))
                    }
                },
            )
            .put("installedApps", valuesToJson(snapshot.installedApps))
            .put("availableSafeHiddenApps", JSONArray(snapshot.availableSafeHiddenApps))
            .put("externalPqReadCandidates", JSONObject(snapshot.pqSnapshots))
            .put("errors", JSONArray(snapshot.errors))
            .toString(2)
    }

    private fun openSecure(host: String) {
        val localTrustManager = DeepLabTrustManager(
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
                connection = DeepLabConnection.CONNECTING,
                message = "WSS 3001 nicht erreichbar – lokaler Port 3000 wird getestet",
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
                            connection = DeepLabConnection.PAIRING,
                            message = "Deep Scan wird autorisiert – Abfrage am TV bei Bedarf bestätigen",
                            secureTransport = secure,
                        )
                    }
                    webSocket.send(helloMessage().toString())
                    mainHandler.postDelayed({ sendRegistration(webSocket) }, 450L)
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
                                connection = DeepLabConnection.ERROR,
                                message = if (mismatch) {
                                    "TV-Zertifikat hat sich geändert. Kopplung im normalen SmartIR bewusst erneuern."
                                } else {
                                    throwable.message ?: "Deep Scan konnte keine Verbindung aufbauen"
                                },
                                scanning = false,
                            )
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    registered = false
                    postState {
                        it.copy(
                            connection = DeepLabConnection.DISCONNECTED,
                            message = reason.ifBlank { "Deep Scan getrennt" },
                            scanning = false,
                        )
                    }
                }
            },
        )
    }

    private fun helloMessage(): JSONObject = JSONObject()
        .put("id", "deep_hello_${requestCounter.incrementAndGet()}")
        .put("type", "hello")
        .put(
            "payload",
            JSONObject()
                .put("sdkVersion", BuildConfig.VERSION_NAME)
                .put("deviceModel", Build.MODEL)
                .put("OSVersion", Build.VERSION.SDK_INT.toString())
                .put("appId", appContext.packageName)
                .put("appName", "SmartIR TV Lab Pro")
                .put("appRegion", Locale.getDefault().country),
        )

    private fun sendRegistration(webSocket: WebSocket) {
        if (registrationSent) return
        registrationSent = true
        webSocket.send(
            WebOsRegistrationProfile.registrationMessage(
                id = "deep_register_${requestCounter.incrementAndGet()}",
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
                        connection = DeepLabConnection.CONNECTED,
                        message = "TV-Labor Pro verbunden – Deep Scan startet",
                        secureTransport = activeSecureTransport,
                    )
                }
                scanAll()
            }

            "response" -> {
                val probe = pending.remove(id) ?: return
                val returnValue = if (payload.has("returnValue")) {
                    payload.optBoolean("returnValue", true)
                } else {
                    true
                }
                if (returnValue) {
                    runCatching { probe.callback(payload) }
                        .onFailure { error ->
                            addError("${probe.label}: ${error.message ?: "Antwort konnte nicht verarbeitet werden"}")
                        }
                } else if (!probe.quiet) {
                    addError(
                        "${probe.label}: ${payload.optString("errorText", "Application error")}",
                    )
                }
                finishProbe()
            }

            "error" -> {
                val probe = pending.remove(id)
                val errorText = message.optString("error", "webOS-Anfrage fehlgeschlagen")
                if (probe?.quiet != true) {
                    addError("${probe?.label ?: id}: $errorText")
                }
                finishProbe()
            }
        }
    }

    private fun scanAll() {
        val probes = buildProbes()
        postState {
            it.copy(
                scanning = true,
                completedRequests = 0,
                totalRequests = probes.size,
                message = "Hardware, Dienste, Einzelwerte und read-only PQ-Kandidaten werden geprüft …",
                systemInfo = emptyList(),
                capabilities = emptyList(),
                settings = emptyMap(),
                liveStatus = emptyList(),
                installedApps = emptyList(),
                availableSafeHiddenApps = emptyList(),
                pqSnapshots = emptyMap(),
                errors = emptyList(),
            )
        }

        probes.forEach { probe ->
            if (
                sendRequest(
                    label = probe.label,
                    uri = probe.uri,
                    payload = probe.payload,
                    quiet = probe.quiet,
                    callback = probe.callback,
                ) == null
            ) {
                if (!probe.quiet) addError("${probe.label}: Anfrage konnte nicht gesendet werden")
                finishProbe()
            }
        }
    }

    private fun buildProbes(): List<Probe> = buildList {
        add(
            Probe(
                label = "Systeminformationen",
                uri = "ssap://system/getSystemInfo",
            ) { payload -> replaceSystem(flattenObject(payload)) },
        )

        add(
            Probe(
                label = "Hardware- und Funktionsscan",
                uri = "ssap://config/getConfigs",
                payload = JSONObject().put("configNames", JSONArray(CONFIG_NAMES)),
            ) { payload ->
                val source = payload.optJSONObject("configs")
                    ?: payload.optJSONObject("config")
                    ?: payload
                replaceCapabilities(flattenObject(source))
            },
        )

        STATUS_PROBES.forEach { (label, uri) ->
            add(
                Probe(
                    label = label,
                    uri = uri,
                    quiet = true,
                ) { payload -> mergeLiveStatus(label, flattenObject(payload)) },
            )
        }

        SETTINGS_READS.forEach { (category, keys) ->
            keys.forEach { key ->
                add(
                    Probe(
                        label = "Einstellung $category/$key",
                        uri = "ssap://settings/getSystemSettings",
                        payload = JSONObject()
                            .put("category", category)
                            .put("keys", JSONArray().put(key)),
                        quiet = true,
                    ) { payload ->
                        val source = payload.optJSONObject("settings") ?: payload
                        mergeSetting(category, flattenObject(source))
                    },
                )
            }
        }

        add(
            Probe(
                label = "Installierte Apps",
                uri = "ssap://com.webos.applicationManager/listApps",
            ) { payload -> parseApps(payload) },
        )

        add(
            Probe(
                label = "Verfügbare webOS-Dienste",
                uri = "ssap://api/getServiceList",
                quiet = true,
            ) { payload -> mergeLiveStatus("serviceList", flattenObject(payload)) },
        )

        PQ_READ_CANDIDATES.forEach { candidate ->
            val label = "PQ ${candidate.first}/${candidate.second}"
            add(
                Probe(
                    label = label,
                    uri = "ssap://externalpq/getExternalPqData",
                    payload = JSONObject()
                        .put("command", candidate.first)
                        .put("picMode", candidate.second),
                    quiet = true,
                ) { payload -> storePqSnapshot(label, payload) },
            )
        }
    }

    private fun sendRequest(
        label: String,
        uri: String,
        payload: JSONObject,
        quiet: Boolean,
        callback: (JSONObject) -> Unit,
    ): String? {
        val webSocket = socket ?: return null
        if (!registered) return null

        val id = "deep_${requestCounter.incrementAndGet()}"
        pending[id] = PendingProbe(label, quiet, callback)
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
            if (!timedOut.quiet) addError("${timedOut.label}: Zeitüberschreitung")
            finishProbe()
        }, REQUEST_TIMEOUT_MS)
        return id
    }

    private fun finishProbe() {
        mainHandler.post {
            val current = state
            if (current.totalRequests <= 0) return@post
            val completed = (current.completedRequests + 1).coerceAtMost(current.totalRequests)
            val finished = completed >= current.totalRequests
            state = current.copy(
                completedRequests = completed,
                scanning = !finished,
                lastScanEpochMillis = if (finished) {
                    System.currentTimeMillis()
                } else {
                    current.lastScanEpochMillis
                },
                message = if (finished) {
                    "Deep Scan abgeschlossen: ${current.capabilities.size} Funktionen, " +
                        "${current.settings.values.sumOf { it.size }} Einstellungswerte, " +
                        "${current.liveStatus.size} Statuswerte"
                } else {
                    "Deep Scan läuft … $completed/${current.totalRequests}"
                },
            )
        }
    }

    private fun replaceSystem(values: List<DeepLabValue>) {
        postState { it.copy(systemInfo = sortValues(values)) }
    }

    private fun replaceCapabilities(values: List<DeepLabValue>) {
        postState { it.copy(capabilities = sortValues(values)) }
    }

    private fun mergeLiveStatus(prefix: String, values: List<DeepLabValue>) {
        val prefixed = values.map { value ->
            DeepLabValue(
                key = if (value.key.isBlank()) prefix else "$prefix.${value.key}",
                value = value.value,
            )
        }
        postState { current ->
            current.copy(liveStatus = sortValues(current.liveStatus + prefixed))
        }
    }

    private fun mergeSetting(category: String, values: List<DeepLabValue>) {
        postState { current ->
            val existing = current.settings[category].orEmpty()
            current.copy(
                settings = current.settings + (
                    category to sortValues(existing + values)
                ),
            )
        }
    }

    private fun parseApps(payload: JSONObject) {
        val array = payload.optJSONArray("apps") ?: JSONArray()
        val apps = mutableListOf<DeepLabValue>()
        val ids = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank() || isSensitiveKey(id)) continue
            ids += id
            val title = item.optString("title", id)
            val visible = if (item.has("visible")) item.optBoolean("visible") else null
            val value = if (visible == null) title else "$title · visible=$visible"
            apps += DeepLabValue(id, value)
        }
        postState {
            it.copy(
                installedApps = sortValues(apps),
                availableSafeHiddenApps = SAFE_HIDDEN_APPS.keys.filter(ids::contains),
            )
        }
    }

    private fun storePqSnapshot(label: String, payload: JSONObject) {
        val clean = redactObject(payload)
        val text = clean.toString(2).take(MAX_PQ_LENGTH)
        if (text.isBlank() || text == "{}") return
        postState { current ->
            if (current.pqSnapshots.isNotEmpty()) current
            else current.copy(pqSnapshots = mapOf(label to text))
        }
    }

    private fun flattenObject(source: JSONObject): List<DeepLabValue> {
        val result = mutableListOf<DeepLabValue>()

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

                is JSONArray -> result += DeepLabValue(prefix, safeValue(value))
                null, JSONObject.NULL -> Unit
                else -> result += DeepLabValue(prefix, safeValue(value))
            }
        }

        visit("", source)
        return result.filter { it.key.isNotBlank() }
    }

    private fun sortValues(values: List<DeepLabValue>): List<DeepLabValue> = values
        .filterNot { isSensitiveKey(it.key) }
        .distinctBy { it.key }
        .sortedBy { it.key.lowercase(Locale.ROOT) }

    private fun safeValue(value: Any): String = when (value) {
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }.take(MAX_VALUE_LENGTH)

    private fun addError(message: String) {
        postState { current ->
            current.copy(errors = (current.errors + message).distinct().takeLast(40))
        }
    }

    private fun postState(transform: (DeepLabState) -> DeepLabState) {
        mainHandler.post { state = transform(state) }
    }

    private fun closeSocket(updateUi: Boolean) {
        registered = false
        registrationSent = false
        pending.clear()
        socket?.close(1000, "SmartIR TV Lab Pro beendet")
        socket = null
        activeClient?.dispatcher?.executorService?.shutdown()
        activeClient?.connectionPool?.evictAll()
        activeClient = null

        if (updateUi) {
            postState {
                it.copy(
                    connection = DeepLabConnection.DISCONNECTED,
                    message = "Deep Scan getrennt",
                    scanning = false,
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

    private fun valuesToJson(values: List<DeepLabValue>): JSONObject =
        JSONObject().also { json -> values.forEach { json.put(it.key, it.value) } }

    private fun redactObject(source: JSONObject): JSONObject = JSONObject().also { clean ->
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isSensitiveKey(key)) continue
            val value = source.opt(key)
            clean.put(
                key,
                when (value) {
                    is JSONObject -> redactObject(value)
                    is JSONArray -> JSONArray().also { array ->
                        for (index in 0 until value.length()) {
                            val item = value.opt(index)
                            array.put(if (item is JSONObject) redactObject(item) else item)
                        }
                    }
                    else -> value
                },
            )
        }
    }

    private class DeepLabTrustManager(expectedFingerprint: String) : X509TrustManager {
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
        val SAFE_HIDDEN_APPS = linkedMapOf(
            "com.webos.app.self-diagnosis" to "Quick Help / Selbstdiagnose",
            "com.webos.app.gameoptimizer" to "Game Optimizer",
            "com.webos.app.miracast" to "Screen Share",
            "com.webos.app.btspeakerapp" to "Bluetooth Audio Playback",
            "com.webos.app.btsurroundautotuning" to "Bluetooth Surround Auto Tuning",
            "com.webos.app.onetouchsoundtuning" to "One Touch Sound Tuning",
            "com.webos.app.channeledit" to "Sender-Manager",
            "com.webos.app.channelsetting" to "Sendersuche",
            "com.webos.app.scheduler" to "TV-Planer",
            "com.webos.app.recordings" to "Aufnahmen",
            "com.webos.app.notificationcenter" to "Benachrichtigungen",
            "com.webos.app.connectionwizard" to "Universalsteuerung",
            "com.webos.app.homeconnect" to "Home Dashboard",
            "com.webos.app.roomconnect" to "Room-to-Room Share",
            "com.webos.app.appcasting" to "App Casting",
            "com.webos.app.igallery" to "Art Gallery",
        )

        private val STATUS_PROBES = linkedMapOf(
            "software" to "ssap://com.webos.service.update/getCurrentSWInformation",
            "foregroundApp" to "ssap://com.webos.applicationManager/getForegroundAppInfo",
            "volume" to "ssap://audio/getVolume",
            "audioStatus" to "ssap://audio/getStatus",
            "soundOutput" to "ssap://com.webos.service.apiadapter/audio/getSoundOutput",
            "power" to "ssap://com.webos.service.tvpower/power/getPowerState",
            "inputs" to "ssap://tv/getExternalInputList",
            "currentChannel" to "ssap://tv/getCurrentChannel",
        )

        private val CONFIG_NAMES = listOf(
            "tv.model.modelname",
            "tv.hw.SoCChipType",
            "tv.hw.dramSize",
            "tv.hw.emmcSize",
            "tv.hw.eepromSize",
            "tv.hw.displayType",
            "tv.hw.panelResolution",
            "tv.hw.SoCOutputFrameRate",
            "tv.model.cellType",
            "tv.model.moduleInchType",
            "tv.model.motionProMode",
            "tv.model.motionRemoconType",
            "tv.model.soundModeType",
            "tv.model.supportDolbyVisionHDR",
            "tv.model.supportHDR",
            "tv.model.supportVRR",
            "tv.model.supportIsf",
            "tv.model.supportWiSA",
            "tv.model.supportOledTconOrbit",
            "tv.model.supportLocalDimming",
            "tv.model.supportHeadPhone",
            "tv.model.supportAudioLineOut",
            "tv.model.supportBluetoothSurround",
            "tv.model.supportOledCare",
            "tv.model.supportGameOptimizer",
            "tv.model.supportHGiG",
            "tv.model.supportALLM",
            "tv.model.supportFreeSync",
            "tv.model.supportGSync",
            "tv.hw.supportOptic",
            "tv.hw.supportSatellite",
            "tv.hw.supportT2Tuner",
            "tv.hw.supportTripleTuner",
            "system.supportBluetoothFeatures",
            "system.supportVoiceRecognition",
            "tv.nyx.firmwareVersion",
            "tv.nyx.platformVersion",
            "tv.nyx.bootloaderVersion",
            "tv.nyx.tvBroadcastSystem",
            "tv.rmm.dvrReady",
            "tv.conti.supportRemoteService",
            "tv.conti.supportSignalTest",
            "tv.conti.supportUsedTime",
        )

        private val SETTINGS_READS = linkedMapOf(
            "network" to listOf(
                "deviceName",
                "wolwowlOnOff",
                "bleAdvertisingOnOff",
            ),
            "picture" to listOf(
                "pictureMode",
                "brightness",
                "backlight",
                "contrast",
                "color",
                "tint",
                "sharpness",
                "colorTemperature",
                "energySaving",
                "blackLevel",
                "gamma",
                "hdrDynamicToneMapping",
                "truMotion",
                "oledMotionPro",
                "superResolution",
                "noiseReduction",
                "mpegNoiseReduction",
                "smoothGradation",
                "colorGamut",
            ),
            "sound" to listOf(
                "avSync",
                "avSyncSpdif",
                "avSyncBypassInput",
                "eArcSupport",
                "soundOutput",
                "soundOutputDigital",
                "soundMode",
                "tvSetupConfiguration",
            ),
            "other" to listOf(
                "simplinkEnable",
                "ueiEnable",
            ),
            "option" to listOf(
                "audioGuidance",
                "country",
                "zipcode",
                "livePlus",
                "firstTvSignalStatus",
                "localeCountryGroup",
                "countryBroadcastSystem",
            ),
            "general" to listOf(
                "alwaysOn",
                "tvOnScreen",
                "tvInstallMethod",
                "powerOffBySCA3SystemChanged",
                "SCA3SystemCountry",
            ),
        )

        private val PQ_READ_CANDIDATES = listOf(
            "1D_DPG_DATA" to "expert1",
            "1D_DPG_DATA" to "expert2",
            "1D_DPG_DATA" to "cinema",
            "1D_DPG_DATA" to "game",
            "1D_DPG_DATA" to "hdrCinema",
            "1D_DPG_DATA" to "hdrGame",
            "1D_DPG_DATA" to "dolbyHdrCinema",
            "3D_LUT_DATA" to "expert1",
            "3D_LUT_DATA" to "expert2",
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
        private const val MAX_PQ_LENGTH = 120_000

        private fun normalizeFingerprint(value: String): String =
            value.replace(":", "").trim().uppercase(Locale.ROOT)
    }
}
