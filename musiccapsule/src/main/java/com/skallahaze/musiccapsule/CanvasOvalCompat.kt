package com.skallahaze.musiccapsule

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF

/** Clip helper for the Stage's animated oval artwork mask. */
fun Canvas.clipOval(rect: RectF) {
    val ovalPath = Path().apply {
        addOval(rect, Path.Direction.CW)
    }
    clipPath(ovalPath)
}
