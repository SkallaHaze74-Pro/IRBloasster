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
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class CapsuleOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var capsuleView: CapsuleOverlayView? = null
    private var edgeView: EdgePanelOverlayView? = null
    private var capsuleParams: WindowManager.LayoutParams? = null
    private var edgeParams: WindowManager.LayoutParams? = null
    private var mode: CapsuleMode = CapsuleMode.COMPACT

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val snapshot = CapsuleRuntime.snapshot()
            capsuleView?.setSnapshot(snapshot)
            mainHandler.postDelayed(this, if (snapshot.signal > .01f) 32L else 120L)
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

        syncPreferencesIntoRuntime()
        if (capsuleView == null || edgeView == null) attachOverlays()
        if (intent?.action == ACTION_REFRESH) applyPreferencesToWindows()

        requestListenerRebind()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)

        val snapshot = CapsuleRuntime.snapshot()
        CapsuleRuntime.updateOverlay(
            running = true,
            mode = mode,
            edgePanelsEnabled = snapshot.edgePanelsEnabled,
            edgeIntensity = snapshot.edgeIntensity,
            sourceLock = snapshot.sourceLock,
            message = if (snapshot.analyzerRunning) {
                "Music Capsule LIVE · 144-Hz-Paneele aktiv"
            } else {
                "Music Capsule sichtbar · Audioanalyse noch starten"
            },
        )
        return START_STICKY
    }

    private fun syncPreferencesIntoRuntime() {
        mode = CapsulePreferences.overlayMode(this)
        CapsuleRuntime.updateOverlay(
            running = capsuleView != null,
            mode = mode,
            edgePanelsEnabled = CapsulePreferences.edgePanelsEnabled(this),
            edgeIntensity = CapsulePreferences.edgeIntensity(this),
            sourceLock = CapsulePreferences.sourceLock(this),
        )
    }

    private fun attachOverlays() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val edges = EdgePanelOverlayView(this)
        edgeView = edges
        edgeParams = createEdgeLayoutParams()
        windowManager.addView(edges, edgeParams)

        val capsule = CapsuleOverlayView(this)
        capsuleView = capsule
        capsuleParams = createCapsuleLayoutParams(mode)
        capsule.onModeChanged = { setMode(it, persist = true) }
        capsule.onMove = { dx, dy -> moveCapsule(dx, dy) }
        windowManager.addView(capsule, capsuleParams)
    }

    private fun createEdgeLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun createCapsuleLayoutParams(mode: CapsuleMode): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            widthFor(mode),
            heightFor(mode),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topFor(mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun applyPreferencesToWindows() {
        val preferredMode = CapsulePreferences.overlayMode(this)
        val edgesEnabled = CapsulePreferences.edgePanelsEnabled(this)
        val intensity = CapsulePreferences.edgeIntensity(this)
        val sourceLock = CapsulePreferences.sourceLock(this)

        CapsuleRuntime.updateOverlay(
            running = true,
            mode = preferredMode,
            edgePanelsEnabled = edgesEnabled,
            edgeIntensity = intensity,
            sourceLock = sourceLock,
        )
        edgeView?.visibility = if (edgesEnabled) View.VISIBLE else View.INVISIBLE
        setMode(preferredMode, persist = false)
        capsuleView?.setSnapshot(CapsuleRuntime.snapshot())
    }

    private fun setMode(value: CapsuleMode, persist: Boolean) {
        if (persist) CapsulePreferences.setOverlayMode(this, value)
        mode = value
        CapsuleRuntime.updateMode(value)

        val params = capsuleParams ?: return
        params.width = widthFor(value)
        params.height = heightFor(value)
        params.x = 0
        params.y = topFor(value)
        capsuleView?.setMode(value)
        capsuleView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun moveCapsule(dx: Float, dy: Float) {
        val params = capsuleParams ?: return
        if (mode == CapsuleMode.EXPANDED) return

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val maxX = max(0, (screenWidth - params.width) / 2)
        val maxY = max(0, screenHeight - params.height)
        params.x = (params.x + dx.toInt()).coerceIn(-maxX, maxX)
        params.y = (params.y + dy.toInt()).coerceIn(0, maxY)
        capsuleView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, params) }
        }
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
            .setContentTitle("Music Capsule · Edge Panels")
            .setContentText("Neon-Seitenpaneele und Mini-Musikleiste laufen")
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
                description = "Schwebende Musikleiste und reaktive Neon-Seitenpaneele"
                setShowBadge(false)
            },
        )
    }

    private fun widthFor(mode: CapsuleMode): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return when (mode) {
            CapsuleMode.RIM -> min(dp(158f), screenWidth - dp(50f))
            CapsuleMode.COMPACT -> min(dp(300f), screenWidth - dp(28f))
            CapsuleMode.EXPANDED -> min(dp(425f), screenWidth - dp(16f))
        }
    }

    private fun heightFor(mode: CapsuleMode): Int = when (mode) {
        CapsuleMode.RIM -> dp(19f)
        CapsuleMode.COMPACT -> dp(57f)
        CapsuleMode.EXPANDED -> min(dp(550f), resources.displayMetrics.heightPixels - dp(108f))
    }

    private fun topFor(mode: CapsuleMode): Int = when (mode) {
        CapsuleMode.RIM -> dp(31f)
        CapsuleMode.COMPACT -> dp(27f)
        CapsuleMode.EXPANDED -> dp(47f)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        capsuleView?.let { runCatching { windowManager.removeView(it) } }
        edgeView?.let { runCatching { windowManager.removeView(it) } }
        capsuleView = null
        edgeView = null
        capsuleParams = null
        edgeParams = null
        CapsuleRuntime.updateOverlay(false, mode = CapsuleMode.COMPACT, message = "Music Capsule aus")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.skallahaze.musiccapsule.action.START_OVERLAY"
        const val ACTION_STOP = "com.skallahaze.musiccapsule.action.STOP_OVERLAY"
        const val ACTION_REFRESH = "com.skallahaze.musiccapsule.action.REFRESH_OVERLAY"

        private const val CHANNEL_ID = "music_capsule_overlay"
        private const val NOTIFICATION_ID = 6101

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_START },
            )
        }

        fun refresh(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_REFRESH },
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
