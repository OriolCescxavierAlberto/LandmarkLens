#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deep dive into why Sagrada Familia is not in top results"""

import json
import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from landmark_model.rag_core import (
    load_landmarks,
    haversine,
    bearing,
    direction,
    DEFAULT_MAX_DIST,
    DEFAULT_MAX_RESULTS,
    fmt_dist,
    LANDMARKS_PATH,
)

# Sagrada Familia coordinates
SF_LAT = 41.4036
SF_LON = 2.1744

print("=" * 80)
print("Investigating: Why isn't Sagrada Familia in top results?")
print("=" * 80)

# Load landmarks
index = load_landmarks()
candidates = index.query_radius(SF_LAT, SF_LON, DEFAULT_MAX_DIST)

print(f"\nTotal candidates from spatial index: {len(candidates)}")
print(f"Max distance: {DEFAULT_MAX_DIST}m\n")

# Calculate distances for all
results = []
sagrada_entry = None

for landmark in candidates:
    distance_m = haversine(SF_LAT, SF_LON, float(landmark["lat"]), float(landmark["lon"]))
    if distance_m > DEFAULT_MAX_DIST:
        continue
    
    bearing_deg = bearing(SF_LAT, SF_LON, float(landmark["lat"]), float(landmark["lon"]))
    
    result = {
        "name": landmark.get("name"),
        "lat": landmark.get("lat"),
        "lon": landmark.get("lon"),
        "distance": round(distance_m, 1),
        "bearing_deg": round(bearing_deg, 1),
        "direction": direction(bearing_deg),
        "fame_score": landmark.get("fame_score", 0),
        "categories": landmark.get("categories", []),
    }
    
    # Store architect if available
    if "architect" in landmark:
        result["architect"] = landmark["architect"]
    
    # Calculate sort key
    sort_key = result["distance"] - result.get("fame_score", 0) * 5
    result["sort_key"] = sort_key
    
    results.append(result)
    
    # Track Sagrada Familia
    if 'sagrada' in result["name"].lower() and 'familia' in result["name"].lower():
        sagrada_entry = result

# Sort by the actual sorting function used in find_nearby
results.sort(key=lambda item: item["distance"] - item.get("fame_score", 0) * 5)

print("Top 15 results (sorted by distance - fame_score*5):\n")
for i, lm in enumerate(results[:15], 1):
    print(f"{i:2d}. {lm['name']}")
    print(f"    Distance: {fmt_dist(lm['distance'])} | Bearing: {lm['bearing_deg']}° ({lm['direction']})")
    print(f"    Fame Score: {lm['fame_score']}")
    print(f"    Sort Key: {lm['sort_key']:.1f}")
    if 'architect' in lm:
        print(f"    Architect: {lm['architect']}")
    print()

if sagrada_entry:
    print("\n" + "-" * 80)
    print("Sagrada Familia Entry found:")
    print("-" * 80)
    print(f"Name: {sagrada_entry['name']}")
    print(f"Distance: {fmt_dist(sagrada_entry['distance'])} ({sagrada_entry['distance']:.1f}m)")
    print(f"Bearing: {sagrada_entry['bearing_deg']}° ({sagrada_entry['direction']})")
    print(f"Fame Score: {sagrada_entry['fame_score']}")
    print(f"Sort Key: {sagrada_entry['sort_key']:.1f}")
    
    # Find its position in the sorted list
    position = None
    for idx, lm in enumerate(results):
        if lm['name'] == sagrada_entry['name']:
            position = idx + 1
            break
    
    if position:
        print(f"\nPosition in sorted results: #{position} out of {len(results)}")
        if position > DEFAULT_MAX_RESULTS:
            print(f"⚠️  NOT in top {DEFAULT_MAX_RESULTS} results (API limit)")
    else:
        print(f"\n✗ Not found in results!")
else:
    print("\n✗ Sagrada Familia not found in spatial index results at all!")
    
    # Search in raw landmarks
    print("\nSearching in raw landmarks file...")
    with open(LANDMARKS_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    sagrada_matches = [
        lm for lm in data.get('landmarks', [])
        if lm.get('name') and 'sagrada' in lm['name'].lower() and 'familia' in lm['name'].lower()
    ]
    
    if sagrada_matches:
        print(f"\nFound {len(sagrada_matches)} matches in full dataset:")
        for lm in sagrada_matches:
            if 'lat' in lm and 'lon' in lm:
                d = haversine(SF_LAT, SF_LON, lm['lat'], lm['lon'])
                print(f"  - {lm['name']} at {d:.1f}m (coords: {lm['lat']}, {lm['lon']})")
            else:
                print(f"  - {lm['name']} (no coordinates)")
