# Data Files - LandmarkLens ML Module

This folder contains data files for the LandmarkLens RAG system: landmarks database, embeddings, and configuration.

## 📁 Files & Structure

### **landmarks.db** (SQLite Database with Vectors)
**Purpose:** Main persistent database for landmarks, embeddings, and spatial indexing.

**Size:** ~11.65 MB (with 50,520 landmarks + embeddings)

**Schema:**
```sql
-- Table 1: Landmarks
CREATE TABLE landmarks (
    id INTEGER PRIMARY KEY,
    osm_id INTEGER UNIQUE,
    name TEXT NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    region TEXT,
    fame_score INTEGER,
    categories TEXT,       -- JSON array
    wikipedia TEXT,
    wikidata TEXT,
    description TEXT,
    created_at TIMESTAMP
);
-- Records: 50,520 landmarks
-- Indexed: lat, lon, region

-- Table 2: Vector Embeddings (nomic-embed-text, 768-dim)
CREATE TABLE embeddings (
    id INTEGER PRIMARY KEY,
    landmark_id INTEGER UNIQUE,
    embedding BLOB NOT NULL,   -- 768 float32 values, 3,072 bytes
    embedding_model TEXT,      -- "nomic-embed-text"
    created_at TIMESTAMP,
    FOREIGN KEY(landmark_id) REFERENCES landmarks(id)
);
-- Records: 50,520 embeddings
-- Type: BLOB (binary float32 arrays)
-- Total size: ~150 MB (50K × 3,072 bytes)

-- Table 3: Spatial Index (Grid-based, O(1) lookup)
CREATE TABLE spatial_index (
    id INTEGER PRIMARY KEY,
    landmark_id INTEGER UNIQUE,
    grid_key TEXT,             -- Format: "lat_grid,lon_grid"
    FOREIGN KEY(landmark_id) REFERENCES landmarks(id)
);
-- Records: 50,520 indexed landmarks
-- Grid size: 0.01 degrees (≈1 km)
```

**Usage:**
```python
from landmark_model.database import LandmarksDB
db = LandmarksDB()
nearby = db.find_nearby(lat=41.4, lon=2.17, radius_km=1.0)
semantic = db.search_by_embedding("iglesia", limit=5)
```

---

### **landmarks.json** (Master Landmarks File)
**Purpose:** Source of truth for all 52,950 landmark records (some without valid coordinates).

**Size:** 8.2 MB

**Format:**
```json
{
  "metadata": {
    "total_landmarks": 52950,
    "sources": ["Cataluña", "Madrid", "Valencia", "País Vasco", "Andorra"],
    "extraction_date": "2026-05-XX",
    "coverage": [
      {"region": "Cataluña", "count": 32646},
      {"region": "Madrid", "count": 4581},
      {"region": "Valencia", "count": 10431},
      {"region": "País Vasco", "count": 4933},
      {"region": "Andorra", "count": 400}
    ]
  },
  "landmarks": [
    {
      "osm_id": 604030704,
      "name": "Torre de Jesús",
      "lat": 41.4036,
      "lon": 2.1744,
      "region": "Cataluña",
      "fame_score": 8,
      "categories": ["architecture", "tower", "historic"],
      "wikipedia": "en:Tower_of_Jesus_Barcelona",
      "wikidata": "Q12345",
      "description": "Historic tower in Barcelona, 19th century"
    },
    ...
  ]
}
```

**Statistics:**
- Total records: 52,950
- With valid coordinates: 50,520
- Without coordinates: 2,430 (excluded from DB)
- Average fame_score: 1.53
- Unique regions: 5

---

### **landmarks_*.json** (Regional Files)
**Purpose:** Individual regional landmark files before merging.

**Files:**
- `landmarks_cataluna.json` - Catalonia: 32,646 landmarks
- `landmarks_madrid.json` - Madrid: 4,581 landmarks
- `landmarks_valencia.json` - Valencia: 10,431 landmarks
- `landmarks_pais-vasco.json` - Basque Country: 4,933 landmarks
- `landmarks_andorra.json` - Andorra: 400 landmarks

**Created by:** `landmark_model/extract_landmarks.py`

---

### **system_prompt.txt** (Ollama System Prompt)
**Purpose:** Instructions for the LLM model when processing queries.

