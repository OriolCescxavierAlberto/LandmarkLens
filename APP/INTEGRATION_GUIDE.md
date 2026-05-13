# Guía de Implementación: API de Análisis Remoto para LandmarkLens

## 📋 Resumen de Cambios

Se ha implementado la integración con tu API de análisis remoto en `http://172.16.110.15:8000/api/v1/query`. Cuando el usuario toma una foto usando la cámara, la aplicación:

1. Captura la imagen, coordenadas GPS (lat/lon) y azimut (brújula)
2. Envía automáticamente estos datos a tu API remota
3. Muestra los resultados del análisis en una carta mejorada en la UI

---

## 📦 Archivos Nuevos/Modificados

### ✅ Nuevos Archivos:

1. **`RemoteAnalysisService.kt`** (data/remote/)
   - Servicio singleton que maneja la comunicación con tu API
   - Método principal: `queryRemoteAnalysis(lat, lon, azimuth, fov)`
   - Utiliza OkHttp para peticiones HTTP POST

2. **`AnalysisResult.kt`** (data/model/)
   - Data class que almacena la respuesta del análisis
   - Campos: `landmark`, `confidence`, `description`, `category`, `historicalInfo`, etc.
   - Mapea automáticamente la respuesta JSON de la API

### 🔧 Modificados:

1. **`LandmarkViewModel.kt`**
   - Añadido estado: `remoteAnalysisResult`, `isLoadingRemoteAnalysis`, `remoteAnalysisError`
   - Método `performRemoteAnalysis()` que dispara la consulta
   - Método `parseRemoteAnalysisResponse()` que parsea la respuesta JSON
   - Integración en `onPhotoCaptured()` para llamar automáticamente al análisis

2. **`MainScreen.kt`**
   - Nueva sección en `CaptureResultScreen` con Card de "📡 Análisis Remoto"
   - Muestra estado de carga, errores, o resultados del análisis
   - Componentes visuales para confianza, descripción e información histórica

---

## 🚀 Flujo de Ejecución

```
Usuario toma foto
    ↓
captureWithHighAccuracyLocation() → onPhotoCaptured()
    ↓
performRemoteAnalysis() [automático]
    ↓
RemoteAnalysisService.queryRemoteAnalysis()
    ↓
POST http://172.16.110.15:8000/api/v1/query
Body: { lat, lon, azimuth: 70 }
    ↓
Esperar respuesta...
    ↓
parseRemoteAnalysisResponse() → AnalysisResult
    ↓
Actualizar UI en CaptureResultScreen
```

---

## 📡 Detalles de la API

### Petición HTTP

```bash
POST http://172.16.110.15:8000/api/v1/query
Content-Type: application/json

{
  "lat": 41.3851,
  "lon": 2.1734,
  "azimuth": 45.5,
  "fov": 70.0
}
```

### Respuesta Esperada (JSON adaptable)

Tu API puede devolver JSON con los siguientes campos (el parser es flexible):

```json
{
  "id": "unique-id",
  "landmark": "Sagrada Familia",
  "confidence": 0.95,
  "category": "Monumento Histórico",
  "description": "Basílica de la Sagrada Familia en Barcelona",
  "historical_info": "Diseñada por Antoni Gaudí en 1883...",
  "distance": 150.5,
  // ... otros campos que necesites
}
```

**Nota:** El parser busca estos campos en orden: `landmark` → `name` → `monument`. 
Puedes adaptar el método `parseRemoteAnalysisResponse()` según tu respuesta exacta.

---

## 🔌 Cómo Adaptar la Respuesta de tu API

Si tu API devuelve un format diferente, modifica en `LandmarkViewModel.kt`:

```kotlin
private fun parseRemoteAnalysisResponse(jsonResponse: org.json.JSONObject): AnalysisResult {
    // Adapta estos campos a los que devuelve tu API
    val landmark = jsonResponse.optString("tu_campo_nombre", "")
    val confidence = jsonResponse.optDouble("tu_campo_confianza", 0.0).toFloat()
    // ... etc
}
```

---

## 🎨 Componentes Visuales

### Card de Análisis Remoto en CaptureResultScreen

La UI muestra:
- **Estado de carga:** Spinner con "Consultando servidor..."
- **Error:** Mensaje de error si la API falla
- **Resultado:** 
  - Nombre del monumento
  - Categoría
  - Nivel de confianza (%)
  - Descripción
  - Información histórica (expandible)

### Log de Depuración

Para monitorear las llamadas en logcat:

```bash
adb logcat | grep "RemoteAnalysisService\|LandmarkViewModel"
```

---

## ⚙️ Configuración Requerida

### 1. Permisos en AndroidManifest.xml (ya deberían estar)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
```

### 2. Dependencias (ya instaladas en build.gradle.kts)

- OkHttp (para HTTP)
- Kotlin Coroutines
- Android Location Services

---

## 🧪 Prueba Rápida

1. Abre la app en Pestaña 1 (Cámara)
2. Toma una foto
3. Revisa logcat para ver:
   ```
   [RemoteAnalysisService] Enviando análisis remoto - Lat: 41.3851, Lon: 2.1734...
   [RemoteAnalysisService] Respuesta exitosa: {...}
   [LandmarkViewModel] Análisis remoto completado: Sagrada Familia
   ```
4. En la UI verás la card "📡 Análisis Remoto" con los resultados

---

## 🐛 Troubleshooting

### Problema: "No se recibió respuesta del servidor"
- **Causa:** Tu API no es accesible desde el dispositivo
- **Solución:** 
  - Verifica que `172.16.110.15:8000` es la IP correcta
  - Prueba desde terminal: `curl http://172.16.110.15:8000/api/v1/query`

### Problema: Timeout en la solicitud
- **Causa:** La API tarda demasiado
- **Solución:** Aumenta TIMEOUT_SECONDS en `RemoteAnalysisService.kt` (actualmente 30s)

### Problema: Campos vacíos en resultados
- **Causa:** Tu API devuelve distintos nombres de campos
- **Solución:** Adapta `parseRemoteAnalysisResponse()` según tu respuesta

---

## 🔄 Próximos Pasos (Sugerencias)

1. **Cachéo de resultados:** Guardar análisis en BD local (Room ya está configurado)
2. **Historial de análisis:** Mostrar análisis previos en Pestaña 2 (Mapa)
3. **Análisis en tiempo real:** Mostrar análisis mientras deslizas la cámara (no solo al capturar)
4. **Comparación:** Mostrar monumentos cercanos alternativos
5. **Mejoras visuales:** Animaciones al recibir resultados

---

## 📞 Soporte

Para dudas o problemas:
- Revisa el logcat con tag `RemoteAnalysisService`
- Verifica conectividad: `adb shell ping 172.16.110.15`
- Revisa la estructura JSON de tu API

¡Listo para funcionar! 🚀

