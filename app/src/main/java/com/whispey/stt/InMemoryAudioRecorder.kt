package com.whispey.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class InMemoryAudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private val recording = AtomicBoolean(false)
    private val pcmBuffer = ByteArrayOutputStream()

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

        pcmBuffer.reset()
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
            error("Could not initialize microphone")
        }

        recording.set(true)
        recorder.startRecording()
        worker = Thread {
            val buffer = ByteArray(bufferSize)
            while (recording.get()) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(pcmBuffer) {
                        pcmBuffer.write(buffer, 0, read)
                    }
                }
            }
        }.also { it.start() }
    }

    fun stop(): ByteArray {
        if (!recording.getAndSet(false)) return ByteArray(0)

        audioRecord?.let { runCatching { it.stop() } }
        worker?.join(500)
        worker = null

        audioRecord?.run {
            release()
        }
        audioRecord = null

        val pcm = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        pcmBuffer.reset()
        return WavEncoder.pcm16MonoToWav(pcm)
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
        pcmBuffer.reset()
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
    }
}
