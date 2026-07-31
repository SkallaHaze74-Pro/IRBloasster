package com.skallahaze.irbloasster.model

enum class AppTab(val label: String) {
    HOME("Home"),
    TV("LG TV"),
    SONY("Sony"),
    LAB("Code Lab"),
    SETTINGS("Setup")
}

enum class ConnectionPhase {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR
}

enum class SceneType(val label: String) {
    MOVIE("Filmabend"),
    GAMING("Gaming"),
    TV_ONLY("Nur TV"),
    ALL_OFF("Alles aus")
}

data class DiscoveredTv(
    val name: String,
    val ipAddress: String,
    val port: Int = 3001,
    val server: String = "",
    val usn: String = ""
)

data class TvApp(
    val id: String,
    val title: String
)

data class TvInput(
    val id: String,
    val label: String,
    val connected: Boolean = true
)

data class UserSettings(
    val tvIp: String = "",
    val tvMac: String = "",
    val clientKey: String = "",
    val certificateFingerprint: String = "",
    val autoConnect: Boolean = true,
    val irFallback: Boolean = true,
    val haptics: Boolean = true,
    val preferredInput: String = "HDMI_1",
    val sonyAddress: Int = 16,
    val sonyBits: Int = 12,
    val sonyPowerCommand: Int = 21,
    val sonyVolumeUpCommand: Int = 18,
    val sonyVolumeDownCommand: Int = 19,
    val sonyMuteCommand: Int = 20
)

data class LivingRoomUiState(
    val selectedTab: AppTab = AppTab.HOME,
    val connectionPhase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val statusMessage: String = "Bereit",
    val settings: UserSettings = UserSettings(),
    val discoveredTvs: List<DiscoveredTv> = emptyList(),
    val irAvailable: Boolean = false,
    val volume: Int? = null,
    val muted: Boolean = false,
    val currentAppId: String = "",
    val currentInput: String = "",
    val powerState: String = "",
    val apps: List<TvApp> = emptyList(),
    val inputs: List<TvInput> = emptyList(),
    val pointerReady: Boolean = false,
    val sceneRunning: SceneType? = null,
    val logs: List<String> = emptyList()
)
