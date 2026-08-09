package com.skallahaze.musiccapsule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

class PlaybackAnalyzerService : Service() {
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var running = false

    private val smoothedLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private var lastSignalAt = 0L
    private var lastPublishAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CapsuleRuntime.markAnalyzerStopped("Audioaufnahme-Berechtigung fehlt")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Int.MIN_VALUE || resultData == null) {
            CapsuleRuntime.markAnalyzerStopped("Systemfreigabe für internes Medienaudio fehlt")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) startCapture(resultCode, resultData)
        return START_NOT_STICKY
    }

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

            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
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
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setBufferSizeInBytes(max(minBuffer * 2, 16_384))
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                error("AudioRecord konnte nicht initialisiert werden")
            }

            recorder = record
            acquireWakeLock()
            running = true
            lastSignalAt = System.currentTimeMillis()
            record.startRecording()
            CapsuleRuntime.updateAnalyzer(
                running = true,
                message = "Audioanalyse LIVE · wartet auf SoundCloud/Medienaudio",
                source = "playback-capture",
            )

            captureThread = thread(name = "MusicCapsule-PlaybackAnalyzer", isDaemon = true) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val bytes = ByteArray(PCM_CHUNK_BYTES)
                while (running) {
                    val read = runCatching {
                        record.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING)
                    }.getOrDefault(-1)

                    if (read > 0) {
                        analyzePcm(bytes, read)
                    } else if (read < 0) {
                        CapsuleRuntime.markAnalyzerStopped("Audioanalyse abgebrochen: $read")
                        break
                    }
                }
                stopSelf()
            }
        }.onFailure { error ->
            CapsuleRuntime.markAnalyzerStopped(
                "Audioanalyse konnte nicht starten: ${error.message ?: error.javaClass.simpleName}",
            )
            stopSelf()
        }
    }

    private fun analyzePcm(bytes: ByteArray, length: Int) {
        val usable = length - (length % 4)
        val frames = usable / 4
        if (frames < 128) return

        val samples = FloatArray(frames)
        var offset = 0
        var squareSum = 0.0
        var peak = 0f
        for (frame in 0 until frames) {
            val left = pcm16(bytes, offset) / 32768f
            val right = pcm16(bytes, offset + 2) / 32768f
            val mono = (left + right) * 0.5f
            samples[frame] = mono
            squareSum += mono * mono
            peak = max(peak, abs(mono))
            offset += 4
        }

        val rms = sqrt(squareSum / frames).toFloat()
        val signal = max(rms * 4.2f, peak * 0.72f).coerceIn(0f, 1f)
        val levels = FloatArray(CapsuleRuntime.BAND_COUNT)

        for (index in BAND_FREQUENCIES.indices) {
            val magnitude = goertzel(samples, BAND_FREQUENCIES[index])
            val weighted = magnitude * (1f + index * 0.035f)
            val normalized = if (weighted < 0.0015f) {
                0f
            } else {
                (ln(1.0 + weighted * 120.0) / ln(61.0)).toFloat().coerceIn(0f, 1f)
            }
            val target = normalized.coerceIn(0f, 1f)
            val previous = smoothedLevels[index]
            val factor = if (target > previous) 0.68f else 0.20f
            val smooth = previous + (target - previous) * factor
            smoothedLevels[index] = smooth
            levels[index] = smooth
        }

        val now = System.currentTimeMillis()
        if (signal > 0.008f) lastSignalAt = now
        if (now - lastPublishAt < PUBLISH_INTERVAL_MS) return
        lastPublishAt = now

        val message = when {
            signal > 0.008f -> "LIVE · echter SoundCloud/Medien-Equalizer"
            now - lastSignalAt > 2_500L ->
                "Capture aktiv, aber noch kein internes Audiosignal – Musik starten oder App-Capture prüfen"
            else -> "Audioanalyse bereit · wartet auf Musik"
        }

        CapsuleRuntime.updateLevels(
            levels = levels,
            signal = signal,
            message = message,
            source = "playback-capture",
        )
    }

    private fun pcm16(bytes: ByteArray, offset: Int): Int {
        val low = bytes[offset].toInt() and 0xFF
        val high = bytes[offset + 1].toInt()
        return ((high shl 8) or low).toShort().toInt()
    }

    private fun goertzel(samples: FloatArray, frequency: Double): Float {
        val size = samples.size
        val k = (0.5 + size * frequency / SAMPLE_RATE).toInt()
        val omega = 2.0 * PI * k / size
        val coefficient = 2.0 * cos(omega)
        var previous = 0.0
        var previous2 = 0.0

        for (sample in samples) {
            val current = sample + coefficient * previous - previous2
            previous2 = previous
            previous = current
        }

        val power = previous2 * previous2 + previous * previous - coefficient * previous * previous2
        return (sqrt(max(0.0, power)) / size).toFloat()
    }

    private fun acquireWakeLock() {
        runCatching {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MusicCapsule:Analyzer",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackAnalyzerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle("Music Capsule Audioanalyse")
            .setContentText("Internes Medienaudio wird nur analysiert; nichts wird gespeichert")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Music Capsule Audioanalyse",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Lokale Audioanalyse für den schwebenden Equalizer"
                setShowBadge(false)
            },
        )
    }

    override fun onDestroy() {
        running = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        captureThread = null
        smoothedLevels.fill(0f)
        CapsuleRuntime.markAnalyzerStopped("Audioanalyse aus")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.skallahaze.musiccapsule.action.START_ANALYZER"
        const val ACTION_STOP = "com.skallahaze.musiccapsule.action.STOP_ANALYZER"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        private const val CHANNEL_ID = "music_capsule_analyzer"
        private const val NOTIFICATION_ID = 6102
        private const val SAMPLE_RATE = 48_000
        private const val PCM_CHUNK_BYTES = 4_096
        private const val PUBLISH_INTERVAL_MS = 45L

        private val BAND_FREQUENCIES = doubleArrayOf(
            60.0,
            90.0,
            140.0,
            220.0,
            340.0,
            520.0,
            800.0,
            1_200.0,
            1_800.0,
            2_700.0,
            4_000.0,
            5_800.0,
            8_000.0,
            10_500.0,
            13_500.0,
            17_000.0,
        )
    }
}
