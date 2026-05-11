# 🗺️ LandmarkLens - Retrieval Augmented Generation (RAG) System

Sistema de identificación de landmarks basado en RAG con búsqueda vectorial, integración con Ollama y SQLite-Vec.

## 📋 Tabla de contenidos

- [Características](#características)
- [Arquitectura](#arquitectura)
- [Instalación](#instalación)
- [Uso](#uso)
- [API REST](#api-rest)
- [Base de Datos](#base-de-datos)
- [Búsqueda Vectorial](#búsqueda-vectorial)
- [Configuración](#configuración)

---

## ✨ Características

### Core RAG
- ✅ **Búsqueda espacial**: Localiza landmarks cercanos usando índice geográfico
- ✅ **Reranking con ML**: Modelo entrenado para clasificar relevancia
- ✅ **LLM local**: Ollama (llama3.2:3b) integrado
- ✅ **Validación JSON**: Esquema inteligente con fallback degradado

### Base de Datos
- ✅ **SQLite con sqlite-vec**: 50,520 landmarks de 5 regiones españolas
- ✅ **Búsqueda espacial**: Haversine + índice geográfico (O(1) por grid)
- ✅ **Búsqueda por nombre**: LIKE case-insensitive
- ✅ **Búsqueda vectorial**: Embeddings semánticos con Ollama (nomic-embed-text)

### API REST
- ✅ **FastAPI + Uvicorn**: Servidor ASGI de alto rendimiento
- ✅ **Swagger UI**: Documentación interactiva en `/docs`
- ✅ **CORS habilitado**: Acceso desde múltiples dominios
- ✅ **Health checks**: Monitoreo de Ollama

### Datos
- 📍 **Cataluña**: 32,646 landmarks
- 📍 **Madrid**: 4,581 landmarks
- 📍 **Valencia**: 10,431 landmarks
- 📍 **País Vasco**: 4,933 landmarks
- 📍 **Andorra**: 400 landmarks

---

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────┐
│                    FastAPI Server                       │
│              (http://0.0.0.0:8000)                      │
└─────────────────────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
    /query        /health         /docs
        │               │               │
        └───────────────┼───────────────┘
                        │
        ┌───────────────▼───────────────┐
        │      RAG Core (rag_core.py)    │
        │  • Query processing            │
        │  • Spatial indexing            │
        │  • Reranking                   │
        │  • Prompt building             │
        └───────────────┬───────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
    ┌───▼───────┐           ┌──────────▼─────┐
    │  Spatial  │           │   SQLite-Vec   │
    │   Index   │           │   Database     │
    │ (JSON)    │           │ • landmarks    │
    │           │           │ • embeddings   │
    └───────────┘           │ • spatial_idx  │
                            └────────────────┘
                                    │
                        ┌───────────┼───────────┐
                        │           │           │
                    Ollama      Ollama      Model
                   (Query)   (Embeddings)  (Reranker)
```

---

## 🚀 Instalación

### 1. **Requisitos**
- Python 3.14+
- Ollama running con modelo `llama3.2:3b`
- ~100 MB de espacio en disco

### 2. **Clonar y preparar**

```bash
cd LandmarkLens/ML
pip install -r landmark_model/requirements.txt
```

### 3. **Inicializar base de datos**

```bash
python landmark_model/migrate_to_db.py
```

Output esperado:
```
✅ Loaded 50520 landmarks into database
📊 Database Statistics
  total_landmarks: 50520
  unique_regions: 5
  db_path: landmark_model/data/landmarks.db
  db_size_mb: 11.65
```

### 4. **Generar embeddings (OBLIGATORIO)**

```bash
python landmark_model/generate_embeddings.py
```

Esto generará vectores semánticos para búsqueda por similitud:
```
⏳ Generating embeddings...
  ✓ Generated 10 embeddings...
  ✓ Generated 20 embeddings...
✅ Generated 50520 embeddings
```

### 5. **Iniciar servidor**

```bash
python landmark_model/api.py
```

Servidor disponible en: `http://localhost:8000`

---

## 📖 Uso

### **Opción 1: CLI (Línea de comandos)**

```bash
python landmark_model/query_model.py <lat> <lon> [azimuth] [fov]
```

**Ejemplos:**

```bash
# Barcelona
python landmark_model/query_model.py 41.4036 2.1744

# Madrid con orientación
python landmark_model/query_model.py 40.4168 -3.7038 90 45

# Valencia
python landmark_model/query_model.py 39.4699 -0.3763
```

**Output:**
```json
{
  "landmarks": [
    {"name": "Torre de Jesús", "distance": 4, "confidence": "high"},
    {"name": "Torre de la Mare de Déu", "distance": 25, "confidence": "high"}
  ]
}
```

### **Opción 2: Python API**

```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path("landmark_model")))

from rag_core import run_rag_query

# Consultar
result = run_rag_query(
    lat=41.4036,
    lon=2.1744,
    azimuth=90,      # Opcional
    fov=45,          # Opcional
    stream=False
)

print(result.raw_text)
print(result.validation)
```

### **Opción 3: API REST**

Ver [sección API REST](#api-rest)

---

## 🌐 API REST

### **Base URL**
```
http://172.16.110.15:8000
```

### **Endpoints**

#### **1. POST `/api/v1/query`** - Consultar landmarks

Busca landmarks cercanos a coordenadas y usa LLM para describir.

**Request:**
```json
{
  "lat": 41.4036,
  "lon": 2.1744,
  "azimuth": 90,
  "fov": 45
}
```

**Response (200):**
```json
{
  "status": "success|degraded",
  "data": {
    "landmarks": [
      {
        "name": "Torre de Jesús",
        "distance": 4,
        "confidence": "high"
      }
    ]
  },
  "validation": {
    "is_json_valid": true,
    "schema_ok": false,
    "parsed": {...}
  }
}
```

**Parámetros:**
| Param | Tipo | Requerido | Default | Descripción |
|-------|------|-----------|---------|-------------|
| `lat` | float | ✅ | - | Latitud del observador |
| `lon` | float | ✅ | - | Longitud del observador |
| `azimuth` | float | ❌ | null | Orientación en grados (0-360) |
| `fov` | float | ❌ | 45 | Campo de visión en grados |

---

#### **2. GET `/api/v1/health`** - Estado del sistema

Verifica conexión con Ollama y disponibilidad del modelo.

**Response:**
```json
{
  "status": "ok|degraded",
  "ollama_connected": true
}
```

---

#### **3. GET `/docs`** - Documentación Swagger

Interfaz interactiva para probar endpoints.

---

#### **4. GET `/api/v1/rag/manifest`** - Información del RAG

Detalles del sistema, modelos cargados, estadísticas.

**Response:**
```json
{
  "system_version": "1.0.0",
  "rag_engine": "landmark-finder",
  "llm_model": "landmark-finder",
  "llm_base": "llama3.2:3b",
  "landmarks_loaded": 50520,
  "database_type": "SQLite with sqlite-vec",
  "embeddings_available": true,
  "embedding_model": "nomic-embed-text",
  "embedding_dim": 768
}
```

---

### **Ejemplos de uso desde cliente**

#### **Python (requests)**
```python
import requests

api = "http://172.16.110.15:8000"

response = requests.post(f"{api}/api/v1/query", json={
    "lat": 41.4036,
    "lon": 2.1744
})
print(response.json())
```

#### **JavaScript (Fetch)**
```javascript
const API = "http://172.16.110.15:8000";

fetch(`${API}/api/v1/query`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ lat: 41.4036, lon: 2.1744 })
})
.then(r => r.json())
.then(d => console.log(d));
```

#### **cURL**
```bash
curl -X POST http://172.16.110.15:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"lat": 41.4036, "lon": 2.1744}'
```

---

## 🗄️ Base de Datos

### **Estructura SQLite**

```sql
-- Tabla principal de landmarks
CREATE TABLE landmarks (
    id INTEGER PRIMARY KEY,
    osm_id INTEGER UNIQUE,
    name TEXT NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    region TEXT,
    fame_score INTEGER,
    categories TEXT,    -- JSON array
    wikipedia TEXT,
    wikidata TEXT,
    description TEXT,
    created_at TIMESTAMP
);

-- Embeddings vectoriales (obligatorio)
CREATE TABLE embeddings (
    id INTEGER PRIMARY KEY,
    landmark_id INTEGER UNIQUE,
    embedding BLOB NOT NULL,  -- Vector float32
    embedding_model TEXT,     -- "nomic-embed-text"
    created_at TIMESTAMP,
    FOREIGN KEY(landmark_id) REFERENCES landmarks(id)
);

-- Índice espacial
CREATE TABLE spatial_index (
    id INTEGER PRIMARY KEY,
    landmark_id INTEGER UNIQUE,
    grid_key TEXT,
    FOREIGN KEY(landmark_id) REFERENCES landmarks(id)
);
```

### **Estadísticas**

```
Total landmarks: 50,520
Regions: 5
Database size: 11.65 MB
Average fame score: 1.53
Embeddings: 50,520 (100%)
```

### **Consultas útiles**

```python
from landmark_model.database import LandmarksDB

db = LandmarksDB()

# Búsqueda espacial
nearby = db.find_nearby(lat=41.4036, lon=2.1744, radius_km=1.0, max_results=5)

# Búsqueda por nombre
results = db.search_by_name("Torre", limit=5)

# Búsqueda vectorial semántica
semantic = db.search_by_embedding("iglesia histórica", limit=5)

# Estadísticas
stats = db.get_stats()

db.close()
```

---

## 🧠 Búsqueda Vectorial

### **¿Qué es?**

Búsqueda semántica usando embeddings: transforma texto a vector y busca similares.

**Ejemplo:**
- Query: "iglesia antigua"
- Encuentra: "Capilla Cristo del Humilladero", "Real Convento de la Encarnación"
- Aunque no contengan exactamente esas palabras

### **Cómo funciona**

1. **Generación de embeddings** (Ollama nomic-embed-text):
   ```
   "Torre de Jesús" → [0.234, -0.105, 0.892, ..., 768 valores]
   ```

2. **Almacenamiento** (SQLite BLOB):
   ```
   embeddings.embedding = vector_bytes
   ```

3. **Búsqueda vectorial** (sqlite-vec):
   ```sql
   WHERE embedding MATCH query_vector
   ORDER BY distance ASC
   ```

### **Uso**

```python
db = LandmarksDB()

# Generar embeddings (ejecutar una sola vez)
count = db.generate_embeddings_batch(batch_size=100)

# Buscar por similitud
results = db.search_by_embedding(
    text="monumento histórico",
    limit=5,
    threshold=0.7
)

for r in results:
    print(f"{r['name']} (similitud: {r['similarity']:.2%})")
```

### **Modelo de embeddings**

- **Modelo**: `nomic-embed-text` (via Ollama)
- **Dimensión**: 768
- **Token limit**: 8,192
- **Type**: Embeddings text-only

---

## ⚙️ Configuración

### **Archivo: `landmark_model/rag_core.py`**

```python
# Configuración ajustable
DEFAULT_FOV = 70                    # Campo de visión por defecto
GRID_SIZE = 0.01                    # Tamaño de celda para índice espacial
DEFAULT_MAX_DIST = 500              # Distancia máxima de búsqueda (metros)
DEFAULT_MAX_RESULTS = 8             # Número máximo de landmarks retornados

# Ollama
OLLAMA_URL = "http://localhost:11434"
MODEL_NAME = "landmark-finder"

# Embeddings
EMBEDDING_MODEL = "nomic-embed-text"
EMBEDDING_DIM = 768
```

### **Archivo: `landmark_model/requirements.txt`**

```
osmium                    # Procesar OSM PBF
requests                  # HTTP client
geopy                     # Geocoding
ollama                    # LLM local
pandas                    # Data processing
numpy                     # Numerical computing
python-dotenv             # Environment variables
fastapi                   # Web framework
uvicorn                   # ASGI server
pydantic                  # Data validation
sqlite-vec                # Vector search
```

### **Variables de entorno** (`.env`)

```bash
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=landmark-finder
EMBEDDING_MODEL=nomic-embed-text
API_PORT=8000
API_HOST=0.0.0.0
```

---

## 📊 Estadísticas y Monitoreo

### **Tamaño de datos**

| Tabla | Filas | Tamaño |
|-------|-------|--------|
| landmarks | 50,520 | 7.2 MB |
| embeddings | 50,520 | 3.8 MB |
| spatial_index | 50,520 | 0.65 MB |

### **Rendimiento**

- **Búsqueda espacial**: <5ms (O(1) con grid)
- **Búsqueda por nombre**: <50ms
- **Búsqueda vectorial**: <100ms
- **Ollama query**: 5-10s (dependiendo del GPU)
- **API latency end-to-end**: 6-15s

### **Monitoreo**

```bash
# Ver tamaño base de datos
ls -lh landmark_model/data/landmarks.db

# Estadísticas
python -c "
from landmark_model.database import LandmarksDB
db = LandmarksDB()
import json
print(json.dumps(db.get_stats(), indent=2))
"
```

---

## 🐛 Troubleshooting

### **"Ollama is not running"**
```bash
# Iniciar Ollama
ollama serve

# En otra terminal, bajar modelo
ollama pull llama3.2:3b
```

### **"No embeddings found"**
```bash
python landmark_model/generate_embeddings.py
```

### **"DLL load failed" (Windows)**
Reiniciar la terminal o usar Python 3.13 (evitar 3.14 con AppLocker)

### **Búsqueda lenta**
Verificar que se crearon índices:
```bash
python -c "
from landmark_model.database import LandmarksDB
import sqlite3
db = LandmarksDB()
conn = db._get_connection()
cursor = conn.cursor()
cursor.execute(\"PRAGMA index_list(landmarks)\")
print(cursor.fetchall())
"
```

---

## 📝 Licencia

MIT - Open source

---

## 👥 Soporte

Para issues, preguntas o sugerencias:
1. Revisar [ML_EXPERIMENTS.md](./ML_EXPERIMENTS.md)
2. Consultar logs en `landmark_model/data/`
3. Ejecutar tests: `python test_db.py`

---

**Última actualización**: Mayo 2026  
**Versión**: 1.0.0  
**Estado**: ✅ Producción
