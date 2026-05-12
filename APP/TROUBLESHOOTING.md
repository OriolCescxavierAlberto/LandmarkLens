# 🔧 Troubleshooting: Problemas Comunes y Soluciones

## ❌ Problema 1: "BUILD FAILED" al compilar

### Síntoma
```
Compilation failed in...
Could not find...
Unresolved reference...
```

### Causa
Faltan dependencias o versiones incompatibles

### Solución
1. Abre `app/build.gradle.kts`
2. Verifica que tienes:
```kotlin
implementation(libs.okhttp)
implementation("org.json:json:20230227")
```
3. Ejecuta: `./gradlew clean build --refresh-dependencies`

---

## ❌ Problema 2: "No se recibió respuesta del servidor"

### Síntoma
- Tomas foto
- Card muestra: "No se recibió respuesta del servidor de análisis"
- Logs: "Error HTTP 0: unable to resolve host"

### Causas Posibles
1. **IP incorrecta**
2. **Puerto incorrecto**
3. **Servidor no corriendo**
4. **Red bloqueada/firewall**
5. **Emulador no puede acceder a host**

### Soluciones

#### Opción A: Verificar IP (Windows)
```bash
# Terminal PowerShell
Test-NetConnection 172.16.110.15 -Port 8000
# Debe decir: "TcpTestSucceeded: True"
```

#### Opción B: Ping desde Emulador
```bash
adb shell
ping 172.16.110.15
# Debe responder sin errores
```

#### Opción C: Probar URL directamente
```bash
curl -v http://172.16.110.15:8000/api/v1/query
# Debe conectar (aunque 405 está bien = POST required)
```

#### Opción D: Cambiar servidor en código
**Archivo:** `RemoteAnalysisService.kt`
```kotlin
private const val ANALYSIS_API_URL = "http://172.16.110.15:8000/api/v1/query"
// Cambiar a:
private const val ANALYSIS_API_URL = "http://127.0.0.1:8000/api/v1/query"
// O:
private const val ANALYSIS_API_URL = "http://tu-servidor.com/api/v1/query"
```

---

## ❌ Problema 3: "Timed out after 30 seconds"

### Síntoma
- Esperas 30 segundos
- Error: "...Connection reset by peer / timeout"
- Logs: "Waited 30s, still no response"

### Causa
Tu API es muy lenta

### Soluciones

#### Opción 1: Aumentar timeout
**Archivo:** `RemoteAnalysisService.kt`
```kotlin
private const val TIMEOUT_SECONDS = 30L
// Cambiar a:
private const val TIMEOUT_SECONDS = 60L  // 60 segundos
```

#### Opción 2: Optimizar tu API
- Cachéa modelos
- Reduce tamaño de imagen procesada
- Usa GPU si está disponible
- Verifica latencia de BD

---

## ❌ Problema 4: "Bad JSON" o campos vacíos

### Síntoma
- Análisis completa
- Pero "landmark" está vacío
- Logs: "Error al parsear respuesta"

### Causa
Tu API devuelve formato diferente

### Solución
Modifica `LandmarkViewModel.kt`:

```kotlin
// EN LUGAR DE:
val landmark = jsonResponse.optString("landmark", "")

// CAMBIA POR (si tu API devuelve "name"):
val landmark = jsonResponse.optString("name", "")

// O SI DEVUELVE "monument":
val landmark = jsonResponse.optString("monument", "")

// O SI QUIERES BUSCAR MÚLTIPLES CAMPOS:
val landmark = jsonResponse.optString("landmark", "")
    ?: jsonResponse.optString("name", "")
    ?: jsonResponse.optString("monument", "")
```

### Verificar Formato con Logcat
```bash
adb logcat | grep "RemoteAnalysisService"
```

Busca línea:
```
D/RemoteAnalysisService: Respuesta exitosa: {"..."}
```

Copia el JSON y úsalo para adaptar.

---

## ❌ Problema 5: App se congela al tomar foto

### Síntoma
- Tomas foto
- UI se congela por 3-5 segundos

### Causa
La consulta API está bloqueando el main thread

### Solución
Verifica en `LandmarkViewModel.kt`:
```kotlin
// DEBE ESTAR ASÍ (en Dispatcher.IO):
suspend fun queryRemoteAnalysis(...) = withContext(Dispatchers.IO) {
    // ...
}

// Y LLAMARSE DESDE viewModelScope:
viewModelScope.launch {
    val response = RemoteAnalysisService.queryRemoteAnalysis(...)
}
```

Si está así, verifica que NO haya `.getBlocking()` en el código.

---

## ❌ Problema 6: No veo logs en logcat

### Síntoma
- Ejecutas la app
- Tomas foto
- No ves nada en `adb logcat`

### Causa
Logs no están llegando correctamente

### Soluciones

