package com.skallahaze.irbloasster.data

import com.skallahaze.irbloasster.model.DiagnosticDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebOsPointerClient(
    private val log: DiagnosticsLog
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var socket: WebSocket? = null

    fun connect(socketPath: String, client: OkHttpClient) {
        disconnect()
        log.info("Pointer", "Connecting to TV pointer socket")
        socket = client.newWebSocket(
            Request.Builder().url(socketPath).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connected.value = true
                    log.info("Pointer", "Pointer socket connected")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connected.value = false
                    log.error("Pointer", t.message ?: "Pointer socket failed")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connected.value = false
                    log.info("Pointer", "Pointer socket closed: $code $reason")
                }
            }
        )
    }

    fun disconnect() {
        socket?.close(1000, "Client disconnect")
        socket = null
        _connected.value = false
    }

    fun click() = send("type:click\n\n")

    fun button(name: String) = send("type:button\nname:${name.uppercase()}\n\n")

    fun move(dx: Float, dy: Float, drag: Boolean = false) = send(
        "type:move\ndx:$dx\ndy:$dy\ndown:${if (drag) 1 else 0}\n\n"
    )

    fun scroll(dx: Float, dy: Float) = send(
        "type:scroll\ndx:$dx\ndy:$dy\n\n"
    )

    private fun send(message: String) {
        val success = socket?.send(message) == true
        if (success) {
            log.add(DiagnosticDirection.OUT, "Pointer", message.trim().replace("\n", " | "))
        } else {
            log.warn("Pointer", "Pointer command skipped because the socket is not connected")
        }
    }
}
