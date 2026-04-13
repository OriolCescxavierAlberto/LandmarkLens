package com.example.landmarklens.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LandmarkEntity::class], version = 1, exportSchema = false)
abstract class LandmarkDatabase : RoomDatabase() {
    abstract fun landmarkDao(): LandmarkDao

    companion object {
        @Volatile
        private var INSTANCE: LandmarkDatabase? = null

        fun getDatabase(context: Context): LandmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LandmarkDatabase::class.java,
                    "landmark_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
