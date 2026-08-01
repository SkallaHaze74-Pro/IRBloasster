package com.skallahaze.irbloasster.ir

/**
 * Exact profile of the photographed JBL Simply Cinema SUB125.
 *
 * The SUB125 has no infrared receiver. SmartIR therefore controls the bass
 * channel indirectly through the Sony STR-DB870. No serial number is stored.
 */
object JBL_SUB125 {
    const val BRAND = "JBL"
    const val MODEL = "SUB125"
    const val SYSTEM = "SCS125"
    const val TYPE = "Active bass-reflex subwoofer"
    const val WOOFER_SIZE_INCH = 8
    const val AMPLIFIER_RMS_W = 75
    const val POWER_INPUT = "AC 230 V · 50 Hz"
    const val MAX_POWER_CONSUMPTION_W = 160
    const val FUSE = "1 A · 250 V"
    const val HAS_IR_RECEIVER = false
    const val AUTO_STANDBY_MINUTES = 20

    val SONY_INDIRECT_CONTROLS = listOf(
        Sony_STR_DB870.SUBWOOFER_UP,
        Sony_STR_DB870.SUBWOOFER_DOWN,
        Sony_STR_DB870.TEST_TONE,
        Sony_STR_DB870.AFD,
        Sony_STR_DB870.MODE_2CH,
    )
}
