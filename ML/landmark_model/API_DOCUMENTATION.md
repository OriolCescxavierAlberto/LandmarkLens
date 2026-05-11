# 🌐 API REST Documentation - LandmarkLens

Complete API reference for LandmarkLens REST endpoints.

## Base URL

```
http://172.16.110.15:8000
```

## Authentication

No authentication required. All endpoints are open.

## Content-Type

All requests and responses use `application/json`.

---

## Endpoints

### 1. POST `/api/v1/query` - Query Landmarks

Find landmarks near coordinates and get AI-generated descriptions.

#### Request

```http
POST /api/v1/query HTTP/1.1
Host: 172.16.110.15:8000
Content-Type: application/json

{
  "lat": 41.4036,
  "lon": 2.1744,
  "azimuth": 90,
  "fov": 45
}
```

#### Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `lat` | number | ✅ | - | Observer latitude (-90 to 90) |
| `lon` | number | ✅ | - | Observer longitude (-180 to 180) |
| `azimuth` | number | ❌ | null | Camera orientation (0-360°) |
| `fov` | number | ❌ | 45 | Field of view in degrees (1-180) |

#### Response (200 OK)

**Success Response** (JSON schema matches exactly):
```json
{
  "status": "success",
  "data": {
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
  },
  "validation": {
    "is_json_valid": true,
    "schema_ok": true,
    "parsed": {...},
    "predicted_names": [],
    "all_predicted_in_candidates": true,
    "issues": []
  }
}
```

**Degraded Response** (JSON valid but schema mismatch):
```json
{
  "status": "degraded",
  "data": {
    "landmarks": [...]
  },
  "raw_response": "{...}",
  "validation": {
    "is_json_valid": true,
    "schema_ok": false,
    "parsed": {...},
    "issues": ["schema_mismatch", "prediction_outside_candidates"]
  }
}
```

#### Response Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | OK | Successfully queried landmarks |
| 400 | Bad Request | Invalid parameters (missing lat/lon) |
| 502 | Bad Gateway | Ollama not responding |
| 500 | Internal Error | Server error |

#### Example Requests

**Python (requests)**
```python
import requests
import json

response = requests.post(
    "http://172.16.110.15:8000/api/v1/query",
    json={
        "lat": 41.4036,
        "lon": 2.1744,
        "azimuth": 90,
        "fov": 45
    }
)

print(json.dumps(response.json(), indent=2, ensure_ascii=False))
```

**JavaScript (Fetch)**
```javascript
const response = await fetch(
  "http://172.16.110.15:8000/api/v1/query",
  {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      lat: 41.4036,
      lon: 2.1744,
      azimuth: 90,
      fov: 45
    })
  }
);

const data = await response.json();
console.log(data);
```

**cURL**
```bash
curl -X POST http://172.16.110.15:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 41.4036,
    "lon": 2.1744,
    "azimuth": 90,
    "fov": 45
  }'
```

**Node.js (axios)**
```javascript
const axios = require('axios');

axios.post('http://172.16.110.15:8000/api/v1/query', {
  lat: 41.4036,
  lon: 2.1744,
  azimuth: 90,
  fov: 45
}).then(res => console.log(res.data));
```

---

### 2. GET `/api/v1/health` - Health Check

Check system status and Ollama connectivity.

#### Request

```http
GET /api/v1/health HTTP/1.1
Host: 172.16.110.15:8000
```

#### Response (200 OK)

**Connected**
```json
{
  "status": "ok",
  "ollama_connected": true
}
```

**Disconnected**
```json
{
  "status": "degraded",
  "ollama_connected": false
}
```

#### Example Requests

**Python**
```python
import requests

response = requests.get("http://172.16.110.15:8000/api/v1/health")
print(response.json())
# Output: {'status': 'ok', 'ollama_connected': True}
```

**cURL**
```bash
curl http://172.16.110.15:8000/api/v1/health
```

---

### 3. GET `/api/v1/rag/manifest` - RAG System Info

Get detailed information about the RAG system configuration.

#### Request

```http
GET /api/v1/rag/manifest HTTP/1.1
Host: 172.16.110.15:8000
```

