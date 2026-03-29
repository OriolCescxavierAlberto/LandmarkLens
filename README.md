# LandmarkLens

Aplicacion Android para identificar monumentos y puntos de interes mediante la camara del dispositivo, combinando GPS, brujula y consultas a una API de lugares. Incluye un chat con modelos de lenguaje local (Ollama) y visualizacion de la ubicacion actual en mapa.

---

## Descripcion del proyecto

LandmarkLens permite al usuario apuntar la camara hacia un lugar, capturar una fotografia y obtener informacion sobre el sitio: nombre, tipo y direccion, obtenidos a partir de las coordenadas GPS en el momento de la captura. Adicionalmente, la aplicacion ofrece un mapa con la posicion actual, un asistente de guia turistico basado en Ollama y una pantalla reservada para futura clasificacion offline mediante modelos ML en el dispositivo.

El proyecto es un prototipo funcional desarrollado como parte de una asignatura universitaria. El objetivo de esta entrega es demostrar una arquitectura solida, navegacion entre pantallas, integracion de sensores y una primera version funcional de la aplicacion.

---

## Arquitectura general

La aplicacion sigue el patron MVVM (Model-View-ViewModel), adaptado a Jetpack Compose:

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

- La capa de UI esta formada exclusivamente por funciones Composable. No contiene logica de negocio ni llamadas de red.
- El ViewModel centraliza todo el estado de la aplicacion mediante Compose State. Al sobrevivir los cambios de configuracion (rotacion de pantalla), el estado de navegacion, el historial del chat y los resultados de GPS se mantienen sin necesidad de restauracion manual.
- Los servicios son clases o singletons sin dependencia del ciclo de vida de Android.

---

## Modulos principales

### LandmarkViewModel
Unico ViewModel de la aplicacion. Gestiona:
- Estado de navegacion entre pestanas (`currentTab`)
- Lectura de sensores: sensor de rotacion (TYPE_ROTATION_VECTOR) para el acimut
- GPS mediante FusedLocationProviderClient con dos niveles de precision: balanceada para el overlay y alta precision en el momento de la captura
- Ciclo de vida de la captura: bitmap, coordenadas y resultado de Places
- Estado completo del chat: historial de mensajes, modelo seleccionado, carga de modelos y envio de preguntas

### PlacesService
Consulta una API de lugares a partir de latitud y longitud. Devuelve un objeto `LandmarkLocation` con nombre, tipo y direccion del sitio. Es invocado desde el ViewModel mediante `viewModelScope`.

### OllamaClient
Cliente HTTP (OkHttp) que se comunica con una instancia local de Ollama. Expone dos funciones suspendidas: `getModels()` para listar los modelos disponibles y `askModel()` para enviar una pregunta y recibir la respuesta.

### FileUtils
Guarda el bitmap capturado en el almacenamiento del dispositivo incluyendo metadatos de GPS y acimut en el nombre del archivo.

### Pantallas (Composables)

| Composable | Descripcion |
|---|---|
| `CameraLandmarkScreen` | Previsualizacion de camara en tiempo real con overlay de GPS y brujula. Boton de captura. |
| `CaptureResultScreen` | Muestra la foto capturada, el mapa del lugar y la informacion obtenida de PlacesService. |
| `MapTab` | Mapa OSMDroid centrado en la posicion actual del usuario. |
| `OllamaChatScreen` | Chat con el modelo Ollama seleccionado. Historial persistente durante la sesion. |
| `MLOfflineScreen` | Pantalla reservada para clasificacion offline (en desarrollo). |

---

## Sensores y APIs integradas

- **Camara**: CameraX (androidx.camera) para previsualizacion y captura de fotogramas.
- **GPS**: Google Play Services Location (FusedLocationProviderClient) con prioridad configurable.
- **Brujula / orientacion**: SensorManager con TYPE_ROTATION_VECTOR y calculo de acimut mediante matriz de rotacion.
- **Mapa**: OSMDroid, mapa de codigo abierto sin necesidad de API Key.
- **Identificacion de lugares**: PlacesService con peticion HTTP a partir de coordenadas.
- **Chat IA**: Ollama ejecutado localmente en la misma red que el dispositivo.

---

## Permisos requeridos

- `CAMERA`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `INTERNET`
- `WRITE_EXTERNAL_STORAGE` (solo en Android 9 o inferior)

---

## Requisitos previos

- Android Studio Hedgehog o superior
- Dispositivo o emulador con Android 7.0 (API 24) o superior
- SDK de compilacion: API 36
- Para el chat: instancia de Ollama accesible desde el dispositivo (misma red local o localhost si se usa un emulador)

---

## Instrucciones para ejecutar la app

1. Clonar el repositorio:
   ```
   git clone https://github.com/usuario/LandmarkLens.git
   ```

2. Abrir la carpeta `APP` con Android Studio (File > Open > seleccionar la carpeta APP).

3. Esperar a que Gradle sincronice las dependencias.

4. Conectar un dispositivo fisico por USB con depuracion activada, o iniciar un emulador con soporte de camara y GPS.

5. Pulsar Run (Shift + F10) o el boton de play en Android Studio.

6. En el primer arranque, aceptar los permisos de camara y ubicacion cuando el sistema los solicite.

7. Para usar el chat con Ollama, asegurarse de que el servidor Ollama esta corriendo y es accesible desde el dispositivo. Si se usa un emulador, Ollama debe escuchar en `0.0.0.0` y la URL configurada en `OllamaClient` debe apuntar a `10.0.2.2` (IP del host desde el emulador).

---

## Estructura del proyecto

```
APP/
  app/
    src/
      main/
        java/com/example/landmarklens/
          MainActivity.kt          # UI: Composables y navegacion
          LandmarkViewModel.kt     # ViewModel central
          PlacesService.kt         # Identificacion de lugares por GPS
          OllamaClient.kt          # Cliente HTTP para Ollama
          FileUtils.kt             # Guardado de imagenes
        res/                       # Recursos: temas, iconos, strings
        AndroidManifest.xml
```

---

## Estado actual del proyecto

La aplicacion es un prototipo funcional en desarrollo activo. Las funcionalidades implementadas son la captura con GPS, la identificacion de lugares, el mapa de posicion actual, el chat con Ollama y la navegacion completa entre pestanas. La pantalla de clasificacion offline (ML en dispositivo) esta reservada para una entrega posterior.