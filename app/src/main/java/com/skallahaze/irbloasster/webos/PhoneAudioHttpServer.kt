package com.skallahaze.irbloasster.webos

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tiny LAN-only HTTP server for the root-free SmartIR Audio Bridge.
 *
 * The selected document is copied into the app cache first so the TV can use
 * ordinary byte-range requests. No cloud upload is involved; the file is served
 * only from this phone while the server is running.
 */
class PhoneAudioHttpServer(context: Context) : Closeable {
    data class PreparedAudio(
        val file: File,
        val mimeType: String,
        val displayName: String,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDirectory = File(appContext.cacheDir, "audio_mix").apply { mkdirs() }

    @Volatile
    private var preparedAudio: PreparedAudio? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var serverPort: Int = -1

    suspend fun prepare(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val displayName = queryDisplayName(uri) ?: "smartir-audio"
            val mimeType = resolver.getType(uri)
                ?: URLConnection.guessContentTypeFromName(displayName)
                ?: "application/octet-stream"
            val extension = displayName.substringAfterLast('.', "").ifBlank {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType).orEmpty()
            }
            val target = File(
                cacheDirectory,
                if (extension.isBlank()) "current_audio" else "current_audio.$extension",
            )

            cacheDirectory.listFiles()?.forEach { old ->
                if (old != target) old.delete()
            }

            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: error("Audiodatei konnte nicht geöffnet werden")

            if (target.length() <= 0L) error("Audiodatei ist leer")

            preparedAudio = PreparedAudio(target, mimeType, displayName)
            ensureServer()
            val host = findLanIpv4()
                ?: error("Keine lokale IPv4-Adresse gefunden. Handy und TV müssen im gleichen WLAN sein.")
            "http://$host:$serverPort/audio"
        }
    }

    fun currentName(): String = preparedAudio?.displayName.orEmpty()

    fun currentUrl(): String? {
        if (preparedAudio == null || serverPort <= 0) return null
        val host = findLanIpv4() ?: return null
        return "http://$host:$serverPort/audio"
    }

    private fun ensureServer() {
        if (serverSocket?.isClosed == false && serverPort > 0) return

        val socket = ServerSocket(0).apply {
            reuseAddress = true
        }
        serverSocket = socket
        serverPort = socket.localPort
        scope.launch { acceptLoop(socket) }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch { serve(client) }
        }
    }

    private fun serve(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 8_000
            val input = client.getInputStream()
            val output = client.getOutputStream().buffered()
            val reader = BufferedReader(InputStreamReader(input, Charsets.US_ASCII))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0)?.uppercase(Locale.ROOT).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?')

            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(':').trim()
                }
            }

            if (path != "/audio") {
                writeSimple(output, 404, "Not Found")
                return
            }

            val audio = preparedAudio
            if (audio == null || !audio.file.isFile) {
                writeSimple(output, 503, "Audio not ready")
                return
            }

            if (method != "GET" && method != "HEAD") {
                writeSimple(output, 405, "Method Not Allowed")
                return
            }

            val total = audio.file.length()
            val range = parseRange(rangeHeader, total)
            if (rangeHeader != null && range == null) {
                output.write("HTTP/1.1 416 Range Not Satisfiable\r\n".toByteArray())
                output.write("Content-Range: bytes */$total\r\n".toByteArray())
                output.write("Connection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }

            val start = range?.first ?: 0L
            val end = range?.last ?: (total - 1L)
            val length = if (total == 0L) 0L else (end - start + 1L)
            val partial = range != null

            output.write(
                "HTTP/1.1 ${if (partial) "206 Partial Content" else "200 OK"}\r\n".toByteArray(),
            )
            output.write("Content-Type: ${audio.mimeType}\r\n".toByteArray())
            output.write("Content-Length: $length\r\n".toByteArray())
            output.write("Accept-Ranges: bytes\r\n".toByteArray())
            output.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
            if (partial) output.write("Content-Range: bytes $start-$end/$total\r\n".toByteArray())
            output.write("Cache-Control: no-store\r\n".toByteArray())
            output.write("Connection: close\r\n\r\n".toByteArray())

            if (method == "HEAD" || length <= 0L) {
                output.flush()
                return
            }

            RandomAccessFile(audio.file, "r").use { file ->
                file.seek(start)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = length
                while (remaining > 0L) {
                    val read = file.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
            output.flush()
        }
    }

    private fun parseRange(header: String?, total: Long): LongRange? {
        if (header.isNullOrBlank()) return null
        if (!header.startsWith("bytes=", ignoreCase = true) || total <= 0L) return null
        val value = header.substringAfter('=').substringBefore(',').trim()
        val dash = value.indexOf('-')
        if (dash < 0) return null

        val left = value.substring(0, dash).trim()
        val right = value.substring(dash + 1).trim()

        return when {
            left.isNotBlank() -> {
                val start = left.toLongOrNull() ?: return null
                val end = right.toLongOrNull()?.coerceAtMost(total - 1L) ?: (total - 1L)
                if (start < 0L || start >= total || end < start) null else start..end
            }

            right.isNotBlank() -> {
                val suffix = right.toLongOrNull() ?: return null
                if (suffix <= 0L) null else (total - suffix.coerceAtMost(total))..(total - 1L)
            }

            else -> null
        }
    }

    private fun writeSimple(output: java.io.BufferedOutputStream, code: Int, message: String) {
        val body = message.toByteArray()
        output.write("HTTP/1.1 $code $message\r\n".toByteArray())
        output.write("Content-Type: text/plain; charset=utf-8\r\n".toByteArray())
        output.write("Content-Length: ${body.size}\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else it.getString(index)
        }
    }

    private fun findLanIpv4(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address: InetAddress = addresses.nextElement()
                if (
                    address is Inet4Address &&
                    !address.isLoopbackAddress &&
                    address.isSiteLocalAddress
                ) {
                    return address.hostAddress
                }
            }
        }
        return null
    }

    override fun close() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverPort = -1
        scope.cancel()
    }
}
