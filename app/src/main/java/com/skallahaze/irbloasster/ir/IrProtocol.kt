package com.skallahaze.irbloasster.ir

import com.skallahaze.irbloasster.model.LgIrCommand
import com.skallahaze.irbloasster.model.SonyCommand

data class IrSignal(
    val carrierFrequency: Int,
    val pattern: IntArray,
    val repeats: Int = 1,
    val repeatGapMs: Long = 45L
)

object NecProtocol {
    private const val CARRIER = 38_000
    private const val HEADER_MARK = 9_000
    private const val HEADER_SPACE = 4_500
    private const val BIT_MARK = 560
    private const val ONE_SPACE = 1_690
    private const val ZERO_SPACE = 560

    /**
     * Encodes the usual NEC hexadecimal notation (for example 20DF10EF).
     * Each displayed byte is emitted least-significant bit first, as required by NEC.
     */
    fun encode(code: Long, repeats: Int = 1): IrSignal {
        require(code in 0..0xFFFF_FFFFL) { "NEC code must fit in 32 bits" }
        val pulses = ArrayList<Int>(67)
        pulses += HEADER_MARK
        pulses += HEADER_SPACE

        for (byteShift in intArrayOf(24, 16, 8, 0)) {
            val byte = ((code shr byteShift) and 0xFF).toInt()
            for (bit in 0 until 8) {
                pulses += BIT_MARK
                pulses += if (((byte shr bit) and 1) == 1) ONE_SPACE else ZERO_SPACE
            }
        }
        pulses += BIT_MARK

        return IrSignal(
            carrierFrequency = CARRIER,
            pattern = pulses.toIntArray(),
            repeats = repeats.coerceIn(1, 8),
            repeatGapMs = 42L
        )
    }
}

object SonySircProtocol {
    private const val CARRIER = 40_000
    private const val HEADER_MARK = 2_400
    private const val SPACE = 600
    private const val ZERO_MARK = 600
    private const val ONE_MARK = 1_200

    /**
     * Encodes a complete raw SIRC frame. Sony transmits bits least-significant first.
     * A valid key press is normally repeated at least three times.
     */
    fun encodeRaw(code: Int, bits: Int, repeats: Int = 3): IrSignal {
        require(bits in setOf(12, 15, 20)) { "Sony SIRC supports 12, 15 or 20 bits" }
        require(code >= 0 && code.toLong() < (1L shl bits)) { "SIRC code does not fit in $bits bits" }

        val pulses = ArrayList<Int>(2 + bits * 2)
        pulses += HEADER_MARK
        pulses += SPACE
        repeat(bits) { bit ->
            pulses += if (((code shr bit) and 1) == 1) ONE_MARK else ZERO_MARK
            pulses += SPACE
        }

        return IrSignal(
            carrierFrequency = CARRIER,
            pattern = pulses.toIntArray(),
            repeats = repeats.coerceIn(1, 8),
            repeatGapMs = 45L
        )
    }

    fun encode(command: Int, device: Int, bits: Int = 12, extension: Int = 0): IrSignal {
        require(command in 0..0x7F)
        return when (bits) {
            12 -> encodeRaw(command or ((device and 0x1F) shl 7), 12)
            15 -> encodeRaw(command or ((device and 0xFF) shl 7), 15)
            20 -> encodeRaw(command or ((device and 0x1F) shl 7) or ((extension and 0xFF) shl 12), 20)
            else -> error("Unsupported SIRC bit count")
        }
    }
}

