package com.whispey.stt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
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

    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeating = false
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            deleteOneCharacter()
            backspaceHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
        }
    }

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
        val ctx = themedContext()
        val root = object : FrameLayout(ctx) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(KEYBOARD_HEIGHT_PX, MeasureSpec.EXACTLY)
                )
            }
        }.apply {
            setBackgroundColor(WhispeyTheme.background(ctx))
            minimumHeight = KEYBOARD_HEIGHT_PX
        }

        val circle = CircleRecordView(ctx).apply {
            setOnClickListener { onRecordTapped() }
        }
        circleView = circle

        val error = TextView(ctx).apply {
            setTextColor(WhispeyTheme.error(ctx))
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

        val iconTint = WhispeyTheme.iconTint(ctx)

        // Top-left: switch to previous keyboard.
        val back = cornerButton(ctx, "ic_keyboard_back", iconTint, "Previous keyboard") {
            switchToPreviousKeyboard()
        }
        root.addView(back, cornerParams(Gravity.TOP or Gravity.START))

        // Top-right: backspace. Tap deletes one char, long-press auto-repeats.
        val backspace = cornerButton(ctx, "ic_backspace", iconTint, "Delete") {
            deleteOneCharacter()
        }.apply {
            setOnTouchListener { v, event -> handleBackspaceTouch(v, event) }
        }
        root.addView(backspace, cornerParams(Gravity.TOP or Gravity.END))

        // Bottom-left: settings.
        val settings = cornerButton(ctx, "ic_settings", iconTint, "Settings") {
            openSettings()
        }
        root.addView(settings, cornerParams(Gravity.BOTTOM or Gravity.START))

        // Bottom-right: enter / newline.
        val enter = cornerButton(ctx, "ic_enter", iconTint, "Enter") {
            commitEnter()
        }
        root.addView(enter, cornerParams(Gravity.BOTTOM or Gravity.END))

        return root
    }

    /**
     * Returns a Context whose configuration reflects the user's saved theme.
     * For "follow system" the service's own configuration is used as-is; for an
     * explicit light/dark choice we override the uiMode night bit so the keyboard
     * picks the right values/ and values-night/ resources.
     */
    private fun themedContext(): Context {
        val mode = WhispeyPrefs.themeMode(this)
        val nightFlag = when (mode) {
            WhispeyPrefs.THEME_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            WhispeyPrefs.THEME_DARK -> Configuration.UI_MODE_NIGHT_YES
            else -> return this
        }
        val overrideConfig = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightFlag
        }
        return createConfigurationContext(overrideConfig)
    }

    override fun onDestroy() {
        stopBackspaceRepeat()
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

    /** Builds a uniformly-sized, transparent corner icon button. */
    private fun cornerButton(
        ctx: Context,
        drawableName: String,
        tint: Int,
        description: String,
        onClick: () -> Unit
    ): ImageButton = ImageButton(ctx).apply {
        setImageResource(resources.getIdentifier(drawableName, "drawable", packageName))
        setColorFilter(tint)
        setBackgroundColor(Color.TRANSPARENT)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        contentDescription = description
        alpha = 0.72f
        setOnClickListener { onClick() }
    }

    /** Identical layout params for every corner so the four buttons line up. */
    private fun cornerParams(gravity: Int): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(dp(BTN_SIZE_DP), dp(BTN_SIZE_DP), gravity).apply {
            topMargin = dp(BTN_MARGIN_DP)
            bottomMargin = dp(BTN_MARGIN_DP)
            leftMargin = dp(BTN_MARGIN_DP)
            rightMargin = dp(BTN_MARGIN_DP)
        }

    private fun commitEnter() {
        maybeHaptic()
        val ic = currentInputConnection ?: return
        // Honor a specified IME action (Send, Search, Go, …) when the field asks
        // for one; otherwise insert a normal newline.
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(
            android.view.inputmethod.EditorInfo.IME_MASK_ACTION
        ) ?: android.view.inputmethod.EditorInfo.IME_ACTION_NONE

        val hasNoEnterAction = (editorInfo?.imeOptions?.and(
            android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION
        ) ?: 0) != 0

        if (!hasNoEnterAction && action != android.view.inputmethod.EditorInfo.IME_ACTION_NONE &&
            action != android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun deleteOneCharacter() {
        val ic = currentInputConnection ?: return
        // If there is a selection, delete it; otherwise delete the char before the cursor.
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleBackspaceTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maybeHaptic()
                startBackspaceRepeat()
            }
            MotionEvent.ACTION_UP -> {
                val wasRepeating = backspaceRepeating
                stopBackspaceRepeat()
                // A short tap (never entered auto-repeat) deletes exactly one char.
                // A long-press already deleted via the repeat loop, so skip the tap delete.
                if (!wasRepeating) {
                    view.performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> stopBackspaceRepeat()
        }
        return true
    }

    private fun startBackspaceRepeat() {
        backspaceRepeating = false
        // Wait for the long-press threshold before starting continuous delete.
        backspaceHandler.postDelayed({
            backspaceRepeating = true
            backspaceRunnable.run()
        }, BACKSPACE_REPEAT_DELAY_MS)
    }

    private fun stopBackspaceRepeat() {
        backspaceHandler.removeCallbacksAndMessages(null)
        backspaceRepeating = false
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
        private const val BACKSPACE_REPEAT_DELAY_MS = 350L
        private const val BACKSPACE_REPEAT_INTERVAL_MS = 55L
        private const val BTN_SIZE_DP = 52
        private const val BTN_MARGIN_DP = 10
    }
}
