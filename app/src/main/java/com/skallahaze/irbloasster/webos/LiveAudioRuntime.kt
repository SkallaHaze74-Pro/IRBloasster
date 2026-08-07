package com.skallahaze.irbloasster.webos

object LiveAudioRuntime {
    @Volatile var running: Boolean = false
    @Volatile var streamUrl: String = ""
    @Volatile var message: String = "Live-Audio aus"

    fun update(running: Boolean, streamUrl: String = "", message: String) {
        this.running = running
        this.streamUrl = streamUrl
        this.message = message
    }
}
