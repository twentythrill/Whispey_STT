package com.whispey.stt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) showPermissionExplanation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContentView(createContent())
    }

    private fun createContent(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(17, 17, 17))

            addView(TextView(this@MainActivity).apply {
                text = "Whispey STT"
                setTextColor(Color.rgb(224, 224, 224))
                textSize = 26f
                gravity = Gravity.CENTER
            })

            addView(Button(this@MainActivity).apply {
                text = "Open keyboard settings"
                setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            })

            addView(Button(this@MainActivity).apply {
                text = "Open app settings"
                setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
            })
        }
    }

    private fun showPermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Microphone permission required")
            .setMessage("Whispey STT needs microphone access to record speech before sending it to Whisper.")
            .setPositiveButton("Try again") { _, _ -> permissionRequest.launch(Manifest.permission.RECORD_AUDIO) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
