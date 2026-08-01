package com.skallahaze.irbloasster.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skallahaze.irbloasster.BuildConfig
import com.skallahaze.irbloasster.ir.SonyCommandMode
import com.skallahaze.irbloasster.ir.Sony_STR_DB870
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ThemePreference(val title: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
}

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        SETTINGS_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val securePreferences = appContext.getSharedPreferences(
        SECURE_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val secureStore = SecureStringStore()

    private val storedSonyMode = runCatching {
        SonyCommandMode.valueOf(
            preferences.getString(KEY_SONY_MODE, SonyCommandMode.AV1.name).orEmpty(),
        )
    }.getOrDefault(SonyCommandMode.AV1)

    private var currentSonyMode by mutableStateOf(
        Sony_STR_DB870.effectiveMode(storedSonyMode),
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

    private var currentWebOsMac by mutableStateOf(preferences.getString(KEY_WEBOS_MAC, "").orEmpty())
    val webOsMac: String
        get() = currentWebOsMac

    private var currentAutoConnect by mutableStateOf(preferences.getBoolean(KEY_AUTO_CONNECT, true))
    val autoConnect: Boolean
        get() = currentAutoConnect

    private var currentLastBackupEpochMillis by mutableStateOf(
        preferences.getLong(KEY_LAST_BACKUP_EPOCH_MILLIS, 0L),
    )
    val lastBackupEpochMillis: Long
        get() = currentLastBackupEpochMillis

    private var currentLastImportEpochMillis by mutableStateOf(
        preferences.getLong(KEY_LAST_IMPORT_EPOCH_MILLIS, 0L),
    )
    val lastImportEpochMillis: Long
        get() = currentLastImportEpochMillis

    init {
        migrateSecurePreferences()

        // Migrate old SmartIR builds that may have stored AV2. The photographed
        // STR-DB870 CEL variant has no selectable receiver COMMAND MODE.
        if (storedSonyMode != currentSonyMode) {
            preferences.edit().putString(KEY_SONY_MODE, currentSonyMode.name).apply()
        }
    }

    fun setSonyMode(value: SonyCommandMode) {
        val effective = Sony_STR_DB870.effectiveMode(value)
        currentSonyMode = effective
        preferences.edit().putString(KEY_SONY_MODE, effective.name).apply()
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
            .removePrefix("ws://")
            .removePrefix("wss://")
            .substringBefore('/')
            .removeSuffix(":3000")
            .removeSuffix(":3001")
        preferences.edit().putString(KEY_WEBOS_HOST, currentWebOsHost).apply()
    }

    fun setWebOsMac(value: String) {
        currentWebOsMac = value.trim().uppercase()
        preferences.edit().putString(KEY_WEBOS_MAC, currentWebOsMac).apply()
    }

    fun setAutoConnect(value: Boolean) {
        currentAutoConnect = value
        preferences.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()
    }

    fun exportBackup(): String {
        val now = System.currentTimeMillis()
        val payload = SmartIrBackupCodec.encode(
            SmartIrBackupSnapshot(
                exportedAtEpochMillis = now,
                appVersionName = BuildConfig.VERSION_NAME,
                packageName = appContext.packageName,
                themePreference = currentThemePreference.name,
                hapticsEnabled = currentHapticsEnabled,
                autoConnect = currentAutoConnect,
                webOsHost = currentWebOsHost,
                webOsMac = currentWebOsMac,
                sonyMode = currentSonyMode.name,
                webOsCertificateFingerprint = getWebOsCertificateFingerprint(),
                webOsClientKeyWasPresent = hasWebOsClientKey(),
            ),
        )

        currentLastBackupEpochMillis = now
        preferences.edit().putLong(KEY_LAST_BACKUP_EPOCH_MILLIS, now).apply()
        return payload
    }

    fun importBackup(payload: String): SmartIrImportResult {
        val snapshot = SmartIrBackupCodec.decode(payload)

        val theme = ThemePreference.entries.firstOrNull {
            it.name == snapshot.themePreference
        } ?: ThemePreference.SYSTEM
        val sonyMode = SonyCommandMode.entries.firstOrNull {
            it.name == snapshot.sonyMode
        } ?: SonyCommandMode.AV1

        setThemePreference(theme)
        setHapticsEnabled(snapshot.hapticsEnabled)
        setAutoConnect(snapshot.autoConnect)
        setWebOsHost(snapshot.webOsHost)
        setWebOsMac(snapshot.webOsMac)
        setSonyMode(sonyMode)

        // Never overwrite a working local pairing. On a fresh installation the
        // known certificate may be restored, but the client key must be paired
        // again because it is intentionally excluded from portable backups.
        if (!hasWebOsClientKey() && snapshot.webOsCertificateFingerprint.isNotBlank()) {
            setWebOsCertificateFingerprint(snapshot.webOsCertificateFingerprint)
        }

        val now = System.currentTimeMillis()
        currentLastImportEpochMillis = now
        preferences.edit().putLong(KEY_LAST_IMPORT_EPOCH_MILLIS, now).apply()

        val repairingRecommended = snapshot.webOsClientKeyWasPresent && !hasWebOsClientKey()
        val message = if (repairingRecommended) {
            "Backup importiert. TV-IP, MAC und App-Einstellungen sind wieder da; den LG-TV bitte einmal neu koppeln."
        } else {
            "SmartIR-Backup erfolgreich importiert."
        }

        return SmartIrImportResult(
            message = message,
            tvRepairingRecommended = repairingRecommended,
        )
    }

    fun getWebOsClientKey(): String {
        val encrypted = securePreferences.getString(KEY_WEBOS_CLIENT_KEY_SECURE, "").orEmpty()
        if (encrypted.isNotBlank()) {
            secureStore.decrypt(encrypted)?.let { return it }
        }

        val legacy = securePreferences.getString(KEY_WEBOS_CLIENT_KEY, "").orEmpty()
        if (legacy.isNotBlank()) {
            setWebOsClientKey(legacy)
        }
        return legacy
    }

    fun setWebOsClientKey(value: String) {
        if (value.isBlank()) {
            securePreferences.edit()
                .remove(KEY_WEBOS_CLIENT_KEY_SECURE)
                .remove(KEY_WEBOS_CLIENT_KEY)
                .apply()
            return
        }

        val encrypted = secureStore.encrypt(value)
        if (encrypted != null) {
            securePreferences.edit()
                .putString(KEY_WEBOS_CLIENT_KEY_SECURE, encrypted)
                .remove(KEY_WEBOS_CLIENT_KEY)
                .apply()
        } else {
            // Very old or vendor-broken keystores still keep the app usable.
            securePreferences.edit().putString(KEY_WEBOS_CLIENT_KEY, value).apply()
        }
    }

    fun isWebOsClientKeyEncrypted(): Boolean =
        securePreferences.getString(KEY_WEBOS_CLIENT_KEY_SECURE, "").orEmpty().isNotBlank()

    fun hasWebOsClientKey(): Boolean =
        securePreferences.getString(KEY_WEBOS_CLIENT_KEY_SECURE, "").orEmpty().isNotBlank() ||
            securePreferences.getString(KEY_WEBOS_CLIENT_KEY, "").orEmpty().isNotBlank()

    fun getWebOsCertificateFingerprint(): String =
        securePreferences.getString(KEY_WEBOS_CERTIFICATE, "").orEmpty()

    fun setWebOsCertificateFingerprint(value: String) {
        securePreferences.edit().putString(KEY_WEBOS_CERTIFICATE, value.trim()).apply()
    }

    fun clearWebOsPairing() {
        securePreferences.edit()
            .remove(KEY_WEBOS_CLIENT_KEY_SECURE)
            .remove(KEY_WEBOS_CLIENT_KEY)
            .remove(KEY_WEBOS_CERTIFICATE)
            .apply()
    }

    private fun migrateSecurePreferences() {
        val secureEditor = securePreferences.edit()
        val generalEditor = preferences.edit()
        var changed = false

        listOf(
            KEY_WEBOS_CLIENT_KEY_SECURE,
            KEY_WEBOS_CLIENT_KEY,
            KEY_WEBOS_CERTIFICATE,
        ).forEach { key ->
            if (preferences.contains(key)) {
                val value = preferences.getString(key, null)
                if (!securePreferences.contains(key) && value != null) {
                    secureEditor.putString(key, value)
                }
                generalEditor.remove(key)
                changed = true
            }
        }

        if (changed) {
            secureEditor.apply()
            generalEditor.apply()
        }
    }

    private companion object {
        const val SETTINGS_PREFERENCES = "smart_ir_settings"
        const val SECURE_PREFERENCES = "smart_ir_secure"

        const val KEY_SONY_MODE = "sony_mode"
        const val KEY_HAPTICS = "haptics"
        const val KEY_THEME = "theme"
        const val KEY_WEBOS_HOST = "webos_host"
        const val KEY_WEBOS_MAC = "webos_mac"
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_LAST_BACKUP_EPOCH_MILLIS = "last_backup_epoch_millis"
        const val KEY_LAST_IMPORT_EPOCH_MILLIS = "last_import_epoch_millis"

        const val KEY_WEBOS_CLIENT_KEY = "webos_client_key"
        const val KEY_WEBOS_CLIENT_KEY_SECURE = "webos_client_key_secure"
        const val KEY_WEBOS_CERTIFICATE = "webos_certificate_fingerprint"
    }
}

private class SecureStringStore {
    fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        "$VERSION:$iv:$payload"
    }.getOrNull()

    fun decrypt(encoded: String): String? = runCatching {
        val parts = encoded.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == VERSION) { "Unknown secure value format" }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val payload = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(payload).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "smartir_webos_client_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION = "v1"
    }
}
