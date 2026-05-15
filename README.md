
# LandmarkLens

Aplicación Android para identificación contextual de monumentos, edificios históricos y puntos de interés utilizando cámara, GPS, orientación espacial y modelos de inteligencia artificial ejecutados mediante Ollama.

El sistema combina:
- captura de imagen en tiempo real,
- geolocalización GPS,
- orientación del dispositivo,
- recuperación contextual de landmarks,
- consultas geoespaciales,
- generación de respuestas mediante modelos LLM.

---

# Descripción del proyecto

LandmarkLens permite al usuario apuntar la cámara hacia un entorno urbano, capturar una fotografía y obtener información contextual sobre el lugar observado.

La aplicación utiliza:
- coordenadas GPS,
- orientación espacial del dispositivo,
- filtrado geográfico,
- recuperación contextual,
- generación aumentada mediante IA.

El sistema devuelve:
- nombre del landmark,
- tipo de lugar,
- dirección aproximada,
- información contextual,
- recomendaciones turísticas generadas por IA.

Además, la aplicación incorpora:
- visualización de ubicación actual en mapa,
- asistente turístico basado en Ollama,
- arquitectura modular MVVM,
- integración completa con sensores Android,
- pipeline preparado para futuras capacidades ML offline.

---

# Objetivo del proyecto

El objetivo principal del proyecto es desarrollar una aplicación Android capaz de identificar landmarks cercanos combinando:
- visión contextual,
- geolocalización,
- orientación espacial,
- recuperación contextual mediante RAG,
- modelos LLM locales.

El proyecto fue desarrollado como prototipo universitario enfocado en:
- arquitectura Android moderna,
- integración de sensores,
- sistemas híbridos IA + geolocalización,
- diseño modular y extensible,
- integración ML end-to-end.

---

# Arquitectura general

La aplicación sigue el patrón MVVM (Model-View-ViewModel) utilizando Jetpack Compose.

```text
UI (Jetpack Compose)
    |
    | observa estado / lanza eventos
    v
ViewModel (LandmarkViewModel)
    |
    | coordina lógica y estado
    v
Servicios y Clientes
(PlacesService, OllamaClient, FileUtils)
```

---

# Arquitectura completa del sistema

```text
Android App
    |
    | GPS + Cámara + Azimuth
    v
Context Retrieval
    |
    | landmarks cercanos
    v
Ranking geoespacial
    |
    v
Ollama LLM
    |
    v
Respuesta contextual JSON
```

---

# Componentes principales

## LandmarkViewModel

ViewModel principal de la aplicación.

Gestiona:
- navegación entre pestañas,
- estado global de UI,
- sensores de orientación,
- GPS y localización,
- resultados de captura,
- estado del chat,
- comunicación con backend,
- flujo completo de datos entre app y modelo IA.

Funciones principales:
- lectura del sensor TYPE_ROTATION_VECTOR,
- obtención de ubicación mediante FusedLocationProviderClient,
- captura y almacenamiento de imágenes,
- gestión del historial del chat,
- sincronización de estados Compose.

---

## PlacesService

Servicio encargado de:
- consultar lugares cercanos,
- recuperar información contextual,
- obtener landmarks mediante coordenadas GPS.

Devuelve objetos `LandmarkLocation` con:
- nombre,
- categoría,
- dirección,
- contexto asociado.

---

## OllamaClient

Cliente HTTP basado en OkHttp que se comunica con Ollama.

Funciones principales:
- carga de modelos disponibles,
- envío de prompts,
- recepción de respuestas IA,
- integración del sistema RAG.

---

## FileUtils

Módulo responsable de:
- almacenamiento de imágenes capturadas,
- generación de nombres con metadatos,
- persistencia local de capturas.

Los archivos incluyen:
- timestamp,
- coordenadas GPS,
- acimut de cámara.

---

# Pantallas principales

| Pantalla | Descripción |
|---|---|
| `CameraLandmarkScreen` | Preview de cámara con overlay GPS y brújula en tiempo real. |
| `CaptureResultScreen` | Resultado contextual tras captura y análisis. |
| `MapTab` | Mapa OSMDroid centrado en posición actual. |
| `OllamaChatScreen` | Chat turístico con modelos Ollama. |
| `MLOfflineScreen` | Pantalla reservada para inferencia ML offline futura. |

---

# Integración ML y sistema RAG

