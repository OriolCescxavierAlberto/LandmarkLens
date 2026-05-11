#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Migration script: Load landmarks from JSON into SQLite database."""

import sys
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent))

from database import LandmarksDB, LANDMARKS_JSON_PATH, DB_PATH


def main():
    print("=" * 60)
    print("🗄️  LandmarkLens SQLite Migration")
    print("=" * 60)
    
    print(f"\n📍 JSON source: {LANDMARKS_JSON_PATH}")
    print(f"📂 SQLite destination: {DB_PATH}\n")
    
    # Check if JSON exists
    if not LANDMARKS_JSON_PATH.exists():
        print(f"❌ Landmarks JSON not found: {LANDMARKS_JSON_PATH}")
        return False
    
    # Initialize database
    db = LandmarksDB()
    
    # Load from JSON
    print("⏳ Loading landmarks from JSON...")
    try:
        loaded = db.load_from_json(LANDMARKS_JSON_PATH)
    except Exception as e:
        print(f"❌ Failed to load landmarks: {e}")
        db.close()
        return False
    
    # Show statistics
    print("\n" + "=" * 60)
    print("📊 Database Statistics")
    print("=" * 60)
    stats = db.get_stats()
    for key, value in stats.items():
        print(f"  {key}: {value}")
    
    # Test queries
    print("\n" + "=" * 60)
    print("🧪 Test Queries")
    print("=" * 60)
    
    print("\n1️⃣  Searching near Barcelona (41.4036, 2.1744)...")
    nearby = db.find_nearby(41.4036, 2.1744, radius_km=1.0, max_results=5)
    if nearby:
        for lm in nearby:
            print(f"     • {lm['name']} - {lm['distance_m']}m ({lm['region']})")
    else:
        print("     No landmarks found")
    
    print("\n2️⃣  Searching by name 'Torre'...")
    results = db.search_by_name("Torre", limit=5)
    if results:
        for lm in results:
            print(f"     • {lm['name']} ({lm['region']})")
    else:
        print("     No landmarks found")
    
    print("\n3️⃣  Searching near Madrid (40.4168, -3.7038)...")
    nearby = db.find_nearby(40.4168, -3.7038, radius_km=1.0, max_results=5)
    if nearby:
        for lm in nearby:
            print(f"     • {lm['name']} - {lm['distance_m']}m ({lm['region']})")
    else:
        print("     No landmarks found")
    
    db.close()
    
    print("\n" + "=" * 60)
    print("✅ Migration completed successfully!")
    print("=" * 60)
    print("\nYou can now use the database in your application.")
    print(f"Database location: {DB_PATH}")
    
    return True


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
