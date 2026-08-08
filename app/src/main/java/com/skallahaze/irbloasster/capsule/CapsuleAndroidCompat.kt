package com.skallahaze.irbloasster.capsule

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

internal fun Canvas.drawBitmap(
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

internal fun Canvas.clipRoundRect(
    rect: RectF,
    radiusX: Float,
    radiusY: Float,
) {
    val rounded = Path()
    rounded.addRoundRect(rect, radiusX, radiusY, Path.Direction.CW)
    clipPath(rounded)
}

internal fun WindowManager.updateViewLayout(
    view: View?,
    params: ViewGroup.LayoutParams,
) {
    if (view != null) updateViewLayout(view, params)
}
