package com.skallahaze.irbloasster.capsule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.skallahaze.irbloasster.MusicCapsuleActivity
import com.skallahaze.irbloasster.webos.LiveAudioRuntime
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class MusicCapsuleOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: MusicCapsuleOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var visualizer: Visualizer? = null
    private var visualizerAvailable = false
    private val smoothedLevels = FloatArray(MusicCapsuleRuntime.BAND_COUNT)
    private var expanded = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!visualizerAvailable) updatePlaybackCaptureFallback()
            val snapshot = MusicCapsuleRuntime.snapshot()
            overlayView?.setSnapshot(snapshot)
            mainHandler.postDelayed(this, if (snapshot.signal > 0.01f) 33L else 120L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            MusicCapsuleRuntime.updateService(
                running = false,
                message = "Berechtigung ‚Über anderen Apps anzeigen‘ fehlt",
            )
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView == null) attachOverlay()
        if (visualizer == null) startGlobalVisualizer()

        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
        MusicCapsuleRuntime.updateService(
            running = true,
            expanded = expanded,
            analyserActive = visualizerAvailable,
            message = if (visualizerAvailable) {
                "Music Capsule läuft · globaler Equalizer aktiv"
            } else {
                "Kapsel läuft · globaler Equalizer blockiert; Capture-Fallback möglich"
            },
        )
        return START_STICKY
    }

    private fun attachOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = MusicCapsuleOverlayView(this)
        overlayView = view
        layoutParams = createLayoutParams(expanded = false)

        view.onExpandedChanged = { value ->
            setExpanded(value)
        }
        view.onMove = { dx, dy ->
            moveOverlay(dx, dy)
        }

        windowManager.addView(view, layoutParams)
    }

    private fun createLayoutParams(expanded: Boolean): WindowManager.LayoutParams {
        val width = if (expanded) expandedWidthPx() else compactWidthPx()
        val height = if (expanded) expandedHeightPx() else compactHeightPx()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(if (expanded) 52f else 34f)
        }
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        val params = layoutParams ?: return
        params.width = if (value) expandedWidthPx() else compactWidthPx()
        params.height = if (value) expandedHeightPx() else compactHeightPx()
        params.x = 0
        params.y = dp(if (value) 52f else 34f)
        overlayView?.setExpanded(value)
        runCatching { windowManager.updateViewLayout(overlayView, params) }
        MusicCapsuleRuntime.updateExpanded(value)
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val maxX = max(0, (screenWidth - params.width) / 2)
        val maxY = max(0, screenHeight - params.height)
        params.x = (params.x + dx.toInt()).coerceIn(-maxX, maxX)
        params.y = (params.y + dy.toInt()).coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(overlayView, params) }
    }

    private fun startGlobalVisualizer() {
        releaseVisualizer()
        visualizerAvailable = runCatching {
            val captureRange = Visualizer.getCaptureSizeRange()
            val captureSize = min(captureRange[1], 1024).coerceAtLeast(captureRange[0])
            val updateRate = (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1_000)

            Visualizer(0).also { effect ->
                effect.enabled = false
                effect.captureSize = captureSize
                effect.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (fft == null || fft.size < 8) return
                            publishFft(fft)
                        }
                    },
                    updateRate,
                    false,
                    true,
                )
                effect.enabled = true
                visualizer = effect
            }
            true
        }.getOrElse { error ->
            MusicCapsuleRuntime.markAnalyserUnavailable(
                "Globaler Equalizer von HyperOS blockiert: ${error.message ?: error.javaClass.simpleName}",
            )
            false
        }
    }

    private fun publishFft(fft: ByteArray) {
        val bandCount = MusicCapsuleRuntime.BAND_COUNT
        val maxBin = max(2, fft.size / 2 - 1)
        val minBin = 1
        val ratio = maxBin.toDouble() / minBin.toDouble()
        val levels = FloatArray(bandCount)
        var overall = 0f

        for (band in 0 until bandCount) {
            val start = max(
                minBin,
                (minBin * ratio.pow(band.toDouble() / bandCount.toDouble())).toInt(),
            )
            val end = min(
                maxBin,
                max(
                    start,
                    (minBin * ratio.pow((band + 1).toDouble() / bandCount.toDouble())).toInt(),
                ),
            )

            var peak = 0.0
            var sum = 0.0
            var samples = 0
            for (bin in start..end) {
                val realIndex = bin * 2
                val imaginaryIndex = realIndex + 1
                if (imaginaryIndex >= fft.size) break
                val real = fft[realIndex].toInt().toDouble()
                val imaginary = fft[imaginaryIndex].toInt().toDouble()
                val magnitude = sqrt(real * real + imaginary * imaginary)
                peak = max(peak, magnitude)
                sum += magnitude
                samples += 1
            }

            val average = if (samples > 0) sum / samples else peak
            val mixed = peak * 0.72 + average * 0.28
            val normalized = (ln(1.0 + mixed) / ln(1.0 + 181.0)).toFloat().coerceIn(0f, 1f)
            val target = normalized.pow(0.72f)
            val previous = smoothedLevels[band]
            val factor = if (target > previous) 0.72f else 0.24f
            val smooth = previous + (target - previous) * factor
            smoothedLevels[band] = smooth
            levels[band] = smooth
            overall = max(overall, smooth)
        }

        MusicCapsuleRuntime.updateLevels(
            levels = levels,
            signal = overall,
            message = if (overall > 0.02f) {
                "Music Capsule LIVE · FFT reagiert auf den Geräteausgang"
            } else {
                "Globaler Equalizer bereit · wartet auf Musik"
            },
        )
    }

    private fun updatePlaybackCaptureFallback() {
        if (!LiveAudioRuntime.running) return
        val signal = (LiveAudioRuntime.signalPercent / 100f).coerceIn(0f, 1f)
        val time = System.currentTimeMillis() / 210.0
        val levels = FloatArray(MusicCapsuleRuntime.BAND_COUNT) { index ->
            val shape = (0.50 + 0.50 * kotlin.math.sin(time + index * 0.72)).toFloat()
            (signal * (0.36f + shape * 0.64f) * (1f - index * 0.018f)).coerceIn(0f, 1f)
        }
        MusicCapsuleRuntime.updateLevels(
            levels = levels,
            signal = signal,
            message = "Playback-Capture-Fallback · ${LiveAudioRuntime.message}",
        )
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        visualizerAvailable = false
        smoothedLevels.fill(0f)
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MusicCapsuleActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicCapsuleOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentTitle("SmartIR Music Capsule")
            .setContentText("Neon-Equalizer läuft über anderen Apps")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SmartIR Music Capsule",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Dauerhafte Anzeige für den schwebenden Musik-Equalizer"
                setShowBadge(false)
            },
        )
    }

    private fun compactWidthPx(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return min(dp(340f), screenWidth - dp(20f))
    }

    private fun compactHeightPx(): Int = dp(68f)

    private fun expandedWidthPx(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return min(dp(430f), screenWidth - dp(18f))
    }

    private fun expandedHeightPx(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        return min(dp(560f), screenHeight - dp(120f))
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        releaseVisualizer()
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        layoutParams = null
        MusicCapsuleRuntime.updateService(
            running = false,
            expanded = false,
            analyserActive = false,
            message = "Music Capsule aus",
        )
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.skallahaze.irbloasster.action.START_MUSIC_CAPSULE"
        const val ACTION_STOP = "com.skallahaze.irbloasster.action.STOP_MUSIC_CAPSULE"

        private const val CHANNEL_ID = "smartir_music_capsule"
        private const val NOTIFICATION_ID = 5102

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MusicCapsuleOverlayService::class.java).apply { action = ACTION_START },
            )
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, MusicCapsuleOverlayService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
