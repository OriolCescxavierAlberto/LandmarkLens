#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Debug find_nearby"""

import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

import landmark_model.rag_core as rag_core

print("Testing find_nearby directly...")
print("=" * 80)

LAT = 41.4036
LON = 2.1744

print(f"\nCoordinates: ({LAT}, {LON})")
print(f"MAX_DIST: {rag_core.DEFAULT_MAX_DIST}m")
print(f"MAX_RESULTS: {rag_core.DEFAULT_MAX_RESULTS}")

# Test 1: Direct database query
print("\n" + "-" * 80)
print("1. Testing database directly...")
print("-" * 80)

try:
    from landmark_model.database import LandmarksDB
    db = LandmarksDB()
    results = db.find_nearby(LAT, LON, radius_km=0.5, max_results=8)
    print(f"✓ DB returned {len(results)} results")
    if results:
        for i, r in enumerate(results[:3], 1):
            print(f"  {i}. {r['name']} - {r['distance_m']}m")
except Exception as e:
    print(f"✗ DB error: {e}")
    import traceback
    traceback.print_exc()

# Test 2: find_nearby from rag_core
print("\n" + "-" * 80)
print("2. Testing find_nearby from rag_core...")
print("-" * 80)

try:
    nearby = rag_core.find_nearby(LAT, LON, azimuth=None, fov=70, 
                                   max_dist=500, max_results=8)
    print(f"✓ find_nearby returned {len(nearby)} results")
    if nearby:
        for i, r in enumerate(nearby[:3], 1):
            print(f"  {i}. {r['name']} - {r['distance']:.0f}m")
    else:
        print("⚠️  No results returned (but no error)")
except Exception as e:
    print(f"✗ Error: {e}")
    import traceback
    traceback.print_exc()

# Test 3: Check if database is None
print("\n" + "-" * 80)
print("3. Checking database state...")
print("-" * 80)
print(f"rag_core.HAS_DB: {rag_core.HAS_DB}")
print(f"rag_core._DB: {rag_core._DB}")

try:
    if rag_core.HAS_DB:
        if rag_core._DB is None:
            print("Database is not initialized, initializing now...")
            from landmark_model.database import LandmarksDB
            rag_core._DB = LandmarksDB()
        
        print(f"rag_core._DB is now: {rag_core._DB}")
        
        # Try stats
        stats = rag_core._DB.get_stats()
        print(f"Database stats: {stats}")
except Exception as e:
    print(f"Error initializing DB: {e}")
    import traceback
    traceback.print_exc()
