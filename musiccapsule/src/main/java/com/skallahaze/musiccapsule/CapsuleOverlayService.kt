package com.skallahaze.musiccapsule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class CapsuleOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var capsuleView: CapsuleOverlayView? = null
    private var capsuleParams: WindowManager.LayoutParams? = null
    private var edgeView: EdgePanelView? = null
    private var edgeParams: WindowManager.LayoutParams? = null
    private var mediaSessionPoller: CapsuleMediaSessionPoller? = null
    private var expanded = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val snapshot = CapsuleRuntime.snapshot()
            val intensity = CapsulePreferences.neonIntensity(this@CapsuleOverlayService)
            val edgeEnabled = CapsulePreferences.edgePanelsEnabled(this@CapsuleOverlayService)
            capsuleView?.setSnapshot(snapshot, intensity)
            ensureEdgePanel(edgeEnabled)
            edgeView?.setSnapshot(snapshot, intensity, edgeEnabled)
            mainHandler.postDelayed(this, if (snapshot.signal > .01f) 28L else 120L)
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

        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }
        if (capsuleView == null) attachCapsule()
        ensureEdgePanel(CapsulePreferences.edgePanelsEnabled(this))
        updateEdgeLayout()
        if (intent?.action == ACTION_APPLY_SETTINGS) applyCurrentSettings()

        requestListenerRebind()
        if (mediaSessionPoller == null) {
            mediaSessionPoller = CapsuleMediaSessionPoller(
                context = this,
                requestListenerRebind = ::requestListenerRebind,
            )
        }
        mediaSessionPoller?.start()
        mediaSessionPoller?.kick()

        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
        CapsuleRuntime.updateOverlay(
            running = true,
            expanded = expanded,
            message = if (CapsuleRuntime.snapshot().analyzerRunning) {
                "Music Capsule LIVE · 1280×2772 Vollbildrahmen"
            } else {
                "Music Capsule sichtbar · Audioanalyse noch starten"
            },
        )
        return START_STICKY
    }

    private fun attachCapsule() {
        val mode = CapsulePreferences.displayMode(this)
        val view = CapsuleOverlayView(this).apply {
            setDisplayMode(mode)
            onExpandedChanged = { setExpanded(it) }
            onDisplayModeRequested = { requested ->
                CapsulePreferences.setDisplayMode(this@CapsuleOverlayService, requested)
                setDisplayMode(requested)
                applyCurrentSettings()
            }
            onMove = { dx, dy -> moveCapsule(dx, dy) }
        }
        capsuleView = view
        capsuleParams = createCapsuleParams(mode, expanded = false)
        windowManager.addView(view, capsuleParams)
    }

    private fun ensureEdgePanel(enabled: Boolean) {
        if (enabled && edgeView == null) {
            val view = EdgePanelView(this)
            val params = createEdgeParams()
            edgeView = view
            edgeParams = params
            // Add behind the touchable capsule. It never intercepts screen touches.
            windowManager.addView(view, params)
            capsuleView?.let { capsule ->
                capsuleParams?.let { paramsForCapsule ->
                    runCatching {
                        windowManager.removeView(capsule)
                        windowManager.addView(capsule, paramsForCapsule)
                    }
                }
            }
        } else if (!enabled && edgeView != null) {
            edgeView?.let { runCatching { windowManager.removeView(it) } }
            edgeView = null
            edgeParams = null
        }
    }

    private fun createCapsuleParams(
        mode: CapsuleDisplayMode,
        expanded: Boolean,
    ): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            capsuleWidthPx(mode, expanded),
            capsuleHeightPx(mode, expanded),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = capsuleTopPx(mode, expanded)
        }
        configureInsets(params, fullScreen = false)
        requestHighestRefreshRate(params)
        return params
    }

    private fun createEdgeParams(): WindowManager.LayoutParams {
        val bounds = screenBounds()
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
            // Android 12+ passes touches through an untrusted non-touchable
            // overlay only below the obscuring-opacity threshold.
            alpha = 0.79f
        }
        configureInsets(params, fullScreen = true)
        requestHighestRefreshRate(params)
        return params
    }

    private fun configureInsets(
        params: WindowManager.LayoutParams,
        fullScreen: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
            params.setFitInsetsIgnoringVisibility(true)
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (fullScreen) {
            params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
    }

    private fun updateEdgeLayout() {
        val params = edgeParams ?: return
        val bounds = screenBounds()
        params.width = bounds.width()
        params.height = bounds.height()
        params.x = bounds.left
        params.y = bounds.top
        configureInsets(params, fullScreen = true)
        requestHighestRefreshRate(params)
        edgeView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun screenBounds(): Rect {
        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Rect(windowManager.maximumWindowMetrics.bounds)
        }

        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun requestHighestRefreshRate(params: WindowManager.LayoutParams) {
        runCatching {
            val displayManager = getSystemService(DisplayManager::class.java)
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return@runCatching
            val best = display.supportedModes.maxWithOrNull(
                compareBy<Display.Mode> { it.refreshRate }
                    .thenBy { it.physicalWidth * it.physicalHeight },
            ) ?: return@runCatching
            params.preferredDisplayModeId = best.modeId
            @Suppress("DEPRECATION")
            params.preferredRefreshRate = best.refreshRate
        }
    }

    private fun applyCurrentSettings() {
        val mode = CapsulePreferences.displayMode(this)
        capsuleView?.setDisplayMode(mode)
        ensureEdgePanel(CapsulePreferences.edgePanelsEnabled(this))
        updateEdgeLayout()
        updateCapsuleLayout(mode, expanded)
        requestListenerRebind()
        mediaSessionPoller?.kick()
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        val mode = CapsulePreferences.displayMode(this)
        capsuleView?.setExpanded(value)
        updateCapsuleLayout(mode, value)
        CapsuleRuntime.updateExpanded(value)
    }

    private fun updateCapsuleLayout(mode: CapsuleDisplayMode, expanded: Boolean) {
        val params = capsuleParams ?: return
        params.width = capsuleWidthPx(mode, expanded)
        params.height = capsuleHeightPx(mode, expanded)
        params.x = 0
        params.y = capsuleTopPx(mode, expanded)
        configureInsets(params, fullScreen = false)
        requestHighestRefreshRate(params)
        capsuleView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun moveCapsule(dx: Float, dy: Float) {
        if (expanded) return
        val params = capsuleParams ?: return
        val bounds = screenBounds()
        val maxX = max(0, (bounds.width() - params.width) / 2)
        val maxY = max(0, bounds.height() - params.height)
        // Allow the visual strip to sit over the clock/status bar while its
        // lower chevron remains in a tappable area below the protected system UI.
        val minY = -statusBarHeightPx() / 2
        params.x = (params.x + dx.toInt()).coerceIn(-maxX, maxX)
        params.y = (params.y + dy.toInt()).coerceIn(minY, maxY)
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
            .setContentTitle("Music Capsule · Full Neon")
            .setContentText("Vollbildrahmen, Mini-Widget und SoundCloud-Watchdog laufen")
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
                description = "Dauerhafte Anzeige für Music Capsule und Neon-Seitenpaneele"
                setShowBadge(false)
            },
        )
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun capsuleWidthPx(mode: CapsuleDisplayMode, expanded: Boolean): Int {
        val screenWidth = screenBounds().width()
        if (expanded) return min(dp(430f), screenWidth - dp(16f))
        return when (mode) {
            CapsuleDisplayMode.MINI -> min(dp(270f), screenWidth - dp(24f))
            CapsuleDisplayMode.RIM -> min(dp(205f), screenWidth - dp(34f))
        }
    }

    private fun capsuleHeightPx(mode: CapsuleDisplayMode, expanded: Boolean): Int {
        if (expanded) return min(dp(560f), screenBounds().height() - dp(110f))
        // 12 dp transparent/tappable chevron area is included underneath the
        // visible bar. This fixes taps after the bar is moved over the clock.
        return when (mode) {
            CapsuleDisplayMode.MINI -> dp(60f)
            CapsuleDisplayMode.RIM -> dp(38f)
        }
    }

    private fun capsuleTopPx(mode: CapsuleDisplayMode, expanded: Boolean): Int {
        if (expanded) return statusBarHeightPx() + dp(8f)
        return when (mode) {
            CapsuleDisplayMode.MINI -> statusBarHeightPx() + dp(2f)
            CapsuleDisplayMode.RIM -> max(0, statusBarHeightPx() - dp(9f))
        }
    }

    private fun statusBarHeightPx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(28f)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        mediaSessionPoller?.stop()
        mediaSessionPoller = null
        capsuleView?.let { runCatching { windowManager.removeView(it) } }
        edgeView?.let { runCatching { windowManager.removeView(it) } }
        capsuleView = null
        capsuleParams = null
        edgeView = null
        edgeParams = null
        expanded = false
        CapsuleRuntime.updateOverlay(false, expanded = false, message = "Music Capsule aus")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.skallahaze.musiccapsule.action.START_OVERLAY"
        const val ACTION_STOP = "com.skallahaze.musiccapsule.action.STOP_OVERLAY"
        const val ACTION_APPLY_SETTINGS = "com.skallahaze.musiccapsule.action.APPLY_SETTINGS"

        private const val CHANNEL_ID = "music_capsule_overlay"
        private const val NOTIFICATION_ID = 6101

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_START },
            )
        }

        fun applySettings(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_APPLY_SETTINGS },
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CapsuleOverlayService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
