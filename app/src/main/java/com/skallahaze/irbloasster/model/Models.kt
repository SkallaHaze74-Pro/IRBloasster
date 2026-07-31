package com.skallahaze.irbloasster.model

enum class WebOsConnectionState {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR
}

data class DiscoveredTv(
    val name: String,
    val ipAddress: String,
    val uuid: String? = null,
    val modelName: String? = null,
    val location: String? = null
)

data class TvStatus(
    val volume: Int? = null,
    val muted: Boolean? = null,
    val foregroundAppId: String? = null,
    val foregroundAppTitle: String? = null,
    val inputId: String? = null,
    val channelName: String? = null,
    val powerState: String? = null,
    val systemModelName: String? = null,
    val pointerConnected: Boolean = false
)

data class TvApp(
    val id: String,
    val title: String,
    val visible: Boolean = true
)

data class TvInput(
    val id: String,
    val label: String,
    val connected: Boolean = false,
    val icon: String? = null
)

enum class DiagnosticDirection {
    INFO,
    OUT,
    IN,
    WARN,
    ERROR
}

data class DiagnosticEntry(
    val timestampMillis: Long,
    val direction: DiagnosticDirection,
    val category: String,
    val message: String
)

data class MacroProgress(
    val running: Boolean = false,
    val macroName: String? = null,
    val stepLabel: String? = null,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val lastError: String? = null
)
