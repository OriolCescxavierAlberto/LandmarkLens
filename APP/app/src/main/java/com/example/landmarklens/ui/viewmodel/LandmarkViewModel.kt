package com.example.landmarklens.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.landmarklens.data.local.LandmarkDatabase
import com.example.landmarklens.data.local.LandmarkEntity
import com.example.landmarklens.data.model.AppTab
import com.example.landmarklens.data.model.ChatMessage
import com.example.landmarklens.data.model.LandmarkHistoryItem
import com.example.landmarklens.data.model.LandmarkLocation
import com.example.landmarklens.data.remote.OllamaClient
import com.example.landmarklens.data.remote.PlacesService
import com.example.landmarklens.util.FileUtils
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class LandmarkViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val TAG = "LandmarkViewModel"
    private val dao = LandmarkDatabase.getDatabase(application).landmarkDao()

    // State
    var lat by mutableDoubleStateOf(0.0)
    var lon by mutableDoubleStateOf(0.0)
    var azimuth by mutableFloatStateOf(0f)

    var capturedBitmap by mutableStateOf<Bitmap?>(null)
    var capturedLat by mutableDoubleStateOf(0.0)
    var capturedLon by mutableDoubleStateOf(0.0)
    var capturedAzimuth by mutableFloatStateOf(0f)
    var showResult by mutableStateOf(false)

    val history = mutableStateListOf<LandmarkHistoryItem>()
    var currentTab by mutableStateOf(AppTab.CAMERA)
    
    // Remote Logic state
    var identifiedLocation by mutableStateOf<LandmarkLocation?>(null)
    var isLoadingLocation by mutableStateOf(false)
    var locationError by mutableStateOf<String?>(null)

    // Chat State
    val chatMessages = mutableStateListOf<ChatMessage>()
    var availableModels by mutableStateOf(listOf("Cargando..."))
    var selectedModel by mutableStateOf("Cargando...")
    var chatQuestion by mutableStateOf("")
    var isChatLoading by mutableStateOf(false)

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            dao.getAllLandmarks().collect { entities ->
                history.clear()
                entities.forEach { entity ->
                    val bitmap = FileUtils.loadBitmap(entity.imagePath)
                    if (bitmap != null) {
                        history.add(LandmarkHistoryItem(
                            id = entity.id,
                            bitmap = bitmap,
                            lat = entity.lat,
                            lon = entity.lon,
                            azimuth = entity.azimuth,
                            location = LandmarkLocation(
                                name = entity.locationName ?: "Desconocido",
                                address = entity.locationAddress ?: "",
                                latitude = entity.lat,
                                longitude = entity.lon,
                                type = entity.locationType ?: ""
                            ),
                            timestamp = entity.timestamp
                        ))
                    }
                }
            }
        }
    }

    fun setTab(tab: AppTab) { currentTab = tab }

    fun deleteHistoryItem(item: LandmarkHistoryItem) {
        viewModelScope.launch {
            dao.deleteById(item.id)
            // Nota: Aquí deberíamos borrar el archivo físico también si tuviéramos la ruta exacta
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.deleteAllLandmarks()
            history.clear()
        }
    }

    fun loadModelsIfNeeded() {
        if (availableModels.size == 1 && availableModels.first().contains("Cargando")) {
            viewModelScope.launch {
                val models = OllamaClient.getModels()
                if (models.isNotEmpty()) {
                    availableModels = models
                    selectedModel = models.first()
                }
            }
        }
    }

    fun sendChatMessage(question: String) {
        if (question.isBlank() || isChatLoading) return
        chatMessages.add(ChatMessage(role = "user", text = question))
        chatQuestion = ""
        isChatLoading = true
        viewModelScope.launch {
            try {
                val reply = OllamaClient.askModel(selectedModel, question)
                chatMessages.add(ChatMessage(role = "assistant", text = reply))
            } finally { isChatLoading = false }
        }
    }

    fun fetchLocationInfo(placesService: PlacesService) {
        if (isLoadingLocation || identifiedLocation != null) return
        isLoadingLocation = true
        viewModelScope.launch {
            try {
                val result = placesService.getCompleteLocationInfo(capturedLat, capturedLon)
                identifiedLocation = result
                capturedBitmap?.let { bitmap ->
                    val path = FileUtils.saveBitmap(getApplication(), bitmap, capturedLat, capturedLon, capturedAzimuth)
                    if (path != null) {
                        dao.insertLandmark(LandmarkEntity(
                            imagePath = path,
                            lat = capturedLat, lon = capturedLon, azimuth = capturedAzimuth,
                            locationName = result?.name, locationAddress = result?.address,
                            locationType = result?.type, timestamp = System.currentTimeMillis()
                        ))
                    }
                }
            } catch (e: Exception) { locationError = e.message }
            finally { isLoadingLocation = false }
        }
    }

    fun viewHistoryItem(item: LandmarkHistoryItem) {
        capturedBitmap = item.bitmap; capturedLat = item.lat; capturedLon = item.lon
        capturedAzimuth = item.azimuth; identifiedLocation = item.location
        showResult = true; currentTab = AppTab.CAMERA
    }

    fun resetCapture() {
        capturedBitmap = null; identifiedLocation = null; showResult = false
    }

    // Sensors & Location Infrastructure
    private val sensorManager by lazy { getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val rotationSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(getApplication<Application>()) }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { lat = it.latitude; lon = it.longitude }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    fun stopLocationUpdates() { fusedLocationClient.removeLocationUpdates(locationCallback) }

    @SuppressLint("MissingPermission")
    fun updateLocationBalanced() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { it?.let { lat = it.latitude; lon = it.longitude } }
    }

    @SuppressLint("MissingPermission")
    fun captureWithHighAccuracyLocation(bitmap: Bitmap) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { 
                it?.let { lat = it.latitude; lon = it.longitude }
                onPhotoCaptured(bitmap)
            }
            .addOnFailureListener { onPhotoCaptured(bitmap) }
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        capturedBitmap = bitmap; capturedLat = lat; capturedLon = lon
        capturedAzimuth = azimuth; identifiedLocation = null; showResult = true
    }

    fun startSensors() { rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } }
    fun stopSensors() { sensorManager.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9); SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val orientation = FloatArray(3); SensorManager.getOrientation(matrix, orientation)
        azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
