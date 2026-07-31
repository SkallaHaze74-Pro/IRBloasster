package com.skallahaze.irbloasster.ir

import android.content.Context
import android.hardware.ConsumerIrManager

class ConsumerIrSender(context: Context) {
    private val manager: ConsumerIrManager? =
        context.applicationContext.getSystemService(ConsumerIrManager::class.java)

    val isAvailable: Boolean
        get() = manager?.hasIrEmitter() == true

    fun transmit(frequency: Int, pattern: IntArray): Boolean {
        val ir = manager ?: return false
        if (!ir.hasIrEmitter() || pattern.isEmpty()) return false

        return runCatching {
            ir.transmit(frequency, pattern)
        }.isSuccess
    }

    fun transmit(command: LgCommand): Boolean =
        transmit(LG_OLED55B1.FREQUENCY, Nec.encode(command.code, command.repeatFrames))

    fun transmit(command: SonyCommand, mode: SonyCommandMode): Boolean =
        transmit(Sony_STR_DB870.FREQUENCY, Sony_STR_DB870.pattern(command, mode))
}
