package com.whispey.stt

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class WhispeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
