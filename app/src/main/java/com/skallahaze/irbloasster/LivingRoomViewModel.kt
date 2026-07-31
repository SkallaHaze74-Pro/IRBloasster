package com.skallahaze.irbloasster

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ir.ConsumerIrSender
import com.skallahaze.irbloasster.ir.LgIrKey
import com.skallahaze.irbloasster.ir.SonySircProtocol
import com.skallahaze.irbloasster.model.AppTab
import com.skallahaze.irbloasster.model.ConnectionPhase
import com.skallahaze.irbloasster.model.DiscoveredTv
import com.skallahaze.irbloasster.model.LivingRoomUiState
import com.skallahaze.irbloasster.model.SceneType
import com.skallahaze.irbloasster.model.TvApp
import com.skallahaze.irbloasster.model.TvInput
import com.skallahaze.irbloasster.model.UserSettings
import com.skallahaze.irbloasster.webos.SsdpDiscovery
import com.skallahaze.irbloasster.webos.WakeOnLan
import com.skallahaze.irbloasster.webos.WebOsClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LivingRoomViewModel(application: Application) : AndroidViewModel(application), WebOsClient.Listener {
    private val settingsRepository = SettingsRepository(application)
    private val irSender = ConsumerIrSender(application)
    private val discovery = SsdpDiscovery(application)
    private val webOs = WebOsClient(this)

    private val _uiState = MutableStateFlow(
        LivingRoomUiState(
            settings = settingsRepository.load(),
            irAvailable = irSender.isAvailable
        )
    )
    val uiState: StateFlow<LivingRoomUiState> = _uiState.asStateFlow()

    init {
        appendLog("App gestartet · IR ${if (irSender.isAvailable) "bereit" else "nicht gemeldet"}")
        val settings = _uiState.value.settings
        if (settings.autoConnect && settings.tvIp.isNotBlank()) {
            viewModelScope.launch {
                delay(450)
                connectTv()
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSettings(settings: UserSettings) {
        settingsRepository.save(settings)
        _uiState.update { it.copy(settings = settings) }
    }

    fun clearPairing() {
        webOs.disconnect(notify = false)
        val updated = settingsRepository.clearPairing()
        _uiState.update {
            it.copy(
                settings = updated,
                connectionPhase = ConnectionPhase.DISCONNECTED,
                pointerReady = false,
                statusMessage = "Pairing zurückgesetzt"
            )
        }
        appendLog("Gespeicherten webOS-Schlüssel und Zertifikat-Fingerabdruck gelöscht")
    }

    fun discoverTvs() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionPhase = ConnectionPhase.DISCOVERING,
                    statusMessage = "Suche LG webOS TVs im WLAN …"
                )
            }
            appendLog("SSDP-Suche gestartet")
            val result = discovery.discover()
            if (result.error != null) {
                _uiState.update {
                    it.copy(
                        connectionPhase = ConnectionPhase.ERROR,
                        statusMessage = result.error
                    )
                }
                appendLog("Suche fehlgeschlagen: ${result.error}")
            } else {
                _uiState.update {
                    it.copy(
                        discoveredTvs = result.devices,
                        connectionPhase = ConnectionPhase.DISCONNECTED,
                        statusMessage = if (result.devices.isEmpty()) {
                            "Kein LG webOS TV gefunden – IP kann manuell eingetragen werden"
                        } else {
                            "${result.devices.size} TV${if (result.devices.size == 1) "" else "s"} gefunden"
                        }
                    )
                }
                appendLog("SSDP-Suche beendet: ${result.devices.size} Treffer")
            }
        }
    }

    fun selectDiscoveredTv(tv: DiscoveredTv) {
        val updated = _uiState.value.settings.copy(tvIp = tv.ipAddress)
        updateSettings(updated)
        _uiState.update { it.copy(statusMessage = "${tv.name} (${tv.ipAddress}) ausgewählt") }
        appendLog("TV ausgewählt: ${tv.name} · ${tv.ipAddress}")
    }

    fun connectTv() {
        val settings = _uiState.value.settings
        if (settings.tvIp.isBlank()) {
            _uiState.update {
                it.copy(
                    connectionPhase = ConnectionPhase.ERROR,
                    statusMessage = "Bitte zuerst TV-IP eintragen oder TV suchen"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.CONNECTING,
                statusMessage = "Verbinde mit ${settings.tvIp} …"
            )
        }
        appendLog("webOS-Verbindung zu ${settings.tvIp} gestartet")
        runCatching {
            webOs.connect(
                WebOsClient.Config(
                    ipAddress = settings.tvIp,
                    clientKey = settings.clientKey,
                    certificateFingerprint = settings.certificateFingerprint
                )
            )
        }.onFailure { onError(it.message ?: "Verbindung konnte nicht gestartet werden") }
    }

    fun disconnectTv() {
        webOs.disconnect()
    }

    fun wakeTv() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            when {
                settings.tvMac.isNotBlank() -> {
                    _uiState.update { it.copy(statusMessage = "Wake-on-LAN wird gesendet …") }
                    WakeOnLan.send(settings.tvMac)
                        .onSuccess {
                            appendLog("Wake-on-LAN an ${settings.tvMac} gesendet")
                            _uiState.update { it.copy(statusMessage = "Wake-on-LAN gesendet") }
                        }
                        .onFailure { error ->
                            appendLog("Wake-on-LAN fehlgeschlagen: ${error.message}")
                            if (settings.irFallback) sendLgIr(LgIrKey.POWER) else onError(error.message.orEmpty())
                        }
                }
                settings.irFallback -> sendLgIr(LgIrKey.POWER)
                else -> onError("Für Einschalten bitte TV-MAC eintragen oder IR-Fallback aktivieren")
            }
        }
    }

    fun powerOffTv() {
        if (webOs.isConnected) {
            webOs.powerOff()
            appendLog("webOS Power Off gesendet")
        } else if (_uiState.value.settings.irFallback) {
            sendLgIr(LgIrKey.POWER)
        } else {
            onError("TV ist nicht verbunden und IR-Fallback ist deaktiviert")
        }
    }

    fun tvVolumeUp() = if (webOs.isConnected) webOs.volumeUp() else sendLgIr(LgIrKey.VOLUME_UP)
    fun tvVolumeDown() = if (webOs.isConnected) webOs.volumeDown() else sendLgIr(LgIrKey.VOLUME_DOWN)

    fun tvMute() {
        if (webOs.isConnected) {
            webOs.setMute(!_uiState.value.muted)
        } else {
            sendLgIr(LgIrKey.MUTE)
        }
    }

    fun setTvVolume(volume: Int) {
        if (webOs.isConnected) webOs.setVolume(volume) else onError("Direkte Lautstärke braucht die WLAN-Verbindung")
    }

    fun tvKey(key: LgIrKey) {
        val pointerButton = when (key) {
            LgIrKey.HOME -> "HOME"
            LgIrKey.BACK -> "BACK"
            LgIrKey.UP -> "UP"
            LgIrKey.DOWN -> "DOWN"
            LgIrKey.LEFT -> "LEFT"
            LgIrKey.RIGHT -> "RIGHT"
            LgIrKey.OK -> null
            else -> null
        }

        if (_uiState.value.pointerReady && key in NAVIGATION_KEYS) {
            if (key == LgIrKey.OK) webOs.pointerClick() else webOs.pointerButton(pointerButton.orEmpty())
        } else {
            sendLgIr(key)
        }
    }

    fun pointerMove(dx: Float, dy: Float) = webOs.pointerMove(dx, dy)
    fun pointerScroll(dx: Float, dy: Float) = webOs.pointerScroll(dx, dy)
    fun pointerClick() = webOs.pointerClick()
    fun mediaPlay() = webOs.play()
    fun mediaPause() = webOs.pause()
    fun mediaStop() = webOs.stop()
    fun mediaFastForward() = webOs.fastForward()
    fun mediaRewind() = webOs.rewind()
    fun channelUp() = webOs.channelUp()
    fun channelDown() = webOs.channelDown()

    fun switchInput(inputId: String) {
        if (webOs.isConnected) {
            webOs.switchInput(inputId)
            _uiState.update { it.copy(currentInput = inputId) }
            appendLog("Eingang angefordert: $inputId")
        } else {
            sendLgIr(LgIrKey.INPUT)
        }
    }

    fun launchApp(appId: String) {
        if (webOs.isConnected) {
            webOs.launchApp(appId)
            appendLog("App-Start angefordert: $appId")
        } else {
            onError("Apps können nur über die webOS-Verbindung gestartet werden")
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        if (webOs.isConnected) {
            webOs.insertText(text)
            appendLog("Text an TV gesendet (${text.length} Zeichen)")
        } else {
            onError("Texteingabe braucht die webOS-Verbindung")
        }
    }

    fun sendEnter() = webOs.sendEnterKey()

    fun sendSonyProfileCommand(command: Int) {
        val settings = _uiState.value.settings
        sendSony(
            command = command,
            address = settings.sonyAddress,
            bits = settings.sonyBits,
            extended = 0
        )
    }

    fun sendSony(
        command: Int,
        address: Int,
        bits: Int,
        extended: Int = 0
    ) {
        val frameBits = SonySircProtocol.FrameBits.from(bits)
        irSender.sendSony(
            command = command,
            address = address,
            bits = frameBits,
            extended = extended,
            haptics = _uiState.value.settings.haptics
        ).onSuccess {
            appendLog("Sony SIRC gesendet: $bits Bit · Adresse $address · Befehl $command")
            _uiState.update { it.copy(statusMessage = "Sony-Befehl $command gesendet") }
        }.onFailure { error -> onError(error.message ?: "Sony-IR konnte nicht gesendet werden") }
    }

    fun sendCustomNec(hexCode: String, repeats: Int) {
        runCatching {
            val normalized = hexCode.trim().removePrefix("0x").removePrefix("0X")
            require(normalized.matches(Regex("[0-9A-Fa-f]{8}"))) { "NEC-Code muss genau 8 Hex-Zeichen haben" }
            normalized.toUInt(16)
        }.onSuccess { code ->
            irSender.sendNec(code, repeats.coerceIn(1, 5), _uiState.value.settings.haptics)
                .onSuccess {
                    appendLog("NEC 0x${code.toString(16).uppercase(Locale.ROOT)} gesendet")
                    _uiState.update { it.copy(statusMessage = "NEC-Code gesendet") }
                }
                .onFailure { error -> onError(error.message ?: "NEC-Code konnte nicht gesendet werden") }
        }.onFailure { error -> onError(error.message ?: "Ungültiger NEC-Code") }
    }

    fun runScene(scene: SceneType) {
        if (_uiState.value.sceneRunning != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(sceneRunning = scene, statusMessage = "Szene ${scene.label} läuft …") }
            appendLog("Szene gestartet: ${scene.label}")
            try {
                when (scene) {
                    SceneType.MOVIE, SceneType.GAMING -> {
                        wakeTv()
                        delay(2_500)
                        if (!webOs.isConnected) connectTv()
                        withTimeoutOrNull(12_000) {
                            uiState.first { it.connectionPhase == ConnectionPhase.CONNECTED }
                        }
                        val input = _uiState.value.settings.preferredInput
                        if (webOs.isConnected && input.isNotBlank()) {
                            webOs.switchInput(input)
                        }
                        sendSonyProfileCommand(_uiState.value.settings.sonyPowerCommand)
                    }
                    SceneType.TV_ONLY -> {
                        wakeTv()
                        delay(2_500)
                        if (!webOs.isConnected) connectTv()
                    }
                    SceneType.ALL_OFF -> {
                        if (webOs.isConnected) webOs.powerOff() else if (_uiState.value.settings.irFallback) sendLgIr(LgIrKey.POWER)
                        delay(450)
                        sendSonyProfileCommand(_uiState.value.settings.sonyPowerCommand)
                    }
                }
                _uiState.update { it.copy(statusMessage = "Szene ${scene.label} abgeschlossen") }
                appendLog("Szene beendet: ${scene.label}")
            } catch (error: Exception) {
                onError("Szene abgebrochen: ${error.message.orEmpty()}")
            } finally {
                _uiState.update { it.copy(sceneRunning = null) }
            }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    override fun onStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
        appendLog(message)
    }

    override fun onPairingRequired() {
        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.PAIRING,
                statusMessage = "Bitte die Verbindung am LG-TV bestätigen"
            )
        }
        appendLog("Pairing-Bestätigung am TV erforderlich")
    }

    override fun onRegistered(clientKey: String, certificateFingerprint: String) {
        val updatedSettings = _uiState.value.settings.copy(
            clientKey = clientKey,
            certificateFingerprint = certificateFingerprint.ifBlank {
                _uiState.value.settings.certificateFingerprint
            }
        )
        settingsRepository.save(updatedSettings)
        _uiState.update {
            it.copy(
                settings = updatedSettings,
                connectionPhase = ConnectionPhase.CONNECTED,
                statusMessage = "LG webOS TV verbunden"
            )
        }
        appendLog("Pairing erfolgreich · Client-Key gespeichert · Zertifikat angeheftet")
        webOs.refresh()
    }

    override fun onDisconnected(reason: String) {
        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.DISCONNECTED,
                pointerReady = false,
                statusMessage = reason
            )
        }
        appendLog("Verbindung getrennt: $reason")
    }

    override fun onError(message: String) {
        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.ERROR,
                statusMessage = message
            )
        }
        appendLog("Fehler: $message")
    }

    override fun onVolume(volume: Int?, muted: Boolean) {
        _uiState.update { it.copy(volume = volume, muted = muted) }
    }

    override fun onForegroundApp(appId: String) {
        _uiState.update { it.copy(currentAppId = appId) }
    }

    override fun onPowerState(state: String) {
        _uiState.update { it.copy(powerState = state) }
    }

    override fun onApps(apps: List<TvApp>) {
        _uiState.update { it.copy(apps = apps) }
        appendLog("${apps.size} installierte TV-Apps geladen")
    }

    override fun onInputs(inputs: List<TvInput>) {
        _uiState.update { it.copy(inputs = inputs) }
        appendLog("${inputs.size} TV-Eingänge geladen")
    }

    override fun onPointerReady(ready: Boolean) {
        _uiState.update { it.copy(pointerReady = ready) }
        if (ready) appendLog("Magic-Remote-Touchpad bereit")
    }

    override fun onCleared() {
        webOs.disconnect(notify = false)
        super.onCleared()
    }

    private fun sendLgIr(key: LgIrKey) {
        if (!_uiState.value.settings.irFallback) {
            onError("IR-Fallback ist deaktiviert")
            return
        }
        irSender.sendLg(key, _uiState.value.settings.haptics)
            .onSuccess {
                appendLog("LG NEC gesendet: ${key.displayName}")
                _uiState.update { it.copy(statusMessage = "IR: ${key.displayName}") }
            }
            .onFailure { error -> onError(error.message ?: "LG-IR konnte nicht gesendet werden") }
    }

    private fun appendLog(message: String) {
        val timestamp = TIME_FORMAT.format(Date())
        _uiState.update { current ->
            current.copy(logs = (listOf("$timestamp  $message") + current.logs).take(120))
        }
    }

    private companion object {
        val NAVIGATION_KEYS = setOf(
            LgIrKey.HOME,
            LgIrKey.BACK,
            LgIrKey.UP,
            LgIrKey.DOWN,
            LgIrKey.LEFT,
            LgIrKey.RIGHT,
            LgIrKey.OK
        )
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
    }
}