object LgOledB1IrProfile {
    private val codes = mapOf(
        LgIrCommand.POWER to 0x20DF10EFL,
        LgIrCommand.POWER_ON to 0x20DF23DCL,
        LgIrCommand.POWER_OFF to 0x20DFA35CL,
        LgIrCommand.VOLUME_UP to 0x20DF40BFL,
        LgIrCommand.VOLUME_DOWN to 0x20DFC03FL,
        LgIrCommand.MUTE to 0x20DF906FL,
        LgIrCommand.CHANNEL_UP to 0x20DF00FFL,
        LgIrCommand.CHANNEL_DOWN to 0x20DF807FL,
        LgIrCommand.INPUT to 0x20DFD02FL,
        LgIrCommand.HOME to 0x20DF3EC1L,
        LgIrCommand.SETTINGS to 0x20DFC23DL,
        LgIrCommand.BACK to 0x20DF14EBL,
        LgIrCommand.INFO to 0x20DF55AAL,
        LgIrCommand.GUIDE to 0x20DFD52AL,
        LgIrCommand.UP to 0x20DF02FDL,
        LgIrCommand.DOWN to 0x20DF827DL,
        LgIrCommand.LEFT to 0x20DFE01FL,
        LgIrCommand.RIGHT to 0x20DF609FL,
        LgIrCommand.OK to 0x20DF22DDL,
        LgIrCommand.PLAY to 0x20DF0DF2L,
        LgIrCommand.PAUSE to 0x20DF5DA2L,
        LgIrCommand.STOP to 0x20DF8D72L,
        LgIrCommand.REWIND to 0x20DFF10EL,
        LgIrCommand.FAST_FORWARD to 0x20DF718EL
    )

    fun signal(command: LgIrCommand): IrSignal = NecProtocol.encode(
        code = codes.getValue(command),
        repeats = 1
    )

    fun codeHex(command: LgIrCommand): String = "%08X".format(codes.getValue(command))
}

data class SonyIrProfile(
    val name: String,
    val description: String,
    val bits: Int,
    val codes: Map<SonyCommand, Int>
) {
    fun signal(command: SonyCommand): IrSignal? = codes[command]?.let { SonySircProtocol.encodeRaw(it, bits) }
    fun codeHex(command: SonyCommand): String? = codes[command]?.let { "0x%X".format(it) }
}

object SonyHtRt3Profiles {
    /**
     * Candidate profiles are intentionally kept selectable because Sony reused several SIRC
     * device families across soundbars. The first profile matches a widely used 15-bit Sony
     * soundbar code family; the second is a generic Sony audio fallback.
     */
    val all: List<SonyIrProfile> = listOf(
        SonyIrProfile(
            name = "Sony Soundbar 15-bit",
            description = "Erster Kandidat für RMT-AH200U / HT-RT3",
            bits = 15,
            codes = mapOf(
                SonyCommand.POWER to 0x540C,
                SonyCommand.POWER_ON to 0x3A0C,
                SonyCommand.POWER_OFF to 0x7A0C,
                SonyCommand.VOLUME_UP to 0x240C,
                SonyCommand.VOLUME_DOWN to 0x640C,
                SonyCommand.MUTE to 0x140C,
                SonyCommand.INPUT to 0x4B0D,
                SonyCommand.SOUND_FIELD to 0x2B0D,
                SonyCommand.CLEAR_AUDIO to 0x6B0D,
                SonyCommand.NIGHT to 0x1B0D,
                SonyCommand.VOICE to 0x5B0D,
                SonyCommand.SUBWOOFER_UP to 0x260C,
                SonyCommand.SUBWOOFER_DOWN to 0x660C
            )
        ),
        SonyIrProfile(
            name = "Sony Audio 12-bit",
            description = "Fallback für klassische Sony-Audio-Geräte",
            bits = 12,
            codes = mapOf(
                SonyCommand.POWER to (21 or (16 shl 7)),
                SonyCommand.POWER_OFF to (47 or (16 shl 7)),
                SonyCommand.VOLUME_UP to (18 or (16 shl 7)),
                SonyCommand.VOLUME_DOWN to (19 or (16 shl 7)),
                SonyCommand.MUTE to (20 or (16 shl 7)),
                SonyCommand.INPUT to (32 or (16 shl 7)),
                SonyCommand.SOUND_FIELD to (62 or (16 shl 7)),
                SonyCommand.NIGHT to (75 or (16 shl 7)),
                SonyCommand.SUBWOOFER_UP to (86 or (16 shl 7)),
                SonyCommand.SUBWOOFER_DOWN to (87 or (16 shl 7))
            )
        )
    )
}
