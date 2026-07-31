package com.skallahaze.irbloasster.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WakeOnLan(
    private val log: DiagnosticsLog
) {
    suspend fun send(macAddress: String, broadcastAddress: String = "255.255.255.255"): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mac = parseMac(macAddress)
                val packetBytes = ByteArray(6 + 16 * mac.size)
                repeat(6) { packetBytes[it] = 0xFF.toByte() }
                for (repeatIndex in 0 until 16) {
                    mac.copyInto(packetBytes, 6 + repeatIndex * mac.size)
                }

                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(
                        DatagramPacket(
                            packetBytes,
                            packetBytes.size,
                            InetAddress.getByName(broadcastAddress),
                            9
                        )
                    )
                }
                log.info("Wake-on-LAN", "Magic packet sent to ${maskMac(macAddress)}")
            }.onFailure {
                log.error("Wake-on-LAN", it.message ?: "Magic packet failed")
            }
        }

    private fun parseMac(value: String): ByteArray {
        val normalized = value.replace(Regex("[^0-9A-Fa-f]"), "")
        require(normalized.length == 12) { "MAC address must contain 12 hexadecimal digits" }
        return ByteArray(6) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun maskMac(value: String): String {
        val normalized = value.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        if (normalized.length != 12) return "configured TV"
        return "**:**:**:${normalized.substring(6, 8)}:${normalized.substring(8, 10)}:${normalized.substring(10, 12)}"
    }
}
