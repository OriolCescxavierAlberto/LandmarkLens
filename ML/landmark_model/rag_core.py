#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Shared RAG runtime for LandmarkLens.

This module centralizes the local retrieval, prompt construction, Ollama call,
and response validation used by the application and the evaluation scripts.
"""

from __future__ import annotations

import json
import math
import os
import pickle
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib import error, request

try:
    from database import LandmarksDB
    HAS_DB = True
except ImportError:
    HAS_DB = False
    LandmarksDB = None


OLLAMA_URL = "http://localhost:11434"
MODEL_NAME = "landmark-finder"
SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR / "data"
ARTIFACT_DIR = SCRIPT_DIR / "artifacts"
LANDMARKS_PATH = DATA_DIR / "landmarks.json"
MODEL_BUNDLE_PATH = ARTIFACT_DIR / "selected_model_bundle.joblib"
SYSTEM_PROMPT_PATH = DATA_DIR / "system_prompt.txt"
MODFILE_PATH = SCRIPT_DIR / "Modelfile"
RAG_MANIFEST_PATH = DATA_DIR / "rag_manifest.json"

DEFAULT_FOV = 70
GRID_SIZE = 0.01
DEFAULT_MAX_DIST = 500
DEFAULT_MAX_RESULTS = 8

NUMERIC_FEATURES = [
    "distance_m",
    "distance_km",
    "bearing_sin",
    "bearing_cos",
    "angle_offset_deg",
    "azimuth_present",
    "fov_deg",
    "fame_score",
    "category_count",
    "has_description",
    "has_wikipedia",
    "has_wikidata",
]

SYSTEM_PROMPT = """You are a landmark identification system. You receive GPS coordinates and a numbered list of nearby landmarks with distances.

CRITICAL RULES - FOLLOW EXACTLY:
1. NEVER invent landmarks. Use ONLY names from the provided list, word for word.
2. Copy landmark names EXACTLY and COMPLETELY from the list. No truncation, no changes.
3. Respond ONLY with valid JSON. NO other text before, after, or between.
4. Use double quotes for ALL JSON keys and string values: "name", "distance", "confidence"
5. All numeric values must be numbers, not strings: "distance": 42 (not "distance": "42")

RESPONSE FORMAT WITHOUT camera orientation (azimuth):
Return a JSON array with ONE object per landmark:
[
  {"name": "landmark name exactly as listed", "distance": meters_as_number, "confidence": "high" or "medium" or "low"},
  {"name": "another landmark", "distance": meters_as_number, "confidence": "high" or "medium" or "low"}
]

RESPONSE FORMAT WITH camera orientation (azimuth):
Return ONE object with target and others list:
{"target": "landmark name exactly as listed", "target_distance": meters_as_number, "confidence": "high" or "medium" or "low", "others": [{"name": "landmark", "distance": meters_as_number}]}

Confidence scoring WITHOUT azimuth:
- distance < 100m: "high"
- distance < 300m: "high"
- distance < 1000m: "medium"
- distance >= 1000m: "low"

Pick target (with azimuth): landmark with smallest angle offset from camera center OR closest if tied.

DO NOT:
- Add extra fields like "magnitude", "categories", "bearing", "fame_score"
- Wrap array in an object: {"landmarks": [...]} is WRONG
- Forget quotes around keys: {distance: 5} is WRONG
- Add explanatory text
- Reorder the landmarks list