El sistema incorpora un pipeline híbrido basado en:
- recuperación contextual,
- filtrado geoespacial,
- orientación de cámara,
- generación mediante modelos LLM.

## Flujo del sistema

```text
GPS + Orientación
        |
        v
Búsqueda de landmarks cercanos
        |
        v
Filtrado angular
        |
        v
Ranking contextual
        |
        v
Construcción del prompt
        |
        v
Ollama LLM
        |
        v
Respuesta estructurada
```

---

# Sensores y APIs integradas

## Cámara
- CameraX (`androidx.camera`)
- preview en tiempo real,
- captura de imágenes.

## GPS
- Google Play Services Location,
- FusedLocationProviderClient,
- precisión configurable.

## Orientación espacial
- SensorManager,
- TYPE_ROTATION_VECTOR,
- cálculo de acimut mediante matrices de rotación.

## Mapa
- OSMDroid,
- mapas open-source sin API Key.

## IA y backend
- Ollama,
- OkHttp,
- consultas HTTP,
- modelos LLM locales.

---

# Tecnologías utilizadas

## Android
- Kotlin
- Jetpack Compose
- Coroutines
- ViewModel
- CameraX
- OSMDroid

## Backend y ML
- Python
- Ollama
- JSON
- OpenStreetMap
- Retrieval-Augmented Generation (RAG)

---

# Permisos requeridos

- `CAMERA`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `INTERNET`
- `WRITE_EXTERNAL_STORAGE` (Android 9 o inferior)

---

# Requisitos previos

- Android Studio Hedgehog o superior
- Android API 24+
- SDK de compilación API 36
- Python 3.10+
- Instancia local de Ollama

---

# Instrucciones de ejecución

## 1. Clonar repositorio

```bash
git clone https://github.com/usuario/LandmarkLens.git
```

---

## 2. Abrir proyecto Android

Abrir la carpeta `APP` desde Android Studio:

```text
File > Open > APP
```

---

## 3. Sincronizar Gradle

Esperar descarga automática de dependencias.

---

## 4. Ejecutar Ollama

Ejemplo:

```bash
ollama run qwen2.5:7b
```

Si se usa emulador Android:
- Ollama debe escuchar en `0.0.0.0`
- usar `10.0.2.2` como host.

---

## 5. Ejecutar aplicación

Conectar dispositivo físico o iniciar emulador.

Ejecutar:
```text
Shift + F10
```

o pulsar botón Run en Android Studio.

---

## 6. Conceder permisos

Aceptar permisos de:
- cámara,
- ubicación,
- almacenamiento.

---

# Estructura del proyecto

```text
APP/
  app/
    src/
      main/
        java/com/example/landmarklens/

          MainActivity.kt
          # Navegación y UI Compose

          LandmarkViewModel.kt
          # Estado global y lógica principal

          PlacesService.kt
          # Recuperación contextual de landmarks

          OllamaClient.kt
          # Cliente HTTP para Ollama

          FileUtils.kt
          # Persistencia local de imágenes

        res/
          # Recursos gráficos y temas

        AndroidManifest.xml
```

---

# Pipeline ML

El sistema ML incluye:
- generación de dataset,
- preprocesamiento,
- ranking contextual,
- recuperación RAG,
- evaluación experimental,
- serving mediante Ollama.

Scripts principales:
- `prepare_data.py`
- `train_model.py`
- `evaluate_model.py`
- `evaluate_online_ollama.py`
- `pipeline.py`

---

# Integración completa con backend

La aplicación se comunica con:
- servicios HTTP,
- sistema RAG,
- Ollama local,
- recuperación contextual geoespacial.

El flujo completo:
- captura,
- contexto,
- inferencia,
- respuesta,
- visualización

está implementado end-to-end.

---

# Optimización para móvil

El sistema incorpora:
- reducción de candidatos,
- filtrado angular,
- minimización de contexto,
- limitación de tokens,
- inferencia delegada a servidor local.

Estas estrategias reducen:
- consumo de memoria,
- uso de CPU,
- latencia en dispositivo móvil.

---

# Estado actual del proyecto

El proyecto implementa actualmente:
- aplicación Android completamente funcional,
- navegación completa,
- captura contextual con GPS,
- integración IA mediante Ollama,
- sistema RAG operativo,
- mapa interactivo,
- arquitectura MVVM modular,
- pipeline ML reproducible.

La pantalla `MLOfflineScreen` queda reservada para futuras capacidades de inferencia offline mediante modelos optimizados para móvil.
