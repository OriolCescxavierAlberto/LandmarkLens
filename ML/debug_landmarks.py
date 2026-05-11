#!/usr/bin/env python3
import sys
sys.path.insert(0, '.')
from landmark_model.rag_core import find_nearby, build_context

nearby = find_nearby(41.4036, 2.1744, max_results=5)
print('=== Landmarks encontrados ===')
for i, lm in enumerate(nearby, 1):
    name = lm.get('name', 'UNKNOWN')
    dist = lm.get('distance', 0)
    print(f'{i}. {name} - {dist}m')

print('\n=== Contexto para el prompt ===')
print(build_context(nearby))
