#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG local + Ollama + camera orientation query helper."""

import sys
import json
import os
import math
import pickle
from collections import defaultdict
from urllib import error, request


OLLAMA_URL = "http://localhost:11434"
MODEL_NAME = "landmark-finder"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LANDMARKS_PATH = os.path.join(SCRIPT_DIR, "data", "landmarks.json")
ARTIFACT_DIR = os.path.join(SCRIPT_DIR, "artifacts")
MODEL_BUNDLE_PATH = os.path.join(ARTIFACT_DIR, "selected_model_bundle.joblib")
DEFAULT_FOV = 70
GRID_SIZE = 0.01


def haversine(lat1, lon1, lat2, lon2):
    radius = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    x = math.sin(dl) * math.cos(p2)
    y = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


def angle_diff(a, b):
    delta = abs(a - b) % 360
    return delta if delta <= 180 else 360 - delta


def direction(value):
    directions = ["norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"]
    return directions[round(value / 45) % 8]


def fmt_dist(meters):
    if meters < 100:
        return f"{int(meters)} m"
    if meters < 1000:
        return f"{int(meters / 10) * 10} m"
    return f"{meters / 1000:.1f} km"


class SpatialIndex:
    def __init__(self, cell_size=GRID_SIZE):
        self.cell_size = cell_size
        self.grid = defaultdict(list)
        self.total = 0

    def _cell(self, lat, lon):
        return int(lat / self.cell_size), int(lon / self.cell_size)

    def insert(self, landmark):
        cell = self._cell(landmark["lat"], landmark["lon"])
        self.grid[cell].append(landmark)
        self.total += 1

    def query_radius(self, lat, lon, max_dist_m):
        delta = int(max_dist_m / 111_000 / self.cell_size) + 1
        center_cell = self._cell(lat, lon)
        candidates = []
        for di in range(-delta, delta + 1):
            for dj in range(-delta, delta + 1):
                cell = (center_cell[0] + di, center_cell[1] + dj)
                if cell in self.grid:
                    candidates.extend(self.grid[cell])
        return candidates


_INDEX = None
_RANKER_BUNDLE = None


def load_landmarks():
    global _INDEX
    if _INDEX is not None:
        return _INDEX

    if not os.path.exists(LANDMARKS_PATH):
        print(f"No se encuentra {LANDMARKS_PATH}")
        print("Ejecuta primero: python extract_landmarks.py")
        sys.exit(1)

    with open(LANDMARKS_PATH, "r", encoding="utf-8") as file:
        data = json.load(file)

    index = SpatialIndex()
    skipped = 0
    for landmark in data.get("landmarks", []):
        if "lat" in landmark and "lon" in landmark:
            index.insert(landmark)
        else:
            skipped += 1

    _INDEX = index
    print(f"{index.total} landmarks indexados ({skipped} sin coordenadas omitidos)")
    return _INDEX


def load_ranker_bundle():
    global _RANKER_BUNDLE
    if _RANKER_BUNDLE is not None:
        return _RANKER_BUNDLE

    if not os.path.exists(MODEL_BUNDLE_PATH):
        return None

    try:
        with open(MODEL_BUNDLE_PATH, "rb") as file:
            _RANKER_BUNDLE = pickle.load(file)
        return _RANKER_BUNDLE
    except Exception:
        return None


def candidate_feature_rows(candidates, azimuth, fov):
    rows = []
    for candidate in candidates:
        bearing_deg = candidate["bearing_deg"]
        angle_offset = candidate.get("angle_from_center")
        if angle_offset is None and azimuth is not None:
            angle_offset = angle_diff(azimuth, bearing_deg)
        if angle_offset is None:
            angle_offset = 180.0

        rows.append(
            {
                "distance_m": float(candidate["distance"]),
                "distance_km": float(candidate["distance"]) / 1000.0,
                "bearing_sin": math.sin(math.radians(bearing_deg)),
                "bearing_cos": math.cos(math.radians(bearing_deg)),
                "angle_offset_deg": float(angle_offset),
                "azimuth_present": 1.0 if azimuth is not None else 0.0,
                "fov_deg": float(fov) if azimuth is not None else 0.0,
                "fame_score": float(candidate.get("fame_score", 0)),
                "category_count": float(len(candidate.get("categories", []))),
                "has_description": 1.0 if candidate.get("description") else 0.0,
                "has_wikipedia": 1.0 if candidate.get("wikipedia") else 0.0,
                "has_wikidata": 1.0 if candidate.get("wikidata") else 0.0,
            }
        )
    return rows


