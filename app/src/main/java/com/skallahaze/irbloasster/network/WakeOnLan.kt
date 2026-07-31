package com.skallahaze.irbloasster.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    suspend fun send(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mac = parseMac(macAddress)
            val packet = ByteArray(6 + 16 * mac.size)
            repeat(6) { packet[it] = 0xFF.toByte() }
            for (copy in 0 until 16) {
                mac.copyInto(packet, destinationOffset = 6 + copy * mac.size)
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                val target = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(packet, packet.size, target, 9))
            }
        }
    }

    private fun parseMac(value: String): ByteArray {
        val normalized = value.replace("-", "").replace(":", "").trim()
        require(normalized.length == 12 && normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "MAC-Adresse muss 12 Hex-Zeichen enthalten"
        }
        return ByteArray(6) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
