package com.example.landmarklens

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
}
