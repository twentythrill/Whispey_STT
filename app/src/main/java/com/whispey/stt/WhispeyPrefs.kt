package com.whispey.stt

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object WhispeyPrefs {
    private const val PREFS_NAME = "whispey_secure_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_HAPTICS = "haptics"

    fun encrypted(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun apiKey(context: Context): String =
        encrypted(context).getString(KEY_API_KEY, null).orEmpty().trim()

    fun saveApiKey(context: Context, apiKey: String) {
        encrypted(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun hapticsEnabled(context: Context): Boolean =
        encrypted(context).getBoolean(KEY_HAPTICS, true)

    fun saveHapticsEnabled(context: Context, enabled: Boolean) {
        encrypted(context).edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }
}