def score_candidates_with_ranker(candidates, azimuth, fov):
    bundle = load_ranker_bundle()
    if not bundle or not candidates:
        return candidates

    model = bundle.get("model")
    if model is None:
        return candidates

    try:
        features = candidate_feature_rows(candidates, azimuth, fov)
        if not features:
            return candidates

        if hasattr(model, "predict_proba"):
            probabilities = model.predict_proba(features)
            scores = []
            for probability in probabilities:
                if isinstance(probability, (list, tuple)):
                    scores.append(float(probability[-1]))
                else:
                    scores.append(float(probability))
        else:
            scores = [float(score) for score in model.decision_function(features)]

        for candidate, score in zip(candidates, scores):
            candidate["rank_score"] = score

        candidates.sort(key=lambda item: (item.get("rank_score", 0.0), -item["distance"]), reverse=True)
        return candidates
    except Exception:
        return candidates


def find_nearby(lat, lon, azimuth=None, fov=DEFAULT_FOV, max_dist=500, max_results=8):
    index = load_landmarks()
    candidates = index.query_radius(lat, lon, max_dist)

    results = []
    for landmark in candidates:
        distance_m = haversine(lat, lon, landmark["lat"], landmark["lon"])
        if distance_m > max_dist:
            continue

        bearing_deg = bearing(lat, lon, landmark["lat"], landmark["lon"])
        result = {
            "name": landmark["name"],
            "lat": landmark["lat"],
            "lon": landmark["lon"],
            "distance": round(distance_m, 1),
            "bearing_deg": round(bearing_deg, 1),
            "direction": direction(bearing_deg),
            "fame_score": landmark.get("fame_score", 0),
            "categories": landmark.get("categories", []),
        }

        for key in ("architect", "year", "style", "address", "wikipedia", "wikidata", "name_es", "name_en", "name_ca", "description"):
            if key in landmark:
                result[key] = landmark[key]

        if azimuth is not None and distance_m > 10:
            offset = angle_diff(azimuth, bearing_deg)
            if offset > fov / 2:
                continue
            result["angle_from_center"] = round(offset, 1)

        results.append(result)

    results.sort(key=lambda item: item["distance"] - item["fame_score"] * 5)
    return score_candidates_with_ranker(results, azimuth, fov)[:max_results]


def build_context(nearby):
    if not nearby:
        return "No landmarks found nearby."

    lines = []
    for index, landmark in enumerate(nearby, 1):
        parts = [f'{index}. "{landmark["name"]}" {fmt_dist(landmark["distance"])} @{landmark["bearing_deg"]}deg']
        if landmark.get("angle_from_center") is not None:
            parts.append(f'off:{landmark["angle_from_center"]}deg')
        parts.append(f'fame:{landmark["fame_score"]}')
        categories = landmark.get("categories", [])
        if categories:
            parts.append(f"[{', '.join(categories[:2])}]")
        if "architect" in landmark:
            parts.append(f"by {landmark['architect']}")
        lines.append(" | ".join(parts))
    return "\n".join(lines)


