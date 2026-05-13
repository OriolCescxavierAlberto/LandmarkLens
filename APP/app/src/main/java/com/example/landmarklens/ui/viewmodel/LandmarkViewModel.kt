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
    private var chatJob: kotlinx.coroutines.Job? = null

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
        viewModelScope.launch {
            val models = OllamaClient.getModels()
            if (models.isNotEmpty()) {
                availableModels = models
                // Priorizar llama3.2:3b si está disponible
                val preferred = models.find { it.contains("3.2:3b") } ?: models.first()
                selectedModel = preferred
            }
        }
    }

    fun startNewChat(question: String) {
        chatJob?.cancel()
        isChatLoading = false
        chatMessages.clear()
        sendChatMessage(question)
    }

    fun sendChatMessage(question: String) {
        if (question.isBlank() || isChatLoading) return
        
        // Si es el primer mensaje, podemos inyectar contexto
        val contextPrompt = if (chatMessages.isEmpty()) {
            buildContextPrompt(question)
        } else {
            question
        }

        chatMessages.add(ChatMessage(role = "user", text = question))
        chatQuestion = ""
        isChatLoading = true
        chatJob = viewModelScope.launch {
            try {
                // Asegurarse de que el modelo seleccionado sea válido antes de preguntar
                if (selectedModel == "Cargando...") {
                    val models = OllamaClient.getModels()
                    if (models.isNotEmpty()) {
                        availableModels = models
                        selectedModel = models.find { it.contains("3.2:3b") } ?: models.first()
                    }
                }
                
                val reply = OllamaClient.askModel(selectedModel, contextPrompt)
                chatMessages.add(ChatMessage(role = "assistant", text = reply))
            } catch (e: Exception) {
                Log.e(TAG, "Error en chat: ${e.message}")
                chatMessages.add(ChatMessage(role = "assistant", text = "Lo siento, no puedo responder en este momento. Asegúrate de que Ollama esté corriendo."))
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
            ERES UN GUÍA ARQUITECTÓNICO Y CULTURAL EXPERTO, ESPECIALIZADO EXCLUSIVAMENTE EN ESTRUCTURAS FÍSICAS, EDIFICIOS Y MONUMENTOS TANGIBLES.
        
            TU MISIÓN:
            Analizar la información proporcionada y responder a la pregunta del usuario centrándote ÚNICAMENTE en las construcciones físicas detectadas.
        
            REGLAS ESTRICTAS E INQUEBRANTABLES:
            1. FOCO EXCLUSIVO EN ESTRUCTURAS: Tu respuesta debe centrarse al 100% en la estructura, edificio o monumento principal.
               - PERMITIDO: Sagrada Familia, Arco de Triunfo, Castillo de Montjuic, Casa Batlló, murallas, iglesias, fábricas históricas, estatuas. Habla sobre su arquitectura, estilo, historia, arquitecto y materiales.
            2. IGNORAR VÍAS PÚBLICAS Y ESPACIOS ABIERTOS: Está TOTALMENTE PROHIBIDO mencionar o centrar la respuesta en calles, avenidas, carreteras, plazas, parques o barrios. 
               - NO PERMITIDO: "Estás en la calle...", "Esta plaza es famosa por...", "El parque tiene...".
            3. REDIRECCIÓN ACTIVA: Si el usuario pregunta por una calle o plaza (ej. "¿Qué hay en esta calle?"), ignora la calle y redirige la conversación inmediatamente hacia el edificio o monumento más relevante detectado en los datos (ej. "Justo aquí destaca la majestuosa estructura de...").
            4. CERO DATOS TÉCNICOS: Bajo ninguna circunstancia menciones coordenadas GPS, identificadores de OpenStreetMap (osm_id), puntuaciones (fame_score) o datos del servidor. Usa esa información solo para entender el contexto, no para hablar.
            5. TONO: Educativo, fascinante y directo. Eres un guía turístico experto compartiendo los secretos de una construcción.
        
            DATOS DE LA OBSERVACIÓN (Contexto para tu análisis):
            - Estructura principal detectada: $landmark
            - Ubicación aproximada: $address
            - Detalles de estructuras cercanas y categorías: $description
            - Estado del sistema: $serverMessage
        
            PREGUNTA DEL USUARIO:
            "$question"
        
            INSTRUCCIÓN FINAL: Responde a la pregunta del usuario en el idioma en que te ha preguntado, aplicando estrictamente las reglas anteriores y describiendo únicamente las estructuras.
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
            
            // Intentar obtener landmarks del objeto 'data' o directamente del root
            var landmarksArray = data.optJSONArray("landmarks")
            
            // Si no está, intentar parsear 'raw_response' que a veces viene como string JSON
            if (landmarksArray == null) {
                val rawRespStr = jsonResponse.optString("raw_response")
                if (rawRespStr.isNotBlank()) {
                    try {
                        val innerJson = org.json.JSONObject(rawRespStr)
                        landmarksArray = innerJson.optJSONArray("landmarks")
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo parsear raw_response como JSON")
                    }
                }
            }

            if (landmarksArray != null && landmarksArray.length() > 0) {
                val allLandmarks = mutableListOf<org.json.JSONObject>()
                for (i in 0 until landmarksArray.length()) {
                    allLandmarks.add(landmarksArray.getJSONObject(i))
                }
                
                // Filtrar para obtener solo monumentos y edificios de interés histórico/turístico
                // Excluimos: hoteles, hostales, apartamentos, galerías, tiendas, restaurantes, parkings, bancos y vías públicas.
                val blackList = listOf(
                    "hotel", "hostel", "apart", "gallery", "shop", "tienda", "restaurant", "restaurante",
                    "parking", "carrer ", "calle ", "avinguda", "avenida", "plaça ", "plaza ", "passatge", 
                    "passeig", "bank", "banco", "oficina", "clinic", "hospital", "school", "colegio", 
                    "university", "universidad", "bar ", "pub ", "cafe ", "supermarket", "supermercado",
                    "farmacia", "pharmacy", "gym", "gimnasio", "station", "estación", "stop", "parada",
                    "oficina", "correos"
                )

                val filteredLandmarks = allLandmarks.filter { item ->
                    val name = item.optString("name").lowercase()
                    !blackList.any { keyword -> name.contains(keyword) }
                }.sortedBy { it.optDouble("distance", Double.MAX_VALUE) }
                 .take(5) // LIMITAR A MÁXIMO 5 MONUMENTOS

                if (filteredLandmarks.isEmpty()) {
                    return AnalysisResult(
                        landmark = "Ubicación detectada",
                        description = "No se encontraron monumentos o edificios históricos específicos en la vecindad inmediata.",
                        message = status,
                        rawResponse = jsonResponse.toMap()
                    )
                }

                val closest = filteredLandmarks[0]

                // Mapear confianza de texto a valor numérico
                val confidenceScore = when(closest.optString("confidence").lowercase()) {
                    "high" -> 0.95f
                    "medium" -> 0.70f
                    else -> 0.45f
                }

                // Generar descripción legible con las estructuras cercanas (máximo 5)
                val descBuilder = StringBuilder()
                descBuilder.append("Se han detectado los siguientes monumentos y edificios históricos:\n\n")
                
                filteredLandmarks.forEachIndexed { index, item ->
                    val isClosest = index == 0
                    val icon = if (isClosest) "🏛️ " else "• "
                    val label = if (isClosest) " (MONUMENTO PRINCIPAL)" else ""
                    
                    descBuilder.append("$icon${item.optString("name")}$label\n")
                    descBuilder.append("  Distancia: ${item.optInt("distance")} metros\n")
//                    descBuilder.append("  Confianza: ${item.optString("confidence")}\n\n")
                }

                val statusDisplay = if (status == "degraded") "Análisis parcial" else "Análisis completado"

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
