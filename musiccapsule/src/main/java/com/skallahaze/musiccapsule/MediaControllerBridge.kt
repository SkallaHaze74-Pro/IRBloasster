package com.skallahaze.musiccapsule

import android.media.session.MediaController
import android.media.session.PlaybackState

object MediaControllerBridge {
    private val lock = Any()
    private var controller: MediaController? = null

    fun setActive(value: MediaController?) = synchronized(lock) {
        controller = value
    }

    fun togglePlayPause(): Boolean = runCatching {
        val active = synchronized(lock) { controller } ?: return false
        when (active.playbackState?.state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            -> active.transportControls.pause()

            else -> active.transportControls.play()
        }
        true
    }.getOrDefault(false)

    fun skipNext(): Boolean = runCatching {
        val active = synchronized(lock) { controller } ?: return false
        active.transportControls.skipToNext()
        true
    }.getOrDefault(false)

    fun skipPrevious(): Boolean = runCatching {
        val active = synchronized(lock) { controller } ?: return false
        active.transportControls.skipToPrevious()
        true
    }.getOrDefault(false)
}
