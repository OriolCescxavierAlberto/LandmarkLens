package com.example.landmarklens.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "landmark_history")
data class LandmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val lat: Double,
    val lon: Double,
    val azimuth: Float,
    val locationName: String?,
    val locationAddress: String?,
    val locationType: String?,
    val timestamp: Long
)
