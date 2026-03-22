## 🚀 EXTENSIONES Y FUTURAS IMPLEMENTACIONES

Este documento muestra ejemplos de código para futuras mejoras.

---

## 📸 Extensión 1: Enviar Foto a Ollama Vision

```kotlin
// En OllamaClient.kt - Añadir método:

suspend fun analyzeImage(model: String, bitmap: Bitmap, prompt: String): String = withContext(Dispatchers.IO) {
    return@withContext try {
        // Convertir Bitmap a Base64
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val imageBytes = byteArrayOutputStream.toByteArray()
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)

        val requestJson = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("images", JSONArray().put(base64Image))
            put("stream", false)
        }

        val request = Request.Builder()
            .url("$OLLAMA_BASE_URL/api/generate")
            .post(requestJson.toString().toRequestBody())
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext "Sin respuesta"
            val json = JSONObject(body)
            json.optString("response", "Sin respuesta")
        } else {
            "Error: ${response.message}"
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error al analizar imagen", e)
        "Error: ${e.message}"
    }
}
```

---

## 💾 Extensión 2: Persistencia con SQLite

```kotlin
// Crear: LandmarkDatabase.kt

import androidx.room.*

@Entity(tableName = "landmarks")
data class LandmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val azimuth: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String? = null,
    val modelUsed: String? = null
)

@Dao
interface LandmarkDao {
    @Insert
    suspend fun insert(landmark: LandmarkEntity)

    @Query("SELECT * FROM landmarks ORDER BY timestamp DESC")
    fun getAllLandmarks(): Flow<List<LandmarkEntity>>

    @Query("SELECT * FROM landmarks WHERE id = :id")
    suspend fun getLandmarkById(id: Int): LandmarkEntity?

    @Delete
    suspend fun delete(landmark: LandmarkEntity)
}

@Database(entities = [LandmarkEntity::class], version = 1)
abstract class LandmarkDatabase : RoomDatabase() {
    abstract fun landmarkDao(): LandmarkDao

    companion object {
        @Volatile
        private var INSTANCE: LandmarkDatabase? = null

        fun getInstance(context: Context): LandmarkDatabase {
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
```

---

## 🗺️ Extensión 3: Integración con Google Maps

```kotlin
// En MainApp, añadir pestaña 4:

enum class AppTab { CAMERA, CHAT, ML, MAP }

// Nueva pantalla:
@Composable
fun MapScreen(landmarks: List<LandmarkEntity>) {
    val context = LocalContext.current
    
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                LatLng(landmarks.firstOrNull()?.latitude ?: 0.0,
                       landmarks.firstOrNull()?.longitude ?: 0.0),
                15f
            )
        }
    ) {
        landmarks.forEach { landmark ->
            Marker(
                state = rememberMarkerState(
                    position = LatLng(landmark.latitude, landmark.longitude)
                ),
                title = "Monumento",
                snippet = landmark.description
            )
        }
    }
}
```

---

## 🧠 Extensión 4: TensorFlow Lite para ML Local

```kotlin
// Crear: MLAnalyzer.kt

import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MLAnalyzer(context: Context) {
    private var interpreter: Interpreter? = null
    
    init {
        interpreter = Interpreter(loadModelFile(context, "model.tflite"))
    }

    fun classifyLandmark(bitmap: Bitmap): String {
        val imageProcessor = ImageProcessor.Builder()
            .add(NormalizeOp(127.5f, 127.5f))
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))
        
        val output = Array(1) { FloatArray(10) } // 10 clases de monumentos
        interpreter?.run(tensorImage.buffer, output)

        // Encontrar clase con mayor probabilidad
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: 0
        val confidence = output[0][maxIndex]

        val monuments = arrayOf(
            "Torre Eiffel", "Estatua de la Libertad", "Gran Muralla",
            "Taj Mahal", "Coliseo", "Cristo Redentor", "Machu Picchu",
            "Pirámide de Giza", "Petra", "Big Ben"
        )

        return "${monuments[maxIndex]} (${(confidence * 100).toInt()}%)"
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter?.close()
    }
}
```

---

## 📡 Extensión 5: Backend Sync

```kotlin
// Crear: BackendService.kt

import retrofit2.http.*

interface LandmarkApiService {
    @POST("/api/landmarks")
    suspend fun uploadLandmark(@Body landmark: LandmarkUploadDTO): Response<LandmarkResponse>

    @GET("/api/landmarks")
    suspend fun getLandmarks(): Response<List<LandmarkDTO>>

    @POST("/api/landmarks/{id}/analyze")
    suspend fun requestAnalysis(@Path("id") id: Int): Response<AnalysisResponse>
}

data class LandmarkUploadDTO(
    val photoBase64: String,
    val latitude: Double,
    val longitude: Double,
    val azimuth: Float,
    val timestamp: Long
)

data class LandmarkResponse(
    val id: Int,
    val photoUrl: String,
    val synced: Boolean
)

// Repository pattern
class LandmarkRepository(
    private val apiService: LandmarkApiService,
    private val database: LandmarkDatabase
) {
    suspend fun uploadAndSync(landmark: LandmarkEntity) {
        try {
            val bitmap = BitmapFactory.decodeFile(landmark.photoPath)
            val base64 = encodeToBase64(bitmap)
            
            val dto = LandmarkUploadDTO(
                photoBase64 = base64,
                latitude = landmark.latitude,
                longitude = landmark.longitude,
                azimuth = landmark.azimuth,
                timestamp = landmark.timestamp
            )

            val response = apiService.uploadLandmark(dto)
            if (response.isSuccessful) {
                // Marcar como sincronizado
                Log.d("BackendSync", "Landmark sincronizado exitosamente")
            }
        } catch (e: Exception) {
            Log.e("BackendSync", "Error en sync", e)
        }
    }

    private fun encodeToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(imageBytes)
    }
}
```

---

## 🎨 Extensión 6: Temas Personalizados

```kotlin
// Crear: ThemeManager.kt

sealed class AppTheme {
    object Light : AppTheme()
    object Dark : AppTheme()
    object Custom : AppTheme()
}

val LandmarkLensColorScheme = colorScheme(
    primary = Color(0xFFF39C12),      // Naranja
    secondary = Color(0xFF2C3E50),    // Azul oscuro
    tertiary = Color(0xFF27AE60),     // Verde
    background = Color(0xFFF3F3F3),
    surface = Color.White,
    error = Color(0xFFE74C3C)
)

@Composable
fun LandmarkLensTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) darkColorScheme() else LandmarkLensColorScheme,
        typography = Typography(
            displayLarge = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            bodyMedium = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        ),
        content = content
    )
}
```

---

## 🔐 Extensión 7: Validación de Seguridad

```kotlin
// Añadir a OllamaClient.kt

private fun validateUrl(url: String): Boolean {
    return try {
        val uri = URI(url)
        uri.scheme in listOf("http", "https") &&
        uri.host != null
    } catch (e: Exception) {
        false
    }
}

private fun validateImageSize(bitmap: Bitmap): Boolean {
    return bitmap.byteCount <= 5_000_000 // 5MB máximo
}

suspend fun askModelSafe(model: String, prompt: String): String {
    return try {
        if (prompt.length > 5000) {
            return "Error: Prompt demasiado largo"
        }
        askModel(model, prompt)
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
```

---

## 📊 Extensión 8: Analytics

```kotlin
// Crear: AnalyticsManager.kt

object AnalyticsManager {
    fun logPhotoCapture(lat: Double, lon: Double, azimuth: Float) {
        val data = Bundle().apply {
            putDouble("latitude", lat)
            putDouble("longitude", lon)
            putFloat("azimuth", azimuth)
        }
        // Firebase Analytics
        // FirebaseAnalytics.getInstance(context).logEvent("photo_capture", data)
        Log.d("Analytics", "Photo captured at ($lat, $lon) facing $azimuth°")
    }

    fun logModelUsed(model: String, responseTime: Long) {
        Log.d("Analytics", "Model: $model, Response time: ${responseTime}ms")
    }

    fun logPermissionsGranted(permissions: List<String>) {
        Log.d("Analytics", "Permissions granted: $permissions")
    }
}
```

---

## 🧪 Extensión 9: Pruebas Unitarias

```kotlin
// Crear: OllamaClientTest.kt

import org.junit.Test
import kotlinx.coroutines.runBlocking

class OllamaClientTest {
    @Test
    fun testGetModels() = runBlocking {
        val models = OllamaClient.getModels()
        assert(models.isNotEmpty())
    }

    @Test
    fun testAskModel() = runBlocking {
        val response = OllamaClient.askModel("mistral", "¿Qué es Madrid?")
        assert(response.isNotEmpty())
        assert(response != "Error: ")
    }

    @Test
    fun testValidateUrl() {
        val validUrl = "http://localhost:11434"
        assert(OllamaClient.validateUrl(validUrl))
    }
}
```

---

## 📝 Extensión 10: Geolocalización Inversa

```kotlin
// Crear: GeocodeManager.kt

import android.location.Geocoder

class GeocodeManager(private val context: Context) {
    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun getAddressFromCoordinates(lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    buildString {
                        append(address.thoroughfare ?: "")
                        append(" ")
                        append(address.locality ?: "")
                        append(", ")
                        append(address.adminArea ?: "")
                        append(", ")
                        append(address.countryName ?: "")
                    }.trim()
                } else {
                    "$lat, $lon"
                }
            } catch (e: Exception) {
                Log.e("Geocode", "Error", e)
                "$lat, $lon"
            }
        }
    }
}
```

---

## 🔄 Paso a Paso: Implementar Análisis de Imagen

1. **Actualizar OllamaClient.kt** con método `analyzeImage()`
2. **Crear data class** para respuesta de IA
3. **En CameraLandmarkScreen**, después de capturar:
   ```kotlin
   val analysis = OllamaClient.analyzeImage(
       model = "llava",
       bitmap = viewModel.capturedBitmap,
       prompt = "Identifica este monumento y describe sus características"
   )
   ```
4. **Mostrar análisis** en pantalla de resultado
5. **Guardar análisis** junto a metadatos

---

¡Estas extensiones te permitirán llevar LandmarkLens al siguiente nivel! 🚀

