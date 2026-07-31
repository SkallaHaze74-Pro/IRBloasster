package com.skallahaze.irbloasster.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putString(key: String, value: String?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    fun getString(key: String, defaultValue: String = ""): String =
        preferences.getString(key, defaultValue) ?: defaultValue

    fun putEncryptedString(key: String, value: String?) {
        if (value == null) {
            preferences.edit().remove(key).apply()
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val packed = cipher.iv + encrypted
        preferences.edit()
            .putString(key, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun getEncryptedString(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_LENGTH_BYTES)
            val iv = packed.copyOfRange(0, IV_LENGTH_BYTES)
            val encrypted = packed.copyOfRange(IV_LENGTH_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "living_room_controller"
        private const val KEY_ALIAS = "living_room_controller_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12

        const val KEY_TV_IP = "tv_ip"
        const val KEY_TV_MAC = "tv_mac"
        const val KEY_TV_CLIENT_KEY = "tv_client_key"
        const val KEY_TV_CERT_FINGERPRINT_PREFIX = "tv_cert_"
        const val KEY_PREFERRED_INPUT = "preferred_input"
        const val KEY_SONY_PROFILE_INDEX = "sony_profile_index"
    }
}
