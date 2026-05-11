# 🧠 Vector Search Documentation - LandmarkLens

Complete guide to semantic search using vector embeddings in LandmarkLens.

## Table of Contents

- [What is Vector Search?](#what-is-vector-search)
- [How It Works](#how-it-works)
- [Setup & Prerequisites](#setup--prerequisites)
- [Using Vector Search](#using-vector-search)
- [Integration with RAG](#integration-with-rag)
- [Performance & Tuning](#performance--tuning)
- [Troubleshooting](#troubleshooting)

---

## What is Vector Search?

Vector search (semantic search) finds landmarks based on **meaning**, not just keyword matching.

### Traditional Search (Keyword)
```
Query: "iglesia"
Results: Only landmarks with "iglesia" in name
❌ Misses: "Catedral", "Convento", "Capilla"
```

### Vector Search (Semantic)
```
Query: "iglesia histórica"
Converts to: [0.234, -0.105, 0.892, ..., 768 values]

Finds similar vectors:
✅ Catedral de la Almudena (92% similar)
✅ Real Convento de la Encarnación (87% similar)
✅ Capilla Cristo del Humilladero (84% similar)
```

---

## How It Works

### 3-Step Process

```
┌─────────────────────────────────────────────────────────┐
│ 1. EMBEDDING GENERATION                                │
│                                                         │
│   Text Input: "iglesia medieval"                      │
│        ↓                                                │
│   Ollama nomic-embed-text                             │
│        ↓                                                │
│   Vector: [0.234, -0.105, 0.892, ..., (768 values)]  │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│ 2. VECTOR STORAGE                                      │
│                                                         │
│   SQLite Table: embeddings                             │
│   ├─ id: 1                                             │
│   ├─ landmark_id: 42                                   │
│   ├─ embedding: BLOB (768-dim vector)                 │
│   └─ model: nomic-embed-text                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│ 3. SIMILARITY SEARCH (sqlite-vec)                      │
│                                                         │
│   Query Vector: [0.234, -0.105, 0.892, ...]         │
│        ↓                                                │
│   Compute Distance to All Stored Vectors              │
│        ↓                                                │
│   Return Top-K Closest Matches                        │
│        ↓                                                │
│   Results with Similarity Scores (0-1)               │
└─────────────────────────────────────────────────────────┘
```

### Mathematical Foundation

**Vector Similarity (Cosine Distance):**
```
similarity = dot(query_vector, landmark_vector) / 
             (||query_vector|| * ||landmark_vector||)

Range: 0 (completely different) to 1 (identical)
```

**Example Calculation:**
```
Query: "iglesia" → [0.5, 0.2, 0.8, ...]
Landmark A: "Catedral" → [0.48, 0.22, 0.78, ...] → similarity = 0.92
Landmark B: "Stadium" → [0.1, 0.9, 0.2, ...] → similarity = 0.31
```

---

## Setup & Prerequisites

### 1. Install Dependencies

```bash
pip install -r landmark_model/requirements.txt
```

**Required packages:**
- `ollama` >= 0.1.0
- `sqlite-vec` >= 0.1.9
- `numpy` >= 1.20.0

### 2. Ensure Ollama is Running

```bash
# Start Ollama service
ollama serve

# In another terminal, verify model availability
ollama list
```

**Required models:**
- `llama3.2:3b` - For query processing
- `nomic-embed-text` - For embedding generation

```bash
ollama pull nomic-embed-text
```

### 3. Initialize Database

```bash
python landmark_model/migrate_to_db.py
```

Output should show:
```
✅ Loaded 50520 landmarks into database
```

### 4. Generate Embeddings (CRITICAL)

```bash
python landmark_model/generate_embeddings.py
```

**This step is MANDATORY for vector search to work.**

Output:
```
⏳ Generating embeddings...
  ✓ Generated 1000 embeddings...
  ✓ Generated 50000 embeddings...
  ✓ Generated 50520 embeddings...
✅ VECTOR EMBEDDINGS GENERATION COMPLETE!
```

**Time:** 30-60 minutes (GPU-dependent)

---

## Using Vector Search

### Method 1: Direct Database Query

```python
from landmark_model.database import LandmarksDB

# Connect to database
db = LandmarksDB()

# Vector search
results = db.search_by_embedding(
    text="iglesia histórica",
    limit=5,
    threshold=0.7  # Minimum similarity score
)

# Display results
for result in results:
    print(f"{result['name']:40} | {result['similarity']:.1%} | {result['region']}")

db.close()

# Output:
# Catedral de la Almudena                      | 92.0% | Madrid
# Real Convento de la Encarnación               | 87.0% | Madrid
# Capilla Cristo del Humilladero                | 84.0% | Madrid
# Basílica de Nuestra Señora del Pilar          | 81.0% | Zaragoza
# Monasterio de San Juan de la Peña             | 78.0% | Aragón
```

### Method 2: Combined Spatial + Vector Search

```python
from landmark_model.database import LandmarksDB
from math import radians, sin, cos, sqrt, atan2

db = LandmarksDB()

# First: Spatial search (fast)
nearby_landmarks = db.find_nearby(
    lat=41.4036,
    lon=2.1744,
    radius_km=5.0,
    max_results=50
)

# Second: Filter by semantic similarity
query = "iglesia gótica"
vector_results = db.search_by_embedding(query, limit=50)

# Combine results
nearby_names = {r['id'] for r in nearby_landmarks}
semantic_results = [r for r in vector_results if r['id'] in nearby_names]

# Sort by relevance
combined = sorted(
    semantic_results,
    key=lambda x: x['similarity'],
    reverse=True
)

# Top results
for r in combined[:10]:
    print(f"{r['name']:40} | Similarity: {r['similarity']:.1%}")

db.close()
```

### Method 3: Query with RAG Pipeline

```python
from landmark_model.rag_core import run_rag_query

# Standard RAG query (uses spatial search by default)
result = run_rag_query(
    lat=41.4036,
    lon=2.1744,
    azimuth=45,
    fov=70
)

print(result.raw_text)

# The RAG pipeline includes:
# 1. Spatial search (find nearby)
# 2. Reranking with ML model
# 3. LLM prompt generation
# 4. Ollama query
# 5. JSON validation
```

### Method 4: Hybrid Search (Recommended)

```python
from landmark_model.database import LandmarksDB

db = LandmarksDB()

def hybrid_search(query, lat, lon, radius_km=2.0, limit=5):
    """
    Combines spatial proximity and semantic similarity.
    
    Returns landmarks that are:
    1. Geographically close to (lat, lon)
    2. Semantically similar to query
    """
    # Get nearby landmarks
    nearby = db.find_nearby(lat, lon, radius_km, max_results=50)
    nearby_ids = {r['id'] for r in nearby}
    
    # Get semantic matches
    semantic = db.search_by_embedding(query, limit=100, threshold=0.6)
    
    # Find intersection (nearby AND semantically relevant)
    combined = [
        {**r, 'spatial_rank': list(nearby_ids).index(r['id']) + 1}
        for r in semantic 
        if r['id'] in nearby_ids
    ]
    
    # Sort by similarity (semantic takes priority)
    combined.sort(key=lambda x: x['similarity'], reverse=True)
    
    return combined[:limit]

# Usage
results = hybrid_search(
    query="iglesia medieval",
    lat=41.4036,
    lon=2.1744,
    radius_km=3.0,
    limit=5
)

for r in results:
    print(f"{r['name']:40} | {r['similarity']:.1%} | {r['distance']:.0f}m")

db.close()
```

---

## Integration with RAG

### Current Integration

The RAG pipeline currently uses **spatial search** as primary retrieval:

```
Query Coordinates
    ↓
Spatial Search (find_nearby)
    ↓
Reranking (ML model)
    ↓
Prompt Building
    ↓
Ollama Query
    ↓
Response Validation
```

### Proposed Vector Integration

Option 1: **Vector as Secondary Candidate Source**
```
Query Coordinates + Semantic Query
    ↓
    ├─ Spatial Search → candidates_spatial
    ├─ Vector Search  → candidates_semantic
    ↓
Merge & Deduplicate
    ↓
Reranking
    ↓
Top-K to LLM
```

Option 2: **Vector-Only Search**
```
Semantic Query
    ↓
Vector Search (embedding generation on-the-fly)
    ↓
Reranking
    ↓
Ollama Query
```

Option 3: **Hybrid Search (Recommended)**
```
Query: GPS + Text
    ↓
    ├─ Spatial Filter (within radius)
    ├─ Vector Filter (semantic relevance)
    ↓
Intersection (nearby AND relevant)
    ↓
Reranking
    ↓
LLM Prompt
```

---

## Performance & Tuning

### Embedding Generation Performance

| GPU | Time per Landmark | Total Time (50K) |
|-----|-------------------|------------------|
| RTX 3060 (6GB) | 2-3ms | 100-150 min |
| RTX 4090 (24GB) | 0.3-0.5ms | 15-25 min |
| CPU | 10-20ms | 500-1000 min |

**Optimization:**
```python
# Larger batch size = faster generation
db.generate_embeddings_batch(batch_size=500)  # vs default 100
```

### Query Performance

```
Spatial Search:        5-20ms   (indexed grid lookup)
Vector Search:         100-200ms (linear scan of 50K vectors)
Reranking:             10-50ms  (sklearn model)
Ollama (with GPU):     5-15s    (network + LLM inference)
Total:                 5-20s
```

### Similarity Threshold Tuning

```python
# Threshold = 0.5: More results, lower quality
results = db.search_by_embedding(query, limit=10, threshold=0.5)

# Threshold = 0.7: Balanced
results = db.search_by_embedding(query, limit=10, threshold=0.7)

# Threshold = 0.9: Fewer, higher quality results
results = db.search_by_embedding(query, limit=10, threshold=0.9)
```

**Guidelines:**
- 0.5-0.6: Broad searches (less specific)
- 0.7-0.8: General use (recommended)
- 0.8-0.9: High precision (very specific)

---

## Advanced Topics

### Custom Query Embedding

Generate embedding for specific queries without searching:

```python
from landmark_model.database import LandmarksDB

db = LandmarksDB()

# Generate embedding for custom query
query_text = "iglesia gótica del siglo XVI"
embedding = db._generate_embedding(query_text)

print(f"Embedding dimension: {len(embedding)}")
print(f"First 10 values: {embedding[:10]}")

# Now use this embedding for custom distance calculations
# or store for batch processing

db.close()
```

### Batch Search

Search for multiple queries efficiently:

```python
from landmark_model.database import LandmarksDB

db = LandmarksDB()

queries = [
    "iglesia histórica",
    "castillo medieval",
    "monumento nacional",
    "plaza mayor"
]

for query in queries:
    results = db.search_by_embedding(query, limit=3)
    print(f"\nQuery: '{query}'")
    for r in results[:3]:
        print(f"  • {r['name']} ({r['similarity']:.1%})")

db.close()
```

### Embedding Quality Inspection

Verify embeddings are correctly generated and stored:

```python
from landmark_model.database import LandmarksDB
import numpy as np

db = LandmarksDB()
conn = db._get_connection()
cursor = conn.cursor()

# Check embedding statistics
cursor.execute("""
    SELECT 
        COUNT(*) as total,
        COUNT(DISTINCT landmark_id) as unique_landmarks,
        AVG(LENGTH(embedding)) as avg_blob_size
    FROM embeddings
""")

stats = cursor.fetchone()
print(f"Total embeddings: {stats[0]}")
print(f"Unique landmarks: {stats[1]}")
print(f"Avg blob size: {stats[2]:.0f} bytes")

# Expected blob size: 768 dimensions × 4 bytes per float32 = 3072 bytes
```

---

## Troubleshooting

### "sqlite-vec not found" Error

```bash
pip install sqlite-vec>=0.1.9
```

Verify installation:
```python
import sqlite_vec
print(sqlite_vec.__version__)
```

### "No embeddings found" / Vector search returns empty

```python
from landmark_model.database import LandmarksDB

db = LandmarksDB()
stats = db.get_stats()
print(f"Embeddings generated: {stats['landmarks_with_embeddings']}")

# If 0, generate them:
# python landmark_model/generate_embeddings.py
```

### Embeddings take too long

**Cause:** CPU-only generation (no GPU access)

**Check GPU:**
```bash
nvidia-smi
```

**If no GPU, accept longer times:**
- CPU: 500-1000 minutes for 50K landmarks
- GPU (RTX 3060): 100-150 minutes

**Optimization for slow GPU:**
- Reduce batch size: `generate_embeddings_batch(batch_size=50)`
- Generate at night/off-peak
- Consider cloud GPU rental

### Vector search returns irrelevant results

**Cause:** Query too broad or threshold too low

**Solutions:**
```python
# 1. Increase threshold (more specific)
results = db.search_by_embedding(
    query, 
    limit=5, 
    threshold=0.85  # higher = more specific
)

# 2. Make query more specific
query = "medieval stone church"  # instead of "church"
results = db.search_by_embedding(query, limit=5)

# 3. Combine with spatial filter
nearby = db.find_nearby(lat, lon, radius_km=2)
nearby_ids = {r['id'] for r in nearby}
semantic = db.search_by_embedding(query, limit=50)
filtered = [r for r in semantic if r['id'] in nearby_ids]
```

### Ollama nomic-embed-text not available

```bash
ollama pull nomic-embed-text

# Verify
ollama list | grep nomic
```

---

## Summary

| Feature | Status | Performance | Use Case |
|---------|--------|-------------|----------|
| Spatial Search | ✅ Active | 5-20ms | GPS-based queries |
| Vector Search | ✅ Available | 100-200ms | Semantic queries |
| Hybrid Search | ✅ Ready | 5-20s | Best accuracy |
| RAG Integration | 🔄 Proposed | TBD | Full pipeline |

**RECOMMENDATION:** Use hybrid search (spatial + vector) for best results in production.

---

## References

- [nomic-embed-text](https://ollama.ai/library/nomic-embed-text) - Ollama embedding model
- [sqlite-vec](https://github.com/asg017/sqlite-vec) - SQLite vector extension
- [Embedding Models](https://huggingface.co/models?library=sentence-transformers) - HuggingFace models

---

**Version**: 1.0.0  
**Last Updated**: May 2026  
**Status**: Production ✅
