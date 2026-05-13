package com.example.landmarklens.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Servicio para conectarse a la API de análisis remoto.
 * Envía coordenadas GPS para análisis de monumentos.
 */
object RemoteAnalysisService {
    private const val TAG = "RemoteAnalysisService"
//    private const val ANALYSIS_API_URL = "http://10.0.2.2:8000/api/v1/query"
    private const val ANALYSIS_API_URL = "http://172.16.110.15:8000/api/v1/query"

    private const val TIMEOUT_SECONDS = 30L
    private const val DEFAULT_FOV = 70f

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Envía una consulta de análisis a la API remota.
     */
    suspend fun queryRemoteAnalysis(
        latitude: Double,
        longitude: Double,
        azimuth: Float,
        fov: Float = DEFAULT_FOV
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            // Construir JSON
            val requestJson = JSONObject().apply {
                put("lat", latitude)
                put("lon", longitude)
            }
            
            Log.d(TAG, ">>> PETICIÓN POST: $ANALYSIS_API_URL")
            Log.d(TAG, ">>> BODY: $requestJson")

            val mediaType = "application/json".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(ANALYSIS_API_URL)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "<<< RESPUESTA [Código: ${response.code}]")
            Log.d(TAG, "<<< BODY: $responseBody")

            if (response.isSuccessful) {
                return@withContext if (responseBody != null) {
                    try {
                        JSONObject(responseBody)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando JSON: ${e.message}")
                        null
                    }
                } else null
            } else {
                Log.e(TAG, "Error HTTP: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error de red: ${e.message}", e)
            null
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(ANALYSIS_API_URL).build()
            val response = client.newCall(request).execute()
            Log.d(TAG, "Test Conexión: ${response.code}")
            return@withContext response.isSuccessful || response.code == 405
        } catch (e: Exception) {
            Log.e(TAG, "Test Fallido: ${e.message}")
            return@withContext false
        }
    }
}
