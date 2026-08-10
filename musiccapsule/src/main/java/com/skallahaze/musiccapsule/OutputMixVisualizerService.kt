package com.skallahaze.musiccapsule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Root-free experiment for Android's global audio output mix.
 *
 * Android documents Visualizer(audioSession = 0) as an output-mix visualizer.
 * It only exposes low-quality waveform/FFT data; Music Capsule never writes the
 * captured data to disk and uses it solely for local visual synchronization.
 */
class OutputMixVisualizerService : Service() {
    private data class Features(
        val bass: Float,
        val mid: Float,
        val treble: Float,
        val beat: Float,
        val flux: Float,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var visualizer: Visualizer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val smoothedLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private val previousRawLevels = FloatArray(CapsuleRuntime.BAND_COUNT)
    private var spectralFluxAverage = .018f
    private var bassAverage = .035f
    private var beatSequence = 0L
    private var lastBeatAt = 0L
    private var lastWaveSignal = 0f
    private var lastCallbackAt = 0L
    private var lastPublishAt = 0L
    private var startedAt = 0L
    private var successfulFrames = 0

    private val heartbeat = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (lastCallbackAt == 0L && now - startedAt > CALLBACK_TIMEOUT_MS) {
                CapsuleRuntime.updateAnalyzer(
                    running = true,
                    message = "SYSTEM MIX gestartet, aber Android liefert noch keine Visualizer-Daten",
                    source = "output-mix-visualizer-no-callback",
                )
            } else if (
                lastCallbackAt > 0L &&
                now - lastCallbackAt > CALLBACK_TIMEOUT_MS
            ) {
                CapsuleRuntime.updateAnalyzer(
                    running = true,
                    message = "SYSTEM MIX Visualizer pausiert · wartet auf Audio",
                    source = "output-mix-visualizer-idle",
                )
            }
            mainHandler.postDelayed(this, 700L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CapsuleRuntime.markAnalyzerStopped("SYSTEM MIX Test braucht RECORD_AUDIO")
            stopSelf()
            return START_NOT_STICKY
        }

        startOutputMixVisualizer()
        return START_STICKY
    }

