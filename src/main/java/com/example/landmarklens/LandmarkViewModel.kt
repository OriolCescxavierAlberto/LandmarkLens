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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

class LandmarkViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val TAG = "LandmarkViewModel"

    // ─── GPS + sensores ───────────────────────────────────────────────────────
    var lat by mutableDoubleStateOf(0.0)
    var lon by mutableDoubleStateOf(0.0)
    var azimuth by mutableFloatStateOf(0f)

    // ─── Captura de foto ──────────────────────────────────────────────────────
    var capturedBitmap by mutableStateOf<Bitmap?>(null)
    var capturedLat by mutableDoubleStateOf(0.0)
    var capturedLon by mutableDoubleStateOf(0.0)
    var capturedAzimuth by mutableFloatStateOf(0f)
    var showResult by mutableStateOf(false)

    // ─── Resultado de Places (antes vivía en CaptureResultScreen) ─────────────
    var identifiedLocation by mutableStateOf<LandmarkLocation?>(null)
        private set
    var isLoadingLocation by mutableStateOf(false)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    // ─── Navegación (sobrevive rotación) ──────────────────────────────────────
    var currentTab by mutableStateOf(AppTab.CAMERA)
        private set

    fun setTab(tab: AppTab) {
        currentTab = tab
    }

    // ─── Chat Ollama (sobrevive rotación) ─────────────────────────────────────
    val chatMessages = mutableStateListOf<ChatMessage>()

    var availableModels by mutableStateOf(listOf("Loading..."))
    var selectedModel by mutableStateOf("Loading...")
    var chatQuestion by mutableStateOf("")
    var isChatLoading by mutableStateOf(false)
        private set

    /** Carga los modelos disponibles desde Ollama si aún no se han cargado. */
    fun loadModelsIfNeeded() {
        if (availableModels.size == 1 && availableModels.first() == "Loading...") {
            viewModelScope.launch {
                val models = OllamaClient.getModels()
                availableModels = models
                if (selectedModel == "Loading...") {
                    selectedModel = models.firstOrNull() ?: "No models"
                }
            }
        }
    }

    /** Envía una pregunta al modelo Ollama y añade los mensajes al historial. */
    fun sendChatMessage(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || isChatLoading) return

        chatMessages.add(ChatMessage(role = "user", text = trimmed))
        chatQuestion = ""
        isChatLoading = true

        viewModelScope.launch {
            try {
                val reply = OllamaClient.askModel(selectedModel, trimmed)
                chatMessages.add(ChatMessage(role = "assistant", text = reply))
            } catch (e: Exception) {
                chatMessages.add(ChatMessage(role = "assistant", text = "Error: ${e.message}"))
            } finally {
                isChatLoading = false
            }
        }
    }

    // ─── Places / resultado de captura ────────────────────────────────────────

    /** Llama a PlacesService y guarda el resultado en el ViewModel. */
    fun fetchLocationInfo(placesService: PlacesService) {
        if (isLoadingLocation || identifiedLocation != null) return
        isLoadingLocation = true
        locationError = null

        viewModelScope.launch {
            try {
                identifiedLocation = placesService.getCompleteLocationInfo(capturedLat, capturedLon)
            } catch (e: Exception) {
                Log.e(TAG, "Error Places: ${e.message}", e)
                locationError = "No se pudo identificar la ubicación: ${e.message}"
            } finally {
                isLoadingLocation = false
            }
        }
    }

    // ─── Infraestructura ──────────────────────────────────────────────────────
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

    @SuppressLint("MissingPermission")
    fun captureWithHighAccuracyLocation(bitmap: Bitmap) {
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let { lat = it.latitude; lon = it.longitude }
                onPhotoCaptured(bitmap)
            }
            .addOnFailureListener { onPhotoCaptured(bitmap) }
    }

    /** Guarda el bitmap y resetea el resultado de Places para la nueva captura. */
    fun onPhotoCaptured(bitmap: Bitmap) {
        capturedBitmap = bitmap
        capturedLat = lat
        capturedLon = lon
        capturedAzimuth = azimuth
        identifiedLocation = null   // limpia resultado anterior
        locationError = null
        showResult = true
    }

    fun resetCapture() {
        capturedBitmap = null
        identifiedLocation = null
        locationError = null
        showResult = false
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