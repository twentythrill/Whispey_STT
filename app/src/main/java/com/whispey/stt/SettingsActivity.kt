package com.whispey.stt

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(com.whispey.stt.R.string.settings_title)
        setContentView(createContent())
    }

    private fun createContent(): LinearLayout {
        val apiKeyInput = EditText(this).apply {
            setText(WhispeyPrefs.apiKey(this@SettingsActivity))
            hint = "OpenAI API key"
            setHintTextColor(Color.rgb(130, 130, 130))
            setTextColor(Color.rgb(224, 224, 224))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundColor(Color.rgb(30, 30, 30))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(17, 17, 17))

            addView(TextView(this@SettingsActivity).apply {
                text = "Whispey STT"
                setTextColor(Color.rgb(224, 224, 224))
                textSize = 24f
            })

            addView(apiKeyInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) })

            addView(Button(this@SettingsActivity).apply {
                text = "Save API key"
                setTextColor(Color.WHITE)
                setOnClickListener {
                    WhispeyPrefs.saveApiKey(this@SettingsActivity, apiKeyInput.text.toString())
                    Toast.makeText(this@SettingsActivity, "Saved", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(22), 0, 0)

                addView(TextView(this@SettingsActivity).apply {
                    text = "Haptic feedback"
                    setTextColor(Color.rgb(224, 224, 224))
                    textSize = 16f
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                addView(Switch(this@SettingsActivity).apply {
                    isChecked = WhispeyPrefs.hapticsEnabled(this@SettingsActivity)
                    setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                        WhispeyPrefs.saveHapticsEnabled(this@SettingsActivity, checked)
                    }
                })
            })

            addView(TextView(this@SettingsActivity).apply {
                text = "Version ${BuildConfig.VERSION_NAME}"
                setTextColor(Color.rgb(150, 150, 150))
                textSize = 13f
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(32) })
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
