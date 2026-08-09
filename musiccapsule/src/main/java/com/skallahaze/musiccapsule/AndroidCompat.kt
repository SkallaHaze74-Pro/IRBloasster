package com.skallahaze.musiccapsule

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

internal fun Canvas.drawBitmapCropped(
    bitmap: Bitmap,
    source: RectF,
    destination: RectF,
    paint: Paint?,
) {
    val sourceRect = Rect(
        source.left.toInt().coerceIn(0, bitmap.width),
        source.top.toInt().coerceIn(0, bitmap.height),
        source.right.toInt().coerceIn(0, bitmap.width),
        source.bottom.toInt().coerceIn(0, bitmap.height),
    )
    drawBitmap(bitmap, sourceRect, destination, paint)
}

internal fun Canvas.clipRounded(rect: RectF, radiusX: Float, radiusY: Float) {
    val path = Path().apply {
        addRoundRect(rect, radiusX, radiusY, Path.Direction.CW)
    }
    clipPath(path)
}
