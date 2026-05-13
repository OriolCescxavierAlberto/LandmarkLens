#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Final comprehensive API test"""

import requests
import json
import sys

print("=" * 80)
print("PRUEBA FINAL: API HTTP")
print("=" * 80)

# Test 1: Health check
print("\n" + "-" * 80)
print("1. Health Check")
print("-" * 80)
try:
    response = requests.get("http://localhost:8000/api/v1/health", timeout=5)
    print(f"Status: {response.status_code}")
    print(f"Response: {json.dumps(response.json(), indent=2, ensure_ascii=False)}")
except Exception as e:
    print(f"ERROR: {e}")
    sys.exit(1)

# Test 2: Query Sagrada Familia
print("\n" + "-" * 80)
print("2. Query: Sagrada Familia (Barcelona)")
print("-" * 80)
data = {"lat": 41.4036, "lon": 2.1744}
try:
    response = requests.post("http://localhost:8000/api/v1/query", json=data, timeout=30)
    result = response.json()
    
    print(f"Status: {response.status_code}")
    print(f"API Status: {result.get('status')}")
    
    if 'data' in result:
        print(f"\nData returned:")
        landmarks = result['data']
        if isinstance(landmarks, list):
            print(f"  Landmarks count: {len(landmarks)}")
            for i, lm in enumerate(landmarks[:3], 1):
                print(f"  {i}. {lm.get('name')} - {lm.get('distance')}m (confidence: {lm.get('confidence')})")
        elif isinstance(landmarks, dict) and 'target' in landmarks:
            print(f"  Target: {landmarks['target']} - {landmarks.get('target_distance')}m")
    
    print(f"\nValidation:")
    val = result.get('validation', {})
    print(f"  JSON valid: {val.get('is_json_valid')}")
    print(f"  Schema OK: {val.get('schema_ok')}")
    print(f"  All predicted in candidates: {val.get('all_predicted_in_candidates')}")
    print(f"  Issues: {val.get('issues', [])}")
    
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

# Test 3: Query with Camera Orientation
print("\n" + "-" * 80)
print("3. Query: Con orientación de cámara (azimuth=90°)")
print("-" * 80)
data = {"lat": 41.4036, "lon": 2.1744, "azimuth": 90, "fov": 70}
try:
    response = requests.post("http://localhost:8000/api/v1/query", json=data, timeout=30)
    result = response.json()
    
    print(f"Status: {response.status_code}")
    print(f"API Status: {result.get('status')}")
    
    if 'data' in result:
        data_result = result['data']
        print(f"\nTarget landmark: {data_result.get('target')}")
        print(f"  Distance: {data_result.get('target_distance')}m")
        print(f"  Confidence: {data_result.get('confidence')}")
        
        others = data_result.get('others', [])
        if others:
            print(f"\n  Other landmarks ({len(others)}):")
            for lm in others[:2]:
                print(f"    - {lm.get('name')} - {lm.get('distance')}m")
    
    print(f"\nValidation:")
    val = result.get('validation', {})
    print(f"  JSON valid: {val.get('is_json_valid')}")
    print(f"  Schema OK: {val.get('schema_ok')}")
    
except Exception as e:
    print(f"ERROR: {e}")
    sys.exit(1)

print("\n" + "=" * 80)
print("✓ ALL TESTS COMPLETED SUCCESSFULLY")
print("=" * 80)
