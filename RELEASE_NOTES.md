# Whispey STT v1.1.0

- Added a **theme setting** (Light / Dark / Follow system). All screens and the keyboard are now theme-aware.
- Added a **microphone source** setting. The chosen input (e.g. a Bluetooth or USB headset mic) is remembered and matched by device type, so it reconnects automatically and falls back to the phone microphone when disconnected.
- Added a **backspace key** to the keyboard (top-right): tap deletes one character, long-press auto-repeats.
- Added an **enter key** (bottom-right) that honors the text field's IME action (Send / Search / Go) or inserts a newline.
- Rearranged the keyboard buttons with consistent sizing and alignment.

# Whispey STT v1.0.1

- Changed transcription model from `whisper-1` to `gpt-4o-mini-transcribe`.
- Added transcription prompt: `Multilingual speech. Preserve all languages as spoken, do not translate.`
- Rebuilt the downloadable APK as `whispey_tts.apk`.
