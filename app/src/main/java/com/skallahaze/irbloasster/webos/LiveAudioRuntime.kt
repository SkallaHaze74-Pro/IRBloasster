package com.skallahaze.irbloasster.webos

object LiveAudioRuntime {
    @Volatile var running: Boolean = false
    @Volatile var streamUrl: String = ""
    @Volatile var message: String = "Live-Audio aus"
    @Volatile var signalPercent: Int = 0
    @Volatile var clientCount: Int = 0
    @Volatile var throughputKbps: Int = 0

    fun update(running: Boolean, streamUrl: String = "", message: String) {
        this.running = running
        this.streamUrl = streamUrl
        this.message = message
        if (!running) {
            signalPercent = 0
            clientCount = 0
            throughputKbps = 0
        }
    }

    fun updateStats(signalPercent: Int, clientCount: Int, throughputKbps: Int) {
        this.signalPercent = signalPercent.coerceIn(0, 100)
        this.clientCount = clientCount.coerceAtLeast(0)
        this.throughputKbps = throughputKbps.coerceAtLeast(0)

        if (!running) return
        message = when {
            this.clientCount <= 0 -> "LIVE · warte auf TV · Signal ${this.signalPercent}%"
            this.signalPercent <= 1 -> "LIVE · TV verbunden · kein internes Audiosignal"
            else -> "LIVE · TV verbunden · Signal ${this.signalPercent}% · PCM 48 kHz Stereo"
        }
    }
}
