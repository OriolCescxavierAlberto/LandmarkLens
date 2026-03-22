package com.example.landmarklens

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Data class para representar un lugar encontrado
 */
data class LandmarkLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "Lugar desconocido",
    val distance: Float = 0f
)

/**
 * Servicio para obtener información de lugares usando Reverse Geocoding y Places API
 * Maneja:
 * - Búsqueda inversa de ubicación (Reverse Geocoding)
 * - Búsqueda de Places cercanos (POI - Points of Interest)
 * - Identificación de monumentos y edificios
 */
class PlacesService(private val context: Context) {
    private val TAG = "PlacesService"
    private val geocoder = Geocoder(context, Locale.getDefault())
    private val placesClient = Places.createClient(context)

    /**
     * Realiza un reverse geocoding para obtener dirección desde coordenadas
     * Este es el método más simple y funciona sin API Key
     */
    suspend fun getReverseGeocodedAddress(latitude: Double, longitude: Double): LandmarkLocation? {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val name = buildString {
                        // Construir nombre del lugar desde componentes de la dirección
                        if (!address.thoroughfare.isNullOrEmpty()) {
                            append(address.thoroughfare)
                            append(" ")
                        }
                        if (!address.featureName.isNullOrEmpty() && address.featureName != address.thoroughfare) {
                            append(address.featureName)
                        }
                    }.trim().ifEmpty { "Ubicación sin nombre" }

                    val addressText = buildString {
                        append(address.locality ?: "")
                        if (!address.adminArea.isNullOrEmpty()) {
                            if (isNotEmpty()) append(", ")
                            append(address.adminArea)
                        }
                        if (!address.countryName.isNullOrEmpty()) {
                            if (isNotEmpty()) append(", ")
                            append(address.countryName)
                        }
                    }.trim()

                    val type = determineLocationType(address)

                    LandmarkLocation(
                        name = name,
                        address = addressText,
                        latitude = latitude,
                        longitude = longitude,
                        type = type
                    ).also {
                        Log.d(TAG, "Reverse geocoding exitoso: ${it.name}")
                    }
                } else {
                    Log.w(TAG, "No se encontraron direcciones para: $latitude, $longitude")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en reverse geocoding: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Intenta obtener información de Places cercanos
     * Nota: Requiere API Key configurada en AndroidManifest.xml
     */
    suspend fun getNearbyPlaces(latitude: Double, longitude: Double): List<LandmarkLocation> {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                // Esta es una implementación simplificada
                // En producción, usarías PlacesClient.findCurrentPlace() con tipos específicos
                val placeTypes = listOf(
                    "TOURIST_ATTRACTION",
                    "POINT_OF_INTEREST",
                    "LANDMARK"
                )

                Log.d(TAG, "Buscando lugares cercanos para: $latitude, $longitude")
                
                // Por ahora retornamos la ubicación obtenida con reverse geocoding
                // La funcionalidad completa de Places requiere más configuración
                emptyList<LandmarkLocation>()
            } catch (e: Exception) {
                Log.e(TAG, "Error al buscar Places cercanos: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Determina el tipo de lugar basándose en componentes de la dirección
     */
    private fun determineLocationType(address: Address): String {
        return when {
            address.thoroughfare?.contains("Calle", ignoreCase = true) == true -> "Calle/Camino"
            address.thoroughfare?.contains("Avenida", ignoreCase = true) == true -> "Avenida"
            address.thoroughfare != null -> "Ubicación urbana"
            address.featureName?.contains("Parque", ignoreCase = true) == true -> "Parque"
            address.featureName?.contains("Plaza", ignoreCase = true) == true -> "Plaza"
            address.featureName?.contains("Iglesia", ignoreCase = true) == true -> "Iglesia"
            address.featureName?.contains("Monumento", ignoreCase = true) == true -> "Monumento"
            address.locality != null -> "Localidad: ${address.locality}"
            else -> "Lugar"
        }
    }

    /**
     * Obtiene información completa combinando reverse geocoding y análisis
     */
    suspend fun getCompleteLocationInfo(latitude: Double, longitude: Double): LandmarkLocation? {
        return withContext(Dispatchers.IO) {
            // Primero intenta reverse geocoding
            val location = getReverseGeocodedAddress(latitude, longitude)
            
            if (location != null) {
                Log.d(TAG, "Información completa obtenida para: ${location.name}")
            } else {
                Log.w(TAG, "No se pudo obtener información de ubicación")
            }
            
            location
        }
    }
}

