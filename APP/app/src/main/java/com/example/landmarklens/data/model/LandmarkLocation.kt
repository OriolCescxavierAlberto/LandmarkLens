package com.example.landmarklens.data.model

data class LandmarkLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "Lugar desconocido",
    val distance: Float = 0f
)