    private fun startOutputMixVisualizer() {
        releaseVisualizer()
        resetDetector()
        SyncLearningRuntime.clear()
        acquireWakeLock()
        startedAt = System.currentTimeMillis()
        lastCallbackAt = 0L
        successfulFrames = 0

        runCatching {
            val engine = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()
            val requestedSize = range.getOrNull(1) ?: range.firstOrNull() ?: 1024
            check(engine.setCaptureSize(requestedSize) == Visualizer.SUCCESS) {
                "CaptureSize $requestedSize nicht akzeptiert"
            }
            engine.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            runCatching { engine.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS }

            val captureRate = Visualizer.getMaxCaptureRate().coerceAtLeast(1_000)
            val listenerResult = engine.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        onWaveform(waveform)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (fft == null || fft.size < 8) return
                        onFft(fft, samplingRate)
                    }
                },
                captureRate,
                true,
                true,
            )
            check(listenerResult == Visualizer.SUCCESS) {
                "DataCaptureListener Fehler $listenerResult"
            }
            check(engine.setEnabled(true) == Visualizer.SUCCESS) {
                "Visualizer konnte nicht aktiviert werden"
            }
            visualizer = engine

            CapsuleRuntime.updateAnalyzer(
                running = true,
                message = "SYSTEM MIX Test läuft · SoundCloud/YouTube starten · keine Bildschirmaufnahme nötig",
                source = "output-mix-visualizer-probing",
            )
            mainHandler.removeCallbacks(heartbeat)
            mainHandler.post(heartbeat)
        }.onFailure { error ->
            CapsuleRuntime.markAnalyzerStopped(
                "SYSTEM MIX Visualizer nicht verfügbar: ${error.message ?: error.javaClass.simpleName}",
            )
            stopSelf()
        }
    }

    @Synchronized
    private fun onWaveform(waveform: ByteArray) {
        var squareSum = 0.0
        var peak = 0f
        waveform.forEach { byte ->
            val sample = ((byte.toInt() and 0xFF) - 128) / 128f
            squareSum += sample * sample
            peak = max(peak, abs(sample))
        }
        val rms = sqrt(squareSum / waveform.size).toFloat()
        lastWaveSignal = max(rms * 2.35f, peak * .88f).coerceIn(0f, 1f)
        lastCallbackAt = System.currentTimeMillis()
    }

    @Synchronized
    private fun onFft(fft: ByteArray, samplingRateMilliHz: Int) {
        val now = System.currentTimeMillis()
        lastCallbackAt = now

        // Visualizer.getSamplingRate / callback samplingRate is documented in mHz.
        val sampleRateHz = (samplingRateMilliHz / 1000f).coerceAtLeast(8_000f)
        val captureSize = fft.size
        val rawLevels = FloatArray(CapsuleRuntime.BAND_COUNT)

        // Android FFT layout: [R0, Rnyquist, R1, I1, R2, I2, ...].
        for (bin in 1 until captureSize / 2) {
            val index = bin * 2
            if (index + 1 >= fft.size) break
            val real = fft[index].toInt().toFloat()
            val imaginary = fft[index + 1].toInt().toFloat()
            val magnitude = hypot(real, imaginary)
            val frequency = bin * sampleRateHz / captureSize
            val band = nearestBand(frequency)
            val normalized = (
                ln(1.0 + magnitude.toDouble()) / ln(1.0 + MAX_FFT_MAGNITUDE)
                ).toFloat().coerceIn(0f, 1f)
            rawLevels[band] = max(rawLevels[band], normalized)
        }

        var strongest = 0f
        for (index in rawLevels.indices) {
            val normalized = (rawLevels[index] * (1.08f + index * .010f)).coerceIn(0f, 1f)
            rawLevels[index] = normalized
            strongest = max(strongest, normalized)
            val previous = smoothedLevels[index]
            val factor = if (normalized > previous) .76f else .24f
            smoothedLevels[index] = previous + (normalized - previous) * factor
        }

        val signal = max(lastWaveSignal, strongest * .82f).coerceIn(0f, 1f)
        if (signal > SIGNAL_THRESHOLD) successfulFrames += 1 else successfulFrames = 0
        val features = detectFeatures(rawLevels, now)

        if (now - lastPublishAt < PUBLISH_INTERVAL_MS && features.beat <= 0f) return
        lastPublishAt = now

        val live = successfulFrames >= SUCCESS_FRAMES
        val source = if (live) {
            "output-mix-visualizer-live"
        } else if (now - startedAt >= PROBE_DECISION_MS) {
            "output-mix-visualizer-zero"
        } else {
            "output-mix-visualizer-probing"
        }
        val message = when {
            live ->
                "SYSTEM MIX LIVE ✅ · Bass ${percent(features.bass)} · Beat ${percent(features.beat)} · rootfrei"
            now - startedAt >= PROBE_DECISION_MS ->
                "SYSTEM MIX läuft, aber liefert nur Nullen/zu wenig Signal ⚠️ · Quelle kann geschützt sein"
            else ->
                "SYSTEM MIX prüft Output-Mix … Signal ${percent(signal)}%"
        }

        CapsuleRuntime.updateLevels(
            levels = smoothedLevels.copyOf(),
            signal = signal,
            bass = features.bass,
            mid = features.mid,
            treble = features.treble,
            beat = features.beat,
            spectralFlux = features.flux,
            beatSequence = beatSequence,
            message = message,
            source = source,
        )
        SyncLearningRuntime.observe(this, CapsuleRuntime.snapshot())
    }

    private fun detectFeatures(rawLevels: FloatArray, now: Long): Features {
        val bass = weightedAverage(rawLevels, 0, 4, 1.18f)
        val mid = weightedAverage(rawLevels, 4, 10, 1f)
        val treble = weightedAverage(rawLevels, 10, rawLevels.lastIndex, 1.05f)

        var flux = 0f
        rawLevels.indices.forEach { index ->
            val delta = rawLevels[index] - previousRawLevels[index]
            if (delta > 0f) flux += delta
            previousRawLevels[index] = rawLevels[index]
        }
        flux = (flux / rawLevels.size).coerceIn(0f, 1f)

        val oldFluxAverage = spectralFluxAverage
        val oldBassAverage = bassAverage
        val fluxRise = (flux - oldFluxAverage * 1.06f).coerceAtLeast(0f)
        val bassRise = (bass - oldBassAverage * 1.035f).coerceAtLeast(0f)
        spectralFluxAverage += (flux - spectralFluxAverage) * .085f
        bassAverage += (bass - bassAverage) * .065f

        val candidate =
            fluxRise * 6.8f +
                bassRise * 4.7f +
                max(0f, bass - .42f) * .30f
        var beat = 0f
        if (candidate > .115f && now - lastBeatAt >= 95L) {
            beat = ((candidate - .115f) / .885f).coerceIn(.08f, 1f)
            lastBeatAt = now
            beatSequence += 1L
        }

        return Features(
            bass = bass,
            mid = mid,
            treble = treble,
            beat = beat,
            flux = (flux * 2.9f).coerceIn(0f, 1f),
        )
    }

    private fun nearestBand(frequency: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (index in BAND_FREQUENCIES.indices) {
            val target = BAND_FREQUENCIES[index].toFloat()
            val distance = abs(ln((frequency.coerceAtLeast(20f) / target).toDouble())).toFloat()
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    private fun weightedAverage(values: FloatArray, start: Int, end: Int, lowBoost: Float): Float {
        val safeStart = start.coerceIn(0, values.lastIndex)
        val safeEnd = end.coerceIn(safeStart, values.lastIndex)
        var total = 0f
        var weights = 0f
        for (index in safeStart..safeEnd) {
            val position = if (safeEnd == safeStart) 0f else
                (index - safeStart) / (safeEnd - safeStart).toFloat()
            val weight = 1f + (1f - position) * (lowBoost - 1f)
            total += values[index] * weight
            weights += weight
        }
        return if (weights == 0f) 0f else (total / weights).coerceIn(0f, 1f)
    }

    private fun resetDetector() {
        smoothedLevels.fill(0f)
        previousRawLevels.fill(0f)
        spectralFluxAverage = .018f
        bassAverage = .035f
        beatSequence = 0L
        lastBeatAt = 0L
        lastWaveSignal = 0f
        lastPublishAt = 0L
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MusicCapsule:OutputMixVisualizer",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseVisualizer() {
        val current = visualizer
        visualizer = null
        runCatching { current?.setEnabled(false) }
        runCatching { current?.release() }
    }

    private fun percent(value: Float): Int = (value.coerceIn(0f, 1f) * 100f).toInt()

    private fun buildNotification(): android.app.Notification {
        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, OutputMixProbeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, OutputMixVisualizerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle("Music Capsule · SYSTEM MIX Test")
            .setContentText("Rootfreier Output-Mix Visualizer · nichts wird gespeichert")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Music Capsule System Mix Test",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Rootfreier Visualizer-Test des Android Audio-Output-Mix"
                setShowBadge(false)
            },
        )
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(heartbeat)
        releaseVisualizer()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        SyncLearningRuntime.clear()
        CapsuleRuntime.markAnalyzerStopped("SYSTEM MIX Test gestoppt")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.skallahaze.musiccapsule.action.START_OUTPUT_MIX_TEST"
        const val ACTION_STOP = "com.skallahaze.musiccapsule.action.STOP_OUTPUT_MIX_TEST"

        private const val CHANNEL_ID = "music_capsule_output_mix_probe"
        private const val NOTIFICATION_ID = 6103
        private const val PUBLISH_INTERVAL_MS = 35L
        private const val SIGNAL_THRESHOLD = .010f
        private const val SUCCESS_FRAMES = 3
        private const val PROBE_DECISION_MS = 2_500L
        private const val CALLBACK_TIMEOUT_MS = 2_700L
        private const val MAX_FFT_MAGNITUDE = 181.0

        private val BAND_FREQUENCIES = doubleArrayOf(
            60.0, 90.0, 140.0, 220.0,
            340.0, 520.0, 800.0, 1_200.0,
            1_800.0, 2_700.0, 4_000.0, 5_800.0,
            8_000.0, 10_500.0, 13_500.0, 17_000.0,
        )

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OutputMixVisualizerService::class.java).apply { action = ACTION_START },
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OutputMixVisualizerService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
