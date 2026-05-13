#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Detailed investigation of landmarks data"""

import json
import sys
import os
from pathlib import Path

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from landmark_model.rag_core import LANDMARKS_PATH, haversine

# Load landmarks
print(f"Loading landmarks from: {LANDMARKS_PATH}\n")
with open(LANDMARKS_PATH, 'r', encoding='utf-8') as f:
    data = json.load(f)

landmarks = data.get('landmarks', [])
print(f"Total landmarks: {len(landmarks)}\n")

# Sagrada Familia coordinates (reference point)
SF_LAT = 41.4036
SF_LON = 2.1744

# Find all "Sagrada" or "Familia" landmarks
print("=" * 80)
print("Searching for 'Sagrada' or 'Familia' landmarks:")
print("=" * 80)
sagrada_landmarks = [
    lm for lm in landmarks 
    if lm.get('name') and ('sagrada' in lm['name'].lower() or 'familia' in lm['name'].lower())
]

print(f"\nFound {len(sagrada_landmarks)} landmarks with 'Sagrada' or 'Familia':\n")

for lm in sagrada_landmarks:
    name = lm.get('name', 'N/A')
    lat = lm.get('lat')
    lon = lm.get('lon')
    dist = None
    if lat and lon:
        dist = haversine(SF_LAT, SF_LON, lat, lon)
    
    print(f"Name: {name}")
    if lat and lon:
        print(f"  Coords: ({lat}, {lon})")
        print(f"  Distance from SF: {dist:.1f}m")
    print(f"  Fame Score: {lm.get('fame_score', 0)}")
    print(f"  Categories: {lm.get('categories', [])}")
    print(f"  Architect: {lm.get('architect', 'N/A')}")
    print(f"  Wikipedia: {lm.get('wikipedia', 'N/A')}")
    print()

# Now check what's in the actual dataset files
print("\n" + "=" * 80)
print("Checking regional datasets:")
print("=" * 80)

for region_file in Path(SCRIPT_DIR).glob('landmark_model/data/landmarks_*.json'):
    print(f"\nFile: {region_file.name}")
    with open(region_file, 'r', encoding='utf-8') as f:
        region_data = json.load(f)
    
    region_landmarks = region_data.get('landmarks', [])
    sagrada_in_region = [
        lm for lm in region_landmarks 
        if lm.get('name') and ('sagrada' in lm['name'].lower() or 'familia' in lm['name'].lower())
    ]
    
    if sagrada_in_region:
        print(f"  Found {len(sagrada_in_region)} matches:")
        for lm in sagrada_in_region:
            name = lm.get('name', 'N/A')
            print(f"    - {name}")

print("\n" + "=" * 80)
print("Analysis complete")
print("=" * 80)
