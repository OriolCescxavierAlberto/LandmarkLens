package com.example.landmarklens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilidad para gestionar archivos y almacenamiento local de capturas
 */
object FileUtils {
    private const val TAG = "FileUtils"
    private const val PHOTOS_DIR = "landmark_photos"

    /**
     * Obtiene el directorio de almacenamiento para fotos
     */
    fun getPhotosDirectory(context: Context): File {
        val storageDir = File(context.filesDir, PHOTOS_DIR)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return storageDir
    }

    /**
     * Guarda un Bitmap como PNG en almacenamiento local
     */
    fun saveBitmap(context: Context, bitmap: Bitmap, lat: Double, lon: Double, azimuth: Float): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "landmark_${timestamp}.png"
            val file = File(getPhotosDirectory(context), fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            // Log metadata en el archivo (podría también guardarse en un archivo JSON separado)
            Log.d(TAG, "Foto guardada: ${file.absolutePath}")
            Log.d(TAG, "Metadatos - Lat: $lat, Lon: $lon, Acimut: $azimuth°")

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar foto", e)
            null
        }
    }

    /**
     * Obtiene la ruta de la foto más reciente
     */
    fun getLatestPhoto(context: Context): File? {
        val dir = getPhotosDirectory(context)
        return dir.listFiles()?.sortedByDescending { it.lastModified() }?.firstOrNull()
    }

    /**
     * Elimina una foto específica
     */
    fun deletePhoto(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar foto", e)
            false
        }
    }
}

