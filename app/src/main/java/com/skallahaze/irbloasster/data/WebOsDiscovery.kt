package com.skallahaze.irbloasster.data

import android.content.Context
import android.net.wifi.WifiManager
import com.skallahaze.irbloasster.model.DiscoveredTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory

class WebOsDiscovery(
    context: Context,
    private val log: DiagnosticsLog
) {
    private val appContext = context.applicationContext

    suspend fun discover(timeoutMillis: Int = 3_500): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("living-room-webos-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        try {
            val results = linkedMapOf<String, DiscoveredTv>()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 450

                val request = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: 239.255.255.250:1900\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: ${WebOsProtocol.DISCOVERY_TARGET}\r\n")
                    append("\r\n")
                }.toByteArray(StandardCharsets.UTF_8)

                socket.send(
                    DatagramPacket(
                        request,
                        request.size,
                        InetAddress.getByName("239.255.255.250"),
                        1900
                    )
                )
                log.info("Discovery", "SSDP search sent")

                val deadline = System.currentTimeMillis() + timeoutMillis
                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(8_192)
                    val response = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(response)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }

                    val text = String(response.data, 0, response.length, StandardCharsets.UTF_8)
                    val headers = parseHeaders(text)
                    val location = headers["location"]
                    val ip = response.address.hostAddress ?: continue
                    if (results.containsKey(ip)) continue

                    val description = location?.let { fetchDescription(it) }
                    val tv = DiscoveredTv(
                        name = description?.friendlyName ?: headers["server"] ?: "LG webOS TV",
                        ipAddress = ip,
                        uuid = description?.uuid ?: headers["usn"],
                        modelName = description?.modelName,
                        location = location
                    )
                    results[ip] = tv
                    log.info("Discovery", "Found ${tv.name} at $ip")
                }
            }
            results.values.toList()
        } finally {
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
    }

    private fun parseHeaders(response: String): Map<String, String> =
        response.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }
            .toMap()

    private data class DeviceDescription(
        val friendlyName: String?,
        val modelName: String?,
        val uuid: String?
    )

    private fun fetchDescription(location: String): DeviceDescription? = runCatching {
        val uri = URI(location)
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 1_500
            readTimeout = 1_500
            requestMethod = "GET"
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        fun text(tag: String): String? {
            val nodes = document.getElementsByTagName(tag)
            if (nodes.length == 0) return null
            return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
        }
        DeviceDescription(
            friendlyName = text("friendlyName"),
            modelName = text("modelName"),
            uuid = text("UDN")
        )
    }.onFailure {
        log.warn("Discovery", "Unable to read TV description: ${it.message}")
    }.getOrNull()
}
