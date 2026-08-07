package com.skallahaze.irbloasster.webos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

class LiveAudioCaptureService : Service() {
    private val capturing = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var server: LiveAudioWebSocketServer? = null
    private var captureThread: Thread? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle("SmartIR Live Audio · HQ")
                .setContentText("PCM 48 kHz Stereo wird im Heimnetz zum TV gestreamt")
                .setOngoing(true)
                .build(),
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            LiveAudioRuntime.update(false, message = "Live-Audio braucht Android 10 oder neuer")
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            LiveAudioRuntime.update(false, message = "Mikrofon-/Audioaufnahme-Berechtigung fehlt")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Int.MIN_VALUE || resultData == null) {
            LiveAudioRuntime.update(false, message = "MediaProjection-Freigabe fehlt")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!capturing.get()) startCapture(resultCode, resultData)
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startCapture(resultCode: Int, resultData: Intent) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            LiveAudioRuntime.update(false, message = "Audioaufnahme-Berechtigung wurde entzogen")
            stopSelf()
            return
        }

        runCatching {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val mediaProjection = manager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjection konnte nicht gestartet werden")
            projection = mediaProjection
            mediaProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopSelf()
                    }
                },
                Handler(Looper.getMainLooper()),
            )

            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val record = try {
                AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setAudioPlaybackCaptureConfig(config)
                    .setBufferSizeInBytes(max(minBuffer * 4, 32_768))
                    .build()
            } catch (security: SecurityException) {
                error("Audioaufnahme nicht erlaubt: ${security.message ?: "Berechtigung fehlt"}")
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                error("AudioRecord konnte nicht initialisiert werden")
            }
            recorder = record

            val wsServer = LiveAudioWebSocketServer()
            val url = wsServer.start()
            server = wsServer

            acquirePerformanceLocks()
            capturing.set(true)
            record.startRecording()
            LiveAudioRuntime.update(true, url, "LIVE · HQ PCM 48 kHz Stereo · $url")

            captureThread = thread(name = "SmartIR-LiveAudio-Capture", isDaemon = true) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val buffer = ByteArray(PCM_CHUNK_BYTES)
                var statsStartedAt = System.currentTimeMillis()
                var statsBytes = 0L
                var statsPeak = 0

                while (capturing.get()) {
                    val read = runCatching {
                        record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    }.getOrDefault(-1)

                    if (read > 0) {
                        wsServer.broadcastPcm(buffer, read)
                        statsBytes += read
                        statsPeak = max(statsPeak, peakMagnitude(buffer, read))

                        val now = System.currentTimeMillis()
                        val elapsed = now - statsStartedAt
                        if (elapsed >= STATS_INTERVAL_MS) {
                            val signal = ((statsPeak.toLong() * 100L) / 32_767L).toInt().coerceIn(0, 100)
                            val kbps = if (elapsed > 0L) ((statsBytes * 8L) / elapsed).toInt() else 0
                            LiveAudioRuntime.updateStats(
                                signalPercent = signal,
                                clientCount = wsServer.clientCount(),
                                throughputKbps = kbps,
                            )
                            statsStartedAt = now
                            statsBytes = 0L
                            statsPeak = 0
                        }
                    } else if (read < 0) {
                        LiveAudioRuntime.update(false, message = "Audioaufnahme abgebrochen: $read")
                        break
                    }
                }
                stopSelf()
            }
        }.onFailure { error ->
            LiveAudioRuntime.update(false, message = error.message ?: "Live-Audio konnte nicht starten")
            stopSelf()
        }
    }

    private fun peakMagnitude(bytes: ByteArray, length: Int): Int {
        var peak = 0
        var index = 0
        while (index + 1 < length) {
            val lo = bytes[index].toInt() and 0xFF
            val hi = bytes[index + 1].toInt()
            val signed = ((hi shl 8) or lo).toShort().toInt()
            peak = max(peak, abs(signed).coerceAtMost(32_767))
            index += 2
        }
        return peak
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun acquirePerformanceLocks() {
        runCatching {
            val wifi = getSystemService(WifiManager::class.java)
            wifiLock = wifi.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "SmartIR:LiveAudioWifi",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        runCatching {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartIR:LiveAudioCpu",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    override fun onDestroy() {
        capturing.set(false)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { server?.close() }
        server = null
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wifiLock = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        captureThread = null
        LiveAudioRuntime.update(false, message = "Live-Audio aus")
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SmartIR Live Audio",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val ACTION_START = "com.skallahaze.irbloasster.action.START_LIVE_AUDIO"
        const val ACTION_STOP = "com.skallahaze.irbloasster.action.STOP_LIVE_AUDIO"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        private const val CHANNEL_ID = "smartir_live_audio"
        private const val NOTIFICATION_ID = 4102
        private const val SAMPLE_RATE = 48_000
        private const val PCM_CHUNK_BYTES = 7_680
        private const val STATS_INTERVAL_MS = 750L
    }
}
