package com.skallahaze.irbloasster.webos

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredWebOsTv(
    val name: String,
    val host: String,
    val server: String = "",
    val usn: String = "",
)

class SsdpDiscovery(context: Context) {
    private val appContext = context.applicationContext

    suspend fun discover(timeoutMs: Long = 3_800L): Result<List<DiscoveredWebOsTv>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val wifiManager = appContext.getSystemService(WifiManager::class.java)
                val multicastLock = wifiManager?.createMulticastLock("smartir-ssdp")?.apply {
                    setReferenceCounted(false)
                }

                try {
                    multicastLock?.acquire()
                    val found = linkedMapOf<String, DiscoveredWebOsTv>()
                    DatagramSocket(null).use { socket ->
                        socket.reuseAddress = true
                        socket.broadcast = true
                        socket.soTimeout = 450
                        socket.bind(InetSocketAddress(0))

                        val multicastAddress = InetAddress.getByName(SSDP_ADDRESS)
                        SEARCH_TARGETS.forEach { target ->
                            val bytes = searchRequest(target).toByteArray(Charsets.UTF_8)
                            socket.send(DatagramPacket(bytes, bytes.size, multicastAddress, SSDP_PORT))
                        }

                        val started = System.currentTimeMillis()
                        val buffer = ByteArray(8_192)
                        while (System.currentTimeMillis() - started < timeoutMs) {
                            try {
                                val packet = DatagramPacket(buffer, buffer.size)
                                socket.receive(packet)
                                val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                                if (!looksLikeWebOs(raw)) continue

                                val headers = parseHeaders(raw)
                                val host = packet.address.hostAddress.orEmpty()
                                if (host.isBlank()) continue

                                val name = headers["dlnadevicename.lge.com"]
                                    ?: headers["friendlyname.dlna.org"]
                                    ?: headers["friendlyname"]
                                    ?: "LG webOS TV"

                                found[host] = DiscoveredWebOsTv(
                                    name = name.trim().ifBlank { "LG webOS TV" },
                                    host = host,
                                    server = headers["server"].orEmpty(),
                                    usn = headers["usn"].orEmpty(),
                                )
                            } catch (_: java.net.SocketTimeoutException) {
                                // Continue until the overall discovery timeout expires.
                            }
                        }
                    }
                    found.values.sortedBy { it.name.lowercase(Locale.ROOT) }
                } finally {
                    runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
                }
            }
        }

    private fun looksLikeWebOs(raw: String): Boolean =
        raw.contains("webos", ignoreCase = true) ||
            raw.contains("lge", ignoreCase = true) ||
            raw.contains("webos-second-screen", ignoreCase = true)

    private fun parseHeaders(raw: String): Map<String, String> = buildMap {
        raw.lineSequence().drop(1).forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                put(
                    line.substring(0, separator).trim().lowercase(Locale.ROOT),
                    line.substring(separator + 1).trim(),
                )
            }
        }
    }

    private fun searchRequest(target: String): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 2\r\n")
        append("ST: $target\r\n")
        append("USER-AGENT: Android SmartIR/1.1\r\n")
        append("\r\n")
    }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        val SEARCH_TARGETS = listOf(
            "urn:lge-com:service:webos-second-screen:1",
            "ssdp:all",
        )
    }
}
