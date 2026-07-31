package com.skallahaze.irbloasster.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skallahaze.irbloasster.data.AppPreferences
import com.skallahaze.irbloasster.ir.ConsumerIrSender
import com.skallahaze.irbloasster.ir.LgOledB1IrProfile
import com.skallahaze.irbloasster.ir.NecProtocol
import com.skallahaze.irbloasster.ir.SonyHtRt3Profiles
import com.skallahaze.irbloasster.ir.SonySircProtocol
import com.skallahaze.irbloasster.model.CommandLogEntry
import com.skallahaze.irbloasster.model.DiscoveredTv
import com.skallahaze.irbloasster.model.LgIrCommand
import com.skallahaze.irbloasster.model.LivingRoomScene
import com.skallahaze.irbloasster.model.MainSection
import com.skallahaze.irbloasster.model.SonyCommand
import com.skallahaze.irbloasster.model.UiState
import com.skallahaze.irbloasster.model.WebOsConnectionStatus
import com.skallahaze.irbloasster.network.LgTvDiscovery
import com.skallahaze.irbloasster.network.WakeOnLan
import com.skallahaze.irbloasster.network.WebOsClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LivingRoomViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val irSender = ConsumerIrSender(application)
    private val discovery = LgTvDiscovery(application)

    private val initialProfileIndex = preferences.sonyProfileIndex
        .coerceIn(SonyHtRt3Profiles.all.indices)

    private val _state = MutableStateFlow(
        UiState(
            darkTheme = preferences.darkTheme,
            hapticsEnabled = preferences.hapticsEnabled,
            irAvailable = irSender.isAvailable,
            irSummary = irSender.summary,
            tvIp = preferences.tvIp,
            tvMac = preferences.tvMac,
            sonyProfileIndex = initialProfileIndex,
            sonyProfileName = SonyHtRt3Profiles.all[initialProfileIndex].name
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var webOsClient: WebOsClient? = null

    fun selectSection(section: MainSection) {
        _state.update { it.copy(section = section) }
    }

    fun setDarkTheme(enabled: Boolean) {
        preferences.darkTheme = enabled
        _state.update { it.copy(darkTheme = enabled) }
    }

    fun setHaptics(enabled: Boolean) {
        preferences.hapticsEnabled = enabled
        _state.update { it.copy(hapticsEnabled = enabled) }
    }

    fun saveTvSettings(ip: String, mac: String) {
        val cleanIp = ip.trim()
        val cleanMac = mac.trim()
        preferences.tvIp = cleanIp
        preferences.tvMac = cleanMac
        _state.update { it.copy(tvIp = cleanIp, tvMac = cleanMac) }
        addLog("Einstellungen", true, "TV-Adresse gespeichert")
    }

    fun connectTv(host: String = state.value.tvIp) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) {
            showError("Bitte zuerst die IP-Adresse des LG TV eintragen oder suchen.")
            return
        }
        if (cleanHost.any { it.isWhitespace() || it == '/' }) {
            showError("Die TV-Adresse ist ungültig.")
            return
        }

        preferences.tvIp = cleanHost
        _state.update { it.copy(tvIp = cleanHost, lastError = null) }
        webOsClient?.disconnect(silent = true)
        webOsClient = createWebOsClient(cleanHost).also { it.connect() }
    }

    fun disconnectTv() {
        webOsClient?.disconnect()
        webOsClient = null
    }

    fun forgetTvPairing() {
        val host = state.value.tvIp
        preferences.clearClientKey(host)
        disconnectTv()
        addLog("webOS Pairing", true, "Gespeicherten Client-Schlüssel gelöscht")
    }

    fun discoverTvs() {
        if (state.value.discoveryRunning) return
        viewModelScope.launch {
            _state.update { it.copy(discoveryRunning = true, discoveredTvs = emptyList(), lastError = null) }
            val result = runCatching { discovery.discover() }
            result.onSuccess { devices ->
                _state.update { it.copy(discoveryRunning = false, discoveredTvs = devices) }
                addLog("TV-Suche", devices.isNotEmpty(), "${devices.size} LG-Gerät(e) gefunden")
            }.onFailure { error ->
                _state.update { it.copy(discoveryRunning = false) }
                showError(error.message ?: "TV-Suche fehlgeschlagen")
            }
        }
    }

    fun useDiscoveredTv(tv: DiscoveredTv) {
        saveTvSettings(tv.host, state.value.tvMac)
        connectTv(tv.host)
    }

    fun refreshTv() {
        webOsClient?.refresh() ?: addLog("webOS", false, "Nicht verbunden")
    }

    fun wakeTv() {
        val mac = state.value.tvMac
        if (mac.isBlank()) {
            showError("Für Wake-on-LAN fehlt die TV-MAC-Adresse.")
            return
        }
        viewModelScope.launch {
            val result = WakeOnLan.send(mac)
            addLog("Wake-on-LAN", result.isSuccess, result.exceptionOrNull()?.message ?: mac)
        }
    }

    fun sendLgIr(command: LgIrCommand) {
        viewModelScope.launch {
            val code = LgOledB1IrProfile.codeHex(command)
            val result = irSender.transmit(LgOledB1IrProfile.signal(command))
            addLog("LG IR", result.isSuccess, "${command.label} · $code${result.errorSuffix()}")
        }
    }

    fun sendSony(command: SonyCommand) {
        val profile = selectedSonyProfile()
        val signal = profile.signal(command)
        if (signal == null) {
            addLog("Sony IR", false, "${command.label} ist in ${profile.name} nicht belegt")
            return
        }
        viewModelScope.launch {
            val result = irSender.transmit(signal)
            val code = profile.codeHex(command).orEmpty()
            addLog("Sony IR", result.isSuccess, "${profile.name}: ${command.label} · $code${result.errorSuffix()}")
        }
    }

    fun selectSonyProfile(index: Int) {
        val safeIndex = index.coerceIn(SonyHtRt3Profiles.all.indices)
        preferences.sonyProfileIndex = safeIndex
        _state.update {
            it.copy(
                sonyProfileIndex = safeIndex,
                sonyProfileName = SonyHtRt3Profiles.all[safeIndex].name
            )
        }
        addLog("Sony Profil", true, SonyHtRt3Profiles.all[safeIndex].name)
    }

    fun sendRawIr(protocol: String, hexCode: String, bits: Int) {
        val cleaned = hexCode.trim().removePrefix("0x").removePrefix("0X")
        val code = cleaned.toLongOrNull(16)
        if (code == null) {
            showError("Der Rohcode ist keine gültige Hex-Zahl.")
            return
        }

        val signal = runCatching {
            when (protocol.uppercase(Locale.ROOT)) {
                "NEC" -> NecProtocol.encode(code)
                "SIRC" -> SonySircProtocol.encodeRaw(code.toInt(), bits)
                else -> error("Unbekanntes IR-Protokoll")
            }
        }.getOrElse {
            showError(it.message ?: "Rohcode ungültig")
            return
        }

        viewModelScope.launch {
            val result = irSender.transmit(signal)
            addLog("IR-Labor", result.isSuccess, "$protocol $bits Bit · 0x${cleaned.uppercase()}${result.errorSuffix()}")
        }
    }

    fun webOsVolumeUp() = sendWebOs("Lauter") { it.volumeUp() }
    fun webOsVolumeDown() = sendWebOs("Leiser") { it.volumeDown() }
    fun webOsChannelUp() = sendWebOs("Sender +") { it.channelUp() }
    fun webOsChannelDown() = sendWebOs("Sender −") { it.channelDown() }
    fun webOsPowerOff() = sendWebOs("TV ausschalten") { it.turnOff() }

    fun toggleWebOsMute() {
        val target = !(state.value.muted ?: false)
        sendWebOs(if (target) "Stumm an" else "Stumm aus") { it.setMute(target) }
    }

    fun switchInput(inputId: String) = sendWebOs("Eingang $inputId") { it.switchInput(inputId) }
    fun launchApp(appId: String) = sendWebOs("App $appId") { it.launchApp(appId) }
    fun insertText(text: String) = sendWebOs("Text senden") { it.insertText(text) }
    fun remoteButton(name: String) = sendWebOs("Remote $name") { it.sendRemoteButton(name) }
    fun pointerClick() = sendWebOs("Touchpad Klick") { it.pointerClick() }

    fun pointerMove(dx: Float, dy: Float) {
        webOsClient?.pointerMove(dx, dy)
    }

    fun pointerScroll(dy: Float) {
        webOsClient?.pointerScroll(dy)
    }

    fun runScene(scene: LivingRoomScene) {
        if (state.value.busyScene != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyScene = scene, lastError = null) }
            addLog("Szene", true, "${scene.label} gestartet")
            try {
                when (scene) {
                    LivingRoomScene.TELEVISION -> {
                        wakeTvIfConfigured()
                        sendLgAndWait(LgIrCommand.POWER_ON, 700)
                        connectTvIfConfigured(1_600)
                    }

                    LivingRoomScene.HOME_CINEMA -> {
                        wakeTvIfConfigured()
                        sendLgAndWait(LgIrCommand.POWER_ON, 350)
                        sendSonyAndWait(SonyCommand.POWER_ON, 900)
                        sendSonyAndWait(SonyCommand.INPUT, 500)
                        connectTvIfConfigured(900)
                    }

                    LivingRoomScene.GAMING -> {
                        wakeTvIfConfigured()
                        sendLgAndWait(LgIrCommand.POWER_ON, 700)
                        connectTvIfConfigured(1_600)
                        val hdmi = state.value.inputs.firstOrNull { it.id.contains("HDMI", true) }
                        if (hdmi != null) switchInput(hdmi.id)
                    }

                    LivingRoomScene.MUSIC -> {
                        sendSonyAndWait(SonyCommand.POWER_ON, 900)
                        sendSonyAndWait(SonyCommand.INPUT, 400)
                    }

                    LivingRoomScene.ALL_OFF -> {
                        val networkAccepted = webOsClient?.turnOff() == true
                        if (!networkAccepted) sendLgAndWait(LgIrCommand.POWER_OFF, 350)
                        sendSonyAndWait(SonyCommand.POWER_OFF, 250)
                    }
                }
                addLog("Szene", true, "${scene.label} abgeschlossen")
            } catch (error: Throwable) {
                addLog("Szene", false, "${scene.label}: ${error.message.orEmpty()}")
            } finally {
                _state.update { it.copy(busyScene = null) }
            }
        }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
    }

    fun logText(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)
        return buildString {
            appendLine("Living Room Controller – Diagnose")
            appendLine("LG TV: ${state.value.tvIp.ifBlank { "nicht konfiguriert" }}")
            appendLine("Sony-Profil: ${state.value.sonyProfileName}")
            appendLine("IR: ${state.value.irSummary}")
            appendLine()
            state.value.logs.reversed().forEach { entry ->
                append(formatter.format(Date(entry.timestamp)))
                append(" | ")
                append(if (entry.success) "OK" else "FEHLER")
                append(" | ${entry.category} | ${entry.detail}")
                appendLine()
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(lastError = null) }
    }

    private fun createWebOsClient(host: String): WebOsClient = WebOsClient(
        host = host,
        initialClientKey = preferences.clientKey(host),
        onClientKey = { preferences.saveClientKey(host, it) },
        onStatus = { status, message ->
            _state.update {
                it.copy(
                    webOsStatus = status,
                    webOsMessage = message,
                    lastError = if (status == WebOsConnectionStatus.ERROR) message else it.lastError
                )
            }
        },
        onLog = { command, success, detail -> addLog(command, success, detail) },
        onVolume = { volume, muted -> _state.update { it.copy(volume = volume, muted = muted) } },
        onInputs = { inputs -> _state.update { it.copy(inputs = inputs) } },
        onApps = { apps -> _state.update { it.copy(apps = apps) } },
        onForegroundApp = { app -> _state.update { it.copy(foregroundApp = app) } }
    )

    private fun sendWebOs(label: String, command: (WebOsClient) -> Boolean) {
        val client = webOsClient
        if (client == null || state.value.webOsStatus != WebOsConnectionStatus.CONNECTED) {
            addLog("webOS", false, "$label · nicht verbunden")
            return
        }
        val accepted = runCatching { command(client) }.getOrDefault(false)
        addLog("webOS", accepted, if (accepted) "$label gesendet" else "$label abgelehnt")
    }

    private fun selectedSonyProfile() = SonyHtRt3Profiles.all[
        state.value.sonyProfileIndex.coerceIn(SonyHtRt3Profiles.all.indices)
    ]

    private suspend fun wakeTvIfConfigured() {
        val mac = state.value.tvMac
        if (mac.isNotBlank()) {
            val result = WakeOnLan.send(mac)
            addLog("Wake-on-LAN", result.isSuccess, result.exceptionOrNull()?.message ?: mac)
        }
    }

    private suspend fun connectTvIfConfigured(delayMs: Long) {
        delay(delayMs)
        if (state.value.tvIp.isNotBlank()) connectTv()
    }

    private suspend fun sendLgAndWait(command: LgIrCommand, delayMs: Long) {
        val result = irSender.transmit(LgOledB1IrProfile.signal(command))
        addLog("LG IR", result.isSuccess, "${command.label} · ${LgOledB1IrProfile.codeHex(command)}${result.errorSuffix()}")
        delay(delayMs)
    }

    private suspend fun sendSonyAndWait(command: SonyCommand, delayMs: Long) {
        val profile = selectedSonyProfile()
        val signal = profile.signal(command) ?: profile.signal(SonyCommand.POWER)
        if (signal == null) {
            addLog("Sony IR", false, "${command.label} nicht belegt")
            return
        }
        val result = irSender.transmit(signal)
        addLog("Sony IR", result.isSuccess, "${profile.name}: ${command.label}${result.errorSuffix()}")
        delay(delayMs)
    }

    private fun addLog(category: String, success: Boolean, detail: String) {
        val entry = CommandLogEntry(category = category, command = detail, success = success, detail = detail)
        _state.update { current ->
            current.copy(logs = (listOf(entry) + current.logs).take(MAX_LOG_ENTRIES))
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(lastError = message) }
        addLog("Fehler", false, message)
    }

    override fun onCleared() {
        webOsClient?.disconnect(silent = true)
        webOsClient = null
        super.onCleared()
    }

    private fun Result<Unit>.errorSuffix(): String = exceptionOrNull()?.message?.let { " · $it" }.orEmpty()

    companion object {
        private const val MAX_LOG_ENTRIES = 120
    }
}
