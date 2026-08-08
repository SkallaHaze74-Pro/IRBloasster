package com.skallahaze.irbloasster.capsule

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicCapsuleNotificationListener : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var sessionManager: MediaSessionManager
    private lateinit var listenerComponent: ComponentName
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishController(activeController, metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishController(activeController, activeController?.metadata, state)
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectController(controllers.orEmpty())
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        listenerComponent = ComponentName(this, MusicCapsuleNotificationListener::class.java)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                listenerComponent,
                mainHandler,
            )
        }
        refreshSessions()
    }

    override fun onListenerDisconnected() {
        clearController()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.notification?.category == android.app.Notification.CATEGORY_TRANSPORT) {
            mainHandler.postDelayed(::refreshSessions, 120L)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.notification?.category == android.app.Notification.CATEGORY_TRANSPORT) {
            mainHandler.postDelayed(::refreshSessions, 120L)
        }
    }

    private fun refreshSessions() {
        val controllers = runCatching {
            sessionManager.getActiveSessions(listenerComponent)
        }.getOrDefault(emptyList())
        selectController(controllers)
    }

    private fun controllerScore(controller: MediaController): Int {
        val stateScore = when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> 100
            PlaybackState.STATE_BUFFERING -> 90
            PlaybackState.STATE_CONNECTING -> 70
            PlaybackState.STATE_PAUSED -> 40
            else -> 10
        }
        val metadataScore = if (controller.metadata != null) 5 else 0
        return stateScore + metadataScore
    }

    private fun selectController(controllers: List<MediaController>) {
        val selected = controllers.maxByOrNull(::controllerScore)
        if (selected?.sessionToken == activeController?.sessionToken) {
            publishController(selected, selected?.metadata, selected?.playbackState)
            return
        }

        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = selected
        MusicCapsuleMediaController.setActive(selected)

        if (selected == null) {
            MusicCapsuleRuntime.clearMedia()
            return
        }

        runCatching { selected.registerCallback(controllerCallback, mainHandler) }
        publishController(selected, selected.metadata, selected.playbackState)
    }

    private fun publishController(
        controller: MediaController?,
        metadata: MediaMetadata?,
        playbackState: PlaybackState? = controller?.playbackState,
    ) {
        if (controller == null) {
            MusicCapsuleRuntime.clearMedia()
            return
        }

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.description?.title?.toString()
            ?: "Unbekannter Titel"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.description?.subtitle?.toString()
            ?: ""
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.description?.iconBitmap
        val playing = playbackState?.state == PlaybackState.STATE_PLAYING ||
            playbackState?.state == PlaybackState.STATE_BUFFERING

        MusicCapsuleRuntime.updateMedia(
            title = title,
            artist = artist,
            packageName = controller.packageName,
            artwork = artwork,
            isPlaying = playing,
        )
    }

    private fun clearController() {
        runCatching {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
        MusicCapsuleMediaController.setActive(null)
        MusicCapsuleRuntime.clearMedia()
    }

    override fun onDestroy() {
        clearController()
        super.onDestroy()
    }
}
