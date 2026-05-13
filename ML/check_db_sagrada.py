#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Check if Sagrada Familia is in the database"""

import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from landmark_model.database import LandmarksDB

db = LandmarksDB()

# Query for Sagrada Familia
conn = db._get_connection()
cursor = conn.cursor()

cursor.execute(
    "SELECT name, lat, lon, fame_score FROM landmarks WHERE name LIKE '%Sagrada%' OR name LIKE '%Familia%' ORDER BY fame_score DESC LIMIT 10"
)
results = cursor.fetchall()

print("=" * 80)
print("Landmarks with 'Sagrada' or 'Familia' in database:")
print("=" * 80)
print()

if results:
    for row in results:
        print(f"Name: {row[0]}")
        print(f"  Coords: ({row[1]}, {row[2]})")
        print(f"  Fame Score: {row[3]}")
        print()
else:
    print("No results found!")

# Also check for the exact coordinates we set
print("\n" + "=" * 80)
print("Checking for landmarks at exact coordinates (41.4036, 2.1744):")
print("=" * 80)
print()

cursor.execute(
    "SELECT name, lat, lon, fame_score FROM landmarks WHERE lat = 41.4036 AND lon = 2.1744 ORDER BY fame_score DESC"
)
results = cursor.fetchall()

if results:
    for row in results:
        print(f"Name: {row[0]}")
        print(f"  Coords: ({row[1]}, {row[2]})")
        print(f"  Fame Score: {row[3]}")
        print()
else:
    print("No landmarks at those exact coordinates")

# Finally, test the find_nearby function
print("\n" + "=" * 80)
print("Testing find_nearby at (41.4036, 2.1744):")
print("=" * 80)
print()

nearby = db.find_nearby(41.4036, 2.1744, radius_km=1.0, max_results=8)

for i, lm in enumerate(nearby, 1):
    dist_m = lm['distance_m']
    score = dist_m / 1000 - (lm['fame_score'] * 0.005)
    print(f"{i}. {lm['name']}")
    print(f"   Distance: {dist_m}m | Fame: {lm['fame_score']} | Sort score: {score:.3f}")
    print()
