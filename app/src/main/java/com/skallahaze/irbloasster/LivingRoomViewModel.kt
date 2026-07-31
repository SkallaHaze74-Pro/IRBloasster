package com.skallahaze.irbloasster

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skallahaze.irbloasster.data.DiagnosticsLog
import com.skallahaze.irbloasster.data.SecurePreferences
import com.skallahaze.irbloasster.data.WakeOnLan
import com.skallahaze.irbloasster.data.WebOsClient
import com.skallahaze.irbloasster.data.WebOsDiscovery
import com.skallahaze.irbloasster.ir.ConsumerIrTransmitter
import com.skallahaze.irbloasster.ir.LgIrCommand
import com.skallahaze.irbloasster.ir.LgTvIrProfile
import com.skallahaze.irbloasster.ir.SonyCommand
import com.skallahaze.irbloasster.ir.SonyProfiles
import com.skallahaze.irbloasster.macro.MacroEngine
import com.skallahaze.irbloasster.model.DiscoveredTv
import com.skallahaze.irbloasster.model.WebOsConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LivingRoomViewModel(application: Application) : AndroidViewModel(application) {
    val diagnostics = DiagnosticsLog()
    private val preferences = SecurePreferences(application)
    private val webOs = WebOsClient(application, preferences, diagnostics)
    private val discovery = WebOsDiscovery(application, diagnostics)
    private val wakeOnLan = WakeOnLan(diagnostics)
    private val ir = ConsumerIrTransmitter(application, diagnostics)
    private val macroEngine = MacroEngine(diagnostics)

    val connectionState = webOs.connectionState
    val tvStatus = webOs.status
    val tvApps = webOs.apps
    val tvInputs = webOs.inputs
    val webOsError = webOs.lastError
    val macroProgress = macroEngine.progress

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _manualIp = MutableStateFlow(preferences.getString(SecurePreferences.KEY_TV_IP))
    val manualIp: StateFlow<String> = _manualIp.asStateFlow()

    private val _tvMacAddress = MutableStateFlow(preferences.getString(SecurePreferences.KEY_TV_MAC))
    val tvMacAddress: StateFlow<String> = _tvMacAddress.asStateFlow()

    private val _preferredInput = MutableStateFlow(preferences.getString(SecurePreferences.KEY_PREFERRED_INPUT))
    val preferredInput: StateFlow<String> = _preferredInput.asStateFlow()

    private val savedSonyIndex = preferences
        .getString(SecurePreferences.KEY_SONY_PROFILE_INDEX, "0")
        .toIntOrNull()
        ?.coerceIn(SonyProfiles.candidates.indices)
        ?: 0

    private val _sonyProfileIndex = MutableStateFlow(savedSonyIndex)
    val sonyProfileIndex: StateFlow<Int> = _sonyProfileIndex.asStateFlow()

    val sonyProfiles = SonyProfiles.candidates
    val irAvailable: Boolean get() = ir.available

    init {
        if (_manualIp.value.isNotBlank()) {
            viewModelScope.launch {
                delay(600)
                connectTv()
            }
        }
    }

    fun setManualIp(value: String) {
        _manualIp.value = value
        preferences.putString(SecurePreferences.KEY_TV_IP, value.trim())
    }

    fun setTvMacAddress(value: String) {
        _tvMacAddress.value = value
        preferences.putString(SecurePreferences.KEY_TV_MAC, value.trim())
    }

    fun setPreferredInput(value: String) {
        _preferredInput.value = value
        preferences.putString(SecurePreferences.KEY_PREFERRED_INPUT, value)
    }

    fun discoverTvs() {
        if (_isDiscovering.value) return
        viewModelScope.launch {
            _isDiscovering.value = true
            runCatching { discovery.discover() }
                .onSuccess { _discoveredTvs.value = it }
                .onFailure { diagnostics.error("Discovery", it.message ?: "Discovery failed") }
            _isDiscovering.value = false
        }
    }

    fun useDiscoveredTv(tv: DiscoveredTv) {
        setManualIp(tv.ipAddress)
        connectTv()
    }

    fun connectTv() {
        webOs.connect(_manualIp.value)
    }

    fun disconnectTv() = webOs.disconnect()

    fun refreshTv() = webOs.refresh()

    fun forgetPairingAndReconnect() {
        preferences.putEncryptedString(SecurePreferences.KEY_TV_CLIENT_KEY, null)
        val ip = _manualIp.value.trim()
        if (ip.isNotBlank()) {
            preferences.putString(SecurePreferences.KEY_TV_CERT_FINGERPRINT_PREFIX + ip, null)
        }
        connectTv()
    }

    fun wakeTv() {
        viewModelScope.launch {
            val mac = _tvMacAddress.value
            if (mac.isBlank()) {
                diagnostics.warn("Wake-on-LAN", "No TV MAC address configured")
                sendLgIr(LgIrCommand.POWER)
            } else {
                wakeOnLan.send(mac)
                delay(1_000)
                connectTv()
            }
        }
    }

    fun tvPowerOff() = webOs.powerOff()
    fun volumeUp() = webOs.volumeUp()
    fun volumeDown() = webOs.volumeDown()
    fun setVolume(value: Int) = webOs.setVolume(value)
    fun toggleMute() = webOs.toggleMute()
    fun channelUp() = webOs.channelUp()
    fun channelDown() = webOs.channelDown()
    fun mediaPlay() = webOs.mediaPlay()
    fun mediaPause() = webOs.mediaPause()
    fun mediaStop() = webOs.mediaStop()
    fun mediaRewind() = webOs.mediaRewind()
    fun mediaFastForward() = webOs.mediaFastForward()
    fun switchInput(inputId: String) = webOs.switchInput(inputId)
    fun launchApp(appId: String) = webOs.launchApp(appId)

    fun connectPointer() = webOs.requestPointerSocket()
    fun pointerClick() = webOs.pointerClick()
    fun pointerButton(name: String) = webOs.pointerButton(name)
    fun pointerMove(dx: Float, dy: Float, drag: Boolean = false) = webOs.pointerMove(dx, dy, drag)
    fun pointerScroll(dx: Float, dy: Float) = webOs.pointerScroll(dx, dy)

    fun sendText(text: String) {
        webOs.registerKeyboard()
        webOs.insertText(text)
    }

    fun deleteText() = webOs.deleteText()
    fun sendEnter() = webOs.sendEnter()

    fun sendLgIr(command: LgIrCommand) {
        viewModelScope.launch {
            ir.transmit(LgTvIrProfile.signal(command))
        }
    }

    fun selectSonyProfile(index: Int) {
        val valid = index.coerceIn(SonyProfiles.candidates.indices)
        _sonyProfileIndex.value = valid
        preferences.putString(SecurePreferences.KEY_SONY_PROFILE_INDEX, valid.toString())
    }

    fun nextSonyProfile() {
        selectSonyProfile((_sonyProfileIndex.value + 1) % SonyProfiles.candidates.size)
    }

    fun previousSonyProfile() {
        val next = if (_sonyProfileIndex.value == 0) SonyProfiles.candidates.lastIndex
        else _sonyProfileIndex.value - 1
        selectSonyProfile(next)
    }

    fun sendSony(command: SonyCommand) {
        val profile = SonyProfiles.candidates[_sonyProfileIndex.value]
        val signal = profile.signal(command)
        if (signal == null) {
            diagnostics.warn("IR", "No ${command.name} command in ${profile.name}")
            return
        }
        viewModelScope.launch { ir.transmit(signal) }
    }

    fun runFilmScene() {
        viewModelScope.launch {
            val steps = buildList {
                add(
                    MacroEngine.Step("TV aufwecken", delayAfterMillis = 1_200) {
                        val mac = _tvMacAddress.value
                        if (mac.isNotBlank()) wakeOnLan.send(mac)
                        else ir.transmit(LgTvIrProfile.signal(LgIrCommand.POWER))
                    }
                )
                add(
                    MacroEngine.Step("Mit LG TV verbinden", delayAfterMillis = 1_000) {
                        webOs.connect(_manualIp.value)
                        waitForWebOsConnection()
                    }
                )
                if (_preferredInput.value.isNotBlank()) {
                    add(
                        MacroEngine.Step("TV-Eingang ${_preferredInput.value}", delayAfterMillis = 500) {
                            webOs.switchInput(_preferredInput.value)
                            Result.success(Unit)
                        }
                    )
                }
                add(
                    MacroEngine.Step("Sony Heimkino einschalten") {
                        val signal = SonyProfiles.candidates[_sonyProfileIndex.value]
                            .signal(SonyCommand.POWER)
                        if (signal == null) {
                            Result.failure(IllegalStateException("Sony power code missing"))
                        } else {
                            ir.transmit(signal)
                        }
                    }
                )
            }
            macroEngine.run("Filmabend", steps)
        }
    }

    fun runGamingScene() {
        viewModelScope.launch {
            val steps = listOf(
                MacroEngine.Step("TV aufwecken", delayAfterMillis = 1_200) {
                    val mac = _tvMacAddress.value
                    if (mac.isNotBlank()) wakeOnLan.send(mac)
                    else ir.transmit(LgTvIrProfile.signal(LgIrCommand.POWER))
                },
                MacroEngine.Step("Mit LG TV verbinden", delayAfterMillis = 1_000) {
                    webOs.connect(_manualIp.value)
                    waitForWebOsConnection()
                },
                MacroEngine.Step("Bevorzugten HDMI-Eingang öffnen", delayAfterMillis = 500) {
                    if (_preferredInput.value.isNotBlank()) webOs.switchInput(_preferredInput.value)
                    Result.success(Unit)
                },
                MacroEngine.Step("Sony Heimkino einschalten") {
                    val signal = SonyProfiles.candidates[_sonyProfileIndex.value]
                        .signal(SonyCommand.POWER)
                    if (signal == null) {
                        Result.failure(IllegalStateException("Sony power code missing"))
                    } else {
                        ir.transmit(signal)
                    }
                }
            )
            macroEngine.run("Gaming", steps)
        }
    }

    fun runAllOffScene() {
        viewModelScope.launch {
            val steps = listOf(
                MacroEngine.Step("Sony Heimkino ausschalten", delayAfterMillis = 500) {
                    val signal = SonyProfiles.candidates[_sonyProfileIndex.value]
                        .signal(SonyCommand.POWER)
                    if (signal == null) {
                        Result.failure(IllegalStateException("Sony power code missing"))
                    } else {
                        ir.transmit(signal)
                    }
                },
                MacroEngine.Step("LG TV ausschalten") {
                    if (connectionState.value == WebOsConnectionState.CONNECTED) {
                        webOs.powerOff()
                        Result.success(Unit)
                    } else {
                        ir.transmit(LgTvIrProfile.signal(LgIrCommand.POWER))
                    }
                }
            )
            macroEngine.run("Alles aus", steps)
        }
    }

    fun clearDiagnostics() = diagnostics.clear()

    private suspend fun waitForWebOsConnection(): Result<Unit> {
        val state = withTimeoutOrNull(12_000) {
            connectionState.first {
                it == WebOsConnectionState.CONNECTED ||
                    it == WebOsConnectionState.PAIRING ||
                    it == WebOsConnectionState.ERROR
            }
        }
        return when (state) {
            WebOsConnectionState.CONNECTED,
            WebOsConnectionState.PAIRING -> Result.success(Unit)

            WebOsConnectionState.ERROR -> Result.failure(
                IllegalStateException(webOsError.value ?: "TV connection failed")
            )

            else -> Result.failure(IllegalStateException("TV connection timed out"))
        }
    }

    override fun onCleared() {
        webOs.close()
        super.onCleared()
    }
}
