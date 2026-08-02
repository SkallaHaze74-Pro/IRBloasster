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

enum class TvLabConnection {
    DISCONNECTED,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR,
}

enum class TvLabProfile(val title: String) {
    SDR_DARK("SDR · dunkler Raum"),
    SDR_BRIGHT("SDR · heller Raum"),
    HDR_CINEMA("HDR / Dolby Vision"),
    GAMING("Gaming / VRR"),
}

enum class TvLabAdviceLevel {
    INFO,
    CHECK,
    IMPORTANT,
}

data class TvLabValue(
    val key: String,
    val value: String,
)

data class TvLabAdvice(
    val title: String,
    val detail: String,
    val level: TvLabAdviceLevel = TvLabAdviceLevel.INFO,
)

data class TvLabState(
    val connection: TvLabConnection = TvLabConnection.DISCONNECTED,
    val message: String = "TV-Labor bereit",
    val host: String = "",
    val scanning: Boolean = false,
    val completedRequests: Int = 0,
    val totalRequests: Int = 0,
    val lastScanEpochMillis: Long = 0L,
    val profile: TvLabProfile = TvLabProfile.SDR_DARK,
    val systemInfo: List<TvLabValue> = emptyList(),
    val capabilities: List<TvLabValue> = emptyList(),
    val settings: Map<String, List<TvLabValue>> = emptyMap(),
    val availableHiddenApps: List<String> = emptyList(),
    val pqSnapshot: String = "",
    val advice: List<TvLabAdvice> = emptyList(),
    val errors: List<String> = emptyList(),
    val secureTransport: Boolean = false,
)

/**
 * Read-only-first laboratory connection for the user's own LG webOS TV.
 *
 * The client deliberately exposes no settings writer and no External-PQ writer.
 * It reads capabilities, selected whitelisted settings, installed apps and an
 * optional External-PQ snapshot, then creates local recommendations. A real
 * colourimeter can be integrated later without changing the safety boundary.
 */
