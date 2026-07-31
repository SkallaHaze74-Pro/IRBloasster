package com.skallahaze.irbloasster.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skallahaze.irbloasster.ir.SonyCommandMode

enum class ThemePreference(val title: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
}

class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "smart_ir_settings",
        Context.MODE_PRIVATE,
    )

    var sonyMode by mutableStateOf(
        runCatching {
            SonyCommandMode.valueOf(preferences.getString(KEY_SONY_MODE, SonyCommandMode.AV1.name).orEmpty())
        }.getOrDefault(SonyCommandMode.AV1),
    )
        private set

    var hapticsEnabled by mutableStateOf(preferences.getBoolean(KEY_HAPTICS, true))
        private set

    var themePreference by mutableStateOf(
        runCatching {
            ThemePreference.valueOf(preferences.getString(KEY_THEME, ThemePreference.SYSTEM.name).orEmpty())
        }.getOrDefault(ThemePreference.SYSTEM),
    )
        private set

    var webOsHost by mutableStateOf(preferences.getString(KEY_WEBOS_HOST, "").orEmpty())
        private set

    fun updateSonyMode(value: SonyCommandMode) {
        sonyMode = value
        preferences.edit().putString(KEY_SONY_MODE, value.name).apply()
    }

    fun updateHapticsEnabled(value: Boolean) {
        hapticsEnabled = value
        preferences.edit().putBoolean(KEY_HAPTICS, value).apply()
    }

    fun updateThemePreference(value: ThemePreference) {
        themePreference = value
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    fun updateWebOsHost(value: String) {
        webOsHost = value.trim()
        preferences.edit().putString(KEY_WEBOS_HOST, webOsHost).apply()
    }

    fun getWebOsClientKey(): String = preferences.getString(KEY_WEBOS_CLIENT_KEY, "").orEmpty()

    fun setWebOsClientKey(value: String) {
        preferences.edit().putString(KEY_WEBOS_CLIENT_KEY, value).apply()
    }

    fun clearWebOsPairing() {
        preferences.edit().remove(KEY_WEBOS_CLIENT_KEY).apply()
    }

    private companion object {
        const val KEY_SONY_MODE = "sony_mode"
        const val KEY_HAPTICS = "haptics"
        const val KEY_THEME = "theme"
        const val KEY_WEBOS_HOST = "webos_host"
        const val KEY_WEBOS_CLIENT_KEY = "webos_client_key"
    }
}
