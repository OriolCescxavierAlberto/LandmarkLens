## 🏗️ ARQUITECTURA Y ESPECIFICACIONES TÉCNICAS

---

## 📐 Diagrama de Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                          LANDMARKLENS APP                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                      UI LAYER (Compose)                  │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │   │
│  │  │ Camera   │  │   Chat   │  │   ML     │  │   Map    │ │   │
│  │  │ Screen   │  │  Screen  │  │  Screen  │  │ (Future) │ │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   VIEWMODEL LAYER (MVVM)                 │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │        LandmarkViewModel                         │   │   │
│  │  │  • GPS State (lat, lon)                          │   │   │
│  │  │  • Acimuth State                                 │   │   │
│  │  │  • Photo Capture State                           │   │   │
│  │  │  • Sensor Management                            │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  DATA LAYER (Repositories)               │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐   │   │
│  │  │ CameraX     │  │  GPS         │  │  Sensors     │   │   │
│  │  │ Capture     │  │  (Fused)     │  │  (Manager)   │   │   │
│  │  └─────────────┘  └──────────────┘  └──────────────┘   │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐   │   │
│  │  │ OllamaClient│  │  FileUtils   │  │   Database   │   │   │
│  │  │ (HTTP)      │  │  (Storage)   │  │   (Future)   │   │   │
│  │  └─────────────┘  └──────────────┘  └──────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  EXTERNAL SERVICES                        │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │   │
│  │  │ Ollama   │  │ Location │  │ Camera   │  │ Sensors  │ │   │
│  │  │  API     │  │ Services │  │    X     │  │ Manager  │ │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 Flujo de Datos Detallado

### 1. CAPTURA DE FOTO

```
┌─────────────┐
│   Usuario   │
│  Presiona   │
│     FAB     │
└──────┬──────┘
       │
       ▼
┌────────────────────────────────────────┐
│  CameraLandmarkScreen.takePicture()    │
│  ┌────────────────────────────────────┐│
│  │ 1. Obtener ubicación GPS           ││
│  │    viewModel.updateLocation()      ││
│  │    └─> FusedLocationProviderClient ││
│  └────────────────────────────────────┘│
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  2. Capturar foto actual               │
│     previewView?.bitmap               │
│     ├─> Bitmap (ARGB_8888)            │
│     └─> Latitud + Longitud + Acimut   │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  3. Guardar localmente                 │
│     FileUtils.saveBitmap(...)          │
│     ├─> PNG formato                    │
│     ├─> Ruta: landmark_YYYYMMDD_HHMMSS│
│     └─> Log de metadatos               │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  4. Mostrar resultado                  │
│     CaptureResultScreen()              │
│     ├─> Imagen capturada              │
│     ├─> Lat/Lon/Azimut exactos        │
│     └─> Botón para volver             │
└────────────────────────────────────────┘
```

### 2. CHAT CON OLLAMA

```
┌─────────────┐
│   Usuario   │
│  Escribe    │
│  Pregunta   │
└──────┬──────┘
       │
       ▼
┌────────────────────────────────────────┐
│  OllamaChatScreen.sendMessage()        │
│  ┌────────────────────────────────────┐│
│  │ 1. Validar entrada                 ││
│  │    ├─> No vacío                    ││
│  │    └─> Máx 5000 caracteres        ││
│  └────────────────────────────────────┘│
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  2. Añadir a historial (UI)            │
│     messages.add(ChatMessage(...))     │
│     └─> Role: "user"                  │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  3. Solicitud HTTP (coroutine)         │
│     OllamaClient.askModel()            │
│     └─> scope.launch { }               │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  4. OkHttp3 POST Request               │
│     URL: http://localhost:11434/api/gen│
│     Body: JSON { model, prompt, stream}│
│     Timeout: 120s                      │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  5. Procesar respuesta                 │
│     ├─> JSON parse                    │
│     ├─> Extraer campo "response"      │
│     └─> Log first 100 chars           │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  6. Añadir a historial (respuesta)     │
│     messages.add(ChatMessage(...))     │
│     └─> Role: "assistant"             │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│  7. Actualizar UI                      │
│     ├─> Scroll automático              │
│     ├─> Desactivar botón send          │
│     └─> Limpiar campo input            │
└────────────────────────────────────────┘
```

---

## 🔧 Stack Tecnológico Completo

### Frontend
```
Jetpack Compose          → UI Framework
Kotlin                   → Lenguaje
Material 3               → Design System
Composables              → Reusable components
State Management         → Mutable states
```

### Backend Local
```
CameraX                  → Captura de fotos
ProcessCameraProvider    → Gestor de cámaras
SensorManager           → Brújula (acimut)
FusedLocationProvider   → GPS
SensorEventListener     → Escucha de eventos
```

### Conectividad
```
OkHttp3 4.11.0          → Cliente HTTP
JSON (org.json)         → Parsing JSON
Coroutines              → Operaciones async
HTTP/1.1                → Protocolo
```

### Almacenamiento
```
Internal Storage        → Fotos PNG
Context.filesDir        → Directorio privado
FileOutputStream        → Escritura de archivos
```

### Arquitectura
```
MVVM                    → Patrón de diseño
ViewModel               → Gestión de estado
Composable Functions    → Declarativo
Singleton Objects       → OllamaClient, FileUtils
```

---

## 📈 Flujo de Permisos

