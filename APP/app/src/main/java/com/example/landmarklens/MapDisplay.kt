package com.example.landmarklens

import android.graphics.PorterDuff
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Mapa con OSMDroid optimizado para evitar parpadeos y con marcadores de colores
 */
@Composable
fun MapDisplay(
    latitude: Double,
    longitude: Double,
    locationName: String = "Ubicación capturada"
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        OsmMapView(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            modifier = Modifier.fillMaxWidth().height(300.dp)
        )
    }
}

@Composable
fun MapWithHistoryMarkers(
    currentLat: Double,
    currentLon: Double,
    history: List<LandmarkHistoryItem>,
    onMarkerClick: (LandmarkHistoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configuración inicial
    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = "LandmarkLens/1.0"
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }

    // Centrado inicial
    var initialCentered by remember { mutableStateOf(false) }
    LaunchedEffect(currentLat, currentLon) {
        if (!initialCentered && currentLat != 0.0 && currentLon != 0.0) {
            mapView.controller.setCenter(GeoPoint(currentLat, currentLon))
            initialCentered = true
        }
    }

    // Actualizar marcadores cuando cambian los datos
    LaunchedEffect(history, currentLat, currentLon) {
        mapView.overlays.clear()
        
        // 1. Marcador ROJO para ubicación actual
        if (currentLat != 0.0 && currentLon != 0.0) {
            val userMarker = Marker(mapView).apply {
                position = GeoPoint(currentLat, currentLon)
                title = "Tu ubicación actual"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                // Tintar el icono de ROJO
                val icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()
                icon?.setColorFilter(android.graphics.Color.RED, PorterDuff.Mode.SRC_IN)
                this.icon = icon
            }
            mapView.overlays.add(userMarker)
        }

        // 2. Marcadores VERDES para historial
        history.forEach { item ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(item.lat, item.lon)
                title = item.location?.name ?: "Monumento guardado"
                snippet = "Toca para ver detalles"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                // Tintar el icono de VERDE (o dejar el default si es verde, pero mejor asegurar)
                val icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()
                icon?.setColorFilter(android.graphics.Color.rgb(0, 150, 0), PorterDuff.Mode.SRC_IN)
                this.icon = icon

                setOnMarkerClickListener { _, _ ->
                    onMarkerClick(item)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
fun OsmMapView(
    latitude: Double,
    longitude: Double,
    locationName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = "LandmarkLens/1.0"
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(latitude, longitude))
        }
    }

    LaunchedEffect(latitude, longitude, locationName) {
        mapView.overlays.clear()
        val marker = Marker(mapView).apply {
            position = GeoPoint(latitude, longitude)
            title = locationName
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            // Color por defecto para vista de una sola foto (podría ser azul o naranja)
            val icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()
            icon?.setColorFilter(android.graphics.Color.rgb(255, 165, 0), PorterDuff.Mode.SRC_IN) // Naranja
            this.icon = icon
        }
        mapView.overlays.add(marker)
        mapView.controller.setCenter(GeoPoint(latitude, longitude))
        mapView.invalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
