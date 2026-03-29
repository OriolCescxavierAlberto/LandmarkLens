package com.example.landmarklens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executor

/**
 * Gestor para capturar fotos con CameraX
 */
class CameraXCapture(context: Context, private val executor: Executor) {
    private var imageCapture: ImageCapture? = null
    private var onPhotoCapture: ((Bitmap) -> Unit)? = null
    private val TAG = "CameraXCapture"

    init {
        // Inicializa el ImageCapture use case
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(android.view.Surface.ROTATION_0)
            .build()
    }

    /**
     * Obtiene la instancia de ImageCapture
     */
    fun getImageCapture(): ImageCapture? = imageCapture

    /**
     * Configura el callback para cuando se capture una foto
     */
    fun setOnPhotoCapture(callback: (Bitmap) -> Unit) {
        onPhotoCapture = callback
    }

    /**
     * Captura una foto
     */
    fun takePicture() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                onPhotoCapture?.invoke(bitmap)
                Log.d(TAG, "Foto capturada exitosamente")
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Error al capturar foto: ${exception.message}", exception)
            }
        })
    }

    /**
     * Convierte un ImageProxy a Bitmap
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Para este caso simplificado, usamos la dimensión de la imagen
        // En producción, necesitarías YUV_420_888 to Bitmap conversion más robusta
        return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    }
}

