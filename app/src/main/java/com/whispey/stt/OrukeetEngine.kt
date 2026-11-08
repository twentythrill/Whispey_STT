package com.whispey.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

/**
 * Thread-safe lifecycle manager for the local Orukeet (FastConformer-TDT) STT model via sherpa-onnx.
 *
 * All native JNI operations (preload, transcribe, release) are strictly serialized through
 * [engineMutex] to prevent concurrent access and JNI SIGSEGV crashes.
 */
object OrukeetEngine {
    private const val TAG = "OrukeetEngine"
    private const val SAMPLE_RATE = 16_000
    private const val FEATURE_DIM = 128
    private const val NUM_THREADS = 4
    private const val WINDOW_DURATION_SEC = 45
    private const val SAMPLES_PER_WINDOW = WINDOW_DURATION_SEC * SAMPLE_RATE // 720,000 samples
    private const val BYTES_PER_WINDOW = SAMPLES_PER_WINDOW * 2 // 1,440,000 bytes
    private const val WAV_HEADER_SIZE = 44L

    private val engineMutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    fun getModelDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "orukeet")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun areModelFilesPresent(context: Context): Boolean {
        val dir = getModelDirectory(context)
        val encoder = File(dir, "encoder.int8.onnx")
        val decoder = File(dir, "decoder.int8.onnx")
        val joiner = File(dir, "joiner.int8.onnx")
        val tokens = File(dir, "tokens.txt")
        return encoder.exists() && decoder.exists() && joiner.exists() && tokens.exists()
    }

    /**
     * Preloads the OfflineRecognizer in background (Dispatchers.IO) while holding [engineMutex].
     * If the model is already initialized, returns immediately.
     */
    suspend fun preload(context: Context) = engineMutex.withLock {
        if (recognizer != null) return@withLock
        try {
            recognizer = createRecognizer(context)
            Log.i(TAG, "Orukeet model successfully preloaded into RAM")
        } catch (e: Exception) {
            Log.w(TAG, "Preload failed: ${e.message}")
        }
    }

    /**
     * Transcribes [wavFile] in 45-second sequential windows.
     * Waits for any in-progress preload to complete via [engineMutex].
     */
    suspend fun transcribe(context: Context, wavFile: File): String = engineMutex.withLock {
        if (!wavFile.exists() || wavFile.length() <= WAV_HEADER_SIZE) {
            return@withLock ""
        }

        var activeRecognizer = recognizer
        if (activeRecognizer == null) {
            activeRecognizer = createRecognizer(context)
            recognizer = activeRecognizer
        }

        decodeWavInWindows(activeRecognizer, wavFile)
    }

    /**
     * Aggressively frees the native C++ heap by calling [OfflineRecognizer.release]
     * and nullifying the reference while holding [engineMutex].
     */
    suspend fun release() = engineMutex.withLock {
        recognizer?.let {
            try {
                it.release()
                Log.i(TAG, "Orukeet model native memory released")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing Orukeet recognizer: ${e.message}")
            }
        }
        recognizer = null
    }

    private fun createRecognizer(context: Context): OfflineRecognizer {
        val dir = getModelDirectory(context)
        val encoder = File(dir, "encoder.int8.onnx")
        val decoder = File(dir, "decoder.int8.onnx")
        val joiner = File(dir, "joiner.int8.onnx")
        val tokens = File(dir, "tokens.txt")

        if (!encoder.exists() || !decoder.exists() || !joiner.exists() || !tokens.exists()) {
            throw FileNotFoundException(
                "Orukeet model files missing in ${dir.absolutePath}. " +
                "Push encoder.int8.onnx, decoder.int8.onnx, joiner.int8.onnx, and tokens.txt via adb."
            )
        }

        val config = OfflineRecognizerConfig().apply {
            featConfig.sampleRate = SAMPLE_RATE
            featConfig.featureDim = FEATURE_DIM
            modelConfig.transducer.encoder = encoder.absolutePath
            modelConfig.transducer.decoder = decoder.absolutePath
            modelConfig.transducer.joiner = joiner.absolutePath
            modelConfig.tokens = tokens.absolutePath
            modelConfig.modelType = "nemo_transducer"
            modelConfig.numThreads = NUM_THREADS
            modelConfig.provider = "cpu"
            modelConfig.debug = false
            decodingMethod = "greedy_search"
        }

        return OfflineRecognizer(null, config)
    }

    private fun decodeWavInWindows(activeRecognizer: OfflineRecognizer, wavFile: File): String {
        val transcriptions = mutableListOf<String>()

        FileInputStream(wavFile).use { fis ->
            var skipped = 0L
            while (skipped < WAV_HEADER_SIZE) {
                val s = fis.skip(WAV_HEADER_SIZE - skipped)
                if (s <= 0) break
                skipped += s
            }

            val byteBuffer = ByteArray(BYTES_PER_WINDOW)
            while (true) {
                var bytesReadThisWindow = 0
                while (bytesReadThisWindow < BYTES_PER_WINDOW) {
                    val count = fis.read(byteBuffer, bytesReadThisWindow, BYTES_PER_WINDOW - bytesReadThisWindow)
                    if (count == -1) break
                    bytesReadThisWindow += count
                }
                if (bytesReadThisWindow <= 0) break

                val numSamples = bytesReadThisWindow / 2
                val floatArray = FloatArray(numSamples)
                var byteIdx = 0
                for (i in 0 until numSamples) {
                    val low = byteBuffer[byteIdx++].toInt() and 0xFF
                    val high = byteBuffer[byteIdx++].toInt()
                    val sample = ((high shl 8) or low).toShort()
                    floatArray[i] = sample / 32768.0f
                }

                val stream = activeRecognizer.createStream()
                try {
                    stream.acceptWaveform(floatArray, SAMPLE_RATE)
                    activeRecognizer.decode(stream)
                    val resultText = activeRecognizer.getResult(stream).text.trim()
                    if (resultText.isNotBlank()) {
                        transcriptions.add(resultText)
                    }
                } finally {
                    stream.release()
                }
            }
        }

        return transcriptions.joinToString(" ").trim()
    }
}
