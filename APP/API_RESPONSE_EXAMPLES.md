# Ejemplos de Respuestas de API para LandmarkLens

## Formato Recomendado (Completo)

Este es el formato que ofrece la mejor experiencia en la UI:

```json
{
  "id": "landmark_001_sagrada_familia",
  "landmark": "Sagrada Familia",
  "confidence": 0.98,
  "category": "Monumento Histórico",
  "description": "Basílica de la Sagrada Familia, diseño de Antoni Gaudí. Construcción iniciada en 1883.",
  "historical_info": "La Sagrada Familia es la iglesia más icónica de Barcelona, Patrimonio de la UNESCO. Su construcción continúa con una proyección de finalización hacia 2026. La fachada de la Natividad y la del Pasión son las más visitadas. Cada año recibe más de 3 millones de visitantes.",
  "distance": 250.5,
  "coordinates": {
    "latitude": 41.4036,
    "longitude": 2.1744
  }
}
```

### Mapeo de Campos

| Campo JSON | Tipo | Ubicación en UI | Requerido? |
|-----------|------|-----------------|-----------|
| `landmark` | String | Título grande | ✅ |
| `confidence` | Float (0-1) | Badge % superior derecha | ⚠️ |
| `category` | String | Etiqueta bajo título | ⚠️ |
| `description` | String | Párrafo pequeño | ⚠️ |
| `historical_info` | String | Tarjeta desplegable histórica | ⚠️ |
| `distance` | Float | No mostrado actualmente | ⚠️ |
| `id` | String | Log interno | ⚠️ |

---

## Formatos Alternativos (Flexibles)

El app es tolerante y busca campos alternativos:

### Formato Minimalista (Solo Reconocimiento)

```json
{
  "name": "Torre Eiffel"
}
```

**Resultado:** El app mostrará "Torre Eiffel" sin confianza ni categoría.

### Formato con Nombre Alternativo

```json
{
  "monument": "Cristo Redentor",
  "category": "Estatua Monumental"
}
```

**Resultado:** Se reconoce y muestra sin problemas.

### Formato con Info Histórica Alternativa

```json
{
  "landmark": "Big Ben",
  "history": "Torre del Reloj en el Palacio de Westminster, Londres",
  "confidence": 0.92
}
```

**Resultado:** Busca `historical_info` primero, luego `history` como respaldo.

---

## Ejemplo de Petición y Respuesta Real

### Petición desde Android

```
POST http://172.16.110.15:8000/api/v1/query HTTP/1.1
Host: 172.16.110.15:8000
Content-Type: application/json
Accept: application/json
Content-Length: 47

{
  "lat": 41.3851,
  "lon": 2.1734,
  "azimuth": 45.0,
  "fov": 70.0
}
```

### Respuesta Exitosa (200 OK)

```json
{
  "id": "barca_sagrada_2024",
  "landmark": "Sagrada Familia",
  "confidence": 0.97,
  "category": "Iglesia",
  "description": "Basílica Modernista de Barcelona",
  "historical_info": "Obra maestra de Antoni Gaudí",
  "distance": 123.45
}
```

### Respuesta sin Monumento Identificado

```json
{
  "landmark": null,
  "confidence": 0.0,
  "description": "No se identificó un monumento conocido en esta ubicación"
}
```

### Respuesta con Error (500)

```json
{
  "error": "Internal server error",
  "message": "Modelo no disponible"
}
```

**Resultado en App:** Mostrar "Error: Internal server error"

---

## Códigos de Respuesta HTTP

| Código | Comportamiento | Mensaje Usuario |
|--------|----------------|-----------------|
| 200 | Procesar JSON normalmente | Datos mostrados |
| 400 | Error de validación | "Parámetros inválidos" |
| 401 | No autorizado | "Acceso denegado" |
| 500 | Error del servidor | "Error en servidor de análisis" |
| 503 | Servicio no disponible | "Servidor no disponible" |
| Timeout (30s) | API no responde | "La consulta tardó demasiado" |

