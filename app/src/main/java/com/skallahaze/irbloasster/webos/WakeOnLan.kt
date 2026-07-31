package com.skallahaze.irbloasster.webos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    suspend fun send(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mac = parseMac(macAddress)
            val payload = ByteArray(6 + 16 * mac.size)
            repeat(6) { payload[it] = 0xFF.toByte() }
            for (index in 6 until payload.size) {
                payload[index] = mac[(index - 6) % mac.size]
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                val broadcast = InetAddress.getByName("255.255.255.255")
                repeat(3) {
                    for (port in intArrayOf(9, 7)) {
                        socket.send(DatagramPacket(payload, payload.size, broadcast, port))
                    }
                    delay(70L)
                }
            }
        }
    }

    private fun parseMac(value: String): ByteArray {
        val normalized = value.replace("-", "").replace(":", "").replace(".", "").trim()
        require(normalized.matches(Regex("[0-9A-Fa-f]{12}"))) {
            "MAC-Adresse muss aus 12 Hex-Zeichen bestehen"
        }
        return ByteArray(6) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
