package com.skallahaze.musiccapsule

import android.content.Context
import kotlin.math.max

/**
 * Rendering profile measured on the user's Xiaomi 15T Pro.
 *
 * AIDA64 reports a 1280 × 2772 AMOLED panel, 447 physical dpi,
 * 144 Hz and OpenGL ES 3.2. Android's developer option reports a
 * 393 dp smallest width. Android dp remains the source of truth for layout;
 * the physical values are used to tune effects and diagnostics.
 */
object XiaomiDisplayProfile {
    const val TARGET_WIDTH_PX = 1280
    const val TARGET_HEIGHT_PX = 2772
    const val TARGET_SMALLEST_WIDTH_DP = 393
    const val TARGET_PHYSICAL_DPI = 447
    const val TARGET_REFRESH_HZ = 144
    const val TARGET_GPU = "Mali-G925-Immortalis MC12"
    const val TARGET_GL = "OpenGL ES 3.2"

    fun smallestWidthDp(context: Context): Int {
        val value = context.resources.configuration.smallestScreenWidthDp
        return if (value > 0) value else TARGET_SMALLEST_WIDTH_DP
    }

    /**
     * Keeps the visual proportions exact on the 393 dp target while staying
     * usable on nearby Xiaomi/Android devices.
     */
    fun visualScale(context: Context): Float {
        val sw = smallestWidthDp(context)
        return (sw / TARGET_SMALLEST_WIDTH_DP.toFloat()).coerceIn(.88f, 1.16f)
    }

    fun edgeBudgetDp(context: Context): Float {
        val sw = smallestWidthDp(context).toFloat()
        return max(15f, sw * .052f).coerceAtMost(23f)
    }

    fun diagnosticLabel(context: Context): String {
        return "${smallestWidthDp(context)}dp · ${TARGET_WIDTH_PX}×${TARGET_HEIGHT_PX} · ${TARGET_PHYSICAL_DPI} dpi · ${TARGET_REFRESH_HZ} Hz · AMOLED"
    }
}
