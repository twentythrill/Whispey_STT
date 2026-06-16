package com.whispey.stt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            finish()
        } else {
            showDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            setBackgroundColor(WhispeyTheme.background(this@PermissionActivity))
            setTextColor(WhispeyTheme.textPrimary(this@PermissionActivity))
            gravity = Gravity.CENTER
            text = "Microphone permission is required."
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
        } else {
            permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Microphone permission denied")
            .setMessage("Whispey STT cannot record or transcribe speech without microphone permission.")
            .setPositiveButton("Try again") { _, _ -> permissionRequest.launch(Manifest.permission.RECORD_AUDIO) }
            .setNegativeButton("Close") { _, _ -> finish() }
            .show()
    }
}
