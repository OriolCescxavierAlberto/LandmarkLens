package com.example.landmarklens.data.model

import android.graphics.Bitmap

data class LandmarkHistoryItem(
    val id: Long = System.nanoTime(),
    val bitmap: Bitmap,
    val lat: Double,
    val lon: Double,
    val azimuth: Float,
    val location: LandmarkLocation?,
    val timestamp: Long = System.currentTimeMillis()
)
