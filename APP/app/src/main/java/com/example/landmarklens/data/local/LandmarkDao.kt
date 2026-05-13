package com.example.landmarklens.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LandmarkDao {
    @Query("SELECT * FROM landmark_history ORDER BY timestamp DESC")
    fun getAllLandmarks(): Flow<List<LandmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandmark(landmark: LandmarkEntity): Long

    @Delete
    suspend fun deleteLandmark(landmark: LandmarkEntity)

    @Query("DELETE FROM landmark_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM landmark_history")
    suspend fun deleteAllLandmarks()

    @Query("SELECT * FROM landmark_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLandmark(): LandmarkEntity?

    @Query("UPDATE landmark_history SET aiLandmark = :landmark, aiDescription = :description, aiConfidence = :confidence WHERE id = :id")
    suspend fun updateAIInfo(id: Long, landmark: String, description: String, confidence: Double)
}
