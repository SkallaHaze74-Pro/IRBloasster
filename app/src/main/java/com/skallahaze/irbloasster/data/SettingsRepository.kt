package com.skallahaze.irbloasster.data

import android.content.Context
import com.skallahaze.irbloasster.model.UserSettings

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("living_room_controller", Context.MODE_PRIVATE)

    fun load(): UserSettings = UserSettings(
        tvIp = preferences.getString(KEY_TV_IP, "").orEmpty(),
        tvMac = preferences.getString(KEY_TV_MAC, "").orEmpty(),
        clientKey = preferences.getString(KEY_CLIENT_KEY, "").orEmpty(),
        certificateFingerprint = preferences.getString(KEY_CERT_FINGERPRINT, "").orEmpty(),
        autoConnect = preferences.getBoolean(KEY_AUTO_CONNECT, true),
        irFallback = preferences.getBoolean(KEY_IR_FALLBACK, true),
        haptics = preferences.getBoolean(KEY_HAPTICS, true),
        preferredInput = preferences.getString(KEY_PREFERRED_INPUT, "HDMI_1").orEmpty(),
        sonyAddress = preferences.getInt(KEY_SONY_ADDRESS, 16),
        sonyBits = preferences.getInt(KEY_SONY_BITS, 12),
        sonyPowerCommand = preferences.getInt(KEY_SONY_POWER, 21),
        sonyVolumeUpCommand = preferences.getInt(KEY_SONY_VOL_UP, 18),
        sonyVolumeDownCommand = preferences.getInt(KEY_SONY_VOL_DOWN, 19),
        sonyMuteCommand = preferences.getInt(KEY_SONY_MUTE, 20)
    )

    fun save(settings: UserSettings) {
        preferences.edit()
            .putString(KEY_TV_IP, settings.tvIp.trim())
            .putString(KEY_TV_MAC, settings.tvMac.trim())
            .putString(KEY_CLIENT_KEY, settings.clientKey)
            .putString(KEY_CERT_FINGERPRINT, settings.certificateFingerprint)
            .putBoolean(KEY_AUTO_CONNECT, settings.autoConnect)
            .putBoolean(KEY_IR_FALLBACK, settings.irFallback)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putString(KEY_PREFERRED_INPUT, settings.preferredInput.trim())
            .putInt(KEY_SONY_ADDRESS, settings.sonyAddress)
            .putInt(KEY_SONY_BITS, settings.sonyBits)
            .putInt(KEY_SONY_POWER, settings.sonyPowerCommand)
            .putInt(KEY_SONY_VOL_UP, settings.sonyVolumeUpCommand)
            .putInt(KEY_SONY_VOL_DOWN, settings.sonyVolumeDownCommand)
            .putInt(KEY_SONY_MUTE, settings.sonyMuteCommand)
            .apply()
    }

    fun clearPairing(): UserSettings {
        val updated = load().copy(clientKey = "", certificateFingerprint = "")
        save(updated)
        return updated
    }

    private companion object {
        const val KEY_TV_IP = "tv_ip"
        const val KEY_TV_MAC = "tv_mac"
        const val KEY_CLIENT_KEY = "client_key"
        const val KEY_CERT_FINGERPRINT = "certificate_fingerprint"
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_IR_FALLBACK = "ir_fallback"
        const val KEY_HAPTICS = "haptics"
        const val KEY_PREFERRED_INPUT = "preferred_input"
        const val KEY_SONY_ADDRESS = "sony_address"
        const val KEY_SONY_BITS = "sony_bits"
        const val KEY_SONY_POWER = "sony_power"
        const val KEY_SONY_VOL_UP = "sony_volume_up"
        const val KEY_SONY_VOL_DOWN = "sony_volume_down"
        const val KEY_SONY_MUTE = "sony_mute"
    }
}
