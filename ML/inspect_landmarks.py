#!/usr/bin/env python3
import json
with open('landmark_model/data/landmarks.json') as f:
    data = json.load(f)
landmarks = data.get('landmarks', [])
print(f'Total landmarks: {len(landmarks)}')
print(f'\nPrimeros 3 landmarks:')
for i, lm in enumerate(landmarks[:3]):
    print(f'{i}. {lm}')

# Buscar landmarks cerca de Barcelona
print(f'\n=== Buscando landmarks cerca de Barcelona (41.4036, 2.1744) ===')
import math
def haversine(lat1, lon1, lat2, lon2):
    radius = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    delta_lat = math.radians(lat2 - lat1)
    delta_lon = math.radians(lon2 - lon1)
    a = math.sin(delta_lat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(delta_lon / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

barcelona_lat, barcelona_lon = 41.4036, 2.1744
nearby = []
for lm in landmarks:
    if 'lat' in lm and 'lon' in lm:
        dist = haversine(barcelona_lat, barcelona_lon, lm['lat'], lm['lon'])
        if dist < 1000:  # 1 km
            nearby.append((lm['name'], dist))

print(f'Landmarks dentro de 1 km de Barcelona: {len(nearby)}')
if nearby:
    nearby.sort(key=lambda x: x[1])
    for name, dist in nearby[:5]:
        print(f'  - {name}: {dist:.0f}m')
