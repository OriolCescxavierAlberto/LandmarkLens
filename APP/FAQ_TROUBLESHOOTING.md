## ❓ FAQ & TROUBLESHOOTING - LandmarkLens

---

## 🎥 PROBLEMAS CON LA CÁMARA

### P: "La cámara no se abre"
**R:** 
1. Verifica que otorgaste permisos de cámara
2. En emulador: Asegúrate que tiene cámara simulada habilitada
3. Logs: `adb logcat | grep CameraLandmarkScreen`

### P: "Veo pantalla negra en lugar de vista previa"
**R:**
1. Algunos dispositivos necesitan permisos adicionales
2. Intenta reiniciar la app
3. Si es emulador: Reinicia el emulador
4. Verifica que build.gradle tiene CameraX 1.4+

### P: "Se congela cuando tomo foto"
**R:**
1. El bitmap del PreviewView puede ser null
2. Verifica logs: `adb logcat | grep "Error al capturar"`
3. Considera usar CameraX ImageCapture directamente (ver EXTENSIONES.md)

---

## 📍 PROBLEMAS CON GPS

### P: "GPS muestra 0.0, 0.0"
**R:**
1. **En dispositivo real:**
   - Activa GPS en ajustes
   - Espera 30 segundos para primera lectura
   - Ve a un lugar abierto (no dentro)

2. **En emulador:**
   ```bash
   # Simula ubicación
   adb shell am send-trim-memory com.example.landmarklens MODERATE
   
   # O abre Extended Controls y configura lat/lon manualmente
   ```

3. **Verifica permisos:**
   ```bash
   adb shell pm grant com.example.landmarklens android.permission.ACCESS_FINE_LOCATION
   ```

### P: "GPS tiene precisión baja (error > 50 metros)"
**R:**
1. Usa PRIORITY_HIGH_ACCURACY (ya configurado)
2. Espera más tiempo para mejor triangulación
3. En emulador configura manualmente mejor ubicación

### P: "Solo veo latitud/longitud, sin acimut"
**R:**
1. El acimut requiere SensorManager
2. Algunos emuladores no simulan acelerómetro
3. En dispositivo real: Gira el teléfono para calibrar brújula

---

## 🤖 PROBLEMAS CON OLLAMA

### P: "Error: Cannot connect to localhost:11434"
**R:**
1. Verifica que Ollama está corriendo:
   ```bash
   ollama serve
   ```

2. **Desde emulador** (usar IP especial):
   ```kotlin
   // En OllamaClient.kt, cambiar:
   private const val OLLAMA_BASE_URL = "http://10.0.2.2:11434"
   ```

3. **Desde dispositivo en red local:**
   ```kotlin
   // Obtén IP del PC
   ipconfig getifaddr en0  // Mac/Linux
   ipconfig             // Windows
   
   // Luego en OllamaClient.kt:
   private const val OLLAMA_BASE_URL = "http://192.168.x.x:11434"
   ```

### P: "Modelo no carga o lista vacía"
**R:**
1. Descarga modelo manualmente:
   ```bash
   ollama pull mistral
   ollama pull neural-chat
   ```

2. Verifica modelos disponibles:
   ```bash
   curl http://localhost:11434/api/tags
   ```

3. Logs en app:
   ```bash
   adb logcat | grep OllamaClient
   ```

### P: "Respuesta tarda muchísimo (>2 minutos)"
**R:**
1. Aumenta timeout en OllamaClient.kt:
   ```kotlin
   private const val TIMEOUT_SECONDS = 300L  // 5 minutos
   ```

2. Usa modelo más pequeño:
   ```bash
   ollama pull orca-mini  # Rápido pero menos preciso
   ```

3. Revisa recursos del PC:
   - CPU/RAM disponible
   - GPU habilitada en Ollama

### P: "Chat muestra solo 'Error: null'"
**R:**
1. Verifica conexión:
   ```bash
   # Desde emulador
   adb shell curl http://10.0.2.2:11434/api/tags
   ```

2. Revisa que modelo está seleccionado
3. Lee logs completos:
   ```bash
   adb logcat | grep -A5 "OllamaClient"
   ```

---

## 📱 PROBLEMAS CON LA COMPILACIÓN

### P: "Build falla con 'Unresolved reference okhttp3'"
**R:**
1. Verifica que build.gradle tiene OkHttp3:
   ```kotlin
   implementation("com.squareup.okhttp3:okhttp:4.11.0")
   ```

2. Reconstruye proyecto:
   ```bash
   ./gradlew.bat clean build
   ```

### P: "Error: 'val LocalLifecycleOwner is deprecated'"
**R:**
1. Ya está corregido en la versión actual
2. Si persiste, actualiza import:
   ```kotlin
   import androidx.lifecycle.compose.LocalLifecycleOwner
   ```

### P: "Build successful pero no instala APK"
**R:**
```bash
# Desinstala versión anterior
adb uninstall com.example.landmarklens

# Instala nueva
./gradlew.bat installDebug
```

### P: "Gradle espira (timeout)"
**R:**
1. Aumenta memoria:
   ```bash
   export GRADLE_OPTS=-Xmx2048m
   ./gradlew.bat build
   ```