```
┌──────────────────────────────────────────────────────┐
│         Solicitud de Permisos en Tiempo de Ejecución │
└──────────────────────────────────────────────────────┘

APP INICIA
    │
    ├─→ [Verificar] ¿Hay permisos previos otorgados?
    │
    ├─→ NO
    │   │
    │   └─→ Lanzar: RequestMultiplePermissions()
    │       │
    │       ├─→ android.permission.CAMERA
    │       ├─→ android.permission.ACCESS_FINE_LOCATION
    │       └─→ android.permission.ACCESS_COARSE_LOCATION
    │
    ├─→ USUARIO ACEPTA
    │   │
    │   ├─→ Guardar en runtime preferences
    │   ├─→ Iniciar cámara
    │   └─→ Solicitar ubicación GPS
    │
    └─→ USUARIO RECHAZA
        │
        └─→ Mostrar pantalla de error
            ├─→ Explicación clara
            └─→ Botón "Reintentar"
```

---

## 🔐 Seguridad

### Permisos Solicitados
```xml
<!-- Manifesto -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Runtime (solicitados dinámicamente) -->
CAMERA
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
```

### Protección de Datos
```
Fotos guardadas:    → Internal storage (privado)
Permisos:           → Runtime verification
HTTPS:              → Ready (OkHttp3 soporta)
Validación entrada: → String length check
Timeout:            → 120 segundos
```

---

## 📱 Compatibilidad

```
Min SDK:        24 (Android 7.0)
Target SDK:     36 (Android 15)
Compilate:      36 (Android 15)

Dispositivos soportados:
✓ Phones (4.7" - 6.7")
✓ Tablets
✓ Emuladores Android Studio
✓ Emuladores Google Play

Sensores requeridos:
✓ Cámara trasera
✓ GPS (mejora sin él)
✓ Brújula/Acelerómetro (mejora sin él)
✓ Internet (solo para Ollama)
```

---

## ⚡ Performance

### Tiempos Típicos
```
App launch:             ~2-3 seg
Cámara preview:         <1 seg
Photo capture:          <500ms
GPS first fix:          10-30 seg (dispositivo)
Ollama response:        2-60 seg (según modelo)
Chat rendering:         <200ms
```

### Consumo de Recursos
```
RAM típico:             ~150MB
Internal Storage:       ~2-5MB por foto (PNG)
CPU (preview):          15-20%
CPU (capture):          40-50%
Battery (GPS):          High drain
Battery (Chat):         Moderate
```

---

## 🔗 Endpoints y Conexiones

### GPS
```
Proveedor:              FusedLocationProviderClient
Frecuencia:             Continua (UI updates)
Precisión:              PRIORITY_HIGH_ACCURACY
Timeout:                No específico
```

### Brújula
```
Sensor:                 TYPE_ROTATION_VECTOR
Frecuencia:             SENSOR_DELAY_UI
Rango:                  0° - 360°
Calibración:            Manual (girar dispositivo)
```

### Ollama
```
URL:                    http://localhost:11434
Endpoint:               /api/generate
Método:                 POST
Content-Type:           application/json
Timeout:                120 segundos
Stream:                 No (false)
```

---

## 📦 Estructura de Carpetas

```
app/
├── src/
│   └── main/
│       ├── java/com/example/landmarklens/
│       │   ├── MainActivity.kt           (Activity principal)
│       │   ├── LandmarkViewModel.kt      (ViewModel MVVM)
│       │   ├── OllamaClient.kt           (Cliente HTTP)
│       │   ├── FileUtils.kt              (Almacenamiento)
│       │   └── CameraXCapture.kt         (Gestor cámara)
│       ├── res/
│       │   ├── values/
│       │   │   └── strings.xml
│       │   ├── drawable/
│       │   └── layout/
│       └── AndroidManifest.xml
├── build.gradle.kts                     (Dependencias)
└── proguard-rules.pro                   (Obfuscación)
```

---

## 🧪 Test Plan

### Pruebas Manuales Recomendadas

**Escenario 1: Captura de Foto**
1. Abre app
2. Acepta permisos
3. Espera 30 seg para GPS
4. Observa coordenadas en pantalla
5. Toma foto
6. Verifica resultado con metadatos
7. Verifica foto en storage

**Escenario 2: Chat Ollama**
1. Abre pestaña 2
2. Verifica que carga modelos
3. Selecciona modelo
4. Envía pregunta corta
5. Espera respuesta
6. Verifica historial
7. Cambia de modelo
8. Pregunta sobre monumento capturado

**Escenario 3: Rotación y Permisos**
1. Toma foto
2. Gira dispositivo (rotate)
3. Verifica que UI se adapta
4. Revoca permisos en Settings
5. Vuelve a app
6. Verifica que solicita de nuevo

---

## 🎯 Métricas de Éxito

```
✓ App no crashea
✓ Cámara abre en <1 seg
✓ GPS obtiene ubicación en <30 seg
✓ Fotos se guardan correctamente
✓ Ollama responde en <60 seg
✓ Chat historial persiste
✓ No hay memory leaks
✓ UI responsive siempre
✓ Logs limpios (sin errores)
```

---

## 📚 Referencias Técnicas

**Documentación Oficial:**
- [Jetpack Compose](https://developer.android.com/jetpack/compose/documentation)
- [CameraX](https://developer.android.com/training/camerax)
- [Android Sensors](https://developer.android.com/guide/topics/sensors)
- [Location Services](https://developers.google.com/android/guides/setup)
- [OkHttp3](https://square.github.io/okhttp/)

**Ollama:**
- [Ollama API Docs](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Ollama Models](https://ollama.ai/library)

---

Este documento proporciona una referencia técnica completa de LandmarkLens. 🚀

