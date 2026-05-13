#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Trace through find_nearby step by step"""

import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

import landmark_model.rag_core as rag_core

LAT = 41.4036
LON = 2.1744

print("Tracing through find_nearby...")
print("=" * 80)

print(f"\nInput: lat={LAT}, lon={LON}")
print(f"HAS_DB: {rag_core.HAS_DB}")
print(f"_DB is None: {rag_core._DB is None}")

# Step 1: Check if database is available
if rag_core.HAS_DB and rag_core._DB is None:
    print("\nInitializing database...")
    from landmark_model.database import LandmarksDB
    rag_core._DB = LandmarksDB()

print(f"\nAfter init, _DB: {rag_core._DB}")

# Step 2: Try database query
if rag_core.HAS_DB and rag_core._DB is not None:
    print("\nAttempting database query...")
    try:
        candidates = rag_core._DB.find_nearby(LAT, LON, radius_km=0.5, max_results=16)
        print(f"✓ DB returned {len(candidates)} candidates")
        
        if candidates:
            # Process them like find_nearby does
            results = []
            for db_lm in candidates:
                distance_m = db_lm.get("distance_m", 0)
                bearing_deg = rag_core.bearing(LAT, LON, db_lm["lat"], db_lm["lon"])
                result = {
                    "name": db_lm.get("name"),
                    "lat": db_lm.get("lat"),
                    "lon": db_lm.get("lon"),
                    "distance": distance_m,
                    "bearing_deg": round(bearing_deg, 1),
                    "direction": rag_core.direction(bearing_deg),
                    "fame_score": db_lm.get("fame_score", 0),
                    "categories": db_lm.get("categories", []),
                    "region": db_lm.get("region"),
                }
                results.append(result)
            
            print(f"✓ Processed {len(results)} results")
            
            # Sort
            results.sort(key=lambda item: item["distance"] - item.get("fame_score", 0) * 5)
            print(f"✓ Sorted results")
            
            # Apply ranking
            ranked = rag_core.score_candidates_with_ranker(results, azimuth=None, fov=70)
            print(f"✓ Applied ranking: {len(ranked)} results after ranking")
            
            # Limit to max_results
            final = ranked[:8]
            print(f"✓ Limited to 8: {len(final)} final results")
            
            if final:
                print("\nFinal results:")
                for i, r in enumerate(final[:3], 1):
                    print(f"  {i}. {r['name']} - {r['distance']:.0f}m")
            else:
                print("\n✗ No final results after limiting!")
        else:
            print("✗ No candidates from DB")
    except Exception as e:
        print(f"✗ Exception: {e}")
        import traceback
        traceback.print_exc()
else:
    print("\n✗ Cannot use database")

# Step 3: Now call find_nearby directly
print("\n" + "=" * 80)
print("Now calling find_nearby directly...")
print("=" * 80)

# Clear the index first
rag_core._INDEX = None

result = rag_core.find_nearby(LAT, LON, azimuth=None, fov=70, max_dist=500, max_results=8)
print(f"\nfind_nearby returned: {len(result)} results")
if result:
    for i, r in enumerate(result[:3], 1):
        print(f"  {i}. {r['name']} - {r['distance']:.0f}m")
else:
    print("✗ EMPTY RESULT!")