---

## Recomendaciones de Implementación para tu API

### 1. Responde Siempre en JSON

```python
# ✅ CORRECTO
response = {
    "landmark": "Torre Eiffel",
    "confidence": 0.95
}
return jsonify(response), 200

# ❌ INCORRECTO
response = "Torre Eiffel"
return response, 200
```

### 2. Incluye Siempre un ID Único

```python
{
    "id": f"analysis_{uuid.uuid4()}",
    "landmark": "...",
    "timestamp": datetime.now().isoformat()
}
```

### 3. Normaliza Confianza entre 0-1

```python
# Si tu confianza es 0-100
confidence_normalized = raw_confidence / 100.0

# Si es 0-1, devuelve directamente
confidence = model_output.confidence
```

### 4. Maneja Casos Edge

```python
# Si no hay monumento
if not detected:
    return {
        "landmark": None,
        "confidence": 0.0,
        "description": "Sin identificación disponible"
    }, 200

# Si hay error interno
try:
    result = model.predict(image)
except Exception as e:
    return {
        "error": str(e),
        "landmark": None
    }, 500
```

---

## Ejemplo: API Mock en Python (para testing)

```python
from flask import Flask, request, jsonify
import time

app = Flask(__name__)

LANDMARKS = {
    "Sagrada Familia": {
        "lat": 41.4036,
        "lon": 2.1744,
        "radius": 0.01
    },
    "Torre Eiffel": {
        "lat": 48.8584,
        "lon": 2.2945,
        "radius": 0.01
    }
}

@app.route('/api/v1/query', methods=['POST'])
def query_landmark():
    data = request.json
    lat = data.get('lat')
    lon = data.get('lon')
    azimuth = data.get('azimuth')
    fov = data.get('fov', 70)
    
    # Simular búsqueda
    for landmark_name, landmark_info in LANDMARKS.items():
        dist_lat = abs(lat - landmark_info['lat'])
        dist_lon = abs(lon - landmark_info['lon'])
        
        if dist_lat < landmark_info['radius'] and dist_lon < landmark_info['radius']:
            return jsonify({
                "id": f"landmark_{landmark_name.lower().replace(' ', '_')}",
                "landmark": landmark_name,
                "confidence": 0.85 + (dist_lat + dist_lon) * 10,
                "category": "Monumento",
                "description": f"{landmark_name} detected at {lat}, {lon}",
                "historical_info": f"Historic landmark {landmark_name}",
                "azimuth_match": azimuth,
                "fov_used": fov
            }), 200
    
    return jsonify({
        "landmark": None,
        "confidence": 0.0,
        "description": "No monument detected at this location"
    }), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000)
```

---

## Prueba desde Terminal

### Test 1: Sagrada Familia

```bash
curl -X POST http://172.16.110.15:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"lat": 41.4036, "lon": 2.1744, "azimuth": 0, "fov": 70}'
```

### Test 2: Ubicación sin monumento

```bash
curl -X POST http://172.16.110.15:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"lat": 0, "lon": 0, "azimuth": 0, "fov": 70}'
```

---

## Monitoreo desde App

En el logcat busca:

```bash
adb logcat | grep "RemoteAnalysisService"
```

Verás logs como:

```
D/RemoteAnalysisService: Enviando análisis remoto - Lat: 41.4036, Lon: 2.1744, Azimuth: 0.0, FOV: 70.0
D/RemoteAnalysisService: Respuesta exitosa: {"landmark":"Sagrada Familia","confidence":0.97}
D/LandmarkViewModel: Análisis remoto completado: Sagrada Familia
```

---

## Conclusión

Tu API debería devolver un JSON con al menos el campo `landmark` para que funcione.
Cuantos más campos añadas (confidence, description, etc.), mejor será la experiencia del usuario.

¡Prueba la integración y ajusta según sea necesario! 🚀

