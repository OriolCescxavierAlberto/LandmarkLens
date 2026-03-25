## 🚀 QUICK START - 5 MINUTOS PARA TENER MAPAS FUNCIONANDO

### ⏱️ TIEMPO ESTIMADO
- Obtener API Key: 5 min
- Configurar: 2 min
- Compilar: 1-2 min
- Probar: 2 min
- **TOTAL: 15 minutos**

---

## 1️⃣ OBTENER API KEY (5 minutos)

```
1. Abre: https://console.cloud.google.com/
2. Login con tu cuenta Google
3. Crea proyecto → "LandmarkLens"
4. Habilita APIs:
   - Busca "Maps Android API" → ENABLE
   - Busca "Places API" → ENABLE
   - Busca "Geocoding API" → ENABLE
5. Credenciales → CREATE CREDENTIALS → API Key
6. COPIA tu clave (ej: AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0)
```

**NOTA**: La API Key aparecerá así:
```
AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0
```

---

## 2️⃣ CONFIGURAR EN ANDROID (2 minutos)

**Abre archivo:**
```
C:\Users\amart\Documents\GitHub\LandmarkLens\APP\app\src\main\AndroidManifest.xml
```

**Busca estas líneas:**
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY_HERE" />

<meta-data
    android:name="com.google.android.libraries.places.API_KEY"
    android:value="YOUR_GOOGLE_PLACES_API_KEY_HERE" />
```

**Reemplaza con tu clave:**
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0" />

<meta-data
    android:name="com.google.android.libraries.places.API_KEY"
    android:value="AIzaSyD3j5k8_2k0q8_2k0q8_2k0q8_2k0" />
```

**Guarda el archivo.**

---

## 3️⃣ COMPILAR (1-2 minutos)

**Abre PowerShell y ejecuta:**

```bash
cd C:\Users\amart\Documents\GitHub\LandmarkLens\APP
./gradlew.bat clean build
```

**Espera el resultado:**
```
BUILD SUCCESSFUL in 1m 5s
95 actionable tasks: 94 executed, 1 up-to-date
```

Si ves eso: ✅ **¡Perfecto!**

---

## 4️⃣ INSTALAR (1 minuto)

```bash
./gradlew.bat installDebug
```

**Espera el resultado:**
```
Installed on 1 device.
BUILD SUCCESSFUL in 9s
```

Si ves eso: ✅ **¡Perfecto!**

---

## 5️⃣ PROBAR EN EMULADOR (2 minutos)

### Paso 1: Simular ubicación
```
Emulador → Panel izquierdo → Ubicación
Ingresa: Latitud: 40.4168, Longitud: -3.7038 (Madrid)
Press: "Set Location"
```

### Paso 2: Abrir app
```
Busca: LandmarkLens en aplicaciones
Presiona ícono
Acepta permisos (Cámara, Ubicación)
```

### Paso 3: Tomar foto
```
Pestaña 1 (Cámara)
Presiona botón FAB (naranja en la esquina)
Espera 2-3 segundos
```

### Paso 4: Ver resultado
```
✅ Verás mapa centrado en Madrid
✅ Verás nombre del lugar ("Calle Mayor" o similar)
✅ Verás tipo ("Calle", "Avenida", etc)
✅ Verás dirección
✅ Verás coordenadas exactas
✅ Verás acimut (grados de brújula)
```

---

## ❌ PROBLEMAS COMUNES

### Problema: Mapa aparece gris
**Solución:**
- ✓ Verifica que reemplazaste AMBAS claves en AndroidManifest.xml
- ✓ Verifica que la clave sea válida (comienza con "AIzaSy...")
- ✓ Limpia cache: `./gradlew.bat clean`

### Problema: "Places API not initialized"
**Solución:**
- ✓ Habilita Places API en Google Cloud Console
- ✓ Espera 1-2 minutos para que se propague

### Problema: Reverse geocoding retorna null
**Solución:**
- ✓ Prueba en dispositivo real (el emulador a veces falla)
- ✓ Activa la conexión de red en el emulador
- ✓ Usa ubicación real en lugar de simulada

### Problema: "Compilation error"
**Solución:**
- ✓ Ejecuta: `./gradlew.bat clean build --info`
- ✓ Revisa que todas las dependencias estén instaladas
- ✓ Verifica que AndroidManifest.xml sea válido (XML correcto)

---

## 📋 VERIFICACIÓN

Después de completar, verifica:

```
✅ Archivo compilado sin errores
✅ APK instalado en emulador
✅ App se abre sin crashes
✅ Pestaña 1 muestra cámara
✅ Al tomar foto aparece mapa
✅ Se identifica nombre del lugar
✅ Se muestra dirección
✅ Se muestran coordenadas exactas
```

Si todos marcados: **¡LISTO! 🎉**

---

## 🎓 SIGUIENTE NIVEL (Opcional)

Después de verificar que funciona:

1. **Buscar atracciones turísticas**
   - Edita: PlacesService.kt
   - Implementa: getNearbyPlaces()

2. **Guardar favoritos**
   - Crea: LandmarkEntity.kt
   - Crea: LandmarkDatabase.kt (SQLite)

3. **Historial de capturas**
   - Crea: HistoryScreen (Composable)
   - Muestra: Timeline de fotos

Ver: GUIA_MAPAS_PLACES.md para más detalles

---

## 📞 AYUDA

**Si algo no funciona:**

1. Revisa CONFIGURACION_GOOGLE_API.md
2. Revisa GUIA_MAPAS_PLACES.md
3. Verifica logs: `adb logcat | grep -E "CaptureResultScreen|PlacesService"`

---

## 🎯 RESUMEN

Tu app LandmarkLens V2.0 ahora:
- ✅ Muestra mapas interactivos
- ✅ Identifica lugares automáticamente
- ✅ Captura GPS exacto
- ✅ Muestra dirección completa
- ✅ Tiene interfaz hermosa

**¡Felicidades! 🎉**

---

**Tiempo total: ~15 minutos**

**Próximo paso: CONFIGURACION_GOOGLE_API.md**

