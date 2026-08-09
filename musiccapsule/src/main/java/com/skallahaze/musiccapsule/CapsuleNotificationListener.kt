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

    private val observedControllers = mutableMapOf<MediaSession.Token, ObservedController>()
    private val sessionActivityAt = mutableMapOf<String, Long>()
    private val notificationActivityAt = mutableMapOf<String, Long>()

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
            notificationActivityAt[sbn.packageName] = SystemClock.elapsedRealtime()
            scheduleRefresh(45L)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.packageName != packageName) {
            notificationActivityAt[sbn.packageName] = SystemClock.elapsedRealtime()
            scheduleRefresh(45L)
        }
    }

    private fun scheduleRefresh(delayMs: Long) {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, delayMs)
    }

    private val refreshRunnable = Runnable { refreshMedia() }

    private fun refreshMedia() {
        val sourceLock = CapsulePreferences.sourceLock(this)
        CapsuleRuntime.updateSourceLock(sourceLock)

        val notifications = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.packageName != packageName }

        val controllers = runCatching {
            sessionManager.getActiveSessions(listenerComponent)
        }.getOrDefault(emptyList()).toMutableList()

        notifications.forEach { sbn ->
            val token = mediaSessionToken(sbn.notification) ?: return@forEach
            if (controllers.none { it.sessionToken == token }) {
                runCatching { MediaController(this, token) }.getOrNull()?.let(controllers::add)
            }
        }

        observeControllers(controllers)

        val indexedControllers = controllers.withIndex().toList()
        val lockedControllers = indexedControllers.filter { sourceLock.matches(it.value.packageName) }
        val controllerCandidates = when {
            sourceLock == MediaSourceLock.AUTO -> indexedControllers
            lockedControllers.isNotEmpty() -> lockedControllers
            else -> emptyList()
        }

        val lockedNotifications = notifications.filter { sourceLock.matches(it.packageName) }
        val notificationCandidates = when {
            sourceLock == MediaSourceLock.AUTO -> notifications
            lockedNotifications.isNotEmpty() -> lockedNotifications
            else -> emptyList()
        }

        val selectedController = controllerCandidates.maxByOrNull { indexed ->
            controllerScore(
                controller = indexed.value,
                rank = indexed.index,
                totalControllers = controllers.size,
                notifications = notificationCandidates,
                allControllers = controllers,
                sourceLock = sourceLock,
            )
        }?.value

        val selectedNotification = notificationCandidates
            .filter(::looksLikeMediaNotification)
            .maxByOrNull { notificationScore(it, sourceLock) }

        if (selectedController != null) {
            setController(selectedController)
            val matching = notificationCandidates
                .filter { it.packageName == selectedController.packageName }
                .maxByOrNull { notificationScore(it, sourceLock) }
            publishController(
                selectedController,
                selectedController.metadata,
                selectedController.playbackState,
                matching ?: selectedNotification,
            )
            return
        }

        clearActiveControllerOnly()
        if (selectedNotification != null) {
            publishNotification(selectedNotification)
        } else {
            CapsuleRuntime.clearMedia()
        }
    }

    private fun observeControllers(controllers: List<MediaController>) {
        val activeTokens = controllers.mapTo(mutableSetOf()) { it.sessionToken }
        val staleTokens = observedControllers.keys.filter { it !in activeTokens }
        staleTokens.forEach { token ->
            val observed = observedControllers.remove(token) ?: return@forEach
            runCatching { observed.controller.unregisterCallback(observed.callback) }
        }

        controllers.forEach { controller ->
            if (observedControllers.containsKey(controller.sessionToken)) return@forEach
            val packageName = controller.packageName
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    sessionActivityAt[packageName] = SystemClock.elapsedRealtime()
                    scheduleRefresh(12L)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    sessionActivityAt[packageName] = SystemClock.elapsedRealtime()
                    scheduleRefresh(12L)
                }

                override fun onSessionDestroyed() {
                    sessionActivityAt[packageName] = SystemClock.elapsedRealtime()
                    scheduleRefresh(20L)
                }
            }
            runCatching { controller.registerCallback(callback, mainHandler) }
            observedControllers[controller.sessionToken] = ObservedController(controller, callback)
        }
    }

    private fun controllerScore(
        controller: MediaController,
        rank: Int,
        totalControllers: Int,
        notifications: List<StatusBarNotification>,
        allControllers: List<MediaController>,
        sourceLock: MediaSourceLock,
    ): Int {
        val state = controller.playbackState
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        var score = when (state?.state) {
            PlaybackState.STATE_PLAYING -> 1_000
            PlaybackState.STATE_BUFFERING -> 900
            PlaybackState.STATE_CONNECTING -> 820
            PlaybackState.STATE_PAUSED -> 230
            PlaybackState.STATE_STOPPED -> 30
            PlaybackState.STATE_ERROR -> -120
            else -> 10
        }

        score += max(0, (totalControllers - rank) * 42)

        val updateAge = if ((state?.lastPositionUpdateTime ?: 0L) > 0L) {
            nowElapsed - state!!.lastPositionUpdateTime
        } else {
            Long.MAX_VALUE
        }
        score += when {
            updateAge <= 2_000L -> 360
            updateAge <= 10_000L -> 280
            updateAge <= 45_000L -> 190
            updateAge <= 180_000L -> 100
            updateAge <= 600_000L -> 35
            else -> -35
        }

        val callbackAge = nowElapsed - (sessionActivityAt[controller.packageName] ?: 0L)
        score += when {
            callbackAge <= 2_000L -> 330
            callbackAge <= 10_000L -> 240
            callbackAge <= 60_000L -> 120
            else -> 0
        }

        val matchingNotification = notifications
            .filter { it.packageName == controller.packageName && looksLikeMediaNotification(it) }
            .maxByOrNull { it.postTime }
        if (matchingNotification != null) {
            val age = nowWall - matchingNotification.postTime
            score += when {
                age <= 5_000L -> 330
                age <= 30_000L -> 230
                age <= 120_000L -> 140
                age <= 600_000L -> 55
                else -> 20
            }
            if (matchingNotification.isOngoing) score += 45
        }

        val notificationEventAge = nowElapsed - (notificationActivityAt[controller.packageName] ?: 0L)
        score += when {
            notificationEventAge <= 2_000L -> 280
            notificationEventAge <= 10_000L -> 180
            notificationEventAge <= 60_000L -> 80
            else -> 0
        }

        val metadata = controller.metadata
        if (metadataTitle(metadata).isNotBlank()) score += 65
        if (metadataArtwork(metadata) != null) score += 22
        if (sourceLock != MediaSourceLock.AUTO && sourceLock.matches(controller.packageName)) score += 5_000

        val packageLower = controller.packageName.lowercase()
        if (packageLower.contains("youtube") && state?.state == PlaybackState.STATE_PLAYING) score += 80
        if (packageLower.contains("soundcloud") && state?.state == PlaybackState.STATE_PLAYING) score += 55

        val anotherFreshPlayingSession = allControllers.any { other ->
            other.packageName != controller.packageName &&
                other.playbackState?.state == PlaybackState.STATE_PLAYING &&
                nowElapsed - (other.playbackState?.lastPositionUpdateTime ?: 0L) <= 45_000L
        }
        if (packageLower.contains("twitch") && anotherFreshPlayingSession) score -= 430

        return score
    }

    private fun notificationScore(
        sbn: StatusBarNotification,
        sourceLock: MediaSourceLock,
    ): Int {
        var score = 0
        val notification = sbn.notification
        val age = System.currentTimeMillis() - sbn.postTime
        if (mediaSessionToken(notification) != null) score += 260
        if (notification.category == Notification.CATEGORY_TRANSPORT) score += 170
        if (sbn.isOngoing) score += 60
        if (notificationTitle(notification).isNotBlank()) score += 45
        if (notificationArtwork(notification) != null) score += 15
        score += when {
            age <= 5_000L -> 280
            age <= 30_000L -> 190
            age <= 120_000L -> 95
            age <= 600_000L -> 35
            else -> 0
        }
        if (sourceLock != MediaSourceLock.AUTO && sourceLock.matches(sbn.packageName)) score += 5_000
        if (sbn.packageName.contains("youtube", ignoreCase = true)) score += 40
        if (sbn.packageName.contains("soundcloud", ignoreCase = true)) score += 32
        return score
    }

    private fun looksLikeMediaNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (mediaSessionToken(notification) != null) return true
        if (notification.category == Notification.CATEGORY_TRANSPORT) return true
        if (sbn.packageName.contains("soundcloud", ignoreCase = true) && sbn.isOngoing) return true
        if (sbn.packageName.contains("youtube", ignoreCase = true) && sbn.isOngoing) return true
        val title = notificationTitle(notification)
        val text = notificationArtist(notification)
        return sbn.isOngoing && title.isNotBlank() && text.isNotBlank()
    }

    private fun setController(controller: MediaController) {
        activeController = controller
        MediaControllerBridge.setActive(controller)
    }

    private fun clearActiveControllerOnly() {
        activeController = null
        MediaControllerBridge.setActive(null)
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
        val playing = when (playbackState?.state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            -> true

            else -> false
        }

        CapsuleRuntime.updateMedia(
            title = title,
            artist = artist,
            packageName = controller.packageName,
            artwork = artwork,
            isPlaying = playing,
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
        observedControllers.values.forEach { observed ->
            runCatching { observed.controller.unregisterCallback(observed.callback) }
        }
        observedControllers.clear()
        clearActiveControllerOnly()
        if (removeListener) CapsuleRuntime.clearMedia()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        clearController(removeListener = true)
        super.onDestroy()
    }

    private data class ObservedController(
        val controller: MediaController,
        val callback: MediaController.Callback,
    )
}
