#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Test score_candidates_with_ranker"""

import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from landmark_model.rag_core import (
    score_candidates_with_ranker,
    load_ranker_bundle,
)

print("Checking ranker model...")
print("=" * 80)

bundle = load_ranker_bundle()
print(f"Bundle loaded: {bundle is not None}")

if bundle:
    print(f"Bundle keys: {bundle.keys()}")
    model = bundle.get("model")
    print(f"Model: {model}")
else:
    print("No ranker bundle found - ranking is disabled")

# Test ranking with sample data
print("\n" + "-" * 80)
print("Testing score_candidates_with_ranker with sample data...")
print("-" * 80)

candidates = [
    {"name": "Torre A", "distance": 10, "fame_score": 0, "bearing_deg": 0, "categories": []},
    {"name": "Torre B", "distance": 50, "fame_score": 5, "bearing_deg": 90, "categories": []},
    {"name": "Sagrada", "distance": 0, "fame_score": 12, "bearing_deg": 180, "categories": []},
]

print(f"\nInput: {len(candidates)} candidates")
result = score_candidates_with_ranker(candidates, azimuth=None, fov=70)
print(f"Output: {len(result)} candidates")

for i, cand in enumerate(result, 1):
    rank_score = cand.get("rank_score", "N/A")
    print(f"  {i}. {cand['name']} - distance={cand['distance']}, rank_score={rank_score}")