def query(lat, lon, azimuth=None, fov=DEFAULT_FOV):
    nearby = find_nearby(lat, lon, azimuth=azimuth, fov=fov)
    context = build_context(nearby)

    if azimuth is not None:
        prompt = (
            f"Pos:{lat},{lon} Cam:{azimuth}deg FOV:{fov}deg\n"
            f"Landmarks:\n{context}\n\n"
            'Return JSON: {"target":"FULL landmark name","target_distance":meters,'
            '"confidence":"high|medium|low",'
            '"others":[{"name":"FULL name","distance":meters}]}\n'
            "Use EXACT complete names from the list. Pick closest to camera center."
        )
    else:
        prompt = (
            f"Pos:{lat},{lon}\n"
            f"Landmarks:\n{context}\n\n"
            f"Return a JSON array with ALL {len(nearby)} landmarks listed above.\n"
            'Format: [{"name":"FULL landmark name","distance":meters,"confidence":"high|medium|low"}]\n'
            "Include EVERY landmark. Use EXACT complete names from the list."
        )

    print(f"\nBuscando landmarks cerca de ({lat}, {lon})...")
    if azimuth is not None:
        print(f"Camara apuntando a {azimuth}deg (FOV: {fov}deg)")
    print(f"Encontrados: {len(nearby)} lugares")
    if nearby:
        print(f"Mas cercano: {nearby[0]['name']} a {fmt_dist(nearby[0]['distance'])}")
    print("\nPreguntando al modelo...\n")

    try:
        payload = json.dumps({"model": MODEL_NAME, "prompt": prompt, "stream": True}).encode("utf-8")
        req = request.Request(
            f"{OLLAMA_URL}/api/generate",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        full = ""
        with request.urlopen(req, timeout=180) as response:
            for raw_line in response:
                if not raw_line.strip():
                    continue
                data = json.loads(raw_line.decode("utf-8"))
                token = data.get("response", "")
                print(token, end="", flush=True)
                full += token
                if data.get("done"):
                    break

        print("\n")
        _validate_response(full)
        return full
    except error.URLError:
        print("Ollama no responde. Esta corriendo? (ollama serve)")
    except Exception as exc:
        print(f"Error: {exc}")
    return None


def _validate_response(raw):
    text = raw.strip()
    try:
        parsed = json.loads(text)
        print("Respuesta JSON valida")
        return parsed
    except json.JSONDecodeError:
        pass

    start = text.find("[")
    if start == -1:
        start = text.find("{")
    end = max(text.rfind("]"), text.rfind("}"))
    if start != -1 and end > start:
        try:
            parsed = json.loads(text[start : end + 1])
            print("JSON extraido correctamente")
            return parsed
        except json.JSONDecodeError:
            pass

    if text.startswith("{") and text.endswith("}") and "},{" in text:
        try:
            parsed = json.loads("[" + text + "]")
            print("JSON reparado (anadidos corchetes)")
            return parsed
        except json.JSONDecodeError:
            pass

    print("La respuesta no es JSON valido")
    return None


def check_ollama():
    try:
        with request.urlopen(f"{OLLAMA_URL}/api/tags", timeout=5) as response:
            payload = json.loads(response.read().decode("utf-8"))
        models = [model["name"] for model in payload.get("models", [])]
        if not any(MODEL_NAME in model for model in models):
            print(f"Modelo '{MODEL_NAME}' no encontrado.")
            print(f"Modelos disponibles: {models}")
            print("Ejecuta: python generate_knowledge.py && ollama create landmark-finder -f Modelfile")
            return False
        return True
    except Exception:
        print("Ollama no esta corriendo. Iniciarlo: ollama serve")
        return False


def interactive():
    print("=" * 60)
    print("LANDMARK FINDER - RAG + brujula")
    print("=" * 60)
    print("Formatos:")
    print("  lat, lon                -> busca en todas direcciones")
    print("  lat, lon, azimuth       -> filtra por direccion camara")
    print("  lat, lon, azimuth, fov  -> direccion + FOV custom")
    print()
    print("Azimuth: 0=N, 90=E, 180=S, 270=O")
    print("FOV: campo de vision en grados (default 70)")
    print()
    print("Escribe 'q' para salir.")
    print("=" * 60)

    while True:
        entry = input("lat, lon [, azimuth [, fov]]: ").strip()
        if entry.lower() in ("q", "quit", "salir", "exit"):
            break
        if not entry:
            continue

        try:
            parts = entry.replace(",", " ").split()
            lat, lon = float(parts[0]), float(parts[1])
            azimuth = float(parts[2]) % 360 if len(parts) >= 3 else None
            fov = float(parts[3]) if len(parts) >= 4 else DEFAULT_FOV
        except (ValueError, IndexError):
            print("Formato: lat, lon [, azimuth [, fov]]")
            continue

        query(lat, lon, azimuth=azimuth, fov=fov)


def main():
    if not check_ollama():
        sys.exit(1)
    load_landmarks()

    if len(sys.argv) >= 3:
        try:
            lat = float(sys.argv[1])
            lon = float(sys.argv[2])
            azimuth = float(sys.argv[3]) % 360 if len(sys.argv) >= 4 else None
            fov = float(sys.argv[4]) if len(sys.argv) >= 5 else DEFAULT_FOV
            query(lat, lon, azimuth=azimuth, fov=fov)
        except ValueError:
            print("Uso: python query_model.py <lat> <lon> [azimuth] [fov]")
            sys.exit(1)
    else:
        interactive()


if __name__ == "__main__":
    main()