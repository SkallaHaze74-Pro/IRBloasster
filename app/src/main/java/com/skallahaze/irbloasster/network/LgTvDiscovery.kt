package com.skallahaze.irbloasster.network

import android.content.Context
import android.net.wifi.WifiManager
import com.skallahaze.irbloasster.model.DiscoveredTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.Locale

class LgTvDiscovery(private val context: Context) {
    suspend fun discover(timeoutMs: Long = 3_200L): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("living-room-controller-ssdp")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        val found = linkedMapOf<String, DiscoveredTv>()
        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.soTimeout = 450
                socket.bind(InetSocketAddress(0))

                val multicast = InetAddress.getByName("239.255.255.250")
                val searchTargets = listOf(
                    "urn:lge-com:service:webos-second-screen:1",
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "ssdp:all"
                )
                searchTargets.forEach { target ->
                    val request = buildString {
                        append("M-SEARCH * HTTP/1.1\r\n")
                        append("HOST: 239.255.255.250:1900\r\n")
                        append("MAN: \"ssdp:discover\"\r\n")
                        append("MX: 2\r\n")
                        append("ST: $target\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(request, request.size, multicast, 1900))
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(8_192)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val headers = parseHeaders(text)
                    val haystack = text.lowercase(Locale.ROOT)
                    val looksLikeLg = haystack.contains("webos") ||
                        haystack.contains("lge") ||
                        haystack.contains("lg electronics")
                    if (!looksLikeLg) continue

                    val host = packet.address.hostAddress.orEmpty()
                    if (host.isBlank()) continue
                    val location = headers["location"].orEmpty()
                    val name = headers["server"]
                        ?.substringBefore("/")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "LG webOS TV"
                    found[host] = DiscoveredTv(name = name, host = host, location = location)
                }
            }
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
        found.values.sortedBy { it.host }
    }

    private fun parseHeaders(raw: String): Map<String, String> = raw
        .lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null
            else line.substring(0, separator).trim().lowercase(Locale.ROOT) to
                line.substring(separator + 1).trim()
        }
        .toMap()
}
