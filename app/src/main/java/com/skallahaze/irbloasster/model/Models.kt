package com.skallahaze.irbloasster.model

enum class MainSection(val label: String, val symbol: String) {
    HOME("Übersicht", "⌂"),
    TV("LG TV", "▣"),
    SONY("Sony", "◖"),
    SCENES("Szenen", "✦"),
    ANALYSIS("Analyse", "⌁")
}

enum class WebOsConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR
}

data class TvInput(
    val id: String,
    val label: String,
    val connected: Boolean = false
)

data class TvApp(
    val id: String,
    val title: String,
    val visible: Boolean = true
)

data class DiscoveredTv(
    val name: String,
    val host: String,
    val location: String
)

data class CommandLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val command: String,
    val success: Boolean,
    val detail: String = ""
)

enum class LivingRoomScene(val label: String, val description: String) {
    TELEVISION("Fernsehen", "TV wecken und verbinden"),
    HOME_CINEMA("Heimkino", "TV + Sony gemeinsam starten"),
    GAMING("Gaming", "TV starten und Eingang vorbereiten"),
    MUSIC("Musik", "Sony starten und Eingang wechseln"),
    ALL_OFF("Alles aus", "TV und Sony ausschalten")
}

enum class LgIrCommand(val label: String) {
    POWER("Power"),
    POWER_ON("Power an"),
    POWER_OFF("Power aus"),
    VOLUME_UP("Lauter"),
    VOLUME_DOWN("Leiser"),
    MUTE("Stumm"),
    CHANNEL_UP("Sender +"),
    CHANNEL_DOWN("Sender −"),
    INPUT("Eingang"),
    HOME("Home"),
    SETTINGS("Einstellungen"),
    BACK("Zurück"),
    INFO("Info"),
    GUIDE("Guide"),
    UP("Hoch"),
    DOWN("Runter"),
    LEFT("Links"),
    RIGHT("Rechts"),
    OK("OK"),
    PLAY("Play"),
    PAUSE("Pause"),
    STOP("Stop"),
    REWIND("Zurückspulen"),
    FAST_FORWARD("Vorspulen")
}

enum class SonyCommand(val label: String) {
    POWER("Power"),
    POWER_ON("Power an"),
    POWER_OFF("Power aus"),
    VOLUME_UP("Lauter"),
    VOLUME_DOWN("Leiser"),
    MUTE("Stumm"),
    INPUT("Eingang"),
    SOUND_FIELD("Sound Field"),
    CLEAR_AUDIO("ClearAudio+"),
    NIGHT("Night"),
    VOICE("Voice"),
    SUBWOOFER_UP("Subwoofer +"),
    SUBWOOFER_DOWN("Subwoofer −")
}

data class UiState(
    val section: MainSection = MainSection.HOME,
    val darkTheme: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val irAvailable: Boolean = false,
    val irSummary: String = "IR wird geprüft …",
    val tvIp: String = "",
    val tvMac: String = "",
    val webOsStatus: WebOsConnectionStatus = WebOsConnectionStatus.DISCONNECTED,
    val webOsMessage: String = "Nicht verbunden",
    val volume: Int? = null,
    val muted: Boolean? = null,
    val foregroundApp: String? = null,
    val inputs: List<TvInput> = emptyList(),
    val apps: List<TvApp> = emptyList(),
    val discoveredTvs: List<DiscoveredTv> = emptyList(),
    val discoveryRunning: Boolean = false,
    val sonyProfileIndex: Int = 0,
    val sonyProfileName: String = "Sony Soundbar 15-bit",
    val logs: List<CommandLogEntry> = emptyList(),
    val busyScene: LivingRoomScene? = null,
    val lastError: String? = null
)
