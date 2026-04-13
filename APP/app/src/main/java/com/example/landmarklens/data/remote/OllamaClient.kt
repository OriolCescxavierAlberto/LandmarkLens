package com.example.landmarklens.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OllamaClient {
    private const val TAG = "OllamaClient"
    private const val OLLAMA_BASE_URL = "http://10.0.2.2:11434" // Cambiado para emulador Android
    private const val TIMEOUT_SECONDS = 120L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun getModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$OLLAMA_BASE_URL/api/tags").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val array = json.optJSONArray("models") ?: return@withContext emptyList()
                (0 until array.length()).map { array.getJSONObject(it).getString("name") }
            } else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun askModel(model: String, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", model); put("prompt", prompt); put("stream", false)
            }
            val request = Request.Builder()
                .url("$OLLAMA_BASE_URL/api/generate")
                .post(requestJson.toString().toRequestBody())
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body?.string() ?: "").optString("response", "Sin respuesta")
            } else "Error: ${response.code}"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}
