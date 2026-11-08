package com.whispey.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Disk-backed audio recorder capturing 16 kHz, 16-bit mono PCM directly to a temporary .wav file
 * in [Context.getCacheDir]. Streaming directly to disk ensures zero heap bloat and no boxed floats
 * in RAM during long recording sessions.
 */
class DiskAudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private val recording = AtomicBoolean(false)
    private var currentOutputFile: File? = null
    private var fileOutputStream: FileOutputStream? = null

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission is required")
        }
        if (recording.get()) return

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE)

        val outputFile = File(context.cacheDir, "recording_temp.wav")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val fos = FileOutputStream(outputFile)
        writeWavHeaderPlaceholder(fos)
        currentOutputFile = outputFile
        fileOutputStream = fos

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val recorder = audioRecord ?: error("Could not initialize microphone")
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            audioRecord = null
            fos.close()
            error("Could not initialize microphone")
        }

        applyPreferredDevice(recorder)

        recording.set(true)
        recorder.startRecording()
        worker = Thread {
            val buffer = ByteArray(bufferSize)
            try {
                while (recording.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(this) {
                            fileOutputStream?.write(buffer, 0, read)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.also { it.start() }
    }

    fun stop(): File {
        if (!recording.getAndSet(false)) return currentOutputFile ?: File(context.cacheDir, "recording_temp.wav")

        audioRecord?.let { runCatching { it.stop() } }
        worker?.join(500)
        worker = null

        audioRecord?.run {
            release()
        }
        audioRecord = null

        val outputFile = currentOutputFile
        synchronized(this) {
            fileOutputStream?.flush()
            fileOutputStream?.close()
            fileOutputStream = null
        }

        if (outputFile != null && outputFile.exists() && outputFile.length() >= WAV_HEADER_SIZE) {
            finalizeWavHeader(outputFile)
        }

        return outputFile ?: File(context.cacheDir, "recording_temp.wav")
    }

    fun cancel() {
        if (recording.getAndSet(false)) {
            audioRecord?.let { runCatching { it.stop() } }
            worker?.join(500)
        }
        worker = null
        audioRecord?.run {
            release()
        }
        audioRecord = null

        synchronized(this) {
            fileOutputStream?.close()
            fileOutputStream = null
        }
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    private fun writeWavHeaderPlaceholder(out: OutputStream) {
        val header = ByteArray(WAV_HEADER_SIZE)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(36) // placeholder for 36 + dataSize
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16) // Subchunk1Size (16 for PCM)
        bb.putShort(1) // AudioFormat (1 = PCM)
        bb.putShort(CHANNELS.toShort()) // Mono
        bb.putInt(SAMPLE_RATE)
        bb.putInt(SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)) // ByteRate
        bb.putShort((CHANNELS * (BITS_PER_SAMPLE / 8)).toShort()) // BlockAlign
        bb.putShort(BITS_PER_SAMPLE.toShort())
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(0) // placeholder for dataSize
        out.write(header)
    }

    private fun finalizeWavHeader(file: File) {
        val totalAudioLen = file.length() - WAV_HEADER_SIZE
        if (totalAudioLen < 0) return
        val totalDataLen = totalAudioLen + 36

        RandomAccessFile(file, "rw").use { raf ->
            val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            raf.seek(4)
            bb.putInt(totalDataLen.toInt())
            raf.write(bb.array())

            bb.clear()
            raf.seek(40)
            bb.putInt(totalAudioLen.toInt())
            raf.write(bb.array())
        }
    }

    private fun applyPreferredDevice(recorder: AudioRecord) {
        val savedType = WhispeyPrefs.preferredAudioDeviceType(context)
        if (savedType == WhispeyPrefs.AUDIO_TYPE_NONE) return

        val savedName = WhispeyPrefs.preferredAudioDeviceName(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource }

        val sameType = inputs.filter { it.type == savedType }
        val match: AudioDeviceInfo? = when {
            sameType.isEmpty() -> null
            sameType.size == 1 -> sameType.first()
            else -> sameType.firstOrNull {
                savedName.isNotEmpty() &&
                    savedName.contains(it.productName?.toString()?.trim().orEmpty(), ignoreCase = true)
            } ?: sameType.first()
        }

        if (match != null) {
            runCatching { recorder.preferredDevice = match }
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val WAV_HEADER_SIZE = 44
    }
}
