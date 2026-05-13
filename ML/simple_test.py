#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Simple test for Sagrada Familia"""

import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

# Explicitly initialize everything
import landmark_model.rag_core as rag_core
rag_core._INDEX = None
rag_core._DB = None

from landmark_model.rag_core import (
    run_rag_query,
    check_ollama,
)

LAT = 41.4036
LON = 2.1744

print("Simple Sagrada Familia Test")
print("=" * 80)

# Check Ollama
if not check_ollama():
    print("ERROR: Ollama not available")
    sys.exit(1)

# Run query
print(f"\nQuerying at ({LAT}, {LON})...")
result = run_rag_query(LAT, LON, azimuth=None, fov=70, stream=False)

print(f"\nNearby landmarks: {len(result.nearby)}")
if result.nearby:
    for i, lm in enumerate(result.nearby[:5], 1):
        name = lm.get('name')
        dist = lm.get('distance')
        print(f"  {i}. {name} ({dist:.0f}m)")
        if name and 'Sagrada' in name and 'Família' in name:
            print(f"      ✓ FOUND SAGRADA FAMILIA!")
else:
    print("  (empty)")

print(f"\nValidation:")
print(f"  JSON valid: {result.validation['is_json_valid']}")
print(f"  Schema OK: {result.validation['schema_ok']}")