class TvLabClient(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private data class PendingRequest(
        val label: String,
        val callback: (JSONObject) -> Unit,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestCounter = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, PendingRequest>()

    private var socket: WebSocket? = null
    private var activeClient: OkHttpClient? = null
    private var trustManager: LabTrustManager? = null
    private var registered = false
    private var registrationSent = false
    private var fallbackTried = false
    private var activeSecureTransport = false

    var state by mutableStateOf(
        TvLabState(host = settingsRepository.webOsHost),
    )
        private set

    fun connectAndScan(): Boolean {
        val host = normalizeHost(settingsRepository.webOsHost)
        if (host.isBlank() || !isSafeHost(host)) {
            updateState {
                it.copy(
                    connection = TvLabConnection.ERROR,
                    message = "Im SmartIR-Setup zuerst eine gültige TV-IP speichern",
                )
            }
            return false
        }

        closeSocket(updateUi = false)
        fallbackTried = false
        updateState {
            it.copy(
                connection = TvLabConnection.CONNECTING,
                message = "TV-Labor verbindet sich sicher mit $host …",
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

    fun setProfile(profile: TvLabProfile) {
        updateState { it.copy(profile = profile) }
        rebuildAdvice(profile)
    }

    fun launchSafeHiddenApp(appId: String): Boolean {
        if (appId !in SAFE_HIDDEN_APPS.keys) return false
        return request(
            uri = "ssap://system.launcher/launch",
            payload = JSONObject().put("id", appId),
            label = "App starten: $appId",
        ) { } 
    }

    fun anonymizedReport(): String {
        val snapshot = state
        return JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAtEpochMillis", System.currentTimeMillis())
            .put("mode", "read-only-first")
            .put("profile", snapshot.profile.name)
            .put("systemInfo", valuesToJson(snapshot.systemInfo))
            .put("capabilities", valuesToJson(snapshot.capabilities))
            .put(
                "settings",
                JSONObject().also { categories ->
                    snapshot.settings.forEach { (category, values) ->
                        categories.put(category, valuesToJson(values))
                    }
                },
            )
            .put("availableSafeHiddenApps", JSONArray(snapshot.availableHiddenApps))
            .put("externalPqSnapshotAvailable", snapshot.pqSnapshot.isNotBlank())
            .put(
                "recommendations",
                JSONArray().also { array ->
                    snapshot.advice.forEach { advice ->
                        array.put(
                            JSONObject()
                                .put("level", advice.level.name)
                                .put("title", advice.title)
                                .put("detail", advice.detail),
                        )
                    }
                },
            )
            .put("errors", JSONArray(snapshot.errors))
            .toString(2)
    }

    fun pqBackup(): String {
        val snapshot = state
        return JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAtEpochMillis", System.currentTimeMillis())
            .put("model", findSystemValue("modelName", "model_name"))
            .put("firmware", findSystemValue("firmwareVersion", "sdkVersion"))
            .put("source", "ssap://externalpq/getExternalPqData")
            .put("readOnly", true)
            .put("payload", runCatching { JSONObject(snapshot.pqSnapshot) }.getOrNull() ?: snapshot.pqSnapshot)
            .toString(2)
    }

    private fun openSecure(host: String) {
        val localTrustManager = LabTrustManager(
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
        updateState {
            it.copy(
                connection = TvLabConnection.CONNECTING,
                message = "Port 3001 nicht erreichbar – lokaler Port 3000 wird getestet",
            )
        }
        val client = baseClientBuilder().build()
        openSocket(client, "ws://$host:3000/", secure = false, host = host)
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
                    updateState {
                        it.copy(
                            connection = TvLabConnection.PAIRING,
                            message = "TV-Labor wird autorisiert – Abfrage am TV bei Bedarf bestätigen",
                            secureTransport = secure,
                        )
                    }
                    webSocket.send(helloMessage().toString())
                    mainHandler.postDelayed({ sendRegistration(webSocket) }, 500L)
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
                        updateState {
                            it.copy(
                                connection = TvLabConnection.ERROR,
                                message = if (mismatch) {
                                    "TV-Zertifikat hat sich geändert. Kopplung im SmartIR-Setup bewusst erneuern."
                                } else {
                                    throwable.message ?: "TV-Labor konnte keine Verbindung aufbauen"
                                },
                                scanning = false,
                            )
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    registered = false
                    updateState {
                        it.copy(
                            connection = TvLabConnection.DISCONNECTED,
                            message = reason.ifBlank { "TV-Labor getrennt" },
                            scanning = false,
                        )
                    }
                }
            },
        )
    }

    private fun helloMessage(): JSONObject = JSONObject()
        .put("id", "lab_hello_${requestCounter.incrementAndGet()}")
        .put("type", "hello")
        .put(
            "payload",
            JSONObject()
                .put("sdkVersion", BuildConfig.VERSION_NAME)
                .put("deviceModel", Build.MODEL)
                .put("OSVersion", Build.VERSION.SDK_INT.toString())
                .put("appId", appContext.packageName)
                .put("appName", "SmartIR TV Lab")
                .put("appRegion", Locale.getDefault().country),
        )

    private fun sendRegistration(webSocket: WebSocket) {
        if (registrationSent) return
        registrationSent = true
        webSocket.send(
            WebOsRegistrationProfile.registrationMessage(
                id = "lab_register_${requestCounter.incrementAndGet()}",
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

                updateState {
                    it.copy(
                        connection = TvLabConnection.CONNECTED,
                        message = "TV-Labor verbunden – Scan startet",
                        secureTransport = activeSecureTransport,
                    )
                }
                scanAll()
            }

            "response" -> {
                val request = pending.remove(id) ?: return
                runCatching { request.callback(payload) }
                    .onFailure { addError("${request.label}: ${it.message ?: "Antwort konnte nicht verarbeitet werden"}") }
                completeRequest()
            }

            "error" -> {
                val request = pending.remove(id)
                val error = message.optString("error", "webOS-Anfrage fehlgeschlagen")
                addError("${request?.label ?: id}: $error")
                completeRequest()
            }
        }
    }

    private fun scanAll() {
        val requestCount = 2 + SETTINGS_READS.size + 2
        updateState {
            it.copy(
                scanning = true,
                completedRequests = 0,
                totalRequests = requestCount,
                message = "Hardware, Funktionen und sichere Einstellungen werden gelesen …",
                systemInfo = emptyList(),
                capabilities = emptyList(),
                settings = emptyMap(),
                availableHiddenApps = emptyList(),
                pqSnapshot = "",
                advice = emptyList(),
                errors = emptyList(),
            )
        }

        requestOrComplete(
            uri = "ssap://system/getSystemInfo",
            label = "Systeminformationen",
        ) { payload ->
            val values = SYSTEM_INFO_KEYS.mapNotNull { key ->
                payload.opt(key)?.let { TvLabValue(key, safeValue(it)) }
            }
            updateState { it.copy(systemInfo = values) }
        }

        requestOrComplete(
            uri = "ssap://config/getConfigs",
            payload = JSONObject().put("configNames", JSONArray(CONFIG_NAMES)),
            label = "Hardware- und Funktionsscan",
        ) { payload ->
            val source = payload.optJSONObject("configs")
                ?: payload.optJSONObject("config")
                ?: payload
            updateState {
                it.copy(capabilities = flattenObject(source).sortedBy(TvLabValue::key))
            }
        }

        SETTINGS_READS.forEach { (category, keys) ->
            requestOrComplete(
                uri = "ssap://settings/getSystemSettings",
                payload = JSONObject()
                    .put("category", category)
                    .put("keys", JSONArray(keys)),
                label = "Einstellungen: $category",
            ) { payload ->
                val source = payload.optJSONObject("settings") ?: payload
                val values = flattenObject(source).sortedBy(TvLabValue::key)
                updateState { current ->
                    current.copy(settings = current.settings + (category to values))
                }
            }
        }

        requestOrComplete(
            uri = "ssap://com.webos.applicationManager/listApps",
            label = "Sichere versteckte LG-Apps",
        ) { payload ->
            val apps = payload.optJSONArray("apps") ?: JSONArray()
            val ids = buildSet {
                for (index in 0 until apps.length()) {
                    apps.optJSONObject(index)?.optString("id")
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
            updateState {
                it.copy(availableHiddenApps = SAFE_HIDDEN_APPS.keys.filter(ids::contains))
            }
        }

        requestOrComplete(
            uri = "ssap://externalpq/getExternalPqData",
            label = "Picture-Quality-Snapshot",
        ) { payload ->
            updateState { it.copy(pqSnapshot = payload.toString(2)) }
        }
    }

    private fun requestOrComplete(
        uri: String,
        payload: JSONObject? = null,
        label: String,
        callback: (JSONObject) -> Unit,
    ) {
        if (!request(uri, payload, label, callback)) {
            addError("$label: Anfrage konnte nicht gesendet werden")
            completeRequest()
        }
    }

    private fun request(
        uri: String,
        payload: JSONObject? = null,
        label: String,
        callback: (JSONObject) -> Unit,
    ): Boolean {
        val webSocket = socket ?: return false
        if (!registered) return false

        val id = "lab_${requestCounter.incrementAndGet()}"
        pending[id] = PendingRequest(label, callback)
        val sent = webSocket.send(
            JSONObject()
                .put("id", id)
                .put("type", "request")
                .put("uri", uri)
                .put("payload", payload ?: JSONObject())
                .toString(),
        )
        if (!sent) {
            pending.remove(id)
            return false
        }

        mainHandler.postDelayed({
            val timedOut = pending.remove(id) ?: return@postDelayed
            addError("${timedOut.label}: Zeitüberschreitung")
            completeRequest()
        }, REQUEST_TIMEOUT_MS)
        return true
    }

    private fun completeRequest() {
        updateState { current ->
            val completed = (current.completedRequests + 1).coerceAtMost(current.totalRequests)
            val finished = current.totalRequests > 0 && completed >= current.totalRequests
            current.copy(
                completedRequests = completed,
                scanning = !finished,
                lastScanEpochMillis = if (finished) System.currentTimeMillis() else current.lastScanEpochMillis,
                message = if (finished) {
                    "Scan abgeschlossen: ${current.capabilities.size} Funktionen und ${current.settings.values.sumOf(List<TvLabValue>::size)} Einstellungswerte"
                } else {
                    "TV-Labor scannt … $completed/${current.totalRequests}"
                },
            )
        }
        if (state.totalRequests > 0 && state.completedRequests >= state.totalRequests) {
            rebuildAdvice(state.profile)
        }
    }

    private fun rebuildAdvice(profile: TvLabProfile) {
        val snapshot = state
        val advice = mutableListOf<TvLabAdvice>()

        advice += TvLabAdvice(
            title = "Sicherheitsgrenze aktiv",
            detail = "SmartIR liest und sichert. External-PQ-, LUT-, White-Balance-, Panel- und Servicewerte werden nicht automatisch geschrieben.",
            level = TvLabAdviceLevel.IMPORTANT,
        )

        val energySaving = findSetting(snapshot, "energysaving")
        if (!energySaving.isNullOrBlank() && !isOffValue(energySaving)) {
            advice += TvLabAdvice(
                title = "Energiesparen für Messungen prüfen",
                detail = "Aktuell gemeldet: $energySaving. Automatische Helligkeitsänderungen verfälschen Vergleichsmessungen; vor einer Kalibrierung manuell kontrollieren.",
                level = TvLabAdviceLevel.CHECK,
            )
        }

        findNumericSetting(snapshot, "brightness")?.let { value ->
            if (value !in 45..55) {
                advice += TvLabAdvice(
                    title = "Schwarzpegel mit PLUGE-Testbild prüfen",
                    detail = "Der gemeldete Helligkeitswert ist $value. Nicht blind auf 50 setzen: zuerst prüfen, ob Referenzschwarz unsichtbar und Near-Black noch unterscheidbar ist.",
                    level = TvLabAdviceLevel.CHECK,
                )
            }
        }

        findNumericSetting(snapshot, "contrast")?.let { value ->
            if (value > 95 || value < 80) {
                advice += TvLabAdvice(
                    title = "Weiß-Clipping kontrollieren",
                    detail = "Kontrast $value ist auffällig. Mit dem TV-Testbild prüfen, ob helle Abstufungen zusammenfallen; Änderungen nur schrittweise und mit Vorher-Snapshot.",
                    level = TvLabAdviceLevel.CHECK,
                )
            }
        }

        findNumericSetting(snapshot, "color", "colour")?.let { value ->
            if (value !in 45..55) {
                advice += TvLabAdvice(
                    title = "Farbsättigung verifizieren",
                    detail = "Farbwert $value liegt außerhalb des neutralen Prüfbereichs. Für echte Genauigkeit ist ein Colorimeter nötig; die Handy-Kamera reicht nur für grobe Vergleichsmessungen.",
                    level = TvLabAdviceLevel.CHECK,
                )
            }
        }

        when (profile) {
            TvLabProfile.SDR_DARK -> advice += TvLabAdvice(
                "SDR-Abendprofil",
                "OLED-/Panellicht so niedrig wählen, dass Weiß angenehm bleibt. Gamma, Schwarzwert und Kontrast mit Testbildern prüfen; keine Werte aus einem hellen Tagesprofil übernehmen.",
            )

            TvLabProfile.SDR_BRIGHT -> advice += TvLabAdvice(
                "SDR-Tagesprofil",
                "Nur die Lichtleistung an den Raum anpassen. Schwarzwert, Farbraum und Weißabgleich nicht zur Erzeugung von mehr Helligkeit verbiegen.",
            )

            TvLabProfile.HDR_CINEMA -> advice += TvLabAdvice(
                "HDR getrennt behandeln",
                "HDR und Dolby Vision besitzen eigene Bildmodi. Keine SDR-Kalibrierwerte blind übertragen; Highlight-Clipping, Tonemapping und Near-Black separat messen.",
                TvLabAdviceLevel.IMPORTANT,
            )

            TvLabProfile.GAMING -> advice += TvLabAdvice(
                "Gaming-Pipeline prüfen",
                "Game Optimizer, ALLM/VRR und den tatsächlich verwendeten HDMI-Port getrennt prüfen. Geringe Latenz und Bildgenauigkeit müssen im aktiven Spielmodus gemessen werden.",
                TvLabAdviceLevel.IMPORTANT,
            )
        }

        val supportsVrr = snapshot.capabilities.any {
            it.key.contains("supportVRR", ignoreCase = true) && isOnValue(it.value)
        }
        if (profile == TvLabProfile.GAMING && supportsVrr) {
            advice += TvLabAdvice(
                "VRR erkannt",
                "Der TV meldet VRR-Unterstützung. SmartIR kann Game Optimizer sicher öffnen; die aktive Bildrate muss anschließend am echten Eingang geprüft werden.",
            )
        }

        if (snapshot.pqSnapshot.isNotBlank()) {
            advice += TvLabAdvice(
                "PQ-Backup verfügbar",
                "Die read-only External-PQ-Antwort wurde gespeichert und kann separat kopiert werden. Sie ist noch keine Erlaubnis zum Zurückschreiben.",
            )
        } else {
            advice += TvLabAdvice(
                "PQ-Schnittstelle nicht lesbar",
                "Der TV hat keinen verwertbaren External-PQ-Datensatz geliefert. Der normale Hardware-/Einstellungsscan bleibt trotzdem nutzbar.",
            )
        }

        updateState { it.copy(advice = advice) }
    }

    private fun flattenObject(source: JSONObject): List<TvLabValue> {
        val result = mutableListOf<TvLabValue>()

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

                is JSONArray -> result += TvLabValue(prefix, safeValue(value))
                null, JSONObject.NULL -> Unit
                else -> result += TvLabValue(prefix, safeValue(value))
            }
        }

        visit("", source)
        return result
            .filter { it.key.isNotBlank() }
            .distinctBy(TvLabValue::key)
    }

    private fun safeValue(value: Any): String = when (value) {
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }.take(MAX_VALUE_LENGTH)

    private fun addError(message: String) {
        updateState { current ->
            current.copy(errors = (current.errors + message).distinct().takeLast(20))
        }
    }

    private fun updateState(transform: (TvLabState) -> TvLabState) {
        mainHandler.post { state = transform(state) }
    }

    private fun closeSocket(updateUi: Boolean) {
        registered = false
        registrationSent = false
        pending.clear()
        socket?.close(1000, "SmartIR TV Lab beendet")
        socket = null
        activeClient?.dispatcher?.executorService?.shutdown()
        activeClient?.connectionPool?.evictAll()
        activeClient = null

        if (updateUi) {
            updateState {
                it.copy(
                    connection = TvLabConnection.DISCONNECTED,
                    message = "TV-Labor getrennt",
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

    private fun valuesToJson(values: List<TvLabValue>): JSONObject =
        JSONObject().also { json -> values.forEach { json.put(it.key, it.value) } }

    private fun findSystemValue(vararg names: String): String = state.systemInfo
        .firstOrNull { item -> names.any { item.key.equals(it, ignoreCase = true) } }
        ?.value
        .orEmpty()

    private fun findSetting(snapshot: TvLabState, vararg fragments: String): String? =
        snapshot.settings.values.flatten().firstOrNull { item ->
            fragments.any { item.key.contains(it, ignoreCase = true) }
        }?.value

    private fun findNumericSetting(snapshot: TvLabState, vararg fragments: String): Int? =
        findSetting(snapshot, *fragments)
            ?.trim()
            ?.toDoubleOrNull()
            ?.toInt()

    private fun isOffValue(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT) in setOf("false", "0", "off", "aus", "none")

    private fun isOnValue(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT) in setOf("true", "1", "on", "an", "yes", "supported")

    private class LabTrustManager(expectedFingerprint: String) : X509TrustManager {
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

        private val SYSTEM_INFO_KEYS = listOf(
            "modelName",
            "model_name",
            "sdkVersion",
            "firmwareVersion",
            "boardType",
            "otaId",
        )

        private val SETTINGS_READS = linkedMapOf(
            "network" to listOf("deviceName", "wolwowlOnOff", "bleAdvertisingOnOff"),
            "picture" to listOf("brightness", "backlight", "contrast", "color", "energySaving"),
            "sound" to listOf("avSync", "avSyncSpdif", "avSyncBypassInput", "eArcSupport", "soundOutput", "soundOutputDigital", "soundMode", "tvSetupConfiguration"),
            "other" to listOf("simplinkEnable", "ueiEnable"),
            "option" to listOf("audioGuidance", "country", "zipcode", "livePlus", "firstTvSignalStatus", "localeCountryGroup", "countryBroadcastSystem"),
            "general" to listOf("alwaysOn", "tvOnScreen", "tvInstallMethod", "powerOffBySCA3SystemChanged", "SCA3SystemCountry"),
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

        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val MAX_VALUE_LENGTH = 2_000

        private fun normalizeFingerprint(value: String): String =
            value.replace(":", "").trim().uppercase(Locale.ROOT)
    }
}
