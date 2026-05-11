#!/usr/bin/env python3
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent / "landmark_model"))

from database import LandmarksDB

print("\n" + "="*60)
print("✅ SQLite Database Test")
print("="*60)

db = LandmarksDB()
stats = db.get_stats()

print("\n📊 Database Statistics:")
for k, v in stats.items():
    print(f"  {k}: {v}")

print("\n🏙️  Landmarks near Barcelona (41.4036, 2.1744) - 1km radius:")
nearby = db.find_nearby(41.4036, 2.1744, radius_km=1.0, max_results=5)
if nearby:
    for lm in nearby:
        print(f"  • {lm['name']} - {lm['distance_m']}m ({lm['region']})")
else:
    print("  No landmarks found")

print("\n🔍 Search by name 'Torre':")
results = db.search_by_name("Torre", limit=5)
if results:
    for lm in results:
        print(f"  • {lm['name']} ({lm['region']})")
else:
    print("  No landmarks found")

print("\n🏛️  Landmarks near Madrid (40.4168, -3.7038) - 1km radius:")
nearby = db.find_nearby(40.4168, -3.7038, radius_km=1.0, max_results=5)
if nearby:
    for lm in nearby:
        print(f"  • {lm['name']} - {lm['distance_m']}m ({lm['region']})")
else:
    print("  No landmarks found")

print("\n" + "="*60)
print("✅ Database is ready for use!")
print("="*60 + "\n")

db.close()
