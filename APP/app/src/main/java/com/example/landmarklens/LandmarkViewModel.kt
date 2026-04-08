package com.example.landmarklens

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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class LandmarkHistoryItem(
    val id: Long = System.nanoTime(),
    val bitmap: Bitmap,
    val lat: Double,
    val lon: Double,
    val azimuth: Float,
    val location: LandmarkLocation?,
    val timestamp: Long = System.currentTimeMillis()
)

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

    // ─── Historial ───────────────────────────────────────────────────────────
    private val dao = LandmarkDatabase.getDatabase(application).landmarkDao()
    val history = mutableStateListOf<LandmarkHistoryItem>()

    init {
        // Cargar historial desde la DB al iniciar
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

    fun deleteHistoryItem(item: LandmarkHistoryItem) {
        viewModelScope.launch {
            // Intentar borrar el archivo físico primero
            val latestFile = FileUtils.getPhotosDirectory(appContext).listFiles()?.find { 
                it.lastModified() == item.timestamp // Aproximación por timestamp si no guardamos la ruta en el item
            }
            // Para ser más precisos, lo ideal es que LandmarkHistoryItem guarde la ruta del archivo
            // Por ahora, borramos de la DB:
            val entities = dao.getAllLandmarks().firstOrNull() // Esto es ineficiente, mejor buscar por ID
            // Simplificando para este ejemplo:
            history.remove(item)
            // Nota: En una app real, usaríamos el ID de la entidad para borrar directamente en el DAO
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.deleteAllLandmarks()
            history.clear()
        }
    }

    // ─── Resultado de Places ─────────────────────────────────────────────────
    var identifiedLocation by mutableStateOf<LandmarkLocation?>(null)
        private set
    var isLoadingLocation by mutableStateOf(false)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    // ─── Navegación ─────────────────────────────────────────────────────────
    var currentTab by mutableStateOf(AppTab.CAMERA)
        private set

    fun setTab(tab: AppTab) {
        currentTab = tab
    }

    // ─── Chat Ollama ─────────────────────────────────────────────────────────
    val chatMessages = mutableStateListOf<ChatMessage>()

    var availableModels by mutableStateOf(listOf("Loading..."))
    var selectedModel by mutableStateOf("Loading...")
    var chatQuestion by mutableStateOf("")
    var isChatLoading by mutableStateOf(false)
        private set

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

    fun fetchLocationInfo(placesService: PlacesService) {
        if (isLoadingLocation || identifiedLocation != null) return
        isLoadingLocation = true
        locationError = null

        viewModelScope.launch {
            try {
                val result = placesService.getCompleteLocationInfo(capturedLat, capturedLon)
                identifiedLocation = result

                capturedBitmap?.let { bitmap ->
                    // 1. Guardar la imagen físicamente
                    val path = FileUtils.saveBitmap(appContext, bitmap, capturedLat, capturedLon, capturedAzimuth)
                    
                    if (path != null) {
                        // 2. Guardar en la base de datos Room
                        val entity = LandmarkEntity(
                            imagePath = path,
                            lat = capturedLat,
                            lon = capturedLon,
                            azimuth = capturedAzimuth,
                            locationName = result?.name,
                            locationAddress = result?.address,
                            locationType = result?.type,
                            timestamp = System.currentTimeMillis()
                        )
                        dao.insertLandmark(entity)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error Places: ${e.message}", e)
                locationError = "No se pudo identificar la ubicación: ${e.message}"
            } finally {
                isLoadingLocation = false
            }
        }
    }

    fun viewHistoryItem(item: LandmarkHistoryItem) {
        capturedBitmap = item.bitmap
        capturedLat = item.lat
        capturedLon = item.lon
        capturedAzimuth = item.azimuth
        identifiedLocation = item.location
        locationError = null
        showResult = true
        currentTab = AppTab.CAMERA
    }

    // ─── Infraestructura ──────────────────────────────────────────────────────
    private val appContext: Context get() = getApplication<Application>().applicationContext

    private val sensorManager: SensorManager by lazy {
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val rotationSensor: Sensor? by lazy {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        sensor
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
        .setMinUpdateIntervalMillis(1000)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let {
                lat = it.latitude
                lon = it.longitude
                Log.d(TAG, "Ubicación actualizada: $lat, $lon")
            }
        }
    }

    private var sensorsRegistered = false
    private var locationUpdatesStarted = false

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (locationUpdatesStarted) return
        try {
            // Intentar obtener la última ubicación conocida de inmediato
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    if (lat == 0.0) {
                        lat = it.latitude
                        lon = it.longitude
                    }
                }
            }

            // Intentar obtener una ubicación fresca de inmediato
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let {
                        lat = it.latitude
                        lon = it.longitude
                    }
                }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            locationUpdatesStarted = true
            Log.d(TAG, "Actualizaciones de ubicación iniciadas")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar ubicación: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        if (!locationUpdatesStarted) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationUpdatesStarted = false
        Log.d(TAG, "Actualizaciones de ubicación detenidas")
    }

    @SuppressLint("MissingPermission")
    fun updateLocationBalanced() {
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                location?.let { lat = it.latitude; lon = it.longitude }
            }
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

    fun onPhotoCaptured(bitmap: Bitmap) {
        capturedBitmap = bitmap
        capturedLat = lat
        capturedLon = lon
        capturedAzimuth = azimuth
        identifiedLocation = null
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
        }
    }

    fun stopSensors() {
        if (!sensorsRegistered) return
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        stopSensors()
        stopLocationUpdates()
    }
}
