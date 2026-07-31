package com.skallahaze.irbloasster.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class ConsumerIrSender(private val context: Context) {
    private val manager: ConsumerIrManager? = context.getSystemService(ConsumerIrManager::class.java)

    val isAvailable: Boolean
        get() = manager?.hasIrEmitter() == true

    fun sendLg(key: LgIrKey, haptics: Boolean): Result<Unit> =
        transmit(NecProtocol.FREQUENCY_HZ, NecProtocol.encode(key.code), haptics)

    fun sendNec(code: UInt, repeats: Int, haptics: Boolean): Result<Unit> =
        transmit(NecProtocol.FREQUENCY_HZ, NecProtocol.encode(code, repeats), haptics)

    fun sendSony(
        command: Int,
        address: Int,
        bits: SonySircProtocol.FrameBits,
        extended: Int,
        haptics: Boolean
    ): Result<Unit> = transmit(
        SonySircProtocol.FREQUENCY_HZ,
        SonySircProtocol.encode(command, address, bits, extended),
        haptics
    )

    private fun transmit(frequency: Int, pattern: IntArray, haptics: Boolean): Result<Unit> = runCatching {
        val irManager = manager ?: error("ConsumerIrManager ist nicht verfügbar")
        check(irManager.hasIrEmitter()) { "Dieses Gerät meldet keinen IR-Blaster" }
        irManager.transmit(frequency, pattern)
        if (haptics) vibrate()
    }

    private fun vibrate() {
        val effect = VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
        }
    }
}
