package com.skallahaze.irbloasster.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.skallahaze.irbloasster.data.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConsumerIrTransmitter(
    context: Context,
    private val log: DiagnosticsLog
) {
    private val manager = context.applicationContext
        .getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val transmissionMutex = Mutex()

    val available: Boolean
        get() = manager?.hasIrEmitter() == true

    suspend fun transmit(signal: IrSignal): Result<Unit> = transmissionMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val irManager = requireNotNull(manager) {
                    "No Android Consumer IR service detected"
                }
                require(irManager.hasIrEmitter()) { "No Android Consumer IR emitter detected" }
                require(signal.patternMicros.isNotEmpty()) { "IR pattern is empty" }

                repeat(signal.repeatCount.coerceAtLeast(1)) { index ->
                    irManager.transmit(signal.carrierFrequencyHz, signal.patternMicros)
                    if (index < signal.repeatCount - 1) delay(signal.repeatDelayMillis)
                }
                log.info(
                    "IR",
                    "Sent ${signal.label}: ${signal.carrierFrequencyHz} Hz, ${signal.patternMicros.size} timings, ${signal.repeatCount} repeat(s)"
                )
            }.onFailure {
                log.error("IR", it.message ?: "IR transmission failed")
            }
        }
    }
}
