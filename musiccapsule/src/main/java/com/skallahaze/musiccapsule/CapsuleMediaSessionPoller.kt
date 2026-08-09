package com.skallahaze.musiccapsule

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlin.math.max

/**
 * HyperOS occasionally disconnects or briefly recreates media notifications.
 * The NotificationListener then sees an empty gap and the old implementation
 * replaced valid SoundCloud metadata with "Keine Wiedergabe". This poller runs
 * beside the always-on overlay foreground service and continuously queries the
 * active MediaSessions, keeps the last verified source during short gaps and
 * asks Android to rebind the listener when needed.
 */
class CapsuleMediaSessionPoller(
    context: Context,
    private val requestListenerRebind: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(appContext, CapsuleNotificationListener::class.java)
    private val cachePreferences = appContext.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE)

    private var running = false
    private var lastRebindAt = 0L
    private var lastGood: CachedMedia? = loadPersistedCache()

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            pollNow()
            val snapshot = CapsuleRuntime.snapshot()
            val delay = when {
                snapshot.signal > SIGNAL_ACTIVE -> 450L
                snapshot.analyzerRunning -> 750L
                else -> 1_200L
            }
            mainHandler.postDelayed(this, delay)
        }
    }

    fun start() {
        if (running) {
            kick()
            return
        }
        running = true
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.post(pollRunnable)
    }

    fun kick() {
        if (!running) return
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.post(pollRunnable)
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacks(pollRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun pollNow() {
        rememberRuntimeMedia()
        val sourceLock = CapsulePreferences.sourceLock(appContext)
        val controllers = runCatching {
            sessionManager.getActiveSessions(listenerComponent)
        }.getOrElse {
            requestRebindIfNeeded()
            emptyList()
        }

        val eligible = controllers.filter { sourceLock.matches(it.packageName) }
        val playing = eligible.filter { isActivelyPlaying(it.playbackState) }
        val pool = if (playing.isNotEmpty()) playing else eligible
        val selected = pool.maxByOrNull { controllerScore(it, controllers, sourceLock) }

        if (selected != null) {
            publishController(selected)
            return
        }

        if (controllers.isEmpty()) requestRebindIfNeeded()
        publishGraceFallback(sourceLock)
    }

    private fun controllerScore(
        controller: MediaController,
        allControllers: List<MediaController>,
        sourceLock: MediaSourceLock,
    ): Long {
        val state = controller.playbackState
        val now = SystemClock.elapsedRealtime()
        var score = when (state?.state) {
            PlaybackState.STATE_PLAYING -> 10_000L
            PlaybackState.STATE_BUFFERING -> 9_000L
            PlaybackState.STATE_CONNECTING -> 8_200L
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            -> 7_400L

            PlaybackState.STATE_PAUSED -> 1_400L
            PlaybackState.STATE_STOPPED -> 100L
            else -> 20L
        }

        val updateAge = state?.lastPositionUpdateTime
            ?.takeIf { it > 0L }
            ?.let { (now - it).coerceAtLeast(0L) }
            ?: Long.MAX_VALUE
        score += when {
            updateAge <= 2_000L -> 2_000L
            updateAge <= 8_000L -> 1_400L
            updateAge <= 30_000L -> 700L
            updateAge <= 120_000L -> 220L
            else -> 0L
        }

        val metadata = controller.metadata
        if (metadataTitle(metadata).isNotBlank()) score += 450L
        if (metadataArtist(metadata).isNotBlank()) score += 180L
        if (metadataArtwork(metadata) != null) score += 80L

        if (sourceLock != MediaSourceLock.AUTO && sourceLock.matches(controller.packageName)) {
            score += 100_000L
        }

        val runtime = CapsuleRuntime.snapshot()
        if (runtime.packageName == controller.packageName && runtime.signal > SIGNAL_ACTIVE) {
            score += 500L
        }

        val packageLower = controller.packageName.lowercase()
        if (packageLower.contains("soundcloud") && isActivelyPlaying(state)) score += 260L
        if (packageLower.contains("youtube") && isActivelyPlaying(state)) score += 220L

        val anotherFreshPlaying = allControllers.any { other ->
            other.packageName != controller.packageName &&
                isActivelyPlaying(other.playbackState) &&
                now - (other.playbackState?.lastPositionUpdateTime ?: 0L) <= 30_000L
        }
        if (packageLower.contains("twitch") && anotherFreshPlaying && updateAge > 5_000L) {
            score -= 1_500L
        }
        return score
    }

    private fun publishController(controller: MediaController) {
        val metadata = controller.metadata
        val packageName = controller.packageName
        val cachedForPackage = lastGood?.takeIf { it.packageName == packageName }
        val title = metadataTitle(metadata)
            .ifBlank { cachedForPackage?.title.orEmpty() }
            .ifBlank { packageLabel(packageName) }
        val artist = metadataArtist(metadata)
            .ifBlank { cachedForPackage?.artist.orEmpty() }
            .ifBlank { packageLabel(packageName) }
        val artwork = metadataArtwork(metadata) ?: cachedForPackage?.artwork
        val isPlaying = isActivelyPlaying(controller.playbackState)

        MediaControllerBridge.setActive(controller)
        CapsuleRuntime.updateMedia(
            title = title,
            artist = artist,
            packageName = packageName,
            artwork = artwork,
            isPlaying = isPlaying,
        )
        remember(
            CachedMedia(
                title = title,
                artist = artist,
                packageName = packageName,
                artwork = artwork,
                isPlaying = isPlaying,
                updatedElapsed = SystemClock.elapsedRealtime(),
                updatedWall = System.currentTimeMillis(),
            ),
        )
    }

    private fun publishGraceFallback(sourceLock: MediaSourceLock) {
        val snapshot = CapsuleRuntime.snapshot()
        val nowElapsed = SystemClock.elapsedRealtime()
        val cached = lastGood
        val signalActive = snapshot.signal > SIGNAL_ACTIVE
        val currentMetadataValid = metadataIsValid(snapshot.title, snapshot.packageName)

        if (currentMetadataValid && signalActive) {
            rememberRuntimeMedia()
            return
        }

        val cacheMatchesLock = cached != null && sourceLock.matches(cached.packageName)
        val cacheAge = cached?.let { nowElapsed - it.updatedElapsed } ?: Long.MAX_VALUE
        if (
            cached != null &&
            cacheMatchesLock &&
            (signalActive || cacheAge <= SHORT_GAP_MS)
        ) {
            CapsuleRuntime.updateMedia(
                title = cached.title,
                artist = cached.artist,
                packageName = cached.packageName,
                artwork = cached.artwork,
                isPlaying = signalActive || cached.isPlaying,
            )
            return
        }

        if (signalActive && sourceLock == MediaSourceLock.SOUNDCLOUD) {
            CapsuleRuntime.updateMedia(
                title = cached?.takeIf { it.packageName.startsWith("com.soundcloud") }?.title
                    ?: "SoundCloud läuft",
                artist = cached?.takeIf { it.packageName.startsWith("com.soundcloud") }?.artist
                    ?: "MediaSession wird neu verbunden",
                packageName = "com.soundcloud.android",
                artwork = cached?.artwork,
                isPlaying = true,
            )
            return
        }

        if (!signalActive && cacheAge > CLEAR_AFTER_MS) {
            MediaControllerBridge.setActive(null)
            CapsuleRuntime.clearMedia()
        }
    }

    private fun rememberRuntimeMedia() {
        val snapshot = CapsuleRuntime.snapshot()
        if (!metadataIsValid(snapshot.title, snapshot.packageName)) return
        remember(
            CachedMedia(
                title = snapshot.title,
                artist = snapshot.artist,
                packageName = snapshot.packageName,
                artwork = snapshot.artwork,
                isPlaying = snapshot.isPlaying,
                updatedElapsed = SystemClock.elapsedRealtime(),
                updatedWall = System.currentTimeMillis(),
            ),
        )
    }

    private fun remember(media: CachedMedia) {
        lastGood = media
        cachePreferences.edit()
            .putString(KEY_TITLE, media.title)
            .putString(KEY_ARTIST, media.artist)
            .putString(KEY_PACKAGE, media.packageName)
            .putBoolean(KEY_PLAYING, media.isPlaying)
            .putLong(KEY_UPDATED_WALL, media.updatedWall)
            .apply()
    }

    private fun loadPersistedCache(): CachedMedia? {
        val title = cachePreferences.getString(KEY_TITLE, "").orEmpty()
        val packageName = cachePreferences.getString(KEY_PACKAGE, "").orEmpty()
        val updatedWall = cachePreferences.getLong(KEY_UPDATED_WALL, 0L)
        if (!metadataIsValid(title, packageName)) return null
        if (System.currentTimeMillis() - updatedWall > PERSISTED_CACHE_MAX_AGE_MS) return null
        return CachedMedia(
            title = title,
            artist = cachePreferences.getString(KEY_ARTIST, "").orEmpty(),
            packageName = packageName,
            artwork = null,
            isPlaying = cachePreferences.getBoolean(KEY_PLAYING, false),
            updatedElapsed = SystemClock.elapsedRealtime(),
            updatedWall = updatedWall,
        )
    }

    private fun requestRebindIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRebindAt < REBIND_INTERVAL_MS) return
        lastRebindAt = now
        runCatching {
            NotificationListenerService.requestRebind(listenerComponent)
        }
        runCatching { requestListenerRebind() }
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

    private fun packageLabel(packageName: String): String {
        return runCatching {
            val info = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }

    private fun metadataIsValid(title: String, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (title.isBlank()) return false
        return title != "Keine Wiedergabe" && title != "Unbekannter Titel"
    }

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    private data class CachedMedia(
        val title: String,
        val artist: String,
        val packageName: String,
        val artwork: Bitmap?,
        val isPlaying: Boolean,
        val updatedElapsed: Long,
        val updatedWall: Long,
    )

    private companion object {
        const val SIGNAL_ACTIVE = 0.008f
        const val SHORT_GAP_MS = 20_000L
        const val CLEAR_AFTER_MS = 45_000L
        const val REBIND_INTERVAL_MS = 6_000L
        const val PERSISTED_CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1_000L

        const val CACHE_FILE = "music_capsule_media_watchdog"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_PACKAGE = "package"
        const val KEY_PLAYING = "playing"
        const val KEY_UPDATED_WALL = "updated_wall"
    }
}
