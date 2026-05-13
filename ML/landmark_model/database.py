#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SQLite-vec database layer for LandmarkLens landmarks.

Provides:
- SQLite database with sqlite-vec for vectorial search
- Spatial indexing and efficient retrieval
- Landmark embeddings storage
- Semantic search via vector similarity
"""

import json
import threading
import sqlite3
from pathlib import Path
from typing import Any, Optional
import numpy as np

try:
    import sqlite_vec
except ImportError:
    sqlite_vec = None

try:
    import ollama
    HAS_OLLAMA = True
except ImportError:
    HAS_OLLAMA = False
    ollama = None


SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR / "data"
DB_PATH = DATA_DIR / "landmarks.db"
LANDMARKS_JSON_PATH = DATA_DIR / "landmarks.json"

EMBEDDING_MODEL = "nomic-embed-text"
EMBEDDING_DIM = 768


class LandmarksDB:
    """SQLite database for landmarks with vectorial search support."""

    def __init__(self, db_path: Path = DB_PATH):
        self.db_path = db_path
        self._local = threading.local()
        self._ensure_db()

    def _get_connection(self) -> sqlite3.Connection:
        if not hasattr(self._local, "conn") or self._local.conn is None:
            self._local.conn = sqlite3.connect(str(self.db_path))
            self._local.conn.row_factory = sqlite3.Row
            if sqlite_vec:
                self._local.conn.enable_load_extension(True)
                try:
                    sqlite_vec.load(self._local.conn)
                    self._local.conn.enable_load_extension(False)
                except Exception as e:
                    print(f"⚠️  sqlite-vec not loaded: {e}")
        return self._local.conn

    def _ensure_db(self) -> None:
        """Create database and tables if they don't exist."""
        conn = self._get_connection()
        cursor = conn.cursor()

        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS landmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                osm_id INTEGER UNIQUE,
                name TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                region TEXT,
                fame_score INTEGER DEFAULT 0,
                categories TEXT,
                wikipedia TEXT,
                wikidata TEXT,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """
        )

        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS embeddings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                landmark_id INTEGER NOT NULL UNIQUE,
                embedding BLOB NOT NULL,
                embedding_model TEXT DEFAULT 'ollama:nomic-embed-text',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(landmark_id) REFERENCES landmarks(id)
            )
            """
        )

        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS spatial_index (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                landmark_id INTEGER NOT NULL UNIQUE,
                grid_key TEXT NOT NULL,
                FOREIGN KEY(landmark_id) REFERENCES landmarks(id)
            )
            """
        )

        cursor.execute("CREATE INDEX IF NOT EXISTS idx_landmarks_osm_id ON landmarks(osm_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_landmarks_coords ON landmarks(lat, lon)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_spatial_grid_key ON spatial_index(grid_key)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_embeddings_landmark_id ON embeddings(landmark_id)")

        conn.commit()

    def load_from_json(self, json_path: Path = LANDMARKS_JSON_PATH) -> int:
        """Load landmarks from JSON file into database."""
        if not json_path.exists():
            raise FileNotFoundError(f"Landmarks JSON not found: {json_path}")

        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        landmarks = data.get("landmarks", [])
        conn = self._get_connection()
        cursor = conn.cursor()

        cursor.execute("DELETE FROM landmarks")
        cursor.execute("DELETE FROM spatial_index")
        cursor.execute("DELETE FROM embeddings")

        loaded = 0
        for lm in landmarks:
            try:
                cursor.execute(
                    """
                    INSERT OR REPLACE INTO landmarks
                    (osm_id, name, lat, lon, region, fame_score, categories, wikipedia, wikidata, description)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        lm.get("osm_id", 0),
                        lm.get("name", ""),
                        lm.get("lat"),
                        lm.get("lon"),
                        lm.get("region", ""),
                        lm.get("fame_score", 0),
                        json.dumps(lm.get("categories", [])),
                        lm.get("wikipedia", ""),
                        lm.get("wikidata", ""),
                        lm.get("description", ""),
                    ),
                )
                loaded += 1
            except Exception as e:
                print(f"  ⚠️  Error loading landmark {lm.get('name')}: {e}")

        conn.commit()
        self._update_spatial_index()
        print(f"✅ Loaded {loaded} landmarks into database")
        return loaded

    def _update_spatial_index(self, grid_size: float = 0.01) -> None:
        """Update spatial grid index for fast spatial queries."""
        conn = self._get_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT id, lat, lon FROM landmarks WHERE lat IS NOT NULL AND lon IS NOT NULL")
        landmarks = cursor.fetchall()
        cursor.execute("DELETE FROM spatial_index")

        for lm_id, lat, lon in landmarks:
            grid_x = int(lat / grid_size)
            grid_y = int(lon / grid_size)
            grid_key = f"{grid_x}_{grid_y}"
            cursor.execute(
                "INSERT INTO spatial_index (landmark_id, grid_key) VALUES (?, ?)",
                (lm_id, grid_key),
            )

        conn.commit()

    def find_nearby(
        self, lat: float, lon: float, radius_km: float = 1.0, max_results: int = 8
    ) -> list[dict[str, Any]]:
        """Find landmarks near coordinates using spatial index."""
        conn = self._get_connection()
        cursor = conn.cursor()

        query = """
        SELECT
            id, name, lat, lon, region, fame_score,
            (6371 * acos(
                cos(radians(?)) * cos(radians(lat)) * cos(radians(lon) - radians(?)) +
                sin(radians(?)) * sin(radians(lat))
            )) as distance_km
        FROM landmarks
        WHERE lat IS NOT NULL AND lon IS NOT NULL
        AND (6371 * acos(
            cos(radians(?)) * cos(radians(lat)) * cos(radians(lon) - radians(?)) +
            sin(radians(?)) * sin(radians(lat))
        )) <= ?
        ORDER BY distance_km - (fame_score * 0.005) ASC
        LIMIT ?
        """

        cursor.execute(query, (lat, lon, lat, lat, lon, lat, radius_km, max_results))
        results = cursor.fetchall()

        landmarks = []
        for row in results:
            landmarks.append(
                {
                    "id": row["id"],
                    "name": row["name"],
                    "lat": row["lat"],
                    "lon": row["lon"],
                    "region": row["region"],
                    "fame_score": row["fame_score"],
                    "distance_m": int(row["distance_km"] * 1000),
                }
            )

        return landmarks

    def search_by_name(self, query: str, limit: int = 10) -> list[dict[str, Any]]:
        """Search landmarks by name using full-text search."""
        conn = self._get_connection()
        cursor = conn.cursor()

        search_pattern = f"%{query}%"
        cursor.execute(
            """
            SELECT id, name, lat, lon, region, fame_score
            FROM landmarks
            WHERE name LIKE ?
            ORDER BY fame_score DESC, name ASC
            LIMIT ?
            """,
            (search_pattern, limit),
        )

        results = []
        for row in cursor.fetchall():
            results.append(
                {
                    "id": row["id"],
                    "name": row["name"],
                    "lat": row["lat"],
                    "lon": row["lon"],
                    "region": row["region"],
                    "fame_score": row["fame_score"],
                }
            )

        return results

    def get_landmark(self, landmark_id: int) -> Optional[dict[str, Any]]:
        """Get landmark by ID."""
        conn = self._get_connection()
        cursor = conn.cursor()

        cursor.execute(
            """
            SELECT id, name, lat, lon, region, fame_score, categories, wikipedia, wikidata, description
            FROM landmarks
            WHERE id = ?
            """,
            (landmark_id,),
        )

        row = cursor.fetchone()
        if not row:
            return None

        return {
            "id": row["id"],
            "name": row["name"],
            "lat": row["lat"],
            "lon": row["lon"],
            "region": row["region"],
            "fame_score": row["fame_score"],
            "categories": json.loads(row["categories"] or "[]"),
            "wikipedia": row["wikipedia"],
            "wikidata": row["wikidata"],
            "description": row["description"],
        }

    def get_stats(self) -> dict[str, Any]:
        """Get database statistics."""
        conn = self._get_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT COUNT(*) as total FROM landmarks")
        total = cursor.fetchone()["total"]

        cursor.execute("SELECT COUNT(DISTINCT region) as regions FROM landmarks")
        regions = cursor.fetchone()["regions"]

        cursor.execute("SELECT COUNT(*) as embeddings FROM embeddings")
        embeddings = cursor.fetchone()["embeddings"]

        cursor.execute("SELECT AVG(fame_score) as avg_fame FROM landmarks")
        avg_fame = cursor.fetchone()["avg_fame"] or 0

        return {
            "total_landmarks": total,
            "unique_regions": regions,
            "landmarks_with_embeddings": embeddings,
            "average_fame_score": round(avg_fame, 2),
            "db_path": str(self.db_path),
            "db_size_mb": self.db_path.stat().st_size / (1024 * 1024) if self.db_path.exists() else 0,
        }

    def _generate_embedding(self, text: str) -> Optional[list[float]]:
        """Generate embedding for text using Ollama."""
        if not HAS_OLLAMA or not ollama:
            print("⚠️  Ollama not available, skipping embedding")
            return None

        try:
            response = ollama.embeddings(model=EMBEDDING_MODEL, prompt=text)
            if "embedding" in response:
                return response["embedding"]
        except Exception as e:
            print(f"⚠️  Error generating embedding: {e}")
        return None

    def generate_embeddings_batch(self, batch_size: int = 100) -> int:
        """Generate and store embeddings for all landmarks without them."""
        if not sqlite_vec:
            print("⚠️  sqlite-vec not available, cannot generate embeddings")
            return 0

        conn = self._get_connection()
        cursor = conn.cursor()

        cursor.execute(
            """
            SELECT l.id, l.name, l.description, l.wikipedia, l.region
            FROM landmarks l
            LEFT JOIN embeddings e ON l.id = e.landmark_id
            WHERE e.id IS NULL
            ORDER BY l.fame_score DESC
            LIMIT ?
            """,
            (batch_size,),
        )

        rows = cursor.fetchall()
        generated = 0

        for row in rows:
            landmark_id, name, description, wikipedia, region = row

            text_parts = [name]
            if region:
                text_parts.append(region)
            if wikipedia:
                text_parts.append(wikipedia)
            if description:
                text_parts.append(description)

            text = " | ".join(text_parts)
            embedding = self._generate_embedding(text)

            if embedding:
                try:
                    embedding_bytes = np.array(embedding, dtype=np.float32).tobytes()
                    cursor.execute(
                        """
                        INSERT OR REPLACE INTO embeddings
                        (landmark_id, embedding, embedding_model)
                        VALUES (?, ?, ?)
                        """,
                        (landmark_id, embedding_bytes, EMBEDDING_MODEL),
                    )
                    generated += 1
                    if generated % 10 == 0:
                        print(f"  ✓ Generated {generated} embeddings...")
                except Exception as e:
                    print(f"  ⚠️  Error storing embedding for {name}: {e}")

        conn.commit()
        print(f"✅ Generated {generated} embeddings")
        return generated

    def search_by_embedding(self, text: str, limit: int = 5, threshold: float = 0.7) -> list[dict[str, Any]]:
        """Search landmarks by semantic similarity using embeddings."""
        if not HAS_OLLAMA or not sqlite_vec:
            print("⚠️  Embeddings not available")
            return []

        query_embedding = self._generate_embedding(text)
        if not query_embedding:
            return []

        conn = self._get_connection()
        cursor = conn.cursor()
        query_bytes = np.array(query_embedding, dtype=np.float32).tobytes()

        try:
            cursor.execute(
                """
                SELECT l.id, l.name, l.lat, l.lon, l.region, l.fame_score, distance
                FROM embeddings e
                JOIN landmarks l ON e.landmark_id = l.id
                WHERE embedding MATCH ? AND k = ?
                ORDER BY distance ASC
                LIMIT ?
                """,
                (query_bytes, limit, limit),
            )

            results = []
            for row in cursor.fetchall():
                results.append(
                    {
                        "id": row["id"],
                        "name": row["name"],
                        "lat": row["lat"],
                        "lon": row["lon"],
                        "region": row["region"],
                        "fame_score": row["fame_score"],
                        "similarity": 1 - (row["distance"] / 2),
                    }
                )
            return results
        except Exception as e:
            print(f"⚠️  Error searching embeddings: {e}")
            return []

    def close(self) -> None:
        """Close database connection for the current thread."""
        if hasattr(self._local, "conn") and self._local.conn:
            self._local.conn.close()
            self._local.conn = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


if __name__ == "__main__":
    db = LandmarksDB()

    print("Loading landmarks from JSON...")
    loaded = db.load_from_json()

    print("\nDatabase statistics:")
    stats = db.get_stats()
    for key, value in stats.items():
        print(f"  {key}: {value}")

    print("\nSearching near Barcelona (41.4036, 2.1744)...")
    nearby = db.find_nearby(41.4036, 2.1744, radius_km=1.0, max_results=5)
    for lm in nearby:
        print(f"  • {lm['name']} - {lm['distance_m']}m ({lm['region']})")

    print("\nSearching for 'Torre'...")
    results = db.search_by_name("Torre", limit=5)
    for lm in results:
        print(f"  • {lm['name']} ({lm['region']})")

    db.close()