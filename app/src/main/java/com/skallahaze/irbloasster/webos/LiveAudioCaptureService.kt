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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max

class LiveAudioCaptureService : Service() {
    private val capturing = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var server: LiveAudioWebSocketServer? = null
    private var captureThread: Thread? = null

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
                .setContentTitle("SmartIR Live Audio")
                .setContentText("Handy-Audio wird nur im Heimnetz an den TV gestreamt")
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
            val record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(max(minBuffer * 2, 16_384))
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                error("AudioRecord konnte nicht initialisiert werden")
            }
            recorder = record

            val wsServer = LiveAudioWebSocketServer()
            val url = wsServer.start()
            server = wsServer

            capturing.set(true)
            record.startRecording()
            LiveAudioRuntime.update(true, url, "LIVE · $url")

            captureThread = thread(name = "SmartIR-LiveAudio-Capture", isDaemon = true) {
                val buffer = ByteArray(PCM_CHUNK_BYTES)
                while (capturing.get()) {
                    val read = runCatching { record.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                    if (read > 0) {
                        wsServer.broadcastPcm(buffer, read)
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

    override fun onDestroy() {
        capturing.set(false)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { server?.close() }
        server = null
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
        private const val PCM_CHUNK_BYTES = 3_840
    }
}
