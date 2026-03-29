package com.example.landmarklens

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LandmarkViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val TAG = "LandmarkViewModel"

    var lat by mutableDoubleStateOf(0.0)
    var lon by mutableDoubleStateOf(0.0)
    var azimuth by mutableFloatStateOf(0f)

    var capturedBitmap by mutableStateOf<Bitmap?>(null)
    var capturedLat by mutableDoubleStateOf(0.0)
    var capturedLon by mutableDoubleStateOf(0.0)
    var capturedAzimuth by mutableFloatStateOf(0f)
    var showResult by mutableStateOf(false)

    private val appContext: Context get() = getApplication<Application>().applicationContext

    private val sensorManager: SensorManager by lazy {
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val rotationSensor: Sensor? by lazy {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) Log.w(TAG, "TYPE_ROTATION_VECTOR no disponible en este dispositivo")
        sensor
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    private var sensorsRegistered = false

    /** Alta precisión: solo al capturar foto */
    @SuppressLint("MissingPermission")
    fun updateLocationHighAccuracy() {
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let { lat = it.latitude; lon = it.longitude }
                Log.d(TAG, "GPS alta precisión: $lat, $lon")
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error GPS alta precisión: ${e.message}") }
    }

    /** Precisión balanceada: para overlay de cámara y pestaña mapa. Ahorra batería. */
    @SuppressLint("MissingPermission")
    fun updateLocationBalanced() {
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let { lat = it.latitude; lon = it.longitude }
                Log.d(TAG, "GPS balanceado: $lat, $lon")
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error GPS balanceado: ${e.message}") }
    }

    /**
     * Obtiene GPS de alta precisión y captura la foto en el callback,
     * garantizando coordenadas actualizadas en el momento exacto de captura.
     */
    @SuppressLint("MissingPermission")
    fun captureWithHighAccuracyLocation(bitmap: Bitmap) {
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let { lat = it.latitude; lon = it.longitude }
                onPhotoCaptured(bitmap)
            }
            .addOnFailureListener {
                // Si falla el GPS, capturar con las coords que tengamos
                onPhotoCaptured(bitmap)
            }
    }

    fun startSensors() {
        if (sensorsRegistered) return
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            sensorsRegistered = true
            Log.d(TAG, "Sensor de rotación registrado")
        } ?: Log.w(TAG, "No se pudo registrar: sensor no disponible")
    }

    fun stopSensors() {
        if (!sensorsRegistered) return
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
        Log.d(TAG, "Sensor de rotación desregistrado")
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        capturedBitmap = bitmap
        capturedLat = lat
        capturedLon = lon
        capturedAzimuth = azimuth
        showResult = true
    }

    fun resetCapture() {
        capturedBitmap = null
        showResult = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Precisión sensor cambiada: $accuracy")
    }

    override fun onCleared() {
        super.onCleared()
        stopSensors()
        Log.d(TAG, "ViewModel destruido, sensores liberados")
    }
}