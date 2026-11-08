package com.whispey.stt

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiWhisperClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    fun transcribe(apiKey: String, audioFile: File, targetWords: List<String> = emptyList()): String {
        val prompt = buildPrompt(targetWords)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", TRANSCRIPTION_MODEL)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType())
            )
            .build()

        return executeRequest(apiKey, body)
    }

    fun transcribe(apiKey: String, wavBytes: ByteArray, targetWords: List<String> = emptyList()): String {
        val prompt = buildPrompt(targetWords)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", TRANSCRIPTION_MODEL)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart(
                "file",
                "recording.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        return executeRequest(apiKey, body)
    }

    private fun buildPrompt(targetWords: List<String>): String {
        val base = "Clean transcription without filler words."
        if (targetWords.isEmpty()) {
            return "$base Multilingual speech, preserve spoken languages without translation."
        }
        val sb = StringBuilder(base).append(" Glossary: ")
        var first = true
        for (word in targetWords.distinct()) {
            val addition = if (first) word else ", $word"
            // Whisper 224-token prompt limit guard (~420 characters safe buffer)
            if (sb.length + addition.length > 420) break
            sb.append(addition)
            first = false
        }
        return sb.toString()
    }

    private fun executeRequest(apiKey: String, body: MultipartBody): String {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(parseError(raw).ifBlank { "Transcription failed (${response.code})" })
            }
            return JSONObject(raw).optString("text").trim()
        }
    }

    private fun parseError(raw: String): String {
        return runCatching {
            val error = JSONObject(raw).optJSONObject("error")
            error?.optString("message").orEmpty()
        }.getOrDefault("")
    }

    companion object {
        private const val TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe"
        private const val TRANSCRIPTION_PROMPT =
            "Clean transcription without filler words. Multilingual speech, preserve spoken languages without translation."
    }
}
