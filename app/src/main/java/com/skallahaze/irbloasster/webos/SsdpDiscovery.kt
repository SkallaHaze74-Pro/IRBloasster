package com.skallahaze.irbloasster.webos

import android.content.Context
import android.net.wifi.WifiManager
import com.skallahaze.irbloasster.model.DiscoveredTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale

class SsdpDiscovery(private val context: Context) {
    data class Result(
        val devices: List<DiscoveredTv>,
        val error: String? = null
    )

    suspend fun discover(timeoutMs: Long = 3_800L): Result = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        val multicastLock = wifiManager?.createMulticastLock("livingroom-ssdp")?.apply {
            setReferenceCounted(false)
        }

        try {
            multicastLock?.acquire()
            val discovered = linkedMapOf<String, DiscoveredTv>()
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.soTimeout = 450
                socket.bind(InetSocketAddress(0))

                val address = InetAddress.getByName(SSDP_ADDRESS)
                SEARCH_TARGETS.forEach { target ->
                    val request = buildSearchRequest(target).toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(request, request.size, address, SSDP_PORT))
                }

                val startedAt = System.currentTimeMillis()
                val buffer = ByteArray(8_192)
                while (System.currentTimeMillis() - startedAt < timeoutMs) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val headers = parseHeaders(raw)
                        val server = headers["server"].orEmpty()
                        val st = headers["st"].orEmpty()
                        val location = headers["location"].orEmpty()
                        val name = headers["dlnadevicename.lge.com"]
                            ?: headers["friendlyname.dlna.org"]
                            ?: headers["friendlyname"]
                            ?: "LG webOS TV"

                        val looksLikeWebOs = raw.contains("webos", ignoreCase = true) ||
                            raw.contains("lge", ignoreCase = true) ||
                            st.contains("webos-second-screen", ignoreCase = true)
                        if (!looksLikeWebOs) continue

                        val ip = packet.address.hostAddress.orEmpty()
                        if (ip.isBlank()) continue
                        val port = Regex(":(\\d+)(?:/|$)").find(location)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?: 3001

                        discovered[ip] = DiscoveredTv(
                            name = name.trim().ifBlank { "LG webOS TV" },
                            ipAddress = ip,
                            port = port,
                            server = server,
                            usn = headers["usn"].orEmpty()
                        )
                    } catch (_: java.net.SocketTimeoutException) {
                        // Keep listening until the overall timeout is reached.
                    }
                }
            }
            Result(discovered.values.toList().sortedBy { it.name.lowercase(Locale.ROOT) })
        } catch (error: SecurityException) {
            Result(emptyList(), "Lokales Netzwerk wurde blockiert: ${error.message ?: "Berechtigung fehlt"}")
        } catch (error: Exception) {
            Result(emptyList(), error.message ?: error.javaClass.simpleName)
        } finally {
            runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
        }
    }

    private fun parseHeaders(raw: String): Map<String, String> = buildMap {
        raw.lineSequence().drop(1).forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                put(
                    line.substring(0, separator).trim().lowercase(Locale.ROOT),
                    line.substring(separator + 1).trim()
                )
            }
        }
    }

    private fun buildSearchRequest(target: String): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 2\r\n")
        append("ST: $target\r\n")
        append("USER-AGENT: Android LivingRoomController/1.0\r\n")
        append("\r\n")
    }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        val SEARCH_TARGETS = listOf(
            "urn:lge-com:service:webos-second-screen:1",
            "ssdp:all"
        )
    }
}
