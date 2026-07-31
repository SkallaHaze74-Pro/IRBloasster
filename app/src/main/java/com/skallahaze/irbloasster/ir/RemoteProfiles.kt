package com.skallahaze.irbloasster.ir

enum class LgIrKey(val displayName: String, val code: UInt) {
    POWER("Power", 0x20DF10EFu),
    VOLUME_UP("Lauter", 0x20DF40BFu),
    VOLUME_DOWN("Leiser", 0x20DFC03Fu),
    MUTE("Stumm", 0x20DF906Fu),
    INPUT("Eingang", 0x20DFD02Fu),
    HOME("Home", 0x20DF3EC1u),
    BACK("Zurück", 0x20DF14EBu),
    UP("Hoch", 0x20DF02FDu),
    DOWN("Runter", 0x20DF827Du),
    LEFT("Links", 0x20DFE01Fu),
    RIGHT("Rechts", 0x20DF609Fu),
    OK("OK", 0x20DF22DDu)
}
