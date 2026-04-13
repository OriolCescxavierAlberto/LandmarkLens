package com.example.landmarklens.ui.components

import android.graphics.PorterDuff
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.landmarklens.data.model.LandmarkHistoryItem
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapDisplay(latitude: Double, longitude: Double, locationName: String = "Ubicación capturada") {
    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        OsmMapView(latitude, longitude, locationName, Modifier.fillMaxWidth().height(300.dp))
    }
}

@Composable
fun MapWithHistoryMarkers(
    currentLat: Double, currentLon: Double,
    history: List<LandmarkHistoryItem>,
    onMarkerClick: (LandmarkHistoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true); controller.setZoom(15.0)
        }
    }

    var initialCentered by remember { mutableStateOf(false) }
    LaunchedEffect(currentLat, currentLon) {
        if (!initialCentered && currentLat != 0.0) {
            mapView.controller.setCenter(GeoPoint(currentLat, currentLon)); initialCentered = true
        }
    }

    LaunchedEffect(history, currentLat, currentLon) {
        mapView.overlays.clear()
        if (currentLat != 0.0) {
            val userMarker = Marker(mapView).apply {
                position = GeoPoint(currentLat, currentLon); title = "Tu ubicación"
                icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                    setColorFilter(android.graphics.Color.RED, PorterDuff.Mode.SRC_IN)
                }
            }
            mapView.overlays.add(userMarker)
        }
        history.forEach { item ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(item.lat, item.lon); title = item.location?.name ?: "Lugar"
                icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                    setColorFilter(android.graphics.Color.rgb(0, 150, 0), PorterDuff.Mode.SRC_IN)
                }
                setOnMarkerClickListener { _, _ -> onMarkerClick(item); true }
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDetach() }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
fun OsmMapView(lat: Double, lon: Double, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context).apply { setMultiTouchControls(true); controller.setZoom(16.0) } }

    LaunchedEffect(lat, lon, name) {
        mapView.overlays.clear()
        mapView.overlays.add(Marker(mapView).apply {
            position = GeoPoint(lat, lon); title = name
            icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                setColorFilter(android.graphics.Color.rgb(255, 165, 0), PorterDuff.Mode.SRC_IN)
            }
        })
        mapView.controller.setCenter(GeoPoint(lat, lon)); mapView.invalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDetach() }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
