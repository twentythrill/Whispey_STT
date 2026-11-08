package com.whispey.stt

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    /**
     * Backing data for the audio-source spinner.
     * id == AUDIO_DEVICE_DEFAULT and type == AUDIO_TYPE_NONE means "system default".
     * connected == false marks a saved-but-currently-absent device.
     */
    private data class AudioOption(
        val id: Int,
        val type: Int,
        val name: String,
        val connected: Boolean = true
    )

    private var audioOptions: List<AudioOption> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.settings_title)
        setContentView(createContent())
    }

    private fun createContent(): View {
        val ctx = this
        val textPrimary = WhispeyTheme.textPrimary(ctx)
        val textSecondary = WhispeyTheme.textSecondary(ctx)
        val surface = WhispeyTheme.surface(ctx)

        val apiKeyInput = EditText(this).apply {
            setText(WhispeyPrefs.apiKey(ctx))
            hint = "OpenAI API key"
            setHintTextColor(WhispeyTheme.hint(ctx))
            setTextColor(textPrimary)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundColor(surface)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(WhispeyTheme.background(ctx))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
        }
        scrollView.addView(root)

        root.addView(TextView(this).apply {
            text = "Whispey STT"
            setTextColor(textPrimary)
            textSize = 24f
        })

        // ---- Orukeet Local Offline Engine ----
        root.addView(sectionLabel("Local Engine (Orukeet)"))
        root.addView(TextView(this).apply {
            text = "Offline multilingual FastConformer-TDT model (sherpa-onnx). Runs locally on-device without internet or API key."
            setTextColor(textSecondary)
            textSize = 13f
        }, matchWidth().apply { topMargin = dp(4) })

        val modelDir = OrukeetEngine.getModelDirectory(ctx)
        val modelsPresent = OrukeetEngine.areModelFilesPresent(ctx)

        val statusView = TextView(this).apply {
            if (modelsPresent) {
                text = "● Model detected & ready"
                setTextColor(0xFF4CAF50.toInt()) // Soft green
            } else {
                text = "● Model files missing"
                setTextColor(WhispeyTheme.error(ctx))
            }
            textSize = 14f
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(statusView, matchWidth())

        root.addView(TextView(this).apply {
            text = "Location: ${modelDir.absolutePath}"
            setTextColor(textSecondary)
            textSize = 11f
        }, matchWidth())

        // ---- Cloud Whisper Engine ----
        root.addView(sectionLabel("Cloud Engine (OpenAI Whisper)"))
        root.addView(TextView(this).apply {
            text = "Transcribes speech via OpenAI Whisper cloud API."
            setTextColor(textSecondary)
            textSize = 13f
        }, matchWidth().apply { topMargin = dp(4) })

        root.addView(apiKeyInput, matchWidth().apply { topMargin = dp(8) })

        root.addView(Button(this).apply {
            text = "Save API key"
            setOnClickListener {
                WhispeyPrefs.saveApiKey(ctx, apiKeyInput.text.toString())
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth().apply { topMargin = dp(8) })

        // ---- Custom Vocabulary & Phonetic Replacement ----
        root.addView(sectionLabel("Custom Vocabulary"))
        root.addView(TextView(this).apply {
            text = "Add custom words or sound-alike replacement rules. Standalone words (e.g. Orukeet, PsychAudio) are automatically matched phonetically. Explicit aliases can be defined as 'alias1, alias2 -> Target'."
            setTextColor(textSecondary)
            textSize = 13f
        }, matchWidth().apply { topMargin = dp(4) })

        val vocabListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun refreshVocabList() {
            vocabListContainer.removeAllViews()
            val currentRules = WhispeyPrefs.customVocabulary(ctx)
            if (currentRules.isEmpty()) {
                vocabListContainer.addView(TextView(ctx).apply {
                    text = "No custom vocabulary entries yet."
                    setTextColor(textSecondary)
                    textSize = 13f
                    setPadding(0, dp(8), 0, dp(8))
                })
                return
            }

            for (rule in currentRules) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(surface)
                    setPadding(dp(12), dp(8), dp(8), dp(8))
                }

                val textColumn = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                }

                textColumn.addView(TextView(ctx).apply {
                    text = rule.target
                    setTextColor(textPrimary)
                    textSize = 15f
                })

                if (rule.aliases.isNotEmpty()) {
                    textColumn.addView(TextView(ctx).apply {
                        text = "Aliases: ${rule.aliases.joinToString(", ")}"
                        setTextColor(textSecondary)
                        textSize = 12f
                    })
                } else {
                    textColumn.addView(TextView(ctx).apply {
                        text = "Auto fuzzy & phonetic matching (≥5 chars)"
                        setTextColor(textSecondary)
                        textSize = 11f
                    })
                }

                row.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                val deleteBtn = Button(ctx).apply {
                    text = "✕"
                    setTextColor(WhispeyTheme.error(ctx))
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    textSize = 16f
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        val updated = WhispeyPrefs.customVocabulary(ctx).filterNot {
                            it.target.equals(rule.target, ignoreCase = true) && it.aliases == rule.aliases
                        }
                        WhispeyPrefs.saveCustomVocabulary(ctx, updated)
                        refreshVocabList()
                    }
                }
                row.addView(deleteBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

                vocabListContainer.addView(row, matchWidth().apply { topMargin = dp(6) })
            }
        }

        val vocabInput = EditText(this).apply {
            hint = "e.g. Orukeet or cloud -> Claude"
            setHintTextColor(WhispeyTheme.hint(ctx))
            setTextColor(textPrimary)
            setSingleLine(true)
            setBackgroundColor(surface)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val addBtn = Button(this).apply {
            text = "Add"
            setOnClickListener {
                val inputStr = vocabInput.text.toString().trim()
                if (inputStr.isEmpty()) return@setOnClickListener
                val rule = CustomVocabularyManager.VocabularyRule.fromEntryString(inputStr)
                if (rule == null) {
                    Toast.makeText(ctx, "Invalid format", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val current = WhispeyPrefs.customVocabulary(ctx).toMutableList()
                current.removeAll { it.target.equals(rule.target, ignoreCase = true) }
                current.add(rule)
                WhispeyPrefs.saveCustomVocabulary(ctx, current)
                vocabInput.setText("")
                refreshVocabList()
            }
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(vocabInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(addBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            })
        }

        root.addView(inputRow, matchWidth().apply { topMargin = dp(8) })
        root.addView(vocabListContainer, matchWidth().apply { topMargin = dp(4) })

        val editAsTextBtn = Button(this).apply {
            text = "Bulk edit as text"
            setOnClickListener {
                showBulkEditDialog(ctx) { refreshVocabList() }
            }
        }
        root.addView(editAsTextBtn, matchWidth().apply { topMargin = dp(6) })

        refreshVocabList()

        // ---- Theme dropdown ----
        root.addView(sectionLabel("Theme"))
        root.addView(buildThemeSpinner(), matchWidth().apply { topMargin = dp(6) })

        // ---- Audio source dropdown ----
        root.addView(sectionLabel("Microphone source"))
        root.addView(buildAudioSpinner(), matchWidth().apply { topMargin = dp(6) })
        root.addView(TextView(this).apply {
            text = "The selected device is remembered. When it's disconnected, " +
                "Whispey falls back to the phone microphone automatically."
            setTextColor(textSecondary)
            textSize = 12f
        }, matchWidth().apply { topMargin = dp(6) })

        // ---- Haptics switch ----
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(22), 0, 0)

            addView(TextView(ctx).apply {
                text = "Haptic feedback"
                setTextColor(textPrimary)
                textSize = 16f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(Switch(ctx).apply {
                isChecked = WhispeyPrefs.hapticsEnabled(ctx)
                setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                    WhispeyPrefs.saveHapticsEnabled(ctx, checked)
                }
            })
        })

        root.addView(TextView(this).apply {
            text = "Version ${BuildConfig.VERSION_NAME}"
            setTextColor(textSecondary)
            textSize = 13f
        }, matchWidth().apply { topMargin = dp(32) })

        return scrollView
    }

    private fun showBulkEditDialog(context: Context, onSaved: () -> Unit) {
        val currentRules = WhispeyPrefs.customVocabulary(context)
        val initialText = currentRules.joinToString("\n") { it.toEntryString() }

        val editText = EditText(context).apply {
            setText(initialText)
            setTextColor(WhispeyTheme.textPrimary(context))
            setHintTextColor(WhispeyTheme.hint(context))
            hint = "One entry per line:\nTargetWord\nalias1, alias2 -> TargetWord"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            setBackgroundColor(WhispeyTheme.surface(context))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val container = LinearLayout(context).apply {
            setPadding(dp(20), dp(12), dp(20), dp(12))
            addView(editText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        AlertDialog.Builder(context)
            .setTitle("Custom Vocabulary")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val lines = editText.text.toString().lines()
                val parsed = lines.mapNotNull {
                    CustomVocabularyManager.VocabularyRule.fromEntryString(it)
                }
                WhispeyPrefs.saveCustomVocabulary(context, parsed)
                Toast.makeText(context, "Vocabulary saved", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildThemeSpinner(): Spinner {
        val ctx = this
        val labels = listOf("Follow system", "Light", "Dark")
        val modes = listOf(WhispeyPrefs.THEME_SYSTEM, WhispeyPrefs.THEME_LIGHT, WhispeyPrefs.THEME_DARK)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        return Spinner(this).apply {
            this.adapter = adapter
            setSelection(modes.indexOf(WhispeyPrefs.themeMode(ctx)).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val mode = modes[position]
                    if (mode == WhispeyPrefs.themeMode(ctx)) return
                    WhispeyPrefs.saveThemeMode(ctx, mode)
                    AppCompatDelegate.setDefaultNightMode(WhispeyPrefs.nightModeFor(mode))
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun buildAudioSpinner(): Spinner {
        val ctx = this
        audioOptions = listInputDevices()
        val labels = audioOptions.map { it.name }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val savedType = WhispeyPrefs.preferredAudioDeviceType(ctx)
        // Select the saved device by its stable type. A reconnected device of the
        // same type is matched here automatically (no manual reselection needed).
        val startIndex = if (savedType == WhispeyPrefs.AUDIO_TYPE_NONE) {
            0
        } else {
            audioOptions.indexOfFirst { it.type == savedType }.takeIf { it >= 0 } ?: 0
        }

        return Spinner(this).apply {
            this.adapter = adapter
            setSelection(startIndex)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val option = audioOptions.getOrNull(position) ?: return
                    // Don't overwrite the saved preference with a stale "(disconnected)"
                    // placeholder selection.
                    if (!option.connected) return
                    val baseName = option.name.removeSuffix(" (disconnected)")
                    WhispeyPrefs.savePreferredAudioDevice(ctx, option.id, option.type, baseName)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    /** Returns the default option plus any currently-connected input devices. */
    private fun listInputDevices(): List<AudioOption> {
        val options = mutableListOf(
            AudioOption(WhispeyPrefs.AUDIO_DEVICE_DEFAULT, WhispeyPrefs.AUDIO_TYPE_NONE, "System default")
        )
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        for (device in devices) {
            if (!device.isSource) continue
            options.add(AudioOption(device.id, device.type, labelFor(device)))
        }

        // If a device was saved but is not currently connected (no live input of
        // that type), show it as a disconnected entry so the selection stays
        // visible. When the device reconnects, a live option of the same type is
        // already present above, so we do NOT add a duplicate.
        val savedType = WhispeyPrefs.preferredAudioDeviceType(this)
        if (savedType != WhispeyPrefs.AUDIO_TYPE_NONE && options.none { it.type == savedType }) {
            val savedName = WhispeyPrefs.preferredAudioDeviceName(this)
            val baseName = savedName.removeSuffix(" (disconnected)")
            options.add(
                AudioOption(
                    WhispeyPrefs.preferredAudioDeviceId(this),
                    savedType,
                    "$baseName (disconnected)",
                    connected = false
                )
            )
        }
        return options
    }

    private fun labelFor(device: AudioDeviceInfo): String {
        val product = device.productName?.toString()?.trim().orEmpty()
        val typeName = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone microphone"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB microphone"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
            else -> "Microphone"
        }
        return if (product.isNotEmpty() && !product.equals(typeName, ignoreCase = true)) {
            "$typeName ($product)"
        } else {
            typeName
        }
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(WhispeyTheme.textPrimary(this@SettingsActivity))
        textSize = 16f
        setPadding(0, dp(22), 0, 0)
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
