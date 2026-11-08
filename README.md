# Whispey STT

Whispey STT is a minimal Android voice-to-text keyboard (`InputMethodService`) featuring dual transcription backends: **Local Orukeet** (offline multilingual FastConformer-TDT model running via `sherpa-onnx`) and **Cloud OpenAI Whisper API**.

## Features

- **Dual Transcription Backends:**
  - **Local Orukeet (Offline):** 25-language FastConformer-TDT 0.6B v3 model exported to INT8 ONNX and executed on-device via `sherpa-onnx`. Zero internet required, complete privacy, no cloud latency or API billing.
  - **Cloud Whisper API:** Streams audio directly to OpenAI's speech-to-text endpoint (`gpt-4o-mini-transcribe`).
- **Top-Center Backend Toggle:**
  - Compact toggle switch at the top center of the keyboard layout.
  - Left icon (Microchip): Local Orukeet (offline).
  - Right icon (Cloud): Cloud Whisper API (online).
  - Can be switched freely at any time, including **while actively recording** without interrupting audio capture.
- **Disk-Backed Audio Capture:**
  - Records 16 kHz, 16-bit mono PCM directly to a temporary `.wav` file in `cacheDir`.
  - Zero heap bloat: no boxed floats or massive byte arrays in RAM during long recording sessions.
- **Intelligent Preloading & Memory Lifecycle:**
  - **Always Preloaded at Recording Start:** When recording starts, Orukeet is initialized in the background on `Dispatchers.IO`, ensuring the model is warm even if the user switches to Local Orukeet mid-recording.
  - **Windowed Inference:** Transcribes long recordings in 45-second sequential windows to prevent attention memory spikes on devices with 8 GB RAM.
  - **Thread-Safe Native Lifecycle:** Serializes all native JNI operations (`init`, `decode`, `release`) with a single Kotlin `Mutex`, preventing race conditions and JNI `SIGSEGV` crashes.
  - **Aggressive RAM Cleanup:** Automatically starts a cancellable 5-second timer when the keyboard view is dismissed (`onFinishInputView`) or destroyed, freeing the ~850 MB native C++ heap.
- **Audio Routing & Haptics:**
  - Automatically remembers and routes to preferred microphone devices (Bluetooth headset, USB, etc.).
  - Optional soft haptic feedback.
  - Keyboard switcher and repeat-on-long-press backspace.

## Requirements

- Android 8.0 (API 26) or newer (`arm64-v8a`).
- For Local Orukeet: Orukeet INT8 ONNX model files placed in app storage.
- For Cloud Whisper: OpenAI API key.

## Provisioning Orukeet Model Files

The Orukeet INT8 model files (~670 MB total) are stored in external app-specific storage (`context.getExternalFilesDir(null)/orukeet/`), mapped to:

```text
/sdcard/Android/data/com.whispey.stt/files/orukeet/
```

This location allows direct memory mapping (`mmap`) by `sherpa-onnx` without requiring runtime storage permissions or bloating the APK.

### 1. Download Model Files

Download the official INT8 package from Hugging Face:

```bash
curl -L -o sherpa-onnx-orukeet-v0.1.0-int8.tar.bz2 \
  "https://huggingface.co/oruk/orukeet/resolve/main/onnx/sherpa-onnx-orukeet-v0.1.0-int8.tar.bz2"
tar -xjf sherpa-onnx-orukeet-v0.1.0-int8.tar.bz2
```

The package contains:
- `encoder.int8.onnx`
- `decoder.int8.onnx`
- `joiner.int8.onnx`
- `tokens.txt`

### 2. Push Files to Device via ADB

```bash
adb shell "mkdir -p /sdcard/Android/data/com.whispey.stt/files/orukeet"
adb push sherpa-onnx-orukeet-v0.1.0-int8/encoder.int8.onnx \
         sherpa-onnx-orukeet-v0.1.0-int8/decoder.int8.onnx \
         sherpa-onnx-orukeet-v0.1.0-int8/joiner.int8.onnx \
         sherpa-onnx-orukeet-v0.1.0-int8/tokens.txt \
         /sdcard/Android/data/com.whispey.stt/files/orukeet/
```

Verify that the model status in Whispey Settings indicates **Model detected & ready**.

## Build & Install

Build the debug APK:

```bash
./gradlew assembleDebug
```

Install to connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup

1. Install the APK and launch Whispey STT to grant microphone permission.
2. Under Android **Settings > System > Languages & input > Keyboards**, enable **Whispey STT**.
3. (Optional) In Whispey Settings, add an OpenAI API key if using Cloud Whisper mode.
4. Select Whispey STT as your input method in any text field. Use the top-center toggle to flip between Local Orukeet (microchip icon) and Cloud Whisper (cloud icon).

## License

MIT