**Content:**
```
You are a landmark identification system. You receive GPS coordinates 
and a numbered list of nearby landmarks with distances and directions.

CRITICAL RULES:
1. NEVER invent landmarks. Use ONLY names from the provided list.
2. Copy landmark names EXACTLY and COMPLETELY.
3. Respond ONLY with valid JSON.
4. Use the exact schema provided.

JSON Schema:
{
  "landmarks": [
    {
      "name": "string (landmark name from list)",
      "distance": "integer (meters)",
      "confidence": "string ('high', 'medium', or 'low')"
    }
  ]
}
```

**Usage:** Loaded by `rag_core.py` for Ollama queries

---

### **training_examples.json** (ML Training Data)
**Purpose:** Examples for training the reranker ML model.

**Format:**
```json
[
  {
    "coordinates": {"lat": 41.4036, "lon": 2.1744},
    "query": "Sagrada Familia",
    "candidates": [
      {"name": "Sagrada Familia", "distance": 1200},
      {"name": "Park Güell", "distance": 3500},
      ...
    ],
    "target": "Sagrada Familia",
    "azimuth": 45,
    "fov": 70
  },
  ...
]
```

**Usage:** `landmark_model/train_models.py` uses this for ML training

---

### **rag_manifest.json** (RAG System Metadata)
**Purpose:** System configuration and metadata generated at setup.

**Content:**
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
  "embedding_dim": 768,
  "regions": ["Cataluña", "Madrid", "Valencia", "País Vasco", "Andorra"],
  "default_fov": 70,
  "grid_size": 0.01,
  "default_max_distance": 500,
  "default_max_results": 8
}
```

---

## 📊 Data Statistics

| Metric | Value |
|--------|-------|
| Total landmarks (extracted) | 52,950 |
| Landmarks with valid coords | 50,520 |
| Database size | 11.65 MB |
| Embeddings generated | 50,520 |
| Embedding dimension | 768 |
| Average fame score | 1.53 |
| Regions covered | 5 |

### Regional Distribution
```
Cataluña:    32,646 (61.8%)
Valencia:    10,431 (19.8%)
País Vasco:   4,933 (9.4%)
Madrid:       4,581 (8.7%)
Andorra:        400 (0.8%)
```

---

## 🔧 Data Processing Pipeline

```
1. Extract from OSM
   landmark_model/extract_landmarks.py
   ↓
   landmarks_*.json (regional files)
   ↓
   landmarks.json (merged, 52,950 records)

2. Migrate to Database
   landmark_model/migrate_to_db.py
   ↓
   landmarks.db (SQLite, 50,520 with coords)

3. Generate Embeddings (MANDATORY)
   landmark_model/generate_embeddings.py
   ↓
   landmarks.db (with 50,520 embeddings)

4. System is Ready
   Vector search enabled
   Semantic queries available
   Full RAG pipeline functional
```

---

## 📥 Data Import

To reimport or update landmarks:

```bash
# 1. Re-extract from OpenStreetMap
python landmark_model/extract_landmarks.py

# 2. Reload into SQLite
python landmark_model/migrate_to_db.py

# 3. Regenerate embeddings
python landmark_model/generate_embeddings.py
```

---

## 🔍 Data Inspection

```python
from landmark_model.database import LandmarksDB
import json

db = LandmarksDB()

# View sample landmarks
cursor = db._get_connection().cursor()
cursor.execute("SELECT name, region, fame_score FROM landmarks LIMIT 5")
for row in cursor.fetchall():
    print(row)

# View statistics
print(json.dumps(db.get_stats(), indent=2))

db.close()
```

---

## ⚖️ License

**Landmarks Data:**
- Source: OpenStreetMap (ODbL License)
- Free to use, modify, and distribute with attribution

**License Details:** https://opendatacommons.org/licenses/odbl/

---

## 🔗 References

- **OpenStreetMap:** https://www.openstreetmap.org
- **OSM Data Extract:** https://www.geofabrik.de/
- **SQLite Documentation:** https://www.sqlite.org/docs.html
- **sqlite-vec:** https://github.com/asg017/sqlite-vec

---

**Last Updated:** May 2026  
**Status:** Production ✅  
**Total Data Size:** ~30 MB (JSON + DB + embeddings)
