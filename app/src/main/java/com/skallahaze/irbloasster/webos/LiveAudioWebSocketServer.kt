package com.skallahaze.irbloasster.webos

import android.util.Base64
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LiveAudioWebSocketServer : Closeable {
    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val outboundFrames = ArrayBlockingQueue<ByteArray>(12)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running: Boolean = false

    @Volatile
    var port: Int = -1
        private set

    fun start(): String {
        if (serverSocket?.isClosed == false && port > 0) return url()

        val server = ServerSocket(0).apply { reuseAddress = true }
        serverSocket = server
        port = server.localPort
        running = true

        thread(name = "SmartIR-LiveAudio-Sender", isDaemon = true) {
            sendLoop()
        }

        thread(name = "SmartIR-LiveAudio-Accept", isDaemon = true) {
            while (running && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    client.tcpNoDelay = true
                    client.keepAlive = true
                    client.sendBufferSize = maxOf(client.sendBufferSize, 64 * 1024)
                }
                thread(name = "SmartIR-LiveAudio-Handshake", isDaemon = true) {
                    if (handshake(client)) {
                        clients += client
                    } else {
                        runCatching { client.close() }
                    }
                }
            }
        }
        return url()
    }

    /**
     * Never block the AudioRecord thread on WLAN I/O. If the TV/network is
     * temporarily slower than capture, discard the oldest pending frame instead
     * of creating an ever-growing latency spike.
     */
    fun broadcastPcm(bytes: ByteArray, length: Int) {
        if (length <= 0 || !running) return
        val framed = frame(bytes, length)
        if (!outboundFrames.offer(framed)) {
            outboundFrames.poll()
            outboundFrames.offer(framed)
        }
    }

    fun clientCount(): Int = synchronized(clients) { clients.size }

    fun url(): String {
        val host = findLanIpv4() ?: "127.0.0.1"
        return "ws://$host:$port/live"
    }

    private fun sendLoop() {
        while (running) {
            val frame = runCatching {
                outboundFrames.poll(500, TimeUnit.MILLISECONDS)
            }.getOrNull() ?: continue

            val dead = mutableListOf<Socket>()
            synchronized(clients) {
                clients.forEach { client ->
                    val ok = runCatching {
                        val output = client.getOutputStream()
                        output.write(frame)
                        output.flush()
                    }.isSuccess
                    if (!ok) dead += client
                }
                dead.forEach {
                    clients.remove(it)
                    runCatching { it.close() }
                }
            }
        }
    }

    private fun handshake(socket: Socket): Boolean {
        socket.soTimeout = 8_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
        val requestLine = reader.readLine() ?: return false
        if (!requestLine.contains(" /live ")) return false

        var key: String? = null
        while (true) {
            val line = reader.readLine() ?: return false
            if (line.isBlank()) break
            if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                key = line.substringAfter(':').trim()
            }
        }
        val websocketKey = key ?: return false
        val accept = websocketAccept(websocketKey)
        val response = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: $accept\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
        socket.soTimeout = 0
        return true
    }

    private fun websocketAccept(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest((key + WEBSOCKET_GUID).toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun frame(payload: ByteArray, length: Int): ByteArray {
        val headerSize = when {
            length <= 125 -> 2
            length <= 0xFFFF -> 4
            else -> 10
        }
        val frame = ByteArray(headerSize + length)
        frame[0] = 0x82.toByte()
        when {
            length <= 125 -> frame[1] = length.toByte()
            length <= 0xFFFF -> {
                frame[1] = 126.toByte()
                frame[2] = ((length ushr 8) and 0xFF).toByte()
                frame[3] = (length and 0xFF).toByte()
            }
            else -> {
                frame[1] = 127.toByte()
                val value = length.toLong()
                for (index in 0 until 8) {
                    frame[2 + index] = ((value ushr (56 - index * 8)) and 0xFF).toByte()
                }
            }
        }
        System.arraycopy(payload, 0, frame, headerSize, length)
        return frame
    }

    private fun findLanIpv4(): String? {
        val enumeration = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        val interfaces = Collections.list(enumeration)
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .sortedBy { iface -> if (iface.name.startsWith("wlan", ignoreCase = true)) 0 else 1 }

        for (networkInterface in interfaces) {
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address: InetAddress = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress) {
                    return address.hostAddress
                }
            }
        }
        return null
    }

    override fun close() {
        running = false
        outboundFrames.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        port = -1
        synchronized(clients) {
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }
    }

    private companion object {
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
