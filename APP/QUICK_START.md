# 🚀 Quick Start: Integración API LandmarkLens

## En 3 Pasos

### Step 1: Verificar que todo esté compilando

```bash
# En la carpeta del proyecto
./gradlew clean build
```

Si hay errores, revisa que no falten imports en `build.gradle.kts`.

### Step 2: Asegurar que la URL de la API es correcta

**Archivo:** `RemoteAnalysisService.kt`

```kotlin
private const val ANALYSIS_API_URL = "http://172.16.110.15:8000/api/v1/query"
```

Si necesitas cambiar la IP:

```kotlin
// Reemplaza esto:
private const val ANALYSIS_API_URL = "http://172.16.110.15:8000/api/v1/query"

// Por tu IP real (ejemplo):
private const val ANALYSIS_API_URL = "http://tu.servidor.com/api/v1/query"
```

### Step 3: Ejecutar y Probar

1. **Inicia la app en el emulador o dispositivo**
2. **Ve a Pestaña 1 (Cámara)**
3. **Toma una foto**
4. Verás automáticamente:
   - ⏳ "Consultando servidor..." (mientras se hace la llamada)
   - ✅ Resultados del análisis (si todo funciona)
   - ❌ Error si la API no responde

---

## 🔍 Verificación de Connectivity

Desde tu terminal/cmd:

```bash
# Verifica que la API está accesible
curl -X POST http://172.16.110.15:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"lat":41.3851,"lon":2.1734,"azimuth":0,"fov":70}'
```

Deberías recibir un JSON como respuesta.

---

## 📱 Ver Logs en Tiempo Real

```bash
adb logcat | grep -E "RemoteAnalysisService|LandmarkViewModel"
```

Esperarás ver mensajes como:
```
D/RemoteAnalysisService: Enviando análisis remoto - Lat: 41.3851, Lon: 2.1734, Azimuth: 0.0, FOV: 70.0
D/RemoteAnalysisService: Respuesta exitosa: {"landmark":"Sagrada Familia","confidence":0.97}
D/LandmarkViewModel: Análisis remoto completado: Sagrada Familia
```

---

## 🎯 Si No Funciona

### "No se recibió respuesta del servidor"

```bash
# 1. Verifica conectividad desde el emulador
adb shell ping 172.16.110.15

# 2. Verifica que tu servidor está corriendo en puerto 8000
netstat -an | grep 8000

# 3. Revisa firewall
# Windows: Asegúrate que 8000 no está bloqueado
```

### "Timed out after 30 seconds"

- Tu servidor es lento
- Aumenta TIMEOUT_SECONDS en `RemoteAnalysisService.kt`:

```kotlin
private const val TIMEOUT_SECONDS = 60L  // Cambiar a 60 segundos
```

### "Bad JSON response"

- Tu API devuelve formato incorrecto
- Revisa `API_RESPONSE_EXAMPLES.md` para el formato esperado

---

## 📋 Archivos Que Se Modificaron

```
✅ NUEVOS:
- app/src/main/java/.../data/remote/RemoteAnalysisService.kt
- app/src/main/java/.../data/model/AnalysisResult.kt

✏️ MODIFICADOS:
- app/src/main/java/.../ui/viewmodel/LandmarkViewModel.kt
- app/src/main/java/.../ui/screens/MainScreen.kt

📖 DOCUMENTACIÓN:
- INTEGRATION_GUIDE.md (este doc)
- API_RESPONSE_EXAMPLES.md (formatos de respuesta)
```

---

## 💡 Pro Tips

### 1. Cachear Resultados

Si quieres evitar múltiples llamadas a la API para la misma ubicación:

```kotlin
// En ViewModel, añade un set de IDs procesados
private val processedLocationIds = mutableSetOf<String>()

fun performRemoteAnalysis() {
    val locationId = "${capturedLat}_${capturedLon}"
    if (locationId in processedLocationIds) {
        // Ya procesado, no llamar de nuevo
        return
    }
    processedLocationIds.add(locationId)
    // ... hacer llamada
}
```

### 2. Testear con String Fijo

Para debugging, devuelve una respuesta fija en la API:

```python
@app.route('/api/v1/query', methods=['POST'])
def query_landmark():
    return jsonify({
        "landmark": "Test Landmark",
        "confidence": 0.95,
        "description": "This is a test"
    }), 200
```

### 3. Ver JSON de Respuesta Completo

En `LandmarkViewModel.kt`, modifica para loguear todo:

```kotlin
remoteAnalysisResult = result
Log.d(TAG, "Raw response: ${result.rawResponse}")  // Añade esto
```

---

## ✅ Checklist de Implementación

- [ ] Compilar sin errores: `./gradlew clean build`
- [ ] IP de la API correcta en `RemoteAnalysisService.kt`
- [ ] Servidor API corriendo en el puerto 8000
- [ ] Permisos INTERNET en AndroidManifest.xml
- [ ] Tomar foto → Ver card de "📡 Análisis Remoto"
- [ ] Logs aparecen en adb logcat
- [ ] Resultados mostrados correctamente en UI

---

## 📞 Debugging con Postman (Opcional)

Si tienes Postman instalado, puedes probar la API directamente:

1. Nuevo request → POST
2. URL: `http://172.16.110.15:8000/api/v1/query`
3. Headers:
   - `Content-Type: application/json`
   - `Accept: application/json`
4. Body (raw):
   ```json
   {
     "lat": 41.3851,
     "lon": 2.1734,
     "azimuth": 45,
     "fov": 70
   }
   ```
5. Click Send

---

¡Eso es todo! La integración está lista para usar. 🎉

Si tienes dudas, revisa:
- `INTEGRATION_GUIDE.md` para detalles técnicos
- `API_RESPONSE_EXAMPLES.md` para formatos JSON
- Logs en `adb logcat`

¡Adelante! 🚀

