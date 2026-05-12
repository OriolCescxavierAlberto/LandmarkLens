package com.example.landmarklens.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    private const val TAG = "FileUtils"
    private const val PHOTOS_DIR = "landmark_photos"

    fun getPhotosDirectory(context: Context): File {
        val storageDir = File(context.filesDir, PHOTOS_DIR)
        if (!storageDir.exists()) storageDir.mkdirs()
        return storageDir
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, lat: Double, lon: Double, azimuth: Float): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(getPhotosDirectory(context), "landmark_${timestamp}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}"); null
        }
    }

    fun loadBitmap(path: String): Bitmap? = try {
        android.graphics.BitmapFactory.decodeFile(path)
    } catch (e: Exception) { null }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    fun deletePhoto(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) file.delete() else false
    }
}
