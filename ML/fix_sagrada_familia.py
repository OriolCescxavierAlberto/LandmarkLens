#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Add coordinates to Basílica de la Sagrada Família"""

import json
from pathlib import Path

LANDMARKS_PATH = Path("landmark_model/data/landmarks.json")

# Load landmarks
with open(LANDMARKS_PATH, 'r', encoding='utf-8') as f:
    data = json.load(f)

# Find and update Basílica de la Sagrada Família
updated = False
for landmark in data['landmarks']:
    if landmark.get('name') == 'Basílica de la Sagrada Família' and 'lat' not in landmark:
        # Add the correct coordinates
        landmark['lat'] = 41.4036
        landmark['lon'] = 2.1744
        # Remove the geocoding flag since we've added coordinates
        if 'needs_geocoding' in landmark:
            del landmark['needs_geocoding']
        updated = True
        print(f"✓ Updated: {landmark['name']}")
        print(f"  Added coordinates: ({landmark['lat']}, {landmark['lon']})")
        print(f"  Fame Score: {landmark.get('fame_score', 0)}")
        print(f"  Architect: {landmark.get('architect', 'N/A')}")
        break

if updated:
    # Save the updated file
    with open(LANDMARKS_PATH, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"\n✓ File updated: {LANDMARKS_PATH}")
else:
    print("✗ Basílica de la Sagrada Família not found or already has coordinates")
