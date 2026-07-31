package com.skallahaze.irbloasster.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ConsumerIrSender(context: Context) {
    private val manager = context.applicationContext
        .getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val isAvailable: Boolean
        get() = manager?.hasIrEmitter() == true

    val summary: String
        get() {
            val ir = manager ?: return "Kein ConsumerIrManager"
            if (!ir.hasIrEmitter()) return "Kein IR-Blaster erkannt"
            val ranges = runCatching { ir.carrierFrequencies }
                .getOrNull()
                ?.joinToString { "${it.minFrequency / 1000}–${it.maxFrequency / 1000} kHz" }
                .orEmpty()
            return if (ranges.isBlank()) "IR-Blaster bereit" else "IR bereit: $ranges"
        }

    suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val ir = manager ?: error("IR-Dienst ist auf diesem Gerät nicht verfügbar")
            check(ir.hasIrEmitter()) { "Kein IR-Blaster erkannt" }
            require(signal.carrierFrequency in 20_000..60_000) { "Ungültige Trägerfrequenz" }
            require(signal.pattern.isNotEmpty() && signal.pattern.all { it > 0 }) { "Ungültiges IR-Muster" }

            repeat(signal.repeats) { index ->
                ir.transmit(signal.carrierFrequency, signal.pattern)
                if (index < signal.repeats - 1) delay(signal.repeatGapMs)
            }
        }
    }
}
