# Whispey STT

## Directive

Build and maintain **Whispey STT**, an Android custom keyboard (Input Method Editor / IME) that has no traditional keys and performs voice-to-text transcription through the OpenAI Whisper API.

## Architecture

- Android app written in Kotlin.
- Gradle Kotlin DSL project scoped entirely to this folder.
- The IME is implemented by `WhispeyImeService`.
- Audio capture is handled by `AudioRecord`, using PCM 16-bit, 16 kHz, mono.
- Audio is buffered only in memory as PCM bytes, then encoded to WAV in memory before upload.
- Networking uses OkHttp multipart POST requests to `https://api.openai.com/v1/audio/transcriptions`.
- Async work uses Kotlin coroutines.
- The OpenAI API key is stored in `EncryptedSharedPreferences`.

## Requirements

- Min SDK: 26.
- Target SDK: 34.
- Model: `whisper-1`.
- Do not specify language, so Whisper auto-detects multilingual input.
- No audio files, cache files, MediaStore entries, local databases, or temp files.
- Use `getCurrentInputConnection().commitText(transcribedText, 1)` for insertion.
- Injected transcriptions should include exactly one trailing space after the trimmed text so consecutive recordings do not attach to the previous word.
- Handle null `InputConnection` gracefully.
- Runtime `RECORD_AUDIO` permission is mandatory before recording.
- Settings screen stores API key, haptic feedback setting, and shows app version.
- Haptic feedback requires `android.permission.VIBRATE`; keep the tap effect soft (`12ms`, low amplitude).
- Keyboard input view height is fixed at `818` pixels, with the circle centered in that space.
- Keep the circle centered independently from the error label; hidden or visible error text must not affect the circle's vertical position.
- The top-left keyboard button attempts to switch back to the previous input method; if Android cannot switch directly, it opens the input method picker.

## Build

Run from this folder:

```bash
./gradlew assembleDebug
```

If dependencies are not already cached locally, Gradle must be allowed to resolve project-scoped dependencies. Do not install global dependencies.

Current build notes:

- Android Gradle Plugin is pinned to `8.5.1` because the local wrapper is Gradle `8.7`.
- AndroidX Core/AppCompat/Activity versions are pinned to SDK-34-compatible releases so the project honors target/compile SDK 34.
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Known Edge Cases

- Android IME services cannot directly show runtime permission prompts. The service launches `PermissionActivity` when microphone permission is missing.
- If the user denies microphone permission, `PermissionActivity` shows a clear dark-mode explanation dialog.
- If the API key is missing, the IME opens the settings screen instead of recording.
- API errors are parsed from the OpenAI error response when possible and displayed below the circle.
- Empty transcription responses are ignored and the keyboard returns to idle.
