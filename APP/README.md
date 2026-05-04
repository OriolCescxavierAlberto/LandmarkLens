# LandmarkLens

Aplicacion Android para identificar monumentos y puntos de interes mediante la camara del dispositivo, combinando GPS, brujula y consultas a una API de lugares. Incluye un chat con modelos de lenguaje local (Ollama) y visualizacion de la ubicacion actual en mapa.

---

## Descripcion del proyecto

LandmarkLens permite al usuario apuntar la camara hacia un lugar, capturar una fotografia y obtener informacion sobre el sitio: nombre, tipo y direccion, obtenidos a partir de las coordenadas GPS en el momento de la captura. Adicionalmente, la aplicacion ofrece un mapa con la posicion actual, un asistente de guia turistico basado en Ollama y una pantalla reservada para futura clasificacion offline mediante modelos ML en el dispositivo.

El proyecto es un prototipo funcional desarrollado como parte de una asignatura universitaria. El objetivo de esta entrega es demostrar una arquitectura solida, navegacion entre pantallas, integracion de sensores y una primera version funcional de la aplicacion.

### Flujo de datos del sistema
1. **Captura:** El usuario toma una foto. Se obtienen simultáneamente coordenadas GPS (Lat/Lon) y orientación (Acimut).
2. **Identificación:** Las coordenadas se envían a `PlacesService` para identificar el monumento más cercano.
3. **Persistencia (CRUD):** 
   - La imagen se guarda físicamente en el almacenamiento interno como PNG (`FileUtils`).
   - Los metadatos y la ruta de la imagen se insertan en la base de datos **Room** (`LandmarkDatabase`).
4. **Consulta IA (Backend):** El usuario puede preguntar al asistente. El `OllamaClient` envía el contexto al backend local (Ollama) y recibe la respuesta, gestionando la sincronización de mensajes.
5. **Visualización:** La UI recupera los datos de la DB y las imágenes del disco para mostrar el historial y el mapa.

---

## Arquitectura general

La aplicacion sigue el patron **MVVM (Model-View-ViewModel)**, adaptado a Jetpack Compose y principios de **Clean Architecture**:

```
UI (Composables)
    |
    |  observa estado / lanza eventos
    v
ViewModel (LandmarkViewModel)
    |
    |  llama a
    v
Servicios / Clientes (PlacesService, OllamaClient, FileUtils)
```

- **Capa de UI (`ui/`):** Formada exclusivamente por funciones Composable. No contiene logica de negocio ni llamadas de red. Se divide en `screens`, `components` y el punto de entrada `MainActivity.kt`.
- **ViewModel (`ui/viewmodel/`):** Centraliza todo el estado de la aplicacion mediante Compose State. Al sobrevivir los cambios de configuracion, mantiene la navegacion, el chat y los resultados de GPS.
- **Capa de Datos (`data/`):**
    - `local/`: Persistencia con Room (Database, DAO, Entities).
    - `remote/`: Clientes de red para APIs y el backend (Ollama, Places).
    - `model/`: Clases de datos que representan el dominio.
- **Utilidades (`util/`):** Clases auxiliares para manejo de archivos y persistencia física.

---

## Modulos principales

### LandmarkViewModel
Unico ViewModel de la aplicacion. Gestiona:
- Estado de navegacion entre pestanas (`currentTab`)
- Lectura de sensores: sensor de rotacion (TYPE_ROTATION_VECTOR) para el acimut
- GPS mediante FusedLocationProviderClient con dos niveles de precision
- Ciclo de vida de la captura: bitmap, coordenadas y resultado de Places
- Estado completo del chat: historial de mensajes, modelo seleccionado, carga de modelos y envio de preguntas
- **Operaciones CRUD:** Implementa la lógica para crear (insertar captura), leer (cargar historial) y borrar (individual o total) registros de la base de datos Room.

