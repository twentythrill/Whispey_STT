# Whispey STT v1.2.0

### 🚀 Dual Transcription Backends (Local Orukeet + Cloud Whisper)
- **Local Orukeet (Offline):** Integrated the 25-language FastConformer-TDT 0.6B v3 model exported to INT8 ONNX and powered by `sherpa-onnx`. Zero internet required, complete privacy, zero cloud latency.
- **Top-Center Toggle Switch:** Minimal, high-contrast monochrome switch at the top center of the keyboard to toggle between Local Orukeet (microchip icon) and Cloud Whisper (cloud icon). Can be toggled mid-recording without interrupting audio capture.

### 🧹 Vocal Hesitation & Filler Word Suppression
- Strips standalone hesitations in English and Italian (`um`, `uh`, `uhm`, `umm`, `ehm`, `emm`, `erm`, `ahm`, `uhh`, `ehh`, `hmm`, `hm`, `mm`, `mmm`) with word boundary precision.
- Cleans trailing commas, fixes spacing before punctuation, and restores sentence-start capitalization.
- Built-in Whisper prompt biasing for native hesitation suppression.

### 📖 Custom Vocabulary & Phonetic Replacement
- **Two-Stage Engine:**
  - **Stage A (Explicit Aliases):** Replaces recurring phonetic misheard phrases (`alias1, alias2 -> TargetWord`) with multi-word precedence.
  - **Stage B (Automatic Phonetic & Fuzzy Matching):** Automatically matches phonetically similar speech for custom standalone terms (≥5 characters) over 1–3 word sliding windows.
- **Settings Manager:** Add, view, delete custom vocabulary terms or bulk-edit all entries as plain text.
- **Whisper Glossary Biasing:** Automatically prompts Whisper with custom vocabulary words within safe token limits.

### ⚡ Performance & Memory Optimization
- Direct-to-disk 16 kHz mono WAV recording in `cacheDir` (zero memory bloat during long recordings).
- Thread-safe native JNI lifecycle protected by a single Kotlin mutex.
- 45-second inference windowing for long audio recordings to keep peak RAM within 8 GB device safety budgets.
- Automatic 5-second native memory release timer when keyboard is closed.

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
