package com.example.landmarklens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OllamaClient {

    private const val BASE_URL = "http://10.0.2.2:11434"
    private const val USE_MOCK = false

    suspend fun getModels(): List<String> = withContext(Dispatchers.IO) {
        if (USE_MOCK) {
            return@withContext listOf("llama3.2", "mistral", "gemma:2b", "phi3")
        }

        try {
            val url = URL("$BASE_URL/api/tags")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext listOf("Error loading models")
            }

            val response = connection.inputStream
                .bufferedReader()
                .use(BufferedReader::readText)

            val json = JSONObject(response)
            val modelsArray = json.optJSONArray("models") ?: JSONArray()

            val models = mutableListOf<String>()
            for (i in 0 until modelsArray.length()) {
                val obj = modelsArray.getJSONObject(i)
                models.add(obj.optString("name", "unknown"))
            }

            if (models.isEmpty()) listOf("No models found") else models
        } catch (e: Exception) {
            listOf("Connection error")
        }
    }

    suspend fun askModel(model: String, prompt: String): String = withContext(Dispatchers.IO) {
        if (USE_MOCK) {
            return@withContext "[MOCK][$model] Respuesta simulada para: \"$prompt\""
        }

        try {
            val url = URL("$BASE_URL/api/generate")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream
                .bufferedReader()
                .use(BufferedReader::readText)

            val json = JSONObject(response)
            json.optString("response", "No response from model")
        } catch (e: Exception) {
            "Error connecting to Ollama: ${e.message}"
        }
    }
}