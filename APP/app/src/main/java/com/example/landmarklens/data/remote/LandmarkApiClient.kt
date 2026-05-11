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

data class DetectedLandmark(
    val name: String,
    val distance: Int,
    val confidence: String
)

object LandmarkApiClient {
    private const val TAG = "LandmarkApiClient"
    private const val API_BASE_URL = "http://172.16.110.15:8000"
    private const val TIMEOUT_SECONDS = 15L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun queryLandmarks(lat: Double, lon: Double): List<DetectedLandmark> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("lat", lat)
                put("lon", lon)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$API_BASE_URL/api/v1/query")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                return@withContext emptyList()
            }

            val json = JSONObject(response.body?.string() ?: "")
            val data = json.optJSONObject("data") ?: return@withContext emptyList()
            val landmarks = data.optJSONArray("landmarks") ?: return@withContext emptyList()

            (0 until landmarks.length()).map { i ->
                val obj = landmarks.getJSONObject(i)
                DetectedLandmark(
                    name = obj.getString("name"),
                    distance = obj.optInt("distance", 0),
                    confidence = obj.optString("confidence", "unknown")
                )
            }.also { Log.d(TAG, "Detectados ${it.size} landmarks") }

        } catch (e: Exception) {
            Log.e(TAG, "Error querying landmarks: ${e.message}")
            emptyList()
        }
    }
}   