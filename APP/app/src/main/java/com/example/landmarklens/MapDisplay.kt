package com.example.landmarklens

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapDisplay(
    latitude: Double,
    longitude: Double,
    locationName: String = "Ubicación capturada"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
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
fun MapDisplaySmall(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    OsmMapView(
        latitude = latitude,
        longitude = longitude,
        locationName = "Ubicación",
        modifier = modifier.fillMaxWidth().height(200.dp)
    )
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

    // Inicializar configuración de OSMDroid
    Configuration.getInstance().apply {
        load(context, PreferenceManager.getDefaultSharedPreferences(context))
        userAgentValue = "LandmarkLens/1.0"
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(latitude, longitude))
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }

    // Añadir/actualizar marcador
    remember(latitude, longitude, locationName) {
        mapView.overlays.clear()
        val marker = Marker(mapView).apply {
            position = GeoPoint(latitude, longitude)
            title = locationName
            snippet = "${"%.6f".format(latitude)}, ${"%.6f".format(longitude)}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
        marker.showInfoWindow()
        mapView.invalidate()
    }

    // Gestionar ciclo de vida del mapa (pause/resume)
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

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}