## 🔑 CONFIGURACIÓN DE GOOGLE MAPS Y PLACES API

### 1. Obtener API Key en Google Cloud Console

#### Pasos:

1. **Accede a Google Cloud Console**
   - URL: https://console.cloud.google.com/
   - Inicia sesión con tu cuenta Google

2. **Crear un nuevo proyecto** (si no tienes uno)
   - Click en el dropdown de proyectos (arriba izquierda)
   - "NEW PROJECT"
   - Nombre: "LandmarkLens"
   - Crea el proyecto

3. **Habilitar APIs necesarias**
   - En la barra de búsqueda busca: "Google Maps Android API"
   - Click en el resultado y selecciona "ENABLE"
   
   - Busca: "Places API"
   - Click en el resultado y selecciona "ENABLE"
   
   - Busca: "Geocoding API"
   - Click en el resultado y selecciona "ENABLE"

4. **Crear API Key**
   - Ve a: "Credenciales" (menú izquierdo)
   - Click en "+ CREATE CREDENTIALS" → "API Key"
   - Se generará una clave (ej: AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0)
   - COPIA esta clave

5. **Restringir la API Key (Recomendado)**
   - Click en la API Key creada
   - Bajo "Application restrictions", selecciona "Android apps"
   - Click en "Add package name and fingerprint"
   - Necesitas tu: Package Name y Fingerprint SHA-1

#### Obtener Fingerprint SHA-1:

```bash
# En tu proyecto Android, ejecuta:
./gradlew.bat signingReport

# Busca en la salida:
# Task :app:signingReport
# ...
# SHA1: AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD
```

- **Package Name**: com.example.landmarklens
- **SHA-1 Fingerprint**: La obtenida del comando anterior

---

### 2. Configurar API Key en Android

#### Opción A: En AndroidManifest.xml (Recomendado)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- ... otros permisos ... -->

    <application
        <!-- ... atributos ... -->
        >
        
        <!-- Configurar Google Maps API Key -->
        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0" />
        
        <!-- Configurar Places API Key -->
        <meta-data
            android:name="com.google.android.libraries.places.API_KEY"
            android:value="AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0" />

        <!-- ... activities ... -->
        
    </application>

</manifest>
```

#### Opción B: En código (En MainActivity.kt)

```kotlin
import com.google.android.libraries.places.api.Places

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar Places API
        if (!Places.isInitialized()) {
            Places.initialize(
                applicationContext,
                "AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0"
            )
        }
        
        enableEdgeToEdge()
        setContent {
            // ... tu composable ...
        }
    }
}
```

---

### 3. Permisos en AndroidManifest.xml

Asegúrate de tener estos permisos configurados:

```xml
<!-- Permisos de ubicación (requerido para reverse geocoding) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Permiso de internet (requerido para Maps y Places) -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Permiso para cámara -->
<uses-permission android:name="android.permission.CAMERA" />
```

---

### 4. Importancia de Scopes en Places API

Si usas Places SDK directamente, necesitas especificar qué campos deseas:

```kotlin
// En PlacesService.kt (ejemplo de uso avanzado)

val fields = listOf(
    Place.Field.ID,
    Place.Field.NAME,
    Place.Field.ADDRESS,
    Place.Field.LAT_LNG,
    Place.Field.PLACE_TYPE,
    Place.Field.RATING
)

val request = FindCurrentPlaceRequest.newInstance(fields)

placesClient.findCurrentPlace(request)
    .addOnSuccessListener { response ->
        for (placeLikelihood in response.placeLikelihoods) {
            Log.d("Places", "Place: ${placeLikelihood.place.name}")
        }
    }
```

---

### 5. Costos y Limitaciones

#### **Reverse Geocoding (Geocoder nativo)**
- ✅ GRATUITO (limitado, sin API Key requerida)
- Precisión variable según región
- Funciona offline en algunos casos

#### **Google Maps**
- 💰 $0.007 USD por visita (después de 25,000 visitas gratis)
- Mapas estáticos: $1.33 por 1,000 solicitudes

#### **Google Places API**
- 💰 $0.01-0.17 USD por solicitud según tipo
- 150 solicitudes diarias gratis para desarrollo

**Estimación de costo**: Con 100 usuarios tomando 5 fotos diarias:
- Reverse Geocoding: GRATIS
- Maps: ~$35/mes
- Places API: ~$2,500/mes (usar con cuidado)

---

### 6. Alternativas Económicas

#### Opción 1: Solo Reverse Geocoding (Recomendado)
```kotlin
// Sin API Key
val geocoder = Geocoder(context, Locale.getDefault())
val addresses = geocoder.getFromLocation(lat, lon, 1)
// ✅ GRATIS
```

#### Opción 2: Mapas sin API Key (OSM)
```kotlin
// Usar OpenStreetMap en lugar de Google Maps
implementation("org.osmdroid:osmdroid-android:6.1.13")
```

#### Opción 3: Caché y Optimización
```kotlin
// Almacenar resultados para evitar llamadas repetidas
// Ver PlacesService.kt con SQLite
```

---

### 7. Prueba de Configuración

En MainActivity.kt, puedes verificar que la API está correctamente configurada:

```kotlin
LaunchedEffect(Unit) {
    try {
        // Esto fallará si API Key no está configurada
        val placesClient = Places.createClient(context)
        Log.d("LandmarkLens", "✅ Google Places API inicializado correctamente")
    } catch (e: Exception) {
        Log.e("LandmarkLens", "❌ Error al inicializar Places API: ${e.message}")
    }
}
```

---

### 8. Seguridad: Proteger tu API Key

❌ **NO HAGAS ESTO**:
```kotlin
// NUNCA hardcodees la API key en el código
const val API_KEY = "AIzaSyD3j5k8_2k0q8..." // ❌ INSEGURO
```

✅ **HAZ ESTO MEJOR**:

1. **Usa restricciones de API Key**
   - Restringir por tipo de aplicación (Android)
   - Restringir por package name y SHA-1
   - Restringir por APIs específicas

2. **Almacena en AndroidManifest.xml**
   - Las meta-data en AndroidManifest son compiladas en el APK
   - Google detecta automáticamente maluso

3. **Para producción, usa backend**
   ```kotlin
   // El cliente solicita al backend
   // El backend valida con API Key segura en servidor
   ```

---

### 9. Verificar que todo funciona

Después de configurar:

1. Limpia y reconstruye el proyecto:
   ```bash
   ./gradlew.bat clean build
   ```

2. Ejecuta la app:
   ```bash
   ./gradlew.bat installDebug
   ```

3. Toma una foto en la Pestaña 1

4. Verifica en Logcat:
   ```bash
   adb logcat | grep -E "CaptureResultScreen|PlacesService|GoogleMap"
   ```

5. Deberías ver:
   - `CaptureResultScreen: Ubicación identificada: [Nombre del lugar]`
   - Mapa renderizado en pantalla

---

### 10. Troubleshooting

| Error | Solución |
|-------|----------|
| "Places API not initialized" | Falta meta-data en AndroidManifest.xml o API no habilitada en Cloud Console |
| Mapa aparece gris | API Key inválida o no configurada. Verifica AndroidManifest.xml |
| Reverse geocoding retorna null | Geocoder nativo sin red. Es normal en emulador. |
| "Quota exceeded" | Estableciste límites muy bajos en Cloud Console. Aumenta cuota. |
| SHA-1 no coincide | Asegúrate de usar el SHA-1 correcto del keystore de firma. |

---

**¡Lista tu app para usar Google Maps y Places!** 🗺️📍

