# 🚀 Quick Reference - LandmarkLens Commands

Common commands and usage patterns.

## Installation

```bash
# Install dependencies
pip install -r landmark_model/requirements.txt

# Initialize database
python landmark_model/migrate_to_db.py

# Generate embeddings (MANDATORY)
python landmark_model/generate_embeddings.py

# (Optional) Start API server
python landmark_model/api.py
```

## Usage - CLI

```bash
# Basic query
python landmark_model/query_model.py <lat> <lon>

# With direction (azimuth)
python landmark_model/query_model.py <lat> <lon> <azimuth>

# With direction + field of view
python landmark_model/query_model.py <lat> <lon> <azimuth> <fov>
```

**Examples:**
```bash
python landmark_model/query_model.py 41.4036 2.1744              # Barcelona
python landmark_model/query_model.py 40.4168 -3.7038 45          # Madrid, NE direction
python landmark_model/query_model.py 39.4699 -0.3763 0 90        # Valencia, North, 90° FOV
```

## Usage - Python API

```python
from landmark_model.rag_core import run_rag_query
from landmark_model.database import LandmarksDB

# RAG query
result = run_rag_query(lat=41.4036, lon=2.1744)
print(result.raw_text)

# Spatial search
db = LandmarksDB()
nearby = db.find_nearby(lat=41.4036, lon=2.1744, radius_km=1.0)

# Vector search
semantic = db.search_by_embedding("iglesia histórica", limit=5)

# Name search
results = db.search_by_name("Torre", limit=5)

db.close()
```

## Usage - REST API

```bash
# Health check
curl http://localhost:8000/api/v1/health

# Query landmarks
curl -X POST http://localhost:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"lat": 41.4036, "lon": 2.1744}'

# System info
curl http://localhost:8000/api/v1/rag/manifest

# Swagger UI
# Open in browser: http://localhost:8000/docs
```

## Database Operations

```python
from landmark_model.database import LandmarksDB

db = LandmarksDB()

# Spatial search (fast)
nearby = db.find_nearby(lat, lon, radius_km=1.0, max_results=10)

# Name search
results = db.search_by_name("Catedral", limit=5)

# Vector search (semantic)
semantic = db.search_by_embedding("iglesia gótica", limit=5, threshold=0.7)

# Statistics
stats = db.get_stats()
print(f"Total landmarks: {stats['total_landmarks']}")
print(f"Embeddings: {stats['landmarks_with_embeddings']}")
print(f"Database size: {stats['db_size_mb']} MB")

db.close()
```

## Important Files

| File | Purpose |
|------|---------|
| `api.py` | FastAPI REST server |
| `rag_core.py` | Core RAG engine |
| `database.py` | SQLite-Vec database layer |
| `query_model.py` | CLI query interface |
| `generate_embeddings.py` | Vector embedding generation |
| `data/landmarks.db` | Main database file |
| `data/landmarks.json` | Master landmarks file |
| `API_DOCUMENTATION.md` | API reference |
| `VECTORIAL_SEARCH.md` | Vector search guide |

## Configuration

| Setting | File | Variable |
|---------|------|----------|
| LLM Model | `rag_core.py` | `MODEL_NAME` |
| FOV Default | `rag_core.py` | `DEFAULT_FOV` |
| Max Distance | `rag_core.py` | `DEFAULT_MAX_DIST` |
| Embedding Model | `database.py` | `EMBEDDING_MODEL` |
| Embedding Dimension | `database.py` | `EMBEDDING_DIM` |
| API Port | `api.py` | `PORT` |
| Ollama URL | Environment | `OLLAMA_URL` |

## Performance Tips

```python
# Faster query: Reduce FOV
python query_model.py 41.4 2.17 45 45  # 45° instead of 180°

# Faster search: Smaller radius
db.find_nearby(lat, lon, radius_km=0.5)  # 500m instead of 2km

# Faster embedding generation: Larger batches
db.generate_embeddings_batch(batch_size=500)  # 500 instead of 100

# Faster vector search: Higher threshold
db.search_by_embedding(query, limit=10, threshold=0.85)
```

## Debugging

```python
# Check database integrity
from landmark_model.database import LandmarksDB
db = LandmarksDB()
stats = db.get_stats()
print(stats)

# Check Ollama connection
from landmark_model.rag_core import check_ollama
if check_ollama():
    print("✅ Ollama OK")
else:
    print("❌ Ollama not responding")

# Verify embeddings exist
db.get_stats()['landmarks_with_embeddings']  # Should be 50520

# Manual prompt validation
with open("landmark_model/data/system_prompt.txt") as f:
    prompt = f.read()
    assert "NEVER invent" in prompt
```

## Common Issues

| Issue | Solution |
|-------|----------|
| "Connection refused" | `ollama serve` |
| "Model not found" | `ollama pull nomic-embed-text` |
| No embeddings | `python landmark_model/generate_embeddings.py` |
| Slow queries | Reduce FOV, radius, or increase batch size |
| JSON errors | Check `data/system_prompt.txt` |
| DLL errors (Windows) | Restart terminal or use Python 3.13 |

## Health Checks

```bash
# Is Ollama running?
curl http://localhost:11434/api/tags

# Is database ready?
python -c "from landmark_model.database import LandmarksDB; db = LandmarksDB(); print(db.get_stats())"

# Is API up?
curl http://localhost:8000/api/v1/health

# Do embeddings exist?
python -c "from landmark_model.database import LandmarksDB; db = LandmarksDB(); s = db.get_stats(); print(f'Embeddings: {s[\"landmarks_with_embeddings\"]}/50520')"
```

## Full Workflow

```bash
# 1. Setup
pip install -r landmark_model/requirements.txt

# 2. Database
python landmark_model/migrate_to_db.py
python landmark_model/generate_embeddings.py

# 3. Server (optional)
python landmark_model/api.py

# 4. Query via CLI
python landmark_model/query_model.py 41.4036 2.1744

# 5. Query via API
curl http://localhost:8000/api/v1/query \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"lat": 41.4036, "lon": 2.1744}'

# 6. Monitor
curl http://localhost:8000/api/v1/health
```

---

**For detailed documentation, see:**
- [README.md](./README.md)
- [API_DOCUMENTATION.md](./API_DOCUMENTATION.md)
- [VECTORIAL_SEARCH.md](./VECTORIAL_SEARCH.md)
