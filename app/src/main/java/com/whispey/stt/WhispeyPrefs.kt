package com.whispey.stt

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object WhispeyPrefs {
    private const val PREFS_NAME = "whispey_secure_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_AUDIO_DEVICE_ID = "audio_device_id"
    private const val KEY_AUDIO_DEVICE_NAME = "audio_device_name"
    private const val KEY_AUDIO_DEVICE_TYPE = "audio_device_type"

    // Sentinel for "no specific type" (used by the System default option).
    const val AUDIO_TYPE_NONE = 0

    // Theme mode values
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    // Special audio device value: follow the system default input.
    const val AUDIO_DEVICE_DEFAULT = -1

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

    // ---- Theme ----

    fun themeMode(context: Context): String =
        encrypted(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    fun saveThemeMode(context: Context, mode: String) {
        encrypted(context).edit().putString(KEY_THEME, mode).apply()
    }

    /** Maps the saved theme mode to an AppCompatDelegate night-mode constant. */
    fun nightModeFor(mode: String): Int = when (mode) {
        THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themeMode(context)))
    }

    // ---- Preferred audio input device ----

    /**
     * Returns the persisted preferred input device id, or [AUDIO_DEVICE_DEFAULT]
     * when the user wants the system default microphone.
     *
     * NOTE: AudioDeviceInfo ids are NOT stable across reconnects/reboots, so we
     * additionally persist the device's product name and match on that at runtime.
     */
    fun preferredAudioDeviceId(context: Context): Int =
        encrypted(context).getInt(KEY_AUDIO_DEVICE_ID, AUDIO_DEVICE_DEFAULT)

    fun preferredAudioDeviceName(context: Context): String =
        encrypted(context).getString(KEY_AUDIO_DEVICE_NAME, null).orEmpty()

    /**
     * The AudioDeviceInfo.type of the saved device. Unlike the id, the type is
     * stable across disconnect/reconnect, so it is the primary key we match on.
     */
    fun preferredAudioDeviceType(context: Context): Int =
        encrypted(context).getInt(KEY_AUDIO_DEVICE_TYPE, AUDIO_TYPE_NONE)

    fun savePreferredAudioDevice(context: Context, deviceId: Int, deviceType: Int, deviceName: String) {
        encrypted(context).edit()
            .putInt(KEY_AUDIO_DEVICE_ID, deviceId)
            .putInt(KEY_AUDIO_DEVICE_TYPE, deviceType)
            .putString(KEY_AUDIO_DEVICE_NAME, deviceName)
            .apply()
    }

    fun hasPreferredAudioDevice(context: Context): Boolean =
        preferredAudioDeviceType(context) != AUDIO_TYPE_NONE
}
