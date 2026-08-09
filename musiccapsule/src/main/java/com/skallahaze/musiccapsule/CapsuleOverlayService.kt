package com.skallahaze.musiccapsule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class CapsuleOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: CapsuleOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var expanded = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val snapshot = CapsuleRuntime.snapshot()
            overlayView?.setSnapshot(snapshot)
            mainHandler.postDelayed(this, if (snapshot.signal > 0.01f) 40L else 140L)
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
            CapsuleRuntime.updateOverlay(false, message = "Berechtigung ‚Über anderen Apps anzeigen‘ fehlt")
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView == null) attachOverlay()
        requestListenerRebind()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
        CapsuleRuntime.updateOverlay(
            running = true,
            expanded = expanded,
            message = if (CapsuleRuntime.snapshot().analyzerRunning) {
                "Music Capsule LIVE · Audioanalyse aktiv"
            } else {
                "Music Capsule sichtbar · Audioanalyse noch starten"
            },
        )
        return START_STICKY
    }

    private fun attachOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = CapsuleOverlayView(this)
        overlayView = view
        layoutParams = createLayoutParams(expanded = false)

        view.onExpandedChanged = { setExpanded(it) }
        view.onMove = { dx, dy -> moveOverlay(dx, dy) }
        windowManager.addView(view, layoutParams)
    }

    private fun createLayoutParams(expanded: Boolean): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            if (expanded) expandedWidthPx() else compactWidthPx(),
            if (expanded) expandedHeightPx() else compactHeightPx(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(if (expanded) 48f else 30f)
        }
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        val params = layoutParams ?: return
        params.width = if (value) expandedWidthPx() else compactWidthPx()
        params.height = if (value) expandedHeightPx() else compactHeightPx()
        params.x = 0
        params.y = dp(if (value) 48f else 30f)
        overlayView?.setExpanded(value)
        runCatching { windowManager.updateViewLayout(overlayView, params) }
        CapsuleRuntime.updateExpanded(value)
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

    private fun requestListenerRebind() {
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, CapsuleNotificationListener::class.java),
            )
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
            Intent(this, CapsuleOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle("Music Capsule")
            .setContentText("Schwebender SoundCloud-Equalizer läuft")
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
                "Music Capsule Overlay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Dauerhafte Anzeige für die schwebende Musik-Kapsel"
                setShowBadge(false)
            },
        )
    }

    private fun compactWidthPx(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return min(dp(340f), screenWidth - dp(18f))
    }

    private fun compactHeightPx(): Int = dp(68f)

    private fun expandedWidthPx(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return min(dp(430f), screenWidth - dp(16f))
    }

    private fun expandedHeightPx(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        return min(dp(560f), screenHeight - dp(110f))
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        layoutParams = null
        expanded = false
        CapsuleRuntime.updateOverlay(false, expanded = false, message = "Music Capsule aus")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.skallahaze.musiccapsule.action.START_OVERLAY"
        const val ACTION_STOP = "com.skallahaze.musiccapsule.action.STOP_OVERLAY"

        private const val CHANNEL_ID = "music_capsule_overlay"
        private const val NOTIFICATION_ID = 6101

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_START },
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
