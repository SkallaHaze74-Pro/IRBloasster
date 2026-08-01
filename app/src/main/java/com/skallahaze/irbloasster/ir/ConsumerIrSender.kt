package com.skallahaze.irbloasster.ir

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager

class ConsumerIrSender(context: Context) {
    private val appContext = context.applicationContext

    // Some vendor builds expose the IR service more reliably through the
    // service name than through the class-based lookup, so try both paths.
    private val manager: ConsumerIrManager? =
        (appContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)
            ?: appContext.getSystemService(ConsumerIrManager::class.java)

    @Volatile
    var lastError: String? = null
        private set

    val isAvailable: Boolean
        get() = manager?.let { ir ->
            runCatching { ir.hasIrEmitter() }.getOrDefault(false)
        } == true

    fun transmit(frequency: Int, pattern: IntArray): Boolean {
        lastError = null

        val ir = manager ?: return fail("Android-IR-Dienst nicht gefunden")

        if (!runCatching { ir.hasIrEmitter() }.getOrDefault(false)) {
            return fail("Android meldet keinen nutzbaren IR-Sender")
        }

        if (
            appContext.checkSelfPermission(Manifest.permission.TRANSMIT_IR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return fail("IR-Berechtigung fehlt – SmartIR aktualisieren oder neu installieren")
        }

        if (frequency <= 0) {
            return fail("Ungültige IR-Trägerfrequenz: $frequency Hz")
        }

        if (pattern.isEmpty()) {
            return fail("Das IR-Signal ist leer")
        }

        if (pattern.any { it <= 0 }) {
            return fail("Das IR-Signal enthält ungültige Impulszeiten")
        }

        val totalDurationMicros = pattern.fold(0L) { total, slice -> total + slice }
        if (totalDurationMicros > MAX_PATTERN_DURATION_MICROS) {
            return fail("Das IR-Signal ist länger als 2 Sekunden")
        }

        return try {
            ir.transmit(frequency, pattern)
            true
        } catch (error: SecurityException) {
            fail("IR-Berechtigung fehlt: ${error.message ?: "Zugriff verweigert"}")
        } catch (error: IllegalArgumentException) {
            fail("Ungültiges IR-Signal: ${error.message ?: "unbekannter Formatfehler"}")
        } catch (error: UnsupportedOperationException) {
            fail("IR-Sender nicht verfügbar: ${error.message ?: "nicht unterstützt"}")
        } catch (error: RuntimeException) {
            fail("IR-Systemfehler: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun transmit(command: LgCommand): Boolean =
        transmit(LG_OLED55B1.FREQUENCY, Nec.encode(command.code, command.repeatFrames))

    fun transmit(command: SonyCommand, mode: SonyCommandMode): Boolean =
        transmit(Sony_STR_DB870.FREQUENCY, Sony_STR_DB870.pattern(command, mode))

    private fun fail(message: String): Boolean {
        lastError = message
        return false
    }

    private companion object {
        const val MAX_PATTERN_DURATION_MICROS = 2_000_000L
    }
}