#### Opción 1: Limpiar logcat
```bash
adb logcat -c
# Toma foto
# Luego:
adb logcat | grep "RemoteAnalysisService"
```

#### Opción 2: Ver todo sin filtro
```bash
adb logcat | grep -E "Remote|Landmark|Analysis"
```

#### Opción 3: En Android Studio
- Ventana: "Logcat"
- Arriba busca: `RemoteAnalysisService`
- Click en el filtro verde

---

## ❌ Problema 7: "Permission denied" o crash al tomar foto

### Síntoma
```
Permission Denial: ... ACCESS_FINE_LOCATION
SecurityException: Permission required...
```

### Causa
Falta permiso de ubicación

### Solución

#### Paso 1: Verificar AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

#### Paso 2: Conceder permisos manualmente en dispositivo
```
Configuración → Apps → LandmarkLens → Permisos
→ Ubicación: Activar
→ Cámara: Activar
```

#### Paso 3: Forzar solicitud de permisos
```bash
adb shell pm revoke com.example.landmarklens android.permission.ACCESS_FINE_LOCATION
# Reinicia app y acepta permisos cuando pida
```

---

## ❌ Problema 8: API devuelve 400 Bad Request

### Síntoma
Logs: "Error HTTP 400: Bad Request"

### Causa
Parámetros JSON malformados

### Solución
Verifica que se envía exactamente:
```json
{
  "lat": 41.3851,
  "lon": 2.1734,
  "azimuth": 45.0,
  "fov": 70.0
}
```

**No:**
```json
{
  "latitude": 41.3851,  ← INCORRECTO (debe ser "lat")
  "longitude": 2.1734,  ← INCORRECTO (debe ser "lon")
  "bearing": 45.0       ← INCORRECTO (debe ser "azimuth")
}
```

---

## ❌ Problema 9: API devuelve 405 Method Not Allowed

### Síntoma
Logs: "Error HTTP 405: Method Not Allowed"

### Causa
Tu server solo acepta GET, no POST

### Solución
En tu API, añade:
```python
# Flask
@app.route('/api/v1/query', methods=['POST'])
def query():
    ...

# FastAPI
@app.post("/api/v1/query")
def query():
    ...
```

---

## ❌ Problema 10: Se compila pero la app no abre

### Síntoma
- Build successful
- APK crea
- App se cierra al abrir

### Causa
Crash en la app (ver Logcat)

### Soluciones

#### Ver error en logcat
```bash
adb logcat | grep FATAL
# O:
adb logcat | grep CRASH
# O ver últimas líneas:
adb logcat | tail -50
```

#### Errores comunes
- **ClassNotFound:** Faltan dependencias
- **NullPointerException:** Acceso a null
- **SecurityException:** Permisos
- **NetworkOnMainThread:** API en main thread ← NO debería pasar

---

## ✅ Testing Paso a Paso

### Test 1: Compilación
```bash
cd APP
./gradlew clean build
# Debe decir: BUILD SUCCESSFUL
```

### Test 2: Conectividad
```bash
adb shell ping 172.16.110.15
# Debe responder: bytes=... time=...
```

### Test 3: API
```bash
curl -X POST http://172.16.110.15:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"lat":41.3851,"lon":2.1734,"azimuth":0,"fov":70}'
# Debe devolver JSON
```

### Test 4: App
1. Instalar: `adb install app/build/outputs/apk/debug/app-debug.apk`
2. Abrir app
3. Ir a Pestaña 1 (Cámara)
4. Tomar foto
5. Ver "📡 Análisis Remoto"
6. Esperar resultado

### Test 5: Logs
```bash
adb logcat | grep "RemoteAnalysisService"
# Debe mostrar:
# D/RemoteAnalysisService: Enviando análisis remoto...
# D/RemoteAnalysisService: Respuesta exitosa...
```

---

## 🆘 Si Nada Funciona

### Último recurso: Debug Mode

1. Añade logs detallados:
```kotlin
// En RemoteAnalysisService.kt
Log.d(TAG, "Request URL: $ANALYSIS_API_URL")
Log.d(TAG, "Request body: $requestJson")
Log.d(TAG, "Response code: ${response.code}")
Log.d(TAG, "Response body: ${response.body?.string()}")
```

2. Compila y toma foto
3. Copia TODOS los logs:
```bash
adb logcat > logcat_output.txt
```

4. Comparte el archivo o el error específico para ayuda

---

## 📞 Contacto & Soporte

Si el problema persiste:

1. **Revisa:** `INTEGRATION_GUIDE.md`
2. **Verifica:** Conexión a `172.16.110.15:8000`
3. **Prueba:** `curl` desde terminal
4. **Comparte:** Logs de `adb logcat`

No te desanimes, ¡la mayoría de problemas se resuelven en 5 minutos! 🚀

---

**Última actualización:** 2026-05-11
**Problemas cubiertos:** 10+
**Soluciones incluidas:** Automáticas & Manuales

