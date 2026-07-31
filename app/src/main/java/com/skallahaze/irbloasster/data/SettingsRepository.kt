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

    private var currentSonyMode by mutableStateOf(
        runCatching {
            SonyCommandMode.valueOf(preferences.getString(KEY_SONY_MODE, SonyCommandMode.AV1.name).orEmpty())
        }.getOrDefault(SonyCommandMode.AV1),
    )
    val sonyMode: SonyCommandMode
        get() = currentSonyMode

    private var currentHapticsEnabled by mutableStateOf(preferences.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: Boolean
        get() = currentHapticsEnabled

    private var currentThemePreference by mutableStateOf(
        runCatching {
            ThemePreference.valueOf(preferences.getString(KEY_THEME, ThemePreference.SYSTEM.name).orEmpty())
        }.getOrDefault(ThemePreference.SYSTEM),
    )
    val themePreference: ThemePreference
        get() = currentThemePreference

    private var currentWebOsHost by mutableStateOf(preferences.getString(KEY_WEBOS_HOST, "").orEmpty())
    val webOsHost: String
        get() = currentWebOsHost

    fun setSonyMode(value: SonyCommandMode) {
        currentSonyMode = value
        preferences.edit().putString(KEY_SONY_MODE, value.name).apply()
    }

    fun setHapticsEnabled(value: Boolean) {
        currentHapticsEnabled = value
        preferences.edit().putBoolean(KEY_HAPTICS, value).apply()
    }

    fun setThemePreference(value: ThemePreference) {
        currentThemePreference = value
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    fun setWebOsHost(value: String) {
        currentWebOsHost = value.trim()
        preferences.edit().putString(KEY_WEBOS_HOST, currentWebOsHost).apply()
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