#### Response (200 OK)

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
  "regions": [
    "Cataluña",
    "Madrid",
    "Valencia",
    "País Vasco",
    "Andorra"
  ],
  "default_fov": 70,
  "grid_size": 0.01,
  "default_max_distance": 500,
  "default_max_results": 8
}
```

#### Example

**Python**
```python
import requests
import json

response = requests.get("http://172.16.110.15:8000/api/v1/rag/manifest")
manifest = response.json()

print(f"LLM: {manifest['llm_model']} (base: {manifest['llm_base']})")
print(f"Landmarks: {manifest['landmarks_loaded']}")
print(f"Regions: {', '.join(manifest['regions'])}")
```

---

### 4. GET `/` - Welcome

Get welcome message and API information.

#### Request

```http
GET / HTTP/1.1
Host: 172.16.110.15:8000
```

#### Response

```json
{
  "message": "Welcome to LandmarkLens ML API. Visit /docs for documentation."
}
```

---

### 5. GET `/docs` - Interactive Documentation

Swagger UI for testing endpoints interactively.

```
http://172.16.110.15:8000/docs
```

Open in browser to get interactive API explorer.

---

## Error Handling

### Common Errors

#### Missing required parameters
```json
{
  "detail": "Invalid request parameters"
}
```

#### Ollama not running
```json
{
  "detail": "Failed to query the model or invalid response.",
  "status_code": 502
}
```

#### Server error
```json
{
  "detail": "Internal server error message",
  "status_code": 500
}
```

### Error Codes

| Code | Error | Solution |
|------|-------|----------|
| 400 | Bad Request | Check parameters (lat, lon required) |
| 502 | Bad Gateway | Start Ollama: `ollama serve` |
| 500 | Internal Error | Check server logs |

---

## Rate Limiting

No rate limiting implemented. System handles ~1-2 requests/second per GPU.

---

## Example Workflows

### Workflow 1: Find and Describe Landmarks

```python
import requests
import json

API = "http://172.16.110.15:8000"

# 1. Check health
health = requests.get(f"{API}/api/v1/health").json()
print(f"System status: {health['status']}")

# 2. Query landmarks
query = requests.post(f"{API}/api/v1/query", json={
    "lat": 40.4168,  # Madrid
    "lon": -3.7038
}).json()

# 3. Display results
for landmark in query['data']['landmarks']:
    print(f"• {landmark['name']} ({landmark['distance']}m)")

# 4. Check validation
if query['validation']['schema_ok']:
    print("✅ Response schema valid")
else:
    print(f"⚠️  Issues: {query['validation']['issues']}")
```

### Workflow 2: Directional Query

```python
import requests

# Query with specific direction (looking north)
response = requests.post(
    "http://172.16.110.15:8000/api/v1/query",
    json={
        "lat": 41.4036,    # Barcelona
        "lon": 2.1744,
        "azimuth": 0,      # Looking north
        "fov": 90          # 90° field of view
    }
)

result = response.json()
print(json.dumps(result['data'], indent=2, ensure_ascii=False))
```

---

## CORS & Access Control

**CORS Enabled**: ✅
- Origin: `*` (all origins allowed)
- Methods: GET, POST, OPTIONS
- Headers: Content-Type, Authorization

---

## Response Times

| Operation | Typical Time |
|-----------|--------------|
| Health check | <10ms |
| Spatial search | 5-20ms |
| Reranking | 50-100ms |
| Ollama LLM call | 5-15s |
| **Total query** | **5-20s** |

---

## Database

**Type**: SQLite 3 with sqlite-vec  
**Size**: ~12 MB  
**Landmarks**: 50,520  
**Vector embeddings**: All 50,520 landmarks  
**Embedding model**: `nomic-embed-text` (768-dim)

---

## Monitoring

### Check server is running

```bash
curl -I http://172.16.110.15:8000/
```

### Monitor logs

```bash
# Tail live logs
tail -f landmark_model/data/logs.txt
```

### Get system info

```python
from landmark_model.database import LandmarksDB
db = LandmarksDB()
print(db.get_stats())
```

---

## Support

- **Issues**: Check [README.md](./README.md#troubleshooting)
- **Docs**: Swagger UI at `/docs`
- **Health**: GET `/api/v1/health`

---

**API Version**: 1.0.0  
**Last Updated**: May 2026  
**Status**: Production ✅
