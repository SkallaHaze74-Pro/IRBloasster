package com.skallahaze.irbloasster.data

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("living_room_controller", Context.MODE_PRIVATE)

    var tvIp: String
        get() = prefs.getString(KEY_TV_IP, "")?.trim().orEmpty()
        set(value) = prefs.edit().putString(KEY_TV_IP, value.trim()).apply()

    var tvMac: String
        get() = prefs.getString(KEY_TV_MAC, "")?.trim().orEmpty()
        set(value) = prefs.edit().putString(KEY_TV_MAC, value.trim()).apply()

    var sonyProfileIndex: Int
        get() = prefs.getInt(KEY_SONY_PROFILE, 0)
        set(value) = prefs.edit().putInt(KEY_SONY_PROFILE, value.coerceAtLeast(0)).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    fun clientKey(host: String): String? =
        prefs.getString("$KEY_CLIENT_PREFIX${host.trim()}", null)?.takeIf { it.isNotBlank() }

    fun saveClientKey(host: String, key: String) {
        if (host.isBlank() || key.isBlank()) return
        prefs.edit().putString("$KEY_CLIENT_PREFIX${host.trim()}", key).apply()
    }

    fun clearClientKey(host: String) {
        if (host.isBlank()) return
        prefs.edit().remove("$KEY_CLIENT_PREFIX${host.trim()}").apply()
    }

    companion object {
        private const val KEY_TV_IP = "tv_ip"
        private const val KEY_TV_MAC = "tv_mac"
        private const val KEY_SONY_PROFILE = "sony_profile"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_CLIENT_PREFIX = "webos_client_key_"
    }
}
