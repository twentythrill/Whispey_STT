package com.whispey.stt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class WhispeyImeService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var recorder: InMemoryAudioRecorder
    private val whisperClient = OpenAiWhisperClient()
    private var state = State.Idle
    private var circleView: CircleRecordView? = null
    private var errorView: TextView? = null

    private enum class State {
        Idle,
        Recording,
        Processing
    }

    override fun onCreate() {
        super.onCreate()
        recorder = InMemoryAudioRecorder(this)
    }

    override fun onCreateInputView(): View {
        val root = object : FrameLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(KEYBOARD_HEIGHT_PX, MeasureSpec.EXACTLY)
                )
            }
        }.apply {
            setBackgroundColor(Color.rgb(17, 17, 17))
            minimumHeight = KEYBOARD_HEIGHT_PX
        }

        val circle = CircleRecordView(this).apply {
            setOnClickListener { onRecordTapped() }
        }
        circleView = circle

        val error = TextView(this).apply {
            setTextColor(Color.rgb(196, 106, 106))
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            maxLines = 2
        }
        errorView = error

        root.addView(
            circle,
            FrameLayout.LayoutParams(
                dp(CIRCLE_SIZE_DP),
                dp(CIRCLE_SIZE_DP),
                Gravity.CENTER
            )
        )

        root.addView(
            error,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = (KEYBOARD_HEIGHT_PX / 2) + (dp(CIRCLE_SIZE_DP) / 2) + dp(14)
                leftMargin = dp(24)
                rightMargin = dp(24)
            }
        )

        val back = ImageButton(this).apply {
            setImageResource(resources.getIdentifier("ic_keyboard_back", "drawable", packageName))
            setColorFilter(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Previous keyboard"
            alpha = 0.72f
            setOnClickListener { switchToPreviousKeyboard() }
        }
        root.addView(
            back,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(8)
                leftMargin = dp(8)
            }
        )

        val settings = ImageButton(this).apply {
            setImageResource(resources.getIdentifier("ic_settings", "drawable", packageName))
            setColorFilter(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Settings"
            alpha = 0.72f
            setOnClickListener { openSettings() }
        }
        root.addView(
            settings,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                rightMargin = dp(8)
            }
        )

        return root
    }

    override fun onDestroy() {
        recorder.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun onRecordTapped() {
        when (state) {
            State.Idle -> startRecording()
            State.Recording -> stopAndTranscribe()
            State.Processing -> Unit
        }
    }

    private fun startRecording() {
        hideError()
        if (!hasAudioPermission()) {
            openPermissionActivity()
            showError("Microphone permission is required.")
            return
        }
        if (WhispeyPrefs.apiKey(this).isBlank()) {
            openSettings()
            showError("Add your OpenAI API key in settings.")
            return
        }

        runCatching {
            maybeHaptic()
            recorder.start()
            setState(State.Recording)
        }.onFailure {
            showError(it.message ?: "Could not start microphone.")
            setState(State.Idle)
        }
    }

    private fun stopAndTranscribe() {
        maybeHaptic()
        setState(State.Processing)
        val wavBytes = recorder.stop()
        if (wavBytes.size <= WAV_HEADER_SIZE) {
            setState(State.Idle)
            return
        }
        if (!NetworkStatus.isOnline(this)) {
            showError("No internet connection.")
            setState(State.Idle)
            return
        }

        val apiKey = WhispeyPrefs.apiKey(this)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { whisperClient.transcribe(apiKey, wavBytes) }
            }

            result.onSuccess { text ->
                if (text.isNotBlank()) {
                    commitTranscription(text)
                }
            }.onFailure {
                showError(readableMessage(it))
            }
            setState(State.Idle)
        }
    }

    private fun commitTranscription(text: String) {
        val inputConnection: InputConnection? = currentInputConnection
        if (inputConnection == null) {
            showError("No active text field.")
            return
        }
        inputConnection.commitText("${text.trimEnd()} ", 1)
    }

    private fun setState(next: State) {
        state = next
        val circleState = when (next) {
            State.Idle -> CircleRecordView.State.Idle
            State.Recording -> CircleRecordView.State.Recording
            State.Processing -> CircleRecordView.State.Processing
        }
        circleView?.setState(circleState)
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openPermissionActivity() {
        startActivity(Intent(this, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun switchToPreviousKeyboard() {
        maybeHaptic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && switchToPreviousInputMethod()) {
            return
        }
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun showError(message: String) {
        errorView?.text = message
        errorView?.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorView?.visibility = View.INVISIBLE
        errorView?.text = ""
    }

    private fun readableMessage(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            throwable is IOException && message.isBlank() -> "Network request failed."
            message.isNotBlank() -> message
            else -> "Transcription failed."
        }
    }

    private fun maybeHaptic() {
        if (!WhispeyPrefs.hapticsEnabled(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(12L, SOFT_TAP_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12L)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEYBOARD_HEIGHT_PX = 818
        private const val CIRCLE_SIZE_DP = 132
        private const val SOFT_TAP_AMPLITUDE = 32
        private const val WAV_HEADER_SIZE = 44
    }
}
