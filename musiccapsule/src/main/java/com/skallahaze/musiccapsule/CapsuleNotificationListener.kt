package com.skallahaze.musiccapsule

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlin.math.max

class CapsuleNotificationListener : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var sessionManager: MediaSessionManager
    private lateinit var listenerComponent: ComponentName
    private var activeController: MediaController? = null
    private var listenerRegistered = false
    private var lastPostedPackage = ""
    private var lastPostedAtElapsed = 0L

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishController(activeController, metadata, activeController?.playbackState)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            scheduleRefresh(25L)
        }

        override fun onSessionDestroyed() {
            scheduleRefresh(60L)
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener {
        scheduleRefresh(25L)
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        listenerComponent = ComponentName(this, CapsuleNotificationListener::class.java)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!listenerRegistered) {
            runCatching {
                sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    listenerComponent,
                    mainHandler,
                )
                listenerRegistered = true
            }
        }
        scheduleRefresh(0L)
    }

    override fun onListenerDisconnected() {
        clearController(removeListener = true)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.packageName != packageName) {
            lastPostedPackage = sbn.packageName
            lastPostedAtElapsed = SystemClock.elapsedRealtime()
            scheduleRefresh(35L)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.packageName != packageName) scheduleRefresh(60L)
    }

    private fun scheduleRefresh(delayMs: Long) {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, delayMs)
    }

    private val refreshRunnable = Runnable { refreshMedia() }

    private fun refreshMedia() {
        val sourceLock = CapsulePreferences.sourceLock(this)
        val allNotifications = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.packageName != packageName }

        val notifications = allNotifications.filter { sourceLock.matches(it.packageName) }
        val controllers = runCatching {
            sessionManager.getActiveSessions(listenerComponent)
        }.getOrDefault(emptyList()).toMutableList()

        allNotifications.forEach { sbn ->
            if (!sourceLock.matches(sbn.packageName)) return@forEach
            val token = mediaSessionToken(sbn.notification) ?: return@forEach
            if (controllers.none { it.sessionToken == token }) {
                runCatching { MediaController(this, token) }.getOrNull()?.let(controllers::add)
            }
        }

        val eligibleControllers = controllers.filter { sourceLock.matches(it.packageName) }
        val activelyPlaying = eligibleControllers.filter { isActivelyPlaying(it.playbackState) }
        val controllerPool = if (activelyPlaying.isNotEmpty()) activelyPlaying else eligibleControllers
        val selectedController = controllerPool.maxByOrNull { controllerScore(it, notifications) }
        val selectedNotification = notifications
            .filter(::looksLikeMediaNotification)
            .maxByOrNull(::notificationScore)

        if (selectedController != null) {
            setController(selectedController)
            val matching = notifications
                .filter { it.packageName == selectedController.packageName }
                .maxByOrNull(::notificationScore)
            publishController(
                selectedController,
                selectedController.metadata,
                selectedController.playbackState,
                matching ?: selectedNotification,
            )
            return
        }

        clearController(removeListener = false)
        if (selectedNotification != null) {
            publishNotification(selectedNotification)
        } else if (sourceLock != MediaSourceLock.AUTO) {
            CapsuleRuntime.updateMedia(
                title = "${sourceLock.label} wartet",
                artist = "Quelle ist fest angeheftet",
                packageName = sourceLock.packageNames.firstOrNull().orEmpty(),
                artwork = null,
                isPlaying = false,
            )
        } else {
            CapsuleRuntime.clearMedia()
        }
    }

    private fun isActivelyPlaying(state: PlaybackState?): Boolean {
        return when (state?.state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            -> true

            else -> false
        }
    }

    private fun controllerScore(
        controller: MediaController,
        notifications: List<StatusBarNotification>,
    ): Long {
        val state = controller.playbackState
        var score = when (state?.state) {
            PlaybackState.STATE_PLAYING -> 1_200L
            PlaybackState.STATE_BUFFERING -> 1_080L
            PlaybackState.STATE_CONNECTING -> 980L
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            -> 900L

            PlaybackState.STATE_PAUSED -> 180L
            PlaybackState.STATE_STOPPED -> 35L
            else -> 20L
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val updateAge = state?.lastPositionUpdateTime
            ?.takeIf { it > 0L }
            ?.let { (nowElapsed - it).coerceAtLeast(0L) }
            ?: Long.MAX_VALUE
        score += when {
            updateAge <= 2_500L -> 520L
            updateAge <= 8_000L -> 330L
            updateAge <= 25_000L -> 150L
            updateAge <= 90_000L -> 55L
            else -> 0L
        }

        val metadata = controller.metadata
        if (metadataTitle(metadata).isNotBlank()) score += 80L
        if (metadataArtwork(metadata) != null) score += 28L

        val matchingNotification = notifications
            .filter { it.packageName == controller.packageName && looksLikeMediaNotification(it) }
            .maxByOrNull { it.postTime }
        if (matchingNotification != null) {
            score += 100L
            val age = (System.currentTimeMillis() - matchingNotification.postTime).coerceAtLeast(0L)
            score += when {
                age <= 5_000L -> 340L
                age <= 20_000L -> 190L
                age <= 90_000L -> 80L
                else -> 0L
            }
        }

        if (
            controller.packageName == lastPostedPackage &&
            nowElapsed - lastPostedAtElapsed <= 15_000L
        ) {
            score += 360L
        }

        // A stale Twitch session used to outrank newly started YouTube. In AUTO mode,
        // only give Twitch the lead when it is truly the freshest active source.
        if (
            CapsulePreferences.sourceLock(this) == MediaSourceLock.AUTO &&
            controller.packageName.startsWith("tv.twitch") &&
            updateAge > 8_000L
        ) {
            score -= 260L
        }
        return score
    }

    private fun notificationScore(sbn: StatusBarNotification): Long {
        var score = 0L
        val notification = sbn.notification
        if (mediaSessionToken(notification) != null) score += 380L
        if (notification.category == Notification.CATEGORY_TRANSPORT) score += 260L
        if (sbn.isOngoing) score += 100L
        if (notificationTitle(notification).isNotBlank()) score += 75L
        if (notificationArtwork(notification) != null) score += 25L
        val age = (System.currentTimeMillis() - sbn.postTime).coerceAtLeast(0L)
        score += when {
            age <= 5_000L -> 400L
            age <= 20_000L -> 220L
            age <= 90_000L -> 90L
            else -> 0L
        }
        if (
            sbn.packageName == lastPostedPackage &&
            SystemClock.elapsedRealtime() - lastPostedAtElapsed <= 15_000L
        ) {
            score += 320L
        }
        return score
    }

    private fun looksLikeMediaNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (mediaSessionToken(notification) != null) return true
        if (notification.category == Notification.CATEGORY_TRANSPORT) return true
        val title = notificationTitle(notification)
        val text = notificationArtist(notification)
        return sbn.isOngoing && title.isNotBlank() && text.isNotBlank()
    }

    private fun setController(controller: MediaController) {
        if (controller.sessionToken == activeController?.sessionToken) {
            MediaControllerBridge.setActive(controller)
            return
        }
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = controller
        MediaControllerBridge.setActive(controller)
        runCatching { controller.registerCallback(controllerCallback, mainHandler) }
    }

    private fun publishController(
        controller: MediaController?,
        metadata: MediaMetadata?,
        playbackState: PlaybackState? = controller?.playbackState,
        fallbackNotification: StatusBarNotification? = null,
    ) {
        if (controller == null) return

        val fallback = fallbackNotification?.notification
        val title = metadataTitle(metadata)
            .ifBlank { fallback?.let(::notificationTitle).orEmpty() }
            .ifBlank { "Unbekannter Titel" }
        val artist = metadataArtist(metadata)
            .ifBlank { fallback?.let(::notificationArtist).orEmpty() }
            .ifBlank { packageLabel(controller.packageName) }
        val artwork = metadataArtwork(metadata)
            ?: fallback?.let(::notificationArtwork)

        CapsuleRuntime.updateMedia(
            title = title,
            artist = artist,
            packageName = controller.packageName,
            artwork = artwork,
            isPlaying = isActivelyPlaying(playbackState),
        )
    }

    private fun publishNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val token = mediaSessionToken(notification)
        val controller = token?.let { runCatching { MediaController(this, it) }.getOrNull() }
        if (controller != null) {
            setController(controller)
            publishController(controller, controller.metadata, controller.playbackState, sbn)
            return
        }

        CapsuleRuntime.updateMedia(
            title = notificationTitle(notification).ifBlank { "Unbekannter Titel" },
            artist = notificationArtist(notification).ifBlank { packageLabel(sbn.packageName) },
            packageName = sbn.packageName,
            artwork = notificationArtwork(notification),
            isPlaying = sbn.isOngoing,
        )
    }

    private fun metadataTitle(metadata: MediaMetadata?): String {
        return firstNonBlank(
            metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata?.description?.title?.toString(),
        )
    }

    private fun metadataArtist(metadata: MediaMetadata?): String {
        return firstNonBlank(
            metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            metadata?.description?.subtitle?.toString(),
            metadata?.description?.description?.toString(),
        )
    }

    private fun metadataArtwork(metadata: MediaMetadata?): Bitmap? {
        return metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.description?.iconBitmap
    }

    private fun notificationTitle(notification: Notification): String {
        val extras = notification.extras ?: Bundle.EMPTY
        return firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
        )
    }

    private fun notificationArtist(notification: Notification): String {
        val extras = notification.extras ?: Bundle.EMPTY
        return firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
        )
    }

    private fun notificationArtwork(notification: Notification): Bitmap? {
        @Suppress("DEPRECATION")
        notification.largeIcon?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { notification.getLargeIcon() }
                .getOrNull()
                ?.let(::iconToBitmap)
                ?.let { return it }
        }

        val extras = notification.extras ?: Bundle.EMPTY
        val big = bundleParcelable(extras, Notification.EXTRA_LARGE_ICON_BIG)
        parcelableToBitmap(big)?.let { return it }
        val normal = bundleParcelable(extras, Notification.EXTRA_LARGE_ICON)
        return parcelableToBitmap(normal)
    }

    private fun mediaSessionToken(notification: Notification): MediaSession.Token? {
        val extras = notification.extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
    }

    private fun bundleParcelable(bundle: Bundle, key: String): Parcelable? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable(key)
        }
    }

    private fun parcelableToBitmap(value: Parcelable?): Bitmap? {
        return when (value) {
            is Bitmap -> value
            is Icon -> iconToBitmap(value)
            else -> null
        }
    }

    private fun iconToBitmap(icon: Icon?): Bitmap? {
        if (icon == null) return null
        val drawable = runCatching { icon.loadDrawable(this) }.getOrNull() ?: return null
        return drawableToBitmap(drawable)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = max(1, if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 256)
        val height = max(1, if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 256)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun packageLabel(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    private fun clearController(removeListener: Boolean) {
        if (removeListener && listenerRegistered) {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
            listenerRegistered = false
        }
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
        MediaControllerBridge.setActive(null)
        if (removeListener) CapsuleRuntime.clearMedia()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        clearController(removeListener = true)
        super.onDestroy()
    }
}
