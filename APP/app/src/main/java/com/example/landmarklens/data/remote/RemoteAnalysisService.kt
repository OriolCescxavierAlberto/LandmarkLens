package com.example.landmarklens.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Servicio para conectarse a la API de análisis remoto.
 * Envía coordenadas GPS y múltiples parámetros de sensor para análisis de monumentos.
 */
object RemoteAnalysisService {
    private const val TAG = "RemoteAnalysisService"
    private const val ANALYSIS_API_URL = "http://172.16.110.15:8000/api/v1/query"
    private const val TIMEOUT_SECONDS = 30L
    private const val DEFAULT_FOV = 70f // Campo de visión por defecto (grados)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Envía una consulta de análisis a la API remota.
     *
     * @param latitude Latitud en formato decimal (ej: 41.3851)
     * @param longitude Longitud en formato decimal (ej: 2.1734)
     * @param azimuth Ángulo de la brújula en grados (0-360)
     * @param fov Campo de visión opcional (por defecto 70 grados)
     * @return JSONObject con la respuesta de la API o null si falla
     */
    suspend fun queryRemoteAnalysis(
        latitude: Double,
        longitude: Double,
        azimuth: Float,
        fov: Float = DEFAULT_FOV
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Enviando análisis remoto - Lat: $latitude, Lon: $longitude, Azimuth: $azimuth, FOV: $fov")

            // Construir JSON de la solicitud
            val requestJson = JSONObject().apply {
                put("lat", latitude)
                put("lon", longitude)
                put("azimuth", azimuth.toDouble())
                put("fov", fov.toDouble())
            }

            // Crear y ejecutar la solicitud HTTP POST
            val request = Request.Builder()
                .url(ANALYSIS_API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(requestJson.toString().toRequestBody())
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                return@withContext if (responseBody != null) {
                    val result = JSONObject(responseBody)
                    Log.d(TAG, "Respuesta exitosa: $result")
                    result
                } else {
                    Log.w(TAG, "Respuesta vacía del servidor")
                    null
                }
            } else {
                Log.e(TAG, "Error HTTP ${response.code}: ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en consulta remota: ${e.message}", e)
            null
        }
    }

    /**
     * Método de prueba para validar conectividad con la API.
     * @return true si la API es accesible, false en caso contrario
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ANALYSIS_API_URL)
                .build()

            val response = client.newCall(request).execute()
            val isReachable = response.isSuccessful || response.code == 405 // 405 si solo acepta POST
            Log.d(TAG, "Test de conexión: ${if (isReachable) "OK" else "FALLO"}")
            return@withContext isReachable
        } catch (e: Exception) {
            Log.e(TAG, "Conexión fallida: ${e.message}")
            return@withContext false
        }
    }
}

