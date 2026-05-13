#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Check which Sagrada Familia entries have coordinates"""

import json
from pathlib import Path

LANDMARKS_PATH = Path("landmark_model/data/landmarks.json")

with open(LANDMARKS_PATH, 'r', encoding='utf-8') as f:
    data = json.load(f)

landmarks = data.get('landmarks', [])
sagrada_landmarks = [
    lm for lm in landmarks 
    if lm.get('name') and ('sagrada' in lm['name'].lower() or 'familia' in lm['name'].lower())
]

print("=" * 80)
print("All Sagrada/Familia landmarks in main dataset:")
print("=" * 80)
print()

with_coords = []
without_coords = []

for lm in sagrada_landmarks:
    name = lm.get('name', 'N/A')
    has_coords = 'lat' in lm and 'lon' in lm
    
    if has_coords:
        with_coords.append({
            'name': name,
            'lat': lm['lat'],
            'lon': lm['lon'],
            'fame_score': lm.get('fame_score', 0),
            'categories': lm.get('categories', []),
        })
    else:
        without_coords.append(name)

print(f"Landmarks WITH coordinates: {len(with_coords)}\n")
for lm in with_coords:
    print(f"✓ {lm['name']}")
    print(f"  Coords: ({lm['lat']}, {lm['lon']})")
    print(f"  Fame Score: {lm['fame_score']}")
    print(f"  Categories: {lm['categories']}")
    print()

print(f"\nLandmarks WITHOUT coordinates: {len(without_coords)}\n")
for name in without_coords:
    print(f"✗ {name}")
    
print("\n" + "=" * 80)
print(f"Total: {len(with_coords)} with coords, {len(without_coords)} without")
print("=" * 80)