2. O en gradle.properties:
   ```
   org.gradle.jvmargs=-Xmx2048m
   ```

---

## 🖼️ PROBLEMAS CON FOTOS

### P: "No se guardan fotos localmente"
**R:**
1. Verifica permisos de almacenamiento
2. Revisa la ruta:
   ```bash
   adb shell ls -la /data/data/com.example.landmarklens/files/landmark_photos/
   ```

3. Logs:
   ```bash
   adb logcat | grep FileUtils
   ```

### P: "Foto guardada pero no aparece"
**R:**
1. FileUtils guarda en `context.filesDir` (privado)
2. Para acceder desde PC:
   ```bash
   adb pull /data/data/com.example.landmarklens/files/landmark_photos/ ./
   ```

3. O cambia ruta a carpeta pública en EXTENSIONES.md

---

## 🎨 PROBLEMAS CON LA UI

### P: "Texto muy pequeño en emulador"
**R:**
```kotlin
// En MainActivity.kt, ajusta tamaños:
.size(48.dp)  // Aumentar de 32.dp
```

### P: "Botones no responden"
**R:**
1. Verifica que no está en estado `isLoading`
2. Revisa que tienes permisos otorgados
3. Logs detallados:
   ```bash
   adb logcat | grep "Composable"
   ```

### P: "Overlay de GPS se corta en pantalla"
**R:**
```kotlin
// En CameraLandmarkScreen, ajusta padding:
.padding(16.dp)  // Aumentar si es necesario
```

---

## 💾 PROBLEMAS CON ALMACENAMIENTO

### P: "¿Dónde se guardan las fotos?"
**R:**
```
/data/data/com.example.landmarklens/files/landmark_photos/
landmark_20260322_143025.png
```

### P: "¿Puedo cambiar la ruta?"
**R:**
En FileUtils.kt:
```kotlin
private const val PHOTOS_DIR = "mi_carpeta_personalizada"
```

O usa carpeta pública:
```kotlin
fun getPhotosDirectory(context: Context): File {
    return File(context.getExternalFilesDir(null), PHOTOS_DIR)
}
```

---

## 🔍 DEBUGGING AVANZADO

### Ver todos los logs en tiempo real
```bash
adb logcat -c  # Limpia logs
adb logcat | grep "landmark\|Camera\|Ollama\|File"
```

### Logs filtrados por nivel
```bash
adb logcat *:D  # Debug y superiores
adb logcat *:E  # Solo errores
```

### Captura de pantalla del emulador
```bash
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png
```

### Dump de logs a archivo
```bash
adb logcat > logs.txt
# Ejecuta tu acción
# Ctrl+C para parar
```

---

## 🧪 TESTING CHECKLIST

### Antes de pasar a producción

- [ ] Cámara abre correctamente
- [ ] GPS obtiene coordenadas (espera 30 seg)
- [ ] Brújula muestra acimut (gira teléfono)
- [ ] Foto se guarda (verifica con `adb shell ls`)
- [ ] Chat Ollama responde
- [ ] Cambio de modelos funciona
- [ ] Historial de chat persiste
- [ ] Pestaña ML carga sin errores
- [ ] Permisos se solicitan solo una vez
- [ ] App no se congela al capturar
- [ ] Logs no muestran errores rojo

---

## 🚨 ERRORES CRÍTICOS

### "Segmentation Fault" al compilar
**R:**
```bash
./gradlew.bat clean
./gradlew.bat build -x test  # Sin tests
```

### "Resources not found"
**R:**
```bash
./gradlew.bat clean build --refresh-dependencies
```

### "Gradle sync failed"
**R:**
1. File → Sync Now
2. File → Invalidate Caches / Restart
3. Elimina `.gradle` y `.idea`

---

## 📞 REPORTAR ISSUES

Si encuentras un bug:

1. **Recopila información:**
   ```bash
   adb logcat > full_logs.txt
   ```

2. **Incluye:**
   - Versión Android
   - Dispositivo/emulador
   - Paso a paso para reproducir
   - Logs completos

3. **Revisa GUIA_COMPLETA.md** para arquitectura

---

## 💡 TIPS Y TRUCOS

### Acelerar compilación
```bash
# En gradle.properties
org.gradle.parallel=true
org.gradle.caching=true
```

### Preview sin compilar completa
```bash
@Preview(showBackground = true)
@Composable
fun PreviewCameraScreen() {
    CameraLandmarkScreen(viewModel = LandmarkViewModel())
}
```

### Hot Reload en emulador
```bash
# Cambios de Composables se aplican sin recompilación completa
# Espera 5-10 segundos
```

### Monitor de recursos
```bash
# En Android Studio
View → Tool Windows → Logcat
View → Tool Windows → Android Profiler
```

---

## 🎓 REFERENCIAS ÚTILES

- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [CameraX Docs](https://developer.android.com/training/camerax)
- [Location Services](https://developers.google.com/android/guides/setup)
- [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Android Sensors](https://developer.android.com/guide/topics/sensors)

---

¡Si tienes más preguntas, revisa EXTENSIONES.md para ejemplos de código! 🚀

