package com.whispey.stt

import android.app.Application

class WhispeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply the user's saved theme preference (dark / light / follow system).
        WhispeyPrefs.applyTheme(this)
    }
}
