package com.example.landmarklens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

/**
 * Componente de mapa que muestra la ubicación capturada
 * Muestra:
 * - Mapa centrado en coordenadas GPS
 * - Marcador (pin) en la ubicación exacta
 * - Zoom configurado para visibilidad óptima
 */
@Composable
fun MapDisplay(
    latitude: Double,
    longitude: Double,
    locationName: String = "Ubicación capturada"
) {
    val location = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(location, 15f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth(),
                cameraPositionState = cameraPositionState
            ) {
                Marker(
                    state = rememberMarkerState(position = location),
                    title = locationName,
                    snippet = "$latitude, $longitude"
                )
            }

            // Overlay con información de la ubicación en la esquina superior
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(0.dp, 0.dp, 16.dp, 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "📍 $locationName",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f))
                            .fillMaxWidth()
                    )
                    Text(
                        text = "$latitude, $longitude",
                        color = Color(0xFFB0B0B0),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f))
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Componente compacto de mapa para preview/thumbnail
 */
@Composable
fun MapDisplaySmall(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    val location = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(location, 15f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
    ) {
        GoogleMap(
            modifier = modifier.fillMaxWidth(),
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                state = rememberMarkerState(position = location),
                title = "Ubicación"
            )
        }
    }
}

