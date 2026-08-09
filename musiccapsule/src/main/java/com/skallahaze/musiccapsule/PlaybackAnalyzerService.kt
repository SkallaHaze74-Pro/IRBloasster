package com.skallahaze.musiccapsule

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlin.math.min
import kotlin.math.sqrt

class PlaybackAnalyzerService : Service() {
    private enum class AnalyzerMode {
        PLAYBACK_CAPTURE,
        MICROPHONE_FALLBACK,
    }

    private data class AudioFeatures(
        val bass: Float,
        val mid: Float,
        val treble: Float,
        val beat: Float,
        val spectralFlux: Float,
        val beatSequence: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var projectionCallback: MediaProjection.Callback? = null

    @Volatile
    private var running = false

    @Volatile
    private var switchingMode = false

    @Volatile
    private var shuttingDown = false

    private var recorderGeneration = 0
    private var analyzerMode = AnalyzerMode.PLAYBACK_CAPTURE
    private var playbackSilenceSince = 0L
    private var nonSoundCloudSince = 0L
    private var lastFallbackWarningAt = 0L
    private val smoothedLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val previousRawLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private var spectralFluxAverage = .018f
    private var bassAverage = .03f
    private var lastBeatAt = 0L
    private var beatSequence = 0L
    private var lastSignalAt = 0L
    private var lastPublishAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shuttingDown = true
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

        restartPlaybackCapture(resultCode, resultData)
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun restartPlaybackCapture(resultCode: Int, resultData: Intent) {
        runCatching {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                error("Audioaufnahme-Berechtigung wurde entzogen")
            }

            switchingMode = true
            stopRecorderOnly()
            releaseProjectionOnly()
            resetFeatureDetector(resetSequence = false)
            playbackSilenceSince = 0L
            nonSoundCloudSince = 0L
            analyzerMode = AnalyzerMode.PLAYBACK_CAPTURE

            val manager = getSystemService(MediaProjectionManager::class.java)
            val mediaProjection = manager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjection konnte nicht gestartet werden")
            projection = mediaProjection

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!switchingMode && !shuttingDown) stopSelf()
                }
            }
            projectionCallback = callback
            mediaProjection.registerCallback(callback, mainHandler)

            startPlaybackRecorder(mediaProjection)
            switchingMode = false
        }.onFailure { error ->
            switchingMode = false
            CapsuleRuntime.markAnalyzerStopped(
                "Audioanalyse konnte nicht starten: ${error.message ?: error.javaClass.simpleName}",
            )
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPlaybackRecorder(mediaProjection: MediaProjection) {
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

        analyzerMode = AnalyzerMode.PLAYBACK_CAPTURE
        playbackSilenceSince = 0L
        startReader(
            record = record,
            channels = 2,
            mode = AnalyzerMode.PLAYBACK_CAPTURE,
            startMessage = "BRUTAL REACTIVE bereit · internes Medienaudio",
        )
    }

    @SuppressLint("MissingPermission")
    private fun switchToMicrophoneFallback() {
        if (switchingMode || analyzerMode == AnalyzerMode.MICROPHONE_FALLBACK || shuttingDown) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CapsuleRuntime.updateAnalyzer(
                running = true,
                message = "SoundCloud schützt internen Ton; Mikrofon-Berechtigung fehlt",
                source = "soundcloud-system-only",
            )
            return
        }

        runCatching {
            switchingMode = true
            stopRecorderOnly()
            resetFeatureDetector(resetSequence = false)

            val audioManager = getSystemService(AudioManager::class.java)
            val unprocessedSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val audioSource = if (unprocessedSupported) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val record = AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(format)
                .setBufferSizeInBytes(max(minBuffer * 2, 16_384))
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                error("Mikrofon-Fallback konnte nicht initialisiert werden")
            }

            analyzerMode = AnalyzerMode.MICROPHONE_FALLBACK
            nonSoundCloudSince = 0L
            startReader(
                record = record,
                channels = 1,
                mode = AnalyzerMode.MICROPHONE_FALLBACK,
                startMessage = "SoundCloud Mikrofon-Fallback · Beat-FX aktiv",
            )
            switchingMode = false
        }.onFailure { error ->
            switchingMode = false
            CapsuleRuntime.updateAnalyzer(
                running = true,
                message = "SoundCloud schützt internen Ton; Mikrofon-Fallback fehlgeschlagen: ${error.message}",
                source = "soundcloud-system-only",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun switchBackToPlaybackCapture() {
        val activeProjection = projection ?: return
        if (switchingMode || analyzerMode == AnalyzerMode.PLAYBACK_CAPTURE || shuttingDown) return

        runCatching {
            switchingMode = true
            stopRecorderOnly()
            resetFeatureDetector(resetSequence = false)
            startPlaybackRecorder(activeProjection)
            switchingMode = false
        }.onFailure { error ->
            switchingMode = false
            CapsuleRuntime.updateAnalyzer(
                running = true,
                message = "Interne Audioanalyse konnte nicht wiederhergestellt werden: ${error.message}",
                source = "playback-capture-error",
            )
        }
    }

    private fun startReader(
        record: AudioRecord,
        channels: Int,
        mode: AnalyzerMode,
        startMessage: String,
    ) {
        acquireWakeLock()
        recorder = record
        running = true
        lastSignalAt = System.currentTimeMillis()
        lastPublishAt = 0L
        val generation = ++recorderGeneration
        record.startRecording()

        CapsuleRuntime.updateAnalyzer(
            running = true,
            message = startMessage,
            source = if (mode == AnalyzerMode.PLAYBACK_CAPTURE) {
                "playback-capture"
            } else {
                "microphone-fallback"
            },
        )

        captureThread = thread(name = "MusicCapsule-${mode.name}", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val bytes = ByteArray(PCM_CHUNK_BYTES)
            while (running && recorderGeneration == generation && !shuttingDown) {
                val read = runCatching {
                    record.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING)
                }.getOrDefault(-1)

                if (read > 0) {
                    val signal = analyzePcm(bytes, read, channels, mode)
                    when (mode) {
                        AnalyzerMode.PLAYBACK_CAPTURE -> maybeStartSoundCloudFallback(signal)
                        AnalyzerMode.MICROPHONE_FALLBACK -> maybeReturnToPlaybackCapture()
                    }
                } else if (read < 0 && !switchingMode) {
                    CapsuleRuntime.markAnalyzerStopped("Audioanalyse abgebrochen: $read")
                    break
                }
            }

            if (!switchingMode && !shuttingDown && recorderGeneration == generation) {
                stopSelf()
            }
        }
    }

    private fun maybeStartSoundCloudFallback(signal: Float) {
        val snapshot = CapsuleRuntime.snapshot()
        val soundCloudSelected = CapsulePreferences.sourceLock(this) == MediaSourceLock.SOUNDCLOUD ||
            snapshot.packageName.contains("soundcloud", ignoreCase = true)

        if (!soundCloudSelected || !snapshot.isPlaying) {
            playbackSilenceSince = 0L
            return
        }
        if (signal > SIGNAL_THRESHOLD) {
            playbackSilenceSince = 0L
            return
        }

        val now = System.currentTimeMillis()
        if (playbackSilenceSince == 0L) playbackSilenceSince = now
        if (now - playbackSilenceSince < SOUNDCLOUD_FALLBACK_DELAY_MS) return

        if (externalAudioRouteActive()) {
            if (now - lastFallbackWarningAt > 2_000L) {
                lastFallbackWarningAt = now
                CapsuleRuntime.updateAnalyzer(
                    running = true,
                    message = "SoundCloud erlaubt nur Systemaufnahme. Kopfhörer/Bluetooth erkannt; Mikrofon-Fallback braucht den Handylautsprecher.",
                    source = "soundcloud-system-only",
                )
            }
            return
        }

        mainHandler.post { switchToMicrophoneFallback() }
    }

    private fun maybeReturnToPlaybackCapture() {
        val snapshot = CapsuleRuntime.snapshot()
        val soundCloudSelected = CapsulePreferences.sourceLock(this) == MediaSourceLock.SOUNDCLOUD ||
            snapshot.packageName.contains("soundcloud", ignoreCase = true)
        if (soundCloudSelected) {
            nonSoundCloudSince = 0L
            return
        }

        val now = System.currentTimeMillis()
        if (nonSoundCloudSince == 0L) nonSoundCloudSince = now
        if (now - nonSoundCloudSince >= RETURN_TO_PLAYBACK_DELAY_MS) {
            mainHandler.post { switchBackToPlaybackCapture() }
        }
    }

    private fun externalAudioRouteActive(): Boolean {
        val manager = getSystemService(AudioManager::class.java)
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_HDMI,
                -> true

                else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
    }

    private fun analyzePcm(
        bytes: ByteArray,
        length: Int,
        channels: Int,
        mode: AnalyzerMode,
    ): Float {
        val bytesPerFrame = channels * 2
        val usable = length - (length % bytesPerFrame)
        val frames = usable / bytesPerFrame
        if (frames < 128) return 0f

        val samples = FloatArray(frames)
        var offset = 0
        var squareSum = 0.0
        var peak = 0f
        for (frame in 0 until frames) {
            var mono = 0f
            for (channel in 0 until channels) {
                mono += pcm16(bytes, offset + channel * 2) / 32768f
            }
            mono /= channels
            samples[frame] = mono
            squareSum += mono * mono
            peak = max(peak, abs(mono))
            offset += bytesPerFrame
        }

        val rms = sqrt(squareSum / frames).toFloat()
        val multiplier = if (mode == AnalyzerMode.MICROPHONE_FALLBACK) 7.2f else 4.2f
        val signal = max(rms * multiplier, peak * .72f).coerceIn(0f, 1f)
        val levels = FloatArray(CapsuleRuntime.BAND_COUNT)
        val rawLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

        for (index in BAND_FREQUENCIES.indices) {
            val magnitude = goertzel(samples, BAND_FREQUENCIES[index])
            val weighted = magnitude * (1f + index * .035f)
            val normalization = if (mode == AnalyzerMode.MICROPHONE_FALLBACK) 210.0 else 120.0
            val normalized = if (weighted < .0012f) {
                0f
            } else {
                (ln(1.0 + weighted * normalization) / ln(1.0 + normalization * .50)).toFloat()
                    .coerceIn(0f, 1f)
            }
            rawLevels[index] = normalized
            val previous = smoothedLevels[index]
            val factor = if (normalized > previous) .68f else .20f
            val smooth = previous + (normalized - previous) * factor
            smoothedLevels[index] = smooth
            levels[index] = smooth
        }

        val now = System.currentTimeMillis()
        val features = detectFeatures(rawLevels, mode, now)
        if (signal > SIGNAL_THRESHOLD) lastSignalAt = now
        val forceBeatPublish = features.beat > 0f
        if (!forceBeatPublish && now - lastPublishAt < PUBLISH_INTERVAL_MS) return signal
        lastPublishAt = now

        val message = when (mode) {
            AnalyzerMode.PLAYBACK_CAPTURE -> when {
                signal > SIGNAL_THRESHOLD ->
                    "BRUTAL REACTIVE LIVE · Bass ${percent(features.bass)} · Beat ${percent(features.beat)}"
                now - lastSignalAt > 2_500L ->
                    "Capture aktiv, aber noch kein internes Audiosignal – SoundCloud kann Drittanbieter-Capture sperren"
                else -> "Audioanalyse bereit · wartet auf Musik"
            }

            AnalyzerMode.MICROPHONE_FALLBACK -> when {
                signal > SIGNAL_THRESHOLD ->
                    "SoundCloud Mikrofon-Fallback LIVE · Beat-FX ${percent(features.beat)} · nichts wird gespeichert"
                else ->
                    "SoundCloud schützt internen Ton · Mikrofon-Fallback wartet auf hörbaren Handylautsprecher"
            }
        }

        CapsuleRuntime.updateLevels(
            levels = levels,
            signal = signal,
            bass = features.bass,
            mid = features.mid,
            treble = features.treble,
            beat = features.beat,
            spectralFlux = features.spectralFlux,
            beatSequence = features.beatSequence,
            message = message,
            source = if (mode == AnalyzerMode.PLAYBACK_CAPTURE) {
                "playback-capture"
            } else {
                "soundcloud-microphone-fallback"
            },
        )
        return signal
    }

    private fun detectFeatures(
        rawLevels: FloatArray,
        mode: AnalyzerMode,
        now: Long,
    ): AudioFeatures {
        val bass = weightedAverage(rawLevels, 0, 4, lowBoost = 1.18f)
        val mid = weightedAverage(rawLevels, 4, 10, lowBoost = 1f)
        val treble = weightedAverage(rawLevels, 10, rawLevels.lastIndex, lowBoost = 1.06f)

        var flux = 0f
        for (index in rawLevels.indices) {
            val delta = rawLevels[index] - previousRawLevels[index]
            if (delta > 0f) flux += delta
            previousRawLevels[index] = rawLevels[index]
        }
        flux = (flux / rawLevels.size).coerceIn(0f, 1f)

        val oldFluxAverage = spectralFluxAverage
        val oldBassAverage = bassAverage
        val fluxRise = (flux - oldFluxAverage * 1.08f).coerceAtLeast(0f)
        val bassRise = (bass - oldBassAverage * 1.045f).coerceAtLeast(0f)

        spectralFluxAverage += (flux - spectralFluxAverage) * .075f
        bassAverage += (bass - bassAverage) * .055f

        val modeSensitivity = if (mode == AnalyzerMode.MICROPHONE_FALLBACK) .82f else 1f
        val candidate = (
            fluxRise * 6.4f +
                bassRise * 4.4f +
                max(0f, bass - .42f) * .42f +
                max(0f, treble - .58f) * .12f
            ) * modeSensitivity
        val threshold = if (mode == AnalyzerMode.MICROPHONE_FALLBACK) .17f else .105f
        val minimumGap = if (mode == AnalyzerMode.MICROPHONE_FALLBACK) 125L else 95L

        var beat = 0f
        if (candidate > threshold && now - lastBeatAt >= minimumGap) {
            beat = ((candidate - threshold) / max(.01f, 1f - threshold))
                .coerceIn(.08f, 1f)
            lastBeatAt = now
            beatSequence += 1L
        }

        return AudioFeatures(
            bass = bass.coerceIn(0f, 1f),
            mid = mid.coerceIn(0f, 1f),
            treble = treble.coerceIn(0f, 1f),
            beat = beat,
            spectralFlux = (flux * 2.8f).coerceIn(0f, 1f),
            beatSequence = beatSequence,
        )
    }

    private fun weightedAverage(
        values: FloatArray,
        start: Int,
        end: Int,
        lowBoost: Float,
    ): Float {
        val safeStart = start.coerceIn(0, values.lastIndex)
        val safeEnd = end.coerceIn(safeStart, values.lastIndex)
        var total = 0f
        var weightTotal = 0f
        for (index in safeStart..safeEnd) {
            val position = if (safeEnd == safeStart) 0f else
                (index - safeStart) / (safeEnd - safeStart).toFloat()
            val weight = 1f + (1f - position) * (lowBoost - 1f)
            total += values[index] * weight
            weightTotal += weight
        }
        return if (weightTotal <= 0f) 0f else (total / weightTotal).coerceIn(0f, 1f)
    }

    private fun resetFeatureDetector(resetSequence: Boolean) {
        smoothedLevels.fill(0f)
        previousRawLevels.fill(0f)
        spectralFluxAverage = .018f
        bassAverage = .03f
        lastBeatAt = 0L
        if (resetSequence) beatSequence = 0L
    }

    private fun percent(value: Float): Int = (value.coerceIn(0f, 1f) * 100f).toInt()

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
        if (wakeLock?.isHeld == true) return
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

    private fun stopRecorderOnly() {
        running = false
        recorderGeneration += 1
        val current = recorder
        recorder = null
        runCatching { current?.stop() }
        runCatching { current?.release() }
        captureThread = null
    }

    private fun releaseProjectionOnly() {
        val currentProjection = projection
        val callback = projectionCallback
        projection = null
        projectionCallback = null
        if (currentProjection != null && callback != null) {
            runCatching { currentProjection.unregisterCallback(callback) }
        }
        runCatching { currentProjection?.stop() }
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
            .setContentTitle("Music Capsule · Brutal Reactive")
            .setContentText("Bass, Mitten, Höhen, Beat-Events und Sternenregen werden lokal analysiert")
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
                description = "Lokale Multi-Band- und Beat-Analyse für Music Capsule"
                setShowBadge(false)
            },
        )
    }

    override fun onDestroy() {
        shuttingDown = true
        switchingMode = true
        stopRecorderOnly()
        releaseProjectionOnly()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        resetFeatureDetector(resetSequence = true)
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
        private const val PUBLISH_INTERVAL_MS = 30L
        private const val SIGNAL_THRESHOLD = .008f
        private const val SOUNDCLOUD_FALLBACK_DELAY_MS = 3_200L
        private const val RETURN_TO_PLAYBACK_DELAY_MS = 2_000L

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