### PlacesService
Consulta una API de lugares a partir de latitud y longitud. Devuelve un objeto `LandmarkLocation` con nombre, tipo y direccion del sitio. Es invocado desde el ViewModel mediante `viewModelScope`.

### OllamaClient (Integración de Backend)
Cliente HTTP (OkHttp) que se comunica con una instancia local de **Ollama** (backend de modelos de lenguaje). 
- Expone funciones para listar modelos y generar respuestas.
- Sincroniza las preguntas del usuario con el backend local para actuar como guía histórico.

### FileUtils (Persistencia de datos)
Gestiona la **persistencia de datos binarios** guardando los bitmaps capturados en el almacenamiento interno del dispositivo, asegurando que las imágenes estén disponibles para el historial sin saturar la base de datos SQLite.

---

## Pantallas (Composables)

| Composable | Descripcion |
|---|---|
| `CameraLandmarkScreen` | Previsualizacion de camara en tiempo real con overlay de GPS y brujula. Boton de captura. |
| `CaptureResultScreen` | Muestra la foto capturada, el mapa del lugar y la informacion obtenida de PlacesService. |
| `MapTab` | Mapa OSMDroid centrado en la posicion actual del usuario. Muestra marcadores del historial. |
| `OllamaChatScreen` | Chat con el modelo Ollama seleccionado. Historial persistente durante la sesion. |
| `MLOfflineScreen` | Pantalla reservada para clasificacion offline (en desarrollo). |

---

## Sensores y APIs integradas

- **Camara**: CameraX (androidx.camera) para previsualizacion y captura de fotogramas.
- **GPS**: Google Play Services Location (FusedLocationProviderClient) con prioridad configurable.
- **Brujula / orientacion**: SensorManager con TYPE_ROTATION_VECTOR y calculo de acimut mediante matriz de rotacion.
- **Mapa**: OSMDroid, mapa de codigo abierto sin necesidad de API Key.
- **Identificacion de lugares**: PlacesService con peticion HTTP a partir de coordenadas.
- **Chat IA**: Ollama ejecutado localmente en la misma red que el dispositivo (Backend).

---

## Permisos requeridos

- `CAMERA`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `INTERNET`
- `WRITE_EXTERNAL_STORAGE` (solo en Android 9 o inferior)

---

## Requisitos previos

- Android Studio Ladybug o superior
- Dispositivo o emulador con Android 7.0 (API 24) o superior
- SDK de compilacion: API 36
- Para el chat: instancia de Ollama accesible desde el dispositivo (misma red local o localhost si se usa un emulador)

---

## Instrucciones para ejecutar la app

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/usuario/LandmarkLens.git
   ```
2. **Abrir el proyecto:** Abrir la carpeta `APP` con Android Studio.
3. **Sincronizar:** Esperar a que Gradle sincronice las dependencias.
4. **Dispositivo:** Conectar un dispositivo fisico o iniciar un emulador con soporte de camara y GPS.
5. **Ejecutar:** Pulsar Run (Shift + F10).
6. **Backend:** Para usar el chat, asegurarse de que el servidor Ollama esta corriendo. Si se usa un emulador, la URL en `OllamaClient` debe apuntar a `10.0.2.2`.

---

## Estructura del proyecto

```
APP/
  app/
    src/
      main/
        java/com/example/landmarklens/
          data/            # Capa de datos: Room, Modelos y APIs (Backend)
          ui/              # Capa de UI: Screens, ViewModels y Components
          util/            # Utilidades: Persistencia de archivos (FileUtils)
          MainActivity.kt  # Entry point y navegación
        res/               # Recursos: temas, iconos, strings
        AndroidManifest.xml
```

---

## Estado actual del proyecto

La aplicacion es un prototipo funcional en desarrollo activo. Las funcionalidades implementadas son la captura con GPS, la identificacion de lugares, el mapa de posicion actual, el chat con Ollama y la navegacion completa entre pestanas. La pantalla de clasificacion offline (ML en dispositivo) esta reservada para una entrega posterior.