EXAMPLE CORRECT RESPONSE (no azimuth):
[{"name": "Torre de Jesús", "distance": 4, "confidence": "high"}, {"name": "Plaça", "distance": 50, "confidence": "high"}]
"""


def build_modelfile(base_model: str = "llama3.2:3b") -> str:
    return f'''# Modelfile para Landmark Finder España
# Modelo basado en {base_model} - system prompt ligero, datos via RAG
# Optimizado para RTX 3060 6GB VRAM + 16GB RAM

FROM {base_model}

PARAMETER temperature 0.1
PARAMETER top_p 0.9
PARAMETER num_ctx 8192
PARAMETER num_predict 512
PARAMETER stop "<|eot_id|>"

SYSTEM """{SYSTEM_PROMPT}"""
'''


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")


def _load_landmarks_payload() -> dict[str, Any]:
    if not LANDMARKS_PATH.exists():
        raise FileNotFoundError(f"No se encuentra {LANDMARKS_PATH}")

    with LANDMARKS_PATH.open("r", encoding="utf-8") as file:
        payload = json.load(file)
    if not isinstance(payload, dict):
        raise ValueError("landmarks.json debe contener un objeto JSON")
    return payload


def load_training_examples_count() -> int | None:
    examples_path = DATA_DIR / "training_examples.json"
    if not examples_path.exists():
        return None

    try:
        with examples_path.open("r", encoding="utf-8") as file:
            payload = json.load(file)
        if isinstance(payload, list):
            return len(payload)
    except Exception:
        return None
    return None


class SpatialIndex:
    def __init__(self, cell_size: float = GRID_SIZE):
        self.cell_size = cell_size
        self.grid = defaultdict(list)
        self.total = 0

    def _cell(self, lat: float, lon: float) -> tuple[int, int]:
        return int(lat / self.cell_size), int(lon / self.cell_size)

    def insert(self, landmark: dict[str, Any]) -> None:
        cell = self._cell(float(landmark["lat"]), float(landmark["lon"]))
        self.grid[cell].append(landmark)
        self.total += 1

    def query_radius(self, lat: float, lon: float, max_dist_m: float) -> list[dict[str, Any]]:
        delta = int(max_dist_m / 111_000 / self.cell_size) + 1
        center_cell = self._cell(lat, lon)
        candidates: list[dict[str, Any]] = []
        for row_offset in range(-delta, delta + 1):
            for col_offset in range(-delta, delta + 1):
                cell = (center_cell[0] + row_offset, center_cell[1] + col_offset)
                if cell in self.grid:
                    candidates.extend(self.grid[cell])
        return candidates


_INDEX: SpatialIndex | None = None
_RANKER_BUNDLE: dict[str, Any] | None = None
_DB: "LandmarksDB | None" = None


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    delta_lat = math.radians(lat2 - lat1)
    delta_lon = math.radians(lon2 - lon1)
    a = math.sin(delta_lat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(delta_lon / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    delta_lon = math.radians(lon2 - lon1)
    x = math.sin(delta_lon) * math.cos(p2)
    y = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(delta_lon)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


def angle_diff(angle_a: float, angle_b: float) -> float:
    delta = abs(angle_a - angle_b) % 360
    return delta if delta <= 180 else 360 - delta


def direction(value: float) -> str:
    directions = ["norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"]
    return directions[round(value / 45) % 8]


def fmt_dist(meters: float) -> str:
    if meters < 100:
        return f"{int(meters)} m"
    if meters < 1000:
        return f"{int(meters / 10) * 10} m"
    return f"{meters / 1000:.1f} km"


def load_landmarks() -> SpatialIndex:
    global _INDEX, _DB
    
    # Try to use database first
    if HAS_DB and LandmarksDB is not None:
        if _DB is None:
            _DB = LandmarksDB()
            try:
                stats = _DB.get_stats()
                total = stats.get("total_landmarks", 0)
                if total > 0:
                    print(f"{total} landmarks indexados (from SQLite database)")
                    # Create a dummy index for backwards compatibility
                    _INDEX = SpatialIndex()
                    _INDEX.total = total
                    return _INDEX
            except Exception as e:
                print(f"⚠️  Database error: {e}, falling back to JSON")
                _DB = None
    
    # Fallback to JSON-based index
    if _INDEX is not None:
        return _INDEX

    payload = _load_landmarks_payload()
    index = SpatialIndex()
    skipped = 0

    for landmark in payload.get("landmarks", []):
        if isinstance(landmark, dict) and "lat" in landmark and "lon" in landmark:
            index.insert(landmark)
        else:
            skipped += 1

    _INDEX = index
    print(f"{index.total} landmarks indexados ({skipped} sin coordenadas omitidos)")
    return _INDEX


def load_ranker_bundle() -> dict[str, Any] | None:
    global _RANKER_BUNDLE
    if _RANKER_BUNDLE is not None:
        return _RANKER_BUNDLE

    if not MODEL_BUNDLE_PATH.exists():
        return None

    try:
        with MODEL_BUNDLE_PATH.open("rb") as file:
            _RANKER_BUNDLE = pickle.load(file)
        return _RANKER_BUNDLE
    except Exception:
        return None


def candidate_feature_rows(candidates: list[dict[str, Any]], azimuth: float | None, fov: float) -> list[dict[str, float]]:
    rows = []
    for candidate in candidates:
        bearing_deg = float(candidate["bearing_deg"])
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


def score_candidates_with_ranker(candidates: list[dict[str, Any]], azimuth: float | None, fov: float) -> list[dict[str, Any]]:
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


def find_nearby(
    lat: float,
    lon: float,
    azimuth: float | None = None,
    fov: float = DEFAULT_FOV,
    max_dist: float = DEFAULT_MAX_DIST,
    max_results: int = DEFAULT_MAX_RESULTS,
) -> list[dict[str, Any]]:
    global _DB
    
    results = []
    
    # Try to use database first
    if HAS_DB and _DB is not None:
        try:
            candidates = _DB.find_nearby(lat, lon, radius_km=max_dist/1000, max_results=max_results*2)
            for db_lm in candidates:
                distance_m = db_lm.get("distance_m", 0)
                bearing_deg = bearing(lat, lon, db_lm["lat"], db_lm["lon"])
                result = {
                    "name": db_lm.get("name"),
                    "lat": db_lm.get("lat"),
                    "lon": db_lm.get("lon"),
                    "distance": distance_m,
                    "bearing_deg": round(bearing_deg, 1),
                    "direction": direction(bearing_deg),
                    "fame_score": db_lm.get("fame_score", 0),
                    "categories": db_lm.get("categories", []),
                    "region": db_lm.get("region"),
                }

                if azimuth is not None and distance_m > 10:
                    offset = angle_diff(azimuth, bearing_deg)
                    if offset > fov / 2:
                        continue
                    result["angle_from_center"] = round(offset, 1)

                results.append(result)
            
            if results:
                results.sort(key=lambda item: item["distance"] - item.get("fame_score", 0) * 5)
                ranked_results = score_candidates_with_ranker(results, azimuth, fov)[:max_results]
                if ranked_results:
                    return ranked_results
                # If ranking returned empty, continue to fallback
        except Exception as e:
            print(f"⚠️  Database query failed: {e}, falling back to index")
    
    # Fallback to in-memory index
    index = load_landmarks()
    candidates = index.query_radius(lat, lon, max_dist)

    for landmark in candidates:
        distance_m = haversine(lat, lon, float(landmark["lat"]), float(landmark["lon"]))
        if distance_m > max_dist:
            continue

        bearing_deg = bearing(lat, lon, float(landmark["lat"]), float(landmark["lon"]))
        result = {
            "name": landmark.get("name"),
            "lat": landmark.get("lat"),
            "lon": landmark.get("lon"),
            "distance": round(distance_m, 1),
            "bearing_deg": round(bearing_deg, 1),
            "direction": direction(bearing_deg),
            "fame_score": landmark.get("fame_score", 0),
            "categories": landmark.get("categories", []),
        }

        for key in (
            "architect",
            "year",
            "style",
            "address",
            "wikipedia",
            "wikidata",
            "name_es",
            "name_en",
            "name_ca",
            "description",
        ):
            if key in landmark:
                result[key] = landmark[key]

        if azimuth is not None and distance_m > 10:
            offset = angle_diff(azimuth, bearing_deg)
            if offset > fov / 2:
                continue
            result["angle_from_center"] = round(offset, 1)

        results.append(result)

    results.sort(key=lambda item: item["distance"] - item.get("fame_score", 0) * 5)
    return score_candidates_with_ranker(results, azimuth, fov)[:max_results]


def build_context(nearby: list[dict[str, Any]]) -> str:
    if not nearby:
        return "No landmarks found nearby."

    lines = []
    for index, landmark in enumerate(nearby, 1):
        parts = [f'{index}. "{landmark["name"]}" {fmt_dist(landmark["distance"])} @{landmark["bearing_deg"]}deg']
        if landmark.get("angle_from_center") is not None:
            parts.append(f'off:{landmark["angle_from_center"]}deg')
        parts.append(f'fame:{landmark.get("fame_score", 0)}')
        categories = landmark.get("categories", [])
        if categories:
            parts.append(f"[{', '.join(categories[:2])}]")
        if "architect" in landmark:
            parts.append(f"by {landmark['architect']}")
        lines.append(" | ".join(parts))
    return "\n".join(lines)


def build_prompt(lat: float, lon: float, nearby: list[dict[str, Any]], azimuth: float | None = None, fov: float = DEFAULT_FOV) -> str:
    if azimuth is not None:
        return (
            f"Pos:{lat},{lon} Cam:{azimuth}deg FOV:{fov}deg\n"
            f"Landmarks:\n{build_context(nearby)}\n\n"
            'RESPOND WITH EXACTLY THIS JSON STRUCTURE (with proper quotes):\n'
            '{"target": "LANDMARK NAME", "target_distance": NUMBER, "confidence": "high|medium|low", "others": [{"name": "NAME", "distance": NUMBER}]}\n'
            "Rules: Use EXACT names from list above. Pick landmark closest to camera center. Use quotes around all keys and string values."
        )

    return (
        f"Pos:{lat},{lon}\n"
        f"Landmarks:\n{build_context(nearby)}\n\n"
        f"RESPOND WITH A JSON ARRAY (use proper quotes on all keys):\n"
        '[{"name": "LANDMARK NAME", "distance": NUMBER, "confidence": "high|medium|low"}, ...]\n'
        "Rules: List ALL landmarks above. Use EXACT names. Put quotes around keys and values."
    )



def query_ollama(prompt: str, stream: bool = True, timeout: int = 180, model_name: str = MODEL_NAME) -> str:
    payload = json.dumps({"model": model_name, "prompt": prompt, "stream": stream}).encode("utf-8")
    req = request.Request(
        f"{OLLAMA_URL}/api/generate",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    if not stream:
        with request.urlopen(req, timeout=timeout) as response:
            data = json.loads(response.read().decode("utf-8"))
        return str(data.get("response", ""))

    full_text = ""
    with request.urlopen(req, timeout=timeout) as response:
        for raw_line in response:
            if not raw_line.strip():
                continue
            data = json.loads(raw_line.decode("utf-8"))
            token = data.get("response", "")
            print(token, end="", flush=True)
            full_text += token
            if data.get("done"):
                break

    print("\n")
    return full_text


def extract_json(raw_text: str) -> Any | None:
    text = raw_text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    start_list = text.find("[")
    end_list = text.rfind("]")
    if start_list != -1 and end_list > start_list:
        chunk = text[start_list : end_list + 1]
        try:
            return json.loads(chunk)
        except json.JSONDecodeError:
            pass

    start_object = text.find("{")
    end_object = text.rfind("}")
    if start_object != -1 and end_object > start_object:
        chunk = text[start_object : end_object + 1]
        try:
            return json.loads(chunk)
        except json.JSONDecodeError:
            pass

    if text.startswith("{") and text.endswith("}") and "},{" in text:
        try:
            return json.loads("[" + text + "]")
        except json.JSONDecodeError:
            pass

    return None


def predicted_names(parsed: Any) -> list[str]:
    names: list[str] = []
    if isinstance(parsed, list):
        for item in parsed:
            if isinstance(item, dict) and isinstance(item.get("name"), str):
                names.append(item["name"].strip())
    elif isinstance(parsed, dict):
        target = parsed.get("target")
        if isinstance(target, str):
            names.append(target.strip())
        others = parsed.get("others")
        if isinstance(others, list):
            for item in others:
                if isinstance(item, dict) and isinstance(item.get("name"), str):
                    names.append(item["name"].strip())
    return names


def _validate_schema(parsed: Any, azimuth: float | None = None) -> bool:
    if azimuth is None:
        if not isinstance(parsed, list) or not parsed:
            return False
        for item in parsed:
            if not isinstance(item, dict):
                return False
            if not isinstance(item.get("name"), str) or not item["name"].strip():
                return False
            if not isinstance(item.get("distance"), (int, float)):
                return False
            if item.get("confidence") not in {"high", "medium", "low"}:
                return False
        return True

    if not isinstance(parsed, dict):
        return False
    if not isinstance(parsed.get("target"), str) or not parsed["target"].strip():
        return False
    if not isinstance(parsed.get("target_distance"), (int, float)):
        return False
    if parsed.get("confidence") not in {"high", "medium", "low"}:
        return False

    others = parsed.get("others", [])
    if others is None:
        return True
    if not isinstance(others, list):
        return False
    for item in others:
        if not isinstance(item, dict):
            return False
        if not isinstance(item.get("name"), str) or not item["name"].strip():
            return False
        if not isinstance(item.get("distance"), (int, float)):
            return False
    return True


def validate_response(raw_text: str, allowed_candidates: list[dict[str, Any]] | None = None, azimuth: float | None = None) -> dict[str, Any]:
    parsed = extract_json(raw_text)
    validation = {
        "is_json_valid": parsed is not None,
        "schema_ok": False,
        "parsed": parsed,
        "predicted_names": [],
        "all_predicted_in_candidates": False,
        "issues": [],
    }

    if parsed is None:
        validation["issues"].append("invalid_json")
        return validation

    validation["predicted_names"] = predicted_names(parsed)
    validation["schema_ok"] = _validate_schema(parsed, azimuth=azimuth)
    if not validation["schema_ok"]:
        validation["issues"].append("schema_mismatch")

    if allowed_candidates is not None:
        allowed_names = {
            str(candidate.get("name", "")).strip()
            for candidate in allowed_candidates
            if isinstance(candidate, dict)
        }
        if validation["predicted_names"]:
            validation["all_predicted_in_candidates"] = all(
                name in allowed_names for name in validation["predicted_names"]
            )
        if not validation["all_predicted_in_candidates"]:
            validation["issues"].append("prediction_outside_candidates")

    return validation


@dataclass
class RAGQueryResult:
    prompt: str
    nearby: list[dict[str, Any]]
    raw_text: str | None
    validation: dict[str, Any]


def run_rag_query(
    lat: float,
    lon: float,
    azimuth: float | None = None,
    fov: float = DEFAULT_FOV,
    max_dist: float = DEFAULT_MAX_DIST,
    max_results: int = DEFAULT_MAX_RESULTS,
    stream: bool = True,
    model_name: str = MODEL_NAME,
) -> RAGQueryResult:
    nearby = find_nearby(lat, lon, azimuth=azimuth, fov=fov, max_dist=max_dist, max_results=max_results)
    prompt = build_prompt(lat, lon, nearby, azimuth=azimuth, fov=fov)

    if nearby:
        print(f"\nBuscando landmarks cerca de ({lat}, {lon})...")
        if azimuth is not None:
            print(f"Camara apuntando a {azimuth}deg (FOV: {fov}deg)")
        print(f"Encontrados: {len(nearby)} lugares")
        print(f"Mas cercano: {nearby[0]['name']} a {fmt_dist(nearby[0]['distance'])}")
        print("\nPreguntando al modelo...\n")

    try:
        raw_text = query_ollama(prompt, stream=stream, model_name=model_name)
        validation = validate_response(raw_text, allowed_candidates=nearby, azimuth=azimuth)
        return RAGQueryResult(prompt=prompt, nearby=nearby, raw_text=raw_text, validation=validation)
    except error.URLError:
        print("Ollama no responde. Esta corriendo? (ollama serve)")
    except Exception as exc:
        print(f"Error: {exc}")

    return RAGQueryResult(prompt=prompt, nearby=nearby, raw_text=None, validation={"is_json_valid": False, "schema_ok": False, "parsed": None, "predicted_names": [], "all_predicted_in_candidates": False, "issues": ["query_failed"]})


def check_ollama() -> bool:
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


def build_rag_manifest(base_model: str = "llama3.2:3b") -> dict[str, Any]:
    payload = _load_landmarks_payload()
    landmarks = payload.get("landmarks", [])
    geo_landmarks = [landmark for landmark in landmarks if isinstance(landmark, dict) and "lat" in landmark and "lon" in landmark]
    training_examples_count = load_training_examples_count()

    manifest = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "model_name": MODEL_NAME,
        "base_model": base_model,
        "retrieval": {
            "grid_size": GRID_SIZE,
            "default_fov": DEFAULT_FOV,
            "default_max_dist": DEFAULT_MAX_DIST,
            "default_max_results": DEFAULT_MAX_RESULTS,
        },
        "dataset": {
            "landmarks_total": len(landmarks),
            "landmarks_with_coordinates": len(geo_landmarks),
            "training_examples": training_examples_count,
        },
        "artifacts": {
            "landmarks": str(LANDMARKS_PATH),
            "system_prompt": str(SYSTEM_PROMPT_PATH),
            "modelfile": str(MODFILE_PATH),
            "bundle": str(MODEL_BUNDLE_PATH),
        },
        "feature_columns": NUMERIC_FEATURES,
        "prompt_rules": [
            "never invent landmarks",
            "copy names exactly",
            "return only valid JSON",
            "use target/others when azimuth is provided",
        ],
    }

    write_text(SYSTEM_PROMPT_PATH, SYSTEM_PROMPT)
    write_text(MODFILE_PATH, build_modelfile(base_model=base_model))
    write_json(RAG_MANIFEST_PATH, manifest)
    return manifest
