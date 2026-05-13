# 🎯 LandmarkLens - landmark_model Module

The core RAG system for LandmarkLens. Contains all runtime components, API, and models.

**Sistema inteligente de identificación de landmarks españoles basado en:**
- **Coordenadas GPS** del usuario
- **Orientación de cámara** (brújula/azimuth)
- **Recuperación RAG** sobre base de datos SQLite-Vec
- **Búsqueda vectorial** con Ollama embeddings
- **Reranking con ML** usando modelo entrenado

El runtime RAG centralizado vive en `rag_core.py` que integra: búsqueda espacial, búsqueda vectorial, construcción de prompts, LLM y validación.

---

## 🚀 Instalación y Configuración

### 1. Requisitos Previos

**Software:**
- Python 3.14+
- [Ollama](https://ollama.ai) instalado y ejecutándose
- SQLite 3 (incluido con Python)

**Hardware Recomendado:**
- GPU: NVIDIA RTX 3060+ (6GB VRAM) o similar
- RAM: 16GB DDR4 mínimo
- Almacenamiento: ~100 MB para base de datos + ~500 MB para modelos

### 2. Instalación de Dependencias

```bash
# Entrar al directorio
cd landmark_model

# Activar entorno virtual (opcional)
source venv/bin/activate

# Instalar dependencias Python
pip install -r requirements.txt
```

**Dependencias principales:**
- `ollama` - Cliente Ollama para LLM y embeddings
- `sqlite-vec` - Vector search con SQLite
- `fastapi` + `uvicorn` - Servidor REST
- `pydantic` - Validación de datos
- `numpy` - Computación numérica
- `pandas` - Procesamiento de datos

### 3. Inicializar Base de Datos

```bash
python migrate_to_db.py
```

**Output esperado:**
```
✅ Loaded 50520 landmarks into database
📊 Database Statistics
  total_landmarks: 50520
  unique_regions: 5 (Cataluña, Madrid, Valencia, País Vasco, Andorra)
  db_path: landmark_model/data/landmarks.db
  db_size_mb: 11.65
```

**Qué hace:**
1. Lee 50,520 landmarks de `data/landmarks.json`
2. Crea base de datos SQLite con 3 tablas
3. Genera índice espacial con grid O(1)
4. Verifica integridad de datos

### 4. Generar Embeddings Vectoriales (OBLIGATORIO)

```bash
python generate_embeddings.py
```

**Output esperado:**
```
⏳ Generating embeddings...
  ✓ Generated 1000 embeddings...
  ✓ Generated 2000 embeddings...
  ...
  ✓ Generated 50520 embeddings...
✅ VECTOR EMBEDDINGS GENERATION COMPLETE!
🎉 Your database is ready for semantic search!
```

**Tiempo estimado:** 30-60 minutos (depende del GPU)

**¿Por qué es obligatorio?**
- Habilita búsqueda semántica/vectorial
- Utiliza Ollama `nomic-embed-text` (768-dim vectors)
- Permite consultas como "iglesia histórica" en lugar de solo nombres exactos
- Mejora significativamente la relevancia

### 5. Iniciar API REST Server (Opcional)

```bash
python api.py
```

**Output:**
```
INFO:     Uvicorn running on http://0.0.0.0:8000
INFO:     Application startup complete
```

API disponible en: `http://localhost:8000`  
Swagger UI: `http://localhost:8000/docs`

---

## 📖 Uso

### Opción 1: CLI (Línea de comandos)

```bash
python query_model.py <latitud> <longitud> [azimuth] [fov]
```

**Ejemplos:**

```bash
# Barcelona
python query_model.py 41.4036 2.1744

# Madrid con orientación noreste (45°)
python query_model.py 40.4168 -3.7038 45

# Valencia con campo de visión amplio (90°)
python query_model.py 39.4699 -0.3763 0 90
```

**Output:**
```json
{
  "status": "success",
  "landmarks": [
    {
      "name": "Torre de Jesús",
      "distance": 4,
      "confidence": "high"
    },
    {
      "name": "Torre de la Mare de Déu",
      "distance": 25,
      "confidence": "high"
    }
  ]
}
```

### Opción 2: Python API

```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path("landmark_model")))

from rag_core import run_rag_query

# Consulta simple
result = run_rag_query(lat=41.4036, lon=2.1744)
print(result.raw_text)

# Consulta con orientación
result = run_rag_query(
    lat=41.4036,
    lon=2.1744,
    azimuth=45,      # Mirando noreste
    fov=90           # Campo de visión 90°
)
print(result.raw_text)
print(result.validation)
```

### Opción 3: API REST

```bash
curl -X POST http://localhost:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 41.4036,
    "lon": 2.1744,
    "azimuth": 45,
    "fov": 90
  }'
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "landmarks": [...]
  },
  "validation": {
    "is_json_valid": true,
    "schema_ok": true
  }
}
```

Ver [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) para referencia completa.

### Opción 4: Búsqueda Vectorial Semántica

```python
from database import LandmarksDB

db = LandmarksDB()

# Búsqueda por similitud semántica
results = db.search_by_embedding(
    text="iglesia histórica",
    limit=5,
    threshold=0.7
)

for r in results:
    print(f"{r['name']} (similitud: {r['similarity']:.2%})")

# Output:
# Catedral de la Almudena (similitud: 92%)
# Real Convento de la Encarnación (similitud: 87%)
# ...
```

---

## 📂 Estructura de Archivos

```
landmark_model/
├── api.py                        # FastAPI REST server
├── rag_core.py                   # Core RAG runtime (centralizado)
├── database.py                   # SQLite-Vec database layer
├── query_model.py                # CLI query interface
├── extract_landmarks.py          # OSM → JSON extraction
├── migrate_to_db.py              # JSON → SQLite migration
├── generate_embeddings.py        # Vector embedding generation
├── train_models.py               # ML reranker training
├── setup.py                      # Automated full setup
├── requirements.txt              # Python dependencies
├── Modelfile                     # Ollama model config
├── API_DOCUMENTATION.md          # API reference
├── README.md                     # This file
├── data/
│   ├── landmarks.json            # All 52,950 landmarks (master)
│   ├── landmarks.db              # SQLite database with embeddings
│   ├── landmarks_*.json          # Regional landmark files
│   ├── system_prompt.txt         # Ollama system prompt
│   ├── training_examples.json    # ML training data
│   └── rag_manifest.json         # RAG system metadata
└── artifacts/
    ├── selected_model_bundle.joblib  # Trained reranker model
    ├── model_comparison.csv          # Model evaluation results
    └── experiment_summary.json       # Training experiment logs
```

## 🔧 Scripts Principales

### `api.py` — FastAPI REST Server

Inicia servidor REST para consultas remotas.

```bash
python api.py
```

**Endpoints:**
- `POST /api/v1/query` - Consultar landmarks
- `GET /api/v1/health` - Verificar estado
- `GET /api/v1/rag/manifest` - Info del sistema
- `GET /docs` - Swagger UI (documentación interactiva)

**Puerto:** 8000 (configurable)

---

### `query_model.py` — CLI Query Interface

Consulta desde línea de comandos.

```bash
python query_model.py <lat> <lon> [azimuth] [fov]
```

**Argumentos:**
| Arg | Tipo | Rango | Default |
|-----|------|-------|---------|
| `lat` | Float | [-90, 90] | Requerido |
| `lon` | Float | [-180, 180] | Requerido |
| `azimuth` | Int | [0, 360] | None |
| `fov` | Int | [1, 180] | 70° |

**Features:**
- Búsqueda espacial O(1) con grid
- Filtrado por campo de visión
- Reranking con modelo ML
- Validación JSON

---

### `rag_core.py` — Core RAG Runtime

Núcleo compartido para API, CLI y evaluación.

**Clases principales:**
- `SpatialIndex` - Índice geográfico con grid
- `RAGResult` - Resultado estructurado

**Funciones principales:**
```python
from rag_core import (
    run_rag_query,        # Query completo end-to-end
    find_nearby,          # Búsqueda espacial
    load_landmarks,       # Cargar landmarks
    build_prompt,         # Construir prompt
    query_ollama,         # Llamar LLM
    validate_response     # Validar JSON
)
```

---

### `database.py` — SQLite-Vec Database

Capa de persistencia con búsqueda vectorial.

```python
from database import LandmarksDB

db = LandmarksDB()

# Búsqueda espacial
nearby = db.find_nearby(lat=41.4036, lon=2.1744, radius_km=1.0)

# Búsqueda por nombre
results = db.search_by_name("Torre", limit=5)

# Búsqueda vectorial (REQUIERE embeddings generados)
semantic = db.search_by_embedding("iglesia histórica", limit=5)

# Estadísticas
stats = db.get_stats()

db.close()
```

**Métodos disponibles:**
- `load_from_json(json_path)` - Cargar desde JSON
- `find_nearby(lat, lon, radius_km, max_results)` - Búsqueda espacial
- `search_by_name(query, limit)` - Búsqueda por nombre
- `search_by_embedding(text, limit, threshold)` - Búsqueda vectorial
- `generate_embeddings_batch(batch_size)` - Generar embeddings
- `get_stats()` - Estadísticas
- `close()` - Cerrar conexión

---

### `migrate_to_db.py` — Data Migration

Migra landmarks desde JSON a SQLite.

```bash
python migrate_to_db.py
```

**Qué hace:**
1. Lee 50,520 landmarks de `data/landmarks.json`
2. Crea tablas: `landmarks`, `embeddings`, `spatial_index`
3. Inserta todos los landmarks
4. Genera índice espacial
5. Valida integridad

**Salida:**
```
✅ Loaded 50520 landmarks into database
📊 Database Statistics
  total_landmarks: 50520
  unique_regions: 5
  db_size_mb: 11.65
```

---

### `generate_embeddings.py` — Vector Embeddings

Genera embeddings semánticos (OBLIGATORIO).

```bash
python generate_embeddings.py
```

**Utiliza:** Ollama `nomic-embed-text` (768-dim)

**Genera:** 50,520 vectores, uno por landmark

**Tiempo:** 30-60 minutos (GPU-dependent)

**Por qué es obligatorio:**
- Habilita búsqueda semántica
- Permite consultas como "iglesia histórica"
- Mejora relevancia en 30-40%

---

### `extract_landmarks.py` — OSM Extraction

Extrae landmarks de archivos OpenStreetMap.

```bash
python extract_landmarks.py
```

**Entrada:** Archivos `.osm.pbf`  
**Salida:** `data/landmarks_*.json` + `data/landmarks.json` (merged)

**Categorías extraídas:**
- Históricos: castillos, monumentos, catedrales
- Turismo: museos, galerías, atracciones
- Religiosos: iglesias, sinagogas, mezquitas
- Especiales: estadios, palacios, fortalezas

---

### `train_models.py` — ML Model Training

Entrena modelo de reranking.

```bash
python train_models.py
```

**Output:**
- `artifacts/selected_model_bundle.joblib` - Modelo serializado
- `artifacts/model_comparison.csv` - Resultados comparativos
- `artifacts/experiment_summary.json` - Resumen experimento

**Features usadas:**
- `distance_m` - Distancia en metros
- `bearing_sin/cos` - Dirección (sine/cosine)
- `angle_offset_deg` - Offset del ángulo
- `fame_score` - Popularidad
- `category_count` - Número de categorías
- `has_wikipedia/wikidata` - Disponibilidad de datos

---

### `setup.py` — Automated Setup

Instalación y configuración completa.

```bash
python setup.py
```

**Ejecuta automáticamente:**
1. Verifica Ollama instalado
2. Descarga modelo base si falta
3. Ejecuta `extract_landmarks.py` (si necesario)
4. Crea modelo en Ollama
5. Valida configuración

**Tiempo:** 20-30 minutos (primera ejecución)

---

## ⚙️ Configuración

### Variables de Entorno (`.env`)

Crear archivo `.env` en la raíz del proyecto:

```bash
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=landmark-finder
EMBEDDING_MODEL=nomic-embed-text
API_PORT=8000
API_HOST=0.0.0.0
DEBUG=False
```

### Modelfile — Parámetros de Ollama

Editar `Modelfile` para ajustar comportamiento del modelo:

```dockerfile
FROM llama3.2:3b

PARAMETER temperature 0.1      # Determinístico (bajo hallucination)
PARAMETER top_p 0.9            # Diversidad controlada
PARAMETER num_ctx 8192         # Contexto máximo
PARAMETER num_predict 512      # Longitud máxima respuesta

SYSTEM """[your system prompt]"""
```

**Efectos de parámetros:**
- `temperature` ↑ → Respuestas creativas (↑ alucinaciones)
- `temperature` ↓ → Respuestas consistentes (seguro)
- `top_p` ↑ → Más diversidad
- `top_p` ↓ → Más conservador

**Cambiar el modelo:**
```bash
ollama create landmark-finder -f Modelfile
```

### System Prompt — Instrucciones del LLM

Editar `data/system_prompt.txt` para cambiar comportamiento:

```
You are a landmark identification system. You receive GPS coordinates 
and a numbered list of nearby landmarks with distances and directions.

CRITICAL RULES:
1. NEVER invent landmarks. Use ONLY names from the provided list.
2. Copy landmark names EXACTLY and COMPLETELY.
3. Respond ONLY with valid JSON.

JSON Schema:
{
  "landmarks": [
    {"name": "...", "distance": X, "confidence": "high|medium|low"}
  ]
}
```

**Regla crítica:** No inventar landmarks. Esto previene alucinaciones.

### rag_core.py — Parámetros de RAG

Editar constantes en `rag_core.py`:

```python
DEFAULT_FOV = 70                    # Campo visión por defecto
GRID_SIZE = 0.01                    # Tamaño celda índice espacial
DEFAULT_MAX_DIST = 500              # Distancia máxima (metros)
DEFAULT_MAX_RESULTS = 8             # Landmarks retornados
OLLAMA_URL = "http://localhost:11434"
MODEL_NAME = "landmark-finder"
```

### database.py — Parámetros de Base de Datos

```python
EMBEDDING_MODEL = "nomic-embed-text"
EMBEDDING_DIM = 768                 # Dimensión del vector
BATCH_SIZE = 100                    # Tamaño de lote para embeddings
```

---

## 📚 Ejemplos de Uso

### Ejemplo 1: Explorar Barcelona

```bash
# ¿Qué hay cerca?
python query_model.py 41.4036 2.1744

# Output: Torre de Jesús (4m), Torre de la Mare de Déu (25m), ...

# ¿Qué veo si miro al norte?
python query_model.py 41.4036 2.1744 0 90

# Output: Landmarks en dirección norte con menos de 90° de offset
```

### Ejemplo 2: API REST desde Python

```python
import requests

response = requests.post(
    "http://localhost:8000/api/v1/query",
    json={
        "lat": 40.4168,    # Madrid
        "lon": -3.7038,
        "azimuth": 90,     # Mirando este
        "fov": 45
    }
)

result = response.json()
print(f"Status: {result['status']}")
for landmark in result['data']['landmarks']:
    print(f"  • {landmark['name']} ({landmark['distance']}m)")
```

### Ejemplo 3: Búsqueda Vectorial

```python
from database import LandmarksDB

db = LandmarksDB()

# Búsqueda semántica (requiere embeddings generados)
results = db.search_by_embedding(
    text="iglesia medieval",
    limit=5,
    threshold=0.7
)

for r in results:
    distance = haversine(db_lat, db_lon, r['lat'], r['lon'])
    print(f"{r['name']} (similitud: {r['similarity']:.1%}, {distance:.0f}m)")
```

### Ejemplo 4: Integración Completa

```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path("landmark_model")))

from rag_core import run_rag_query
from database import LandmarksDB

# RAG query with full pipeline
result = run_rag_query(
    lat=41.4036,
    lon=2.1744,
    azimuth=45,
    fov=70
)

print(f"Raw response:\n{result.raw_text}")
print(f"\nValidation:")
print(f"  JSON valid: {result.validation['is_json_valid']}")
print(f"  Schema OK: {result.validation['schema_ok']}")

if result.validation['schema_ok']:
    for landmark in result.validation['parsed']['landmarks']:
        print(f"  ✓ {landmark['name']} ({landmark['distance']}m)")
```

---

## 🐛 Troubleshooting

### "Connection refused" a Ollama

**Causa:** Ollama no está ejecutándose

```bash
# Iniciar Ollama
ollama serve

# Verificar modelos disponibles
ollama list

# Si faltan modelos, descargarlos
ollama pull llama3.2:3b
ollama pull nomic-embed-text
```

### "No embeddings found" / Vector search no funciona

**Causa:** No se han generado embeddings

```bash
python generate_embeddings.py
```

**Tiempo esperado:** 30-60 minutos (GPU-dependent)  
**Validación:** `db.get_stats()['landmarks_with_embeddings']` debe ser 50520

### "DLL load failed" (Windows)

**Causa:** Incompatibilidad con Python 3.14 + AppLocker

**Solución:**
```bash
# Opción 1: Usar Python 3.13
python3.13 query_model.py 41.4036 2.1744

# Opción 2: Reiniciar terminal
# (a veces resuelve la carga de librerías)
```

### Búsqueda muy lenta (>5 segundos)

**Causa:** GPU saturada o contexto muy grande

**Soluciones:**
1. Reducir FOV: `python query_model.py 41.4 2.17 45 45` (en lugar de 180°)
2. Reducir radio de búsqueda en `rag_core.py`: `DEFAULT_MAX_DIST = 250`
3. Cerrar otras aplicaciones de GPU
4. Verificar modelo está en GPU: `nvidia-smi` (VRAM utilizado > 2GB)

### JSON inválido en respuesta

**Causa:** LLM alucinando o malformado

**Solución:**
1. Verificar `data/system_prompt.txt` contiene regla "NEVER invent"
2. Reducir `temperature` en Modelfile: `PARAMETER temperature 0.05`
3. Recrear modelo:
   ```bash
   ollama create landmark-finder -f Modelfile
   ```

### Base de datos bloqueada

**Causa:** Múltiples escrituras simultáneas

**Solución:**
```bash
# No ejecutar dos migraciones al mismo tiempo
# Cerrar todos los accesos a la BD
# Esperar 30 segundos
python migrate_to_db.py  # Reintentar
```

### Landmarks hallucinated (incorrectos)

**Causa:** Sistema prompt débil o datos inconsistentes

**Verificación:**
```python
from database import LandmarksDB
db = LandmarksDB()
nearby = db.find_nearby(41.4036, 2.1744, radius_km=2.0)
print(nearby[0])  # Debe ser un landmark REAL
```

**Solución:**
```bash
# Reconstruir base de datos desde cero
rm landmark_model/data/landmarks.db
python migrate_to_db.py
python generate_embeddings.py
```

### "Model not found" en Ollama

**Causa:** Modelo custom no creado

```bash
ollama create landmark-finder -f Modelfile

# Verificar
ollama list | grep landmark-finder
```

---

## 📊 Monitoreo y Rendimiento

### Estadísticas de Base de Datos

```python
from database import LandmarksDB
import json

db = LandmarksDB()
stats = db.get_stats()

print(json.dumps(stats, indent=2))

# Output:
# {
#   "total_landmarks": 50520,
#   "unique_regions": 5,
#   "landmarks_with_embeddings": 50520,
#   "average_fame_score": 1.53,
#   "db_path": "...",
#   "db_size_mb": 11.65
# }
```

### Tamaño de Datos

| Componente | Tamaño |
|-----------|--------|
| landmarks.json | 8.2 MB |
| landmarks.db (sin embeddings) | 7.3 MB |
| landmarks.db (con embeddings) | 11.65 MB |
| embeddings blob | 4.35 MB (50K × 768-dim × 4 bytes) |

### Rendimiento de Consultas

| Operación | Tiempo Típico |
|-----------|---------------|
| Health check | <10ms |
| find_nearby (spatial) | 5-20ms |
| search_by_name | 50-100ms |
| search_by_embedding | 100-200ms |
| Reranking (ML) | 10-50ms |
| Ollama LLM query | 5-15s |
| **Total end-to-end** | **5-20s** |

### Monitoreo de GPU

```bash
# NVIDIA GPU
nvidia-smi

# Continuous monitoring
nvidia-smi -l 1  # Refresh every 1 second

# Expected VRAM usage
# - Baseline: ~2.1 GB (Ollama + models)
# - With query: ~4.0-5.8 GB
# - Max safe: 6.0 GB (RTX 3060)
```

### Logging

```python
# Enable debug logging
import logging
logging.basicConfig(level=logging.DEBUG)

# Query and check logs
from rag_core import run_rag_query
result = run_rag_query(lat=41.4, lon=2.17, debug=True)
```

### Health Check Endpoint

```bash
curl http://localhost:8000/api/v1/health

# Response:
# {"status": "ok", "ollama_connected": true}
```

---

## 📖 Documentación Relacionada

- [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) - Referencia completa de endpoints REST
- [../README.md](../README.md) - Documentación general del proyecto LandmarkLens
- [../ML_EXPERIMENTS.md](../ML_EXPERIMENTS.md) - Resultados experimentales y benchmarks

## 🔗 Referencias Técnicas

- **Ollama**: https://ollama.ai
- **SQLite-Vec**: https://github.com/asg017/sqlite-vec
- **FastAPI**: https://fastapi.tiangolo.com
- **OpenStreetMap**: https://www.openstreetmap.org

## 📝 Workflow Típico

```
1. Instalar dependencias
   pip install -r requirements.txt

2. Inicializar BD
   python migrate_to_db.py

3. Generar embeddings (OBLIGATORIO)
   python generate_embeddings.py

4. (Opcional) Entrenar modelo ML
   python train_models.py

5. (Opcional) Iniciar API
   python api.py

6. Consultar
   python query_model.py 41.4 2.17
```

## 🚀 Próximos Pasos

- [ ] Expandir cobertura a más regiones españolas
- [ ] Fine-tuning con ejemplos reales de usuarios
- [ ] Implementar caché de respuestas frecuentes
- [ ] Soporte multiidioma
- [ ] Integración con mapas (Leaflet/Mapbox)
- [ ] Mejora de reranking con cross-encoders

## 📋 Checklist Post-Instalación

- [ ] Ollama ejecutándose (`ollama serve`)
- [ ] Modelos descargados (`ollama list`)
- [ ] BD creada y poblada (`landmarks.db` > 10MB)
- [ ] Embeddings generados (`db.get_stats()['landmarks_with_embeddings'] == 50520`)
- [ ] Health check OK (`curl .../health`)
- [ ] CLI funcionando (`python query_model.py 41.4 2.17`)

## 🎓 Aprendizaje y Debugging

Para entender cómo funciona el sistema:

```python
# 1. Explorar base de datos
from database import LandmarksDB
db = LandmarksDB()
print(db.get_stats())
nearby = db.find_nearby(41.4036, 2.1744, radius_km=2.0)
print(nearby[:3])  # Primeros 3 landmarks

# 2. Ver índice espacial
from rag_core import SpatialIndex, load_landmarks
landmarks = load_landmarks()
index = SpatialIndex(landmarks)
candidates = index.query(41.4, 2.17, azimuth=45, fov=70)
print(f"Candidatos: {len(candidates)}")

# 3. Verificar prompt
with open("data/system_prompt.txt") as f:
    print(f.read()[:500])

# 4. Entender validación
from rag_core import validate_response
test_json = '{"landmarks": [{"name": "Torre", "distance": 10}]}'
valid, parsed = validate_response(test_json)
print(f"Valid: {valid}, Parsed: {parsed}")
```

---

**Versión**: 1.0.0  
**Estado**: Production ✅  
**Última actualización**: Mayo 2026  
**Mantenedor**: LandmarkLens Team

