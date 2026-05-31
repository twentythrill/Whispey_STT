package com.whispey.stt

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiWhisperClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    fun transcribe(apiKey: String, wavBytes: ByteArray): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", TRANSCRIPTION_MODEL)
            .addFormDataPart("prompt", TRANSCRIPTION_PROMPT)
            .addFormDataPart(
                "file",
                "recording.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

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
            "Multilingual speech. Preserve all languages as spoken, do not translate."
    }
}
