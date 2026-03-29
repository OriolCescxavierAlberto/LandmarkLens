package com.example.landmarklens

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection

data class LandmarkLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "Lugar desconocido",
    val distance: Float = 0f
)

class PlacesService(private val context: Context) {
    private val TAG = "PlacesService"

    suspend fun getReverseGeocodedAddress(latitude: Double, longitude: Double): LandmarkLocation? {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = "https://nominatim.openstreetmap.org/reverse" +
                        "?format=jsonv2" +
                        "&lat=$latitude" +
                        "&lon=$longitude" +
                        "&zoom=18" +
                        "&addressdetails=1"

                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "LandmarkLens/1.0 (Android)")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val displayName = json.optString("display_name", "")
                val addressObj = json.optJSONObject("address")

                val name = buildString {
                    val poi = listOf("tourism", "historic", "amenity", "building", "shop")
                        .mapNotNull { addressObj?.optString(it, null)?.takeIf { v -> v.isNotEmpty() } }
                        .firstOrNull() ?: ""
                    val road = addressObj?.optString("road", "") ?: ""
                    val houseNumber = addressObj?.optString("house_number", "") ?: ""
                    when {
                        poi.isNotEmpty() -> append(poi)
                        road.isNotEmpty() -> {
                            append(road)
                            if (houseNumber.isNotEmpty()) append(", $houseNumber")
                        }
                        displayName.isNotEmpty() -> append(displayName.split(",").firstOrNull()?.trim() ?: displayName)
                        else -> append("Ubicación sin nombre")
                    }
                }.trim().ifEmpty { "Ubicación sin nombre" }

                val addressText = buildString {
                    val parts = listOfNotNull(
                        (addressObj?.optString("city", null)
                            ?: addressObj?.optString("town", null)
                            ?: addressObj?.optString("village", null))?.takeIf { it.isNotEmpty() },
                        addressObj?.optString("state", null)?.takeIf { it.isNotEmpty() },
                        addressObj?.optString("country", null)?.takeIf { it.isNotEmpty() }
                    )
                    append(parts.joinToString(", "))
                }.trim()

                val category = json.optString("category", "")
                val osmType = json.optString("type", "")
                val type = determineOsmType(category, osmType, addressObj)

                LandmarkLocation(
                    name = name,
                    address = addressText,
                    latitude = latitude,
                    longitude = longitude,
                    type = type
                ).also { Log.d(TAG, "Nominatim OK: ${it.name} (${it.type})") }
            } catch (e: Exception) {
                Log.e(TAG, "Error Nominatim: ${e.message}", e)
                null
            }
        }
    }

    private fun determineOsmType(category: String, type: String, address: JSONObject?): String {
        return when {
            category == "tourism" -> when (type) {
                "attraction" -> "Atracción turística"
                "museum" -> "Museo"
                "monument" -> "Monumento"
                "castle" -> "Castillo"
                "ruins" -> "Ruinas"
                else -> "Lugar turístico"
            }
            category == "historic" -> when (type) {
                "castle" -> "Castillo histórico"
                "monument" -> "Monumento histórico"
                "ruins" -> "Ruinas históricas"
                "church" -> "Iglesia histórica"
                else -> "Lugar histórico"
            }
            category == "amenity" -> when (type) {
                "place_of_worship" -> "Lugar de culto"
                "restaurant" -> "Restaurante"
                "cafe" -> "Cafetería"
                "hospital" -> "Hospital"
                else -> "Equipamiento urbano"
            }
            category == "leisure" -> "Zona de ocio"
            category == "natural" -> "Lugar natural"
            address?.optString("road", "")?.isNotEmpty() == true -> "Ubicación urbana"
            else -> "Lugar"
        }
    }

    suspend fun getCompleteLocationInfo(latitude: Double, longitude: Double): LandmarkLocation? =
        getReverseGeocodedAddress(latitude, longitude)
}
