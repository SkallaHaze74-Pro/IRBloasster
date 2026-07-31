package com.skallahaze.irbloasster.ir

enum class LgIrCommand {
    POWER,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    INPUT,
    HOME,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    OK,
    BACK
}

object LgTvIrProfile {
    private const val FREQUENCY = 38_000

    private val codes = mapOf(
        LgIrCommand.POWER to 0x20DF10EFL,
        LgIrCommand.VOLUME_UP to 0x20DF40BFL,
        LgIrCommand.VOLUME_DOWN to 0x20DFC03FL,
        LgIrCommand.MUTE to 0x20DF906FL,
        LgIrCommand.INPUT to 0x20DFD02FL,
        LgIrCommand.HOME to 0x20DF3EC1L,
        LgIrCommand.UP to 0x20DF02FDL,
        LgIrCommand.DOWN to 0x20DF827DL,
        LgIrCommand.LEFT to 0x20DFE01FL,
        LgIrCommand.RIGHT to 0x20DF609FL,
        LgIrCommand.OK to 0x20DF22DDL,
        LgIrCommand.BACK to 0x20DF14EBL
    )

    fun signal(command: LgIrCommand): IrSignal {
        val code = codes.getValue(command)
        return IrSignal(
            label = "LG ${command.name}",
            carrierFrequencyHz = FREQUENCY,
            patternMicros = NecEncoder.encode(code),
            repeatCount = 1
        )
    }
}

enum class SonyCommand {
    POWER,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    INPUT_NEXT
}

data class SonyIrProfile(
    val name: String,
    val bits: Int,
    val address: Int,
    val commands: Map<SonyCommand, Int>,
    val note: String
) {
    fun signal(command: SonyCommand): IrSignal? {
        val commandCode = commands[command] ?: return null
        return IrSignal(
            label = "$name ${command.name}",
            carrierFrequencyHz = 40_000,
            patternMicros = SonySircEncoder.encode(
                command = commandCode,
                address = address,
                bits = bits
            ),
            repeatCount = 3,
            repeatDelayMillis = 45
        )
    }
}

object SonyProfiles {
    /**
     * Candidate profiles for the built-in test assistant. They are intentionally labelled
     * as candidates until verified on the user's physical Sony receiver/home-cinema system.
     */
    val candidates: List<SonyIrProfile> = listOf(
        SonyIrProfile(
            name = "Sony Audio Test A",
            bits = 12,
            address = 16,
            commands = mapOf(
                SonyCommand.POWER to 21,
                SonyCommand.VOLUME_UP to 18,
                SonyCommand.VOLUME_DOWN to 19,
                SonyCommand.MUTE to 20,
                SonyCommand.INPUT_NEXT to 47
            ),
            note = "SIRC-12, Geräteadresse 16 – zuerst Power und Lautstärke testen"
        ),
        SonyIrProfile(
            name = "Sony Audio Test B",
            bits = 15,
            address = 16,
            commands = mapOf(
                SonyCommand.POWER to 21,
                SonyCommand.VOLUME_UP to 18,
                SonyCommand.VOLUME_DOWN to 19,
                SonyCommand.MUTE to 20,
                SonyCommand.INPUT_NEXT to 47
            ),
            note = "SIRC-15, Geräteadresse 16 – Alternative für neuere Profile"
        ),
        SonyIrProfile(
            name = "Sony Audio Test C",
            bits = 12,
            address = 13,
            commands = mapOf(
                SonyCommand.POWER to 21,
                SonyCommand.VOLUME_UP to 18,
                SonyCommand.VOLUME_DOWN to 19,
                SonyCommand.MUTE to 20,
                SonyCommand.INPUT_NEXT to 47
            ),
            note = "SIRC-12, Geräteadresse 13 – alternativer Empfängerbereich"
        )
    )
}
