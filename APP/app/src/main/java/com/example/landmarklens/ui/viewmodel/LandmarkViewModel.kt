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
import com.example.landmarklens.data.model.AnalysisResult
import com.example.landmarklens.data.model.AppTab
import com.example.landmarklens.data.model.ChatMessage
import com.example.landmarklens.data.model.LandmarkHistoryItem
import com.example.landmarklens.data.model.LandmarkLocation
import com.example.landmarklens.data.remote.OllamaClient
import com.example.landmarklens.data.remote.PlacesService
import com.example.landmarklens.data.remote.RemoteAnalysisService
import com.example.landmarklens.util.FileUtils
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    // Remote Analysis Api state
    var remoteAnalysisResult by mutableStateOf<AnalysisResult?>(null)
    var isLoadingRemoteAnalysis by mutableStateOf(false)
    var remoteAnalysisError by mutableStateOf<String?>(null)

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
        
        // Si es el primer mensaje, podemos inyectar contexto
        val isFirstMessage = chatMessages.isEmpty()
        val contextPrompt = if (isFirstMessage) {
            buildContextPrompt(question)
        } else {
            question
        }

        chatMessages.add(ChatMessage(role = "user", text = question))
        chatQuestion = ""
        isChatLoading = true
        viewModelScope.launch {
            try {
                val reply = OllamaClient.askModel(selectedModel, contextPrompt)
                chatMessages.add(ChatMessage(role = "assistant", text = reply))
            } finally { isChatLoading = false }
        }
    }

    private fun buildContextPrompt(question: String): String {
        val location = identifiedLocation?.name ?: "una ubicación desconocida"
        val address = identifiedLocation?.address ?: ""
        val landmark = remoteAnalysisResult?.landmark ?: location
        val description = remoteAnalysisResult?.description ?: ""
        
        // Si no tenemos un landmark claro pero sí un mensaje del servidor, lo usamos
        val serverMessage = remoteAnalysisResult?.message ?: ""
        
        return """
            Contexto: El usuario está en $landmark ($address). 
            Información del monumento: $description
            Mensaje del servidor: $serverMessage
            Coordenadas: $capturedLat, $capturedLon
            
            Pregunta del usuario: $question
            
            Por favor, responde como un guía experto en historia y monumentos, usando el contexto proporcionado.
            Si no se identificó un monumento específico, intenta ayudar al usuario con la información de ubicación general disponible.
        """.trimIndent()
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

    /**
     * Ejecuta un análisis remoto con los parámetros GPS y sensor actuales.
     * Se llama automáticamente después de capturar una foto.
     */
    private fun performRemoteAnalysis() {
        if (isLoadingRemoteAnalysis || capturedLat == 0.0 || capturedLon == 0.0) return
        
        isLoadingRemoteAnalysis = true
        remoteAnalysisError = null
        remoteAnalysisResult = null
        
        Log.d(TAG, "Iniciando análisis remoto - Lat: $capturedLat, Lon: $capturedLon, Azimuth: $capturedAzimuth")
        
        viewModelScope.launch {
            try {
                // Llamar a la API remota con los parámetros capturados según la especificación exacta
                val response = RemoteAnalysisService.queryRemoteAnalysis(
                    latitude = capturedLat,
                    longitude = capturedLon,
                    azimuth = capturedAzimuth,
                    fov = 70f
                )
                
                if (response != null) {
                    // Parsear la respuesta y crear AnalysisResult
                    val result = parseRemoteAnalysisResponse(response)
                    remoteAnalysisResult = result
                    Log.d(TAG, "Análisis remoto completado: ${result.landmark}")
                } else {
                    remoteAnalysisError = "No se recibió respuesta del servidor de análisis"
                    Log.w(TAG, remoteAnalysisError ?: "Unknown error")
                }
            } catch (e: Exception) {
                remoteAnalysisError = e.message ?: "Error desconocido en el análisis"
                Log.e(TAG, "Error en análisis remoto", e)
            } finally {
                isLoadingRemoteAnalysis = false
            }
        }
    }

    private fun parseRemoteAnalysisResponse(jsonResponse: org.json.JSONObject): AnalysisResult {
        return try {
            val status = jsonResponse.optString("status", "unknown")
            val data = jsonResponse.optJSONObject("data") ?: jsonResponse
            val landmarksArray = data.optJSONArray("landmarks")

            if (landmarksArray != null && landmarksArray.length() > 0) {
                val landmarks = mutableListOf<org.json.JSONObject>()
                for (i in 0 until landmarksArray.length()) {
                    landmarks.add(landmarksArray.getJSONObject(i))
                }
                
                // Ordenar por distancia de menor a mayor (la API ya suele darlos ordenados, pero aseguramos)
                val sortedLandmarks = landmarks.sortedBy { it.optDouble("distance", Double.MAX_VALUE) }
                val closest = sortedLandmarks[0]

                // Mapear confianza de texto a valor numérico
                val confidenceScore = when(closest.optString("confidence").lowercase()) {
                    "high" -> 0.95f
                    "medium" -> 0.70f
                    else -> 0.45f
                }

                // Generar descripción legible con las estructuras cercanas
                val descBuilder = StringBuilder()
                descBuilder.append("Se han detectado las siguientes estructuras en las inmediaciones:\n\n")
                
                sortedLandmarks.forEachIndexed { index, item ->
                    val isClosest = index == 0
                    val icon = if (isClosest) "📍 " else "• "
                    val label = if (isClosest) " (LA MÁS CERCANA)" else ""
                    
                    descBuilder.append("$icon${item.optString("name")}$label\n")
                    descBuilder.append("  Distancia: ${item.optInt("distance")} metros\n")
                    descBuilder.append("  Confianza: ${item.optString("confidence")}\n\n")
                }

                val statusDisplay = if (status == "degraded") "Análisis parcial (Ubicación aproximada)" else "Análisis completado"

                AnalysisResult(
                    landmark = closest.optString("name"),
                    confidence = confidenceScore,
                    estimatedDistance = closest.optDouble("distance").toFloat(),
                    description = descBuilder.toString().trim(),
                    message = statusDisplay,
                    rawResponse = jsonResponse.toMap()
                )
            } else {
                AnalysisResult(
                    landmark = "Ubicación detectada",
                    description = "No se encontraron monumentos específicos en la respuesta.",
                    rawResponse = jsonResponse.toMap()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando respuesta: ${e.message}")
            AnalysisResult(
                landmark = "Error",
                description = "No se pudo interpretar la respuesta del servidor."
            )
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
        capturedBitmap = bitmap
        capturedLat = lat
        capturedLon = lon
        capturedAzimuth = azimuth
        identifiedLocation = null
        showResult = true
        
        // Lanzar el análisis remoto automáticamente
        performRemoteAnalysis()
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

    /**
     * Convierte un JSONObject a Map para almacenar en AnalysisResult.
     */
    private fun org.json.JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = this.get(key)
            if (value != org.json.JSONObject.NULL) {
                map[key] = value
            }
        }
        return map
    }
}
