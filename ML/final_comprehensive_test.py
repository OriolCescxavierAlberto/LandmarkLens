#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Final comprehensive test after all fixes"""

import json
import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

# Clear any cached indexes
import landmark_model.rag_core
landmark_model.rag_core._INDEX = None
landmark_model.rag_core._DB = None

from landmark_model.rag_core import (
    run_rag_query,
    check_ollama,
    DEFAULT_MAX_DIST,
    DEFAULT_MAX_RESULTS,
    DEFAULT_FOV,
)

LAT = 41.4036
LON = 2.1744

print("=" * 80)
print("FINAL TEST: Sagrada Familia Query")
print("=" * 80)
print(f"\nCoordinates: ({LAT}, {LON})")

# 1. Check Ollama
print("\n" + "-" * 80)
print("1. Checking Ollama...")
print("-" * 80)
if not check_ollama():
    print("ERROR: Ollama not available")
    sys.exit(1)
print("✓ Ollama is running")

# 2. Query without azimuth
print("\n" + "-" * 80)
print("2. Query without camera orientation...")
print("-" * 80)
result = run_rag_query(LAT, LON, azimuth=None, fov=DEFAULT_FOV, stream=False)

print(f"\nNearby landmarks found: {len(result.nearby)}")
print("\nFirst 5 landmarks:")
for i, lm in enumerate(result.nearby[:5], 1):
    print(f"  {i}. {lm['name']} - {lm['distance']:.0f}m")

# Check if Sagrada Familia is there
sagrada_in_nearby = any('sagrada' in lm['name'].lower() and 'familia' in lm['name'].lower() 
                        for lm in result.nearby)

print(f"\n{'✓' if sagrada_in_nearby else '✗'} Sagrada Familia in nearby landmarks: {sagrada_in_nearby}")

# Check model response
print(f"\nModel Response Quality:")
print(f"  JSON Valid: {result.validation['is_json_valid']}")
print(f"  Schema OK: {result.validation['schema_ok']}")
print(f"  All predictions in candidates: {result.validation['all_predicted_in_candidates']}")
print(f"  Predicted {len(result.validation['predicted_names'])} landmark names")

if result.validation['is_json_valid'] and result.validation['schema_ok']:
    print("\n✓ Model response is properly formatted JSON")
    
    # Show what was predicted
    print(f"\nModel predicted landmarks:")
    for name in result.validation['predicted_names'][:3]:
        print(f"  - {name}")
else:
    print("\n✗ Model response has issues:")
    for issue in result.validation['issues']:
        print(f"  - {issue}")

# 3. Query WITH azimuth (pointing at landmark)
print("\n" + "-" * 80)
print("3. Query with camera orientation (azimuth=0° - pointing North)...")
print("-" * 80)
result_azimuth = run_rag_query(LAT, LON, azimuth=0, fov=70, stream=False)

print(f"\nNearby landmarks: {len(result_azimuth.nearby)}")
print(f"Validation: JSON={result_azimuth.validation['is_json_valid']}, Schema={result_azimuth.validation['schema_ok']}")

if result_azimuth.validation['schema_ok'] and isinstance(result_azimuth.validation['parsed'], dict):
    target = result_azimuth.validation['parsed'].get('target')
    print(f"Target landmark: {target}")

print("\n" + "=" * 80)
print("FINAL SUMMARY")
print("=" * 80)

all_ok = (
    sagrada_in_nearby and
    result.validation['is_json_valid'] and 
    result.validation['schema_ok']
)

if all_ok:
    print("\n✓✓✓ ALL TESTS PASSED!")
    print("  - Sagrada Familia is correctly identified in nearby landmarks")
    print("  - Model response is properly formatted JSON")
    print("  - Schema validation passes")
else:
    print("\n✗ Some tests failed:")
    if not sagrada_in_nearby:
        print("  - Sagrada Familia not found in nearby landmarks")
    if not result.validation['is_json_valid']:
        print("  - Model JSON response is invalid")
    if not result.validation['schema_ok']:
        print("  - Model response schema is incorrect")
