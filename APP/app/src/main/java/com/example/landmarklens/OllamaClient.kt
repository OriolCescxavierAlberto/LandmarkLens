package com.example.landmarklens

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente para comunicarse con Ollama API local
 * Responsable de obtener modelos disponibles y enviar prompts
 */
object OllamaClient {
    private const val TAG = "OllamaClient"
    private const val OLLAMA_BASE_URL = "http://localhost:11434"
    private const val TIMEOUT_SECONDS = 120L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Obtiene la lista de modelos disponibles en Ollama
     */
    suspend fun getModels(): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$OLLAMA_BASE_URL/api/tags")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val models = json.optJSONArray("models")
                    ?.let { array ->
                        (0 until array.length()).map { i ->
                            array.getJSONObject(i).getString("name")
                        }
                    } ?: emptyList()
                Log.d(TAG, "Modelos disponibles: $models")
                models
            } else {
                Log.e(TAG, "Error al obtener modelos: ${response.code}")
                listOf("Error fetching models")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al obtener modelos", e)
            listOf("Error: ${e.message}")
        }
    }

    /**
     * Envía un prompt al modelo especificado y obtiene la respuesta
     */
    suspend fun askModel(model: String, prompt: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val requestJson = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
            }

            val request = Request.Builder()
                .url("$OLLAMA_BASE_URL/api/generate")
                .post(requestJson.toString().toRequestBody())
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext "Sin respuesta"
                val json = JSONObject(body)
                val result = json.optString("response", "Sin respuesta")
                Log.d(TAG, "Respuesta recibida: ${result.take(100)}...")
                result
            } else {
                Log.e(TAG, "Error en la respuesta: ${response.code}")
                "Error: ${response.message}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al solicitar modelo", e)
            "Error: ${e.message}"
        }
    }
}

