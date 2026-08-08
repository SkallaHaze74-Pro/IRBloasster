package com.skallahaze.irbloasster.capsule

import android.media.session.MediaController
import android.media.session.PlaybackState

object MusicCapsuleMediaController {
    private val lock = Any()
    private var controller: MediaController? = null

    fun setActive(controller: MediaController?) = synchronized(lock) {
        this.controller = controller
    }

    fun togglePlayPause(): Boolean = runCatching {
        val active = synchronized(lock) { controller } ?: return false
        val state = active.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
            active.transportControls.pause()
        } else {
            active.transportControls.play()
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
