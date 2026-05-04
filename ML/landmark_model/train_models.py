#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
train_models.py
===============
Entrena varios modelos ligeros para rankear landmarks, ajusta hiperparámetros
y exporta el mejor candidato para uso en la aplicación.

Este script evita dependencias pesadas para poder ejecutarse en entornos
mínimos. Produce un bundle serializado con `pickle` que `query_model.py`
puede cargar directamente.
"""

from __future__ import annotations

import argparse
import csv
import itertools
import json
import math
import os
import pickle
import random
from dataclasses import asdict, dataclass
from datetime import datetime, timezone

from query_model import DEFAULT_FOV, GRID_SIZE, LANDMARKS_PATH, SpatialIndex, angle_diff, bearing, haversine
from ranker_models import FeatureScaler, HeuristicRanker, LinearRanker, NUMERIC_FEATURES


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
ARTIFACT_DIR = os.path.join(SCRIPT_DIR, "artifacts")
BUNDLE_PATH = os.path.join(ARTIFACT_DIR, "selected_model_bundle.joblib")
COMPARISON_PATH = os.path.join(ARTIFACT_DIR, "model_comparison.csv")
SUMMARY_PATH = os.path.join(ARTIFACT_DIR, "experiment_summary.json")
RESULTS_PATH = os.path.join(DATA_DIR, "experiment_results.json")

@dataclass
class RunResult:
    name: str
    best_params: dict
    validation_candidate_f1: float
    validation_candidate_precision: float
    validation_candidate_recall: float
    validation_candidate_accuracy: float
    validation_query_top1: float
    validation_average_precision: float
    test_candidate_f1: float
    test_candidate_precision: float
    test_candidate_recall: float
    test_candidate_accuracy: float
    test_query_top1: float
    test_average_precision: float
def geodesic_offset(lat, lon, distance_m, bearing_deg):
    """Calcula un desplazamiento geográfico aproximado desde un punto origen."""
    radius = 6_371_000.0
    angular_distance = distance_m / radius
    start_lat = math.radians(lat)
    start_lon = math.radians(lon)
    bearing_rad = math.radians(bearing_deg)

    end_lat = math.asin(
        math.sin(start_lat) * math.cos(angular_distance)
        + math.cos(start_lat) * math.sin(angular_distance) * math.cos(bearing_rad)
    )
    end_lon = start_lon + math.atan2(
        math.sin(bearing_rad) * math.sin(angular_distance) * math.cos(start_lat),
        math.cos(angular_distance) - math.sin(start_lat) * math.sin(end_lat),
    )
    return math.degrees(end_lat), math.degrees(end_lon)


def select_target_landmarks(landmarks, max_targets, seed):
    """Selecciona landmarks con coordenadas para generar queries sintéticas."""
    rng = random.Random(seed)
    candidates = [lm for lm in landmarks if "lat" in lm and "lon" in lm]
    if len(candidates) <= max_targets:
        return candidates

    weights = [max(1.0, float(lm.get("fame_score", 0)) + 1.0) for lm in candidates]
    selected = set()
    while len(selected) < max_targets:
        selected.add(rng.choices(range(len(candidates)), weights=weights, k=1)[0])
    return [candidates[index] for index in sorted(selected)]


def build_spatial_index(landmarks):
    """Construye un índice espacial con los landmarks georreferenciados."""
    index = SpatialIndex(cell_size=GRID_SIZE)
    for landmark in landmarks:
        if "lat" in landmark and "lon" in landmark:
            index.insert(landmark)
    return index


def build_feature_row(candidate, azimuth, fov):
    """Convierte un candidato a las features usadas por el ranker."""
    bearing_deg = candidate["bearing_deg"]
    angle_offset = candidate.get("angle_from_center")
    if angle_offset is None and azimuth is not None:
        angle_offset = angle_diff(azimuth, bearing_deg)
    if angle_offset is None:
        angle_offset = 180.0

    return {
        "distance_m": float(candidate["distance"]),
        "distance_km": float(candidate["distance"]) / 1000.0,
        "bearing_sin": math.sin(math.radians(bearing_deg)),
        "bearing_cos": math.cos(math.radians(bearing_deg)),
        "angle_offset_deg": float(angle_offset),
        "azimuth_present": 1.0 if azimuth is not None else 0.0,
        "fov_deg": float(fov) if azimuth is not None else 0.0,
        "fame_score": float(candidate.get("fame_score", 0.0)),
        "category_count": float(len(candidate.get("categories", []))),
        "has_description": 1.0 if candidate.get("description") else 0.0,
        "has_wikipedia": 1.0 if candidate.get("wikipedia") else 0.0,
        "has_wikidata": 1.0 if candidate.get("wikidata") else 0.0,
    }


def build_training_rows(landmarks, max_targets, samples_per_target, max_candidates, max_dist, seed):
    """Genera ejemplos sintéticos de ranking a partir de landmarks reales."""
    rng = random.Random(seed)
    target_landmarks = select_target_landmarks(landmarks, max_targets=max_targets, seed=seed)
    index = build_spatial_index(landmarks)

    rows = []
    query_id = 0
    skipped_queries = 0

    for target in target_landmarks:
        for sample_index in range(samples_per_target):
            query_id += 1
            query_distance = rng.uniform(25.0, min(350.0, max_dist * 0.7))
            query_bearing = rng.uniform(0.0, 360.0)
            query_lat, query_lon = geodesic_offset(target["lat"], target["lon"], query_distance, query_bearing)

            target_bearing = bearing(query_lat, query_lon, target["lat"], target["lon"])
            if sample_index % 2 == 0:
                azimuth = None
                fov = DEFAULT_FOV
            else:
                fov = rng.choice([50, 70, 90])
                azimuth = (target_bearing + rng.uniform(-fov * 0.2, fov * 0.2)) % 360

            candidates = []
            for landmark in index.query_radius(query_lat, query_lon, max_dist):
                distance_m = haversine(query_lat, query_lon, landmark["lat"], landmark["lon"])
                if distance_m > max_dist:
                    continue

                candidate_bearing = bearing(query_lat, query_lon, landmark["lat"], landmark["lon"])
                candidate = {
                    "name": landmark["name"],
                    "lat": landmark["lat"],
                    "lon": landmark["lon"],
                    "distance": round(distance_m, 1),
                    "bearing_deg": round(candidate_bearing, 1),
                    "fame_score": landmark.get("fame_score", 0),
                    "categories": landmark.get("categories", []),
                    "description": landmark.get("description"),
                    "wikipedia": landmark.get("wikipedia"),
                    "wikidata": landmark.get("wikidata"),
                    "region": landmark.get("region"),
                    "osm_id": landmark.get("osm_id"),
                }

                if azimuth is not None:
                    angle_offset = angle_diff(azimuth, candidate_bearing)
                    if angle_offset > fov / 2:
                        continue
                    candidate["angle_from_center"] = round(angle_offset, 1)

                candidates.append(candidate)

            candidates.sort(key=lambda item: (item["distance"], -item.get("fame_score", 0)))
            if not any(candidate.get("osm_id") == target.get("osm_id") for candidate in candidates):
                skipped_queries += 1
                continue

            candidates = candidates[:max_candidates]
            for candidate in candidates:
                row = build_feature_row(candidate, azimuth, fov)
                row.update(
                    {
                        "label": 1 if candidate.get("osm_id") == target.get("osm_id") else 0,
                        "query_id": query_id,
                        "query_lat": query_lat,
                        "query_lon": query_lon,
                        "query_azimuth": azimuth if azimuth is not None else "",
                        "query_has_azimuth": 1 if azimuth is not None else 0,
                        "query_fov": fov if azimuth is not None else 0,
                        "target_name": target["name"],
                        "target_region": target.get("region", ""),
                        "target_fame_score": target.get("fame_score", 0),
                    }
                )
                rows.append(row)

    stats = {
        "target_landmarks": len(target_landmarks),
        "generated_queries": query_id,
        "skipped_queries": skipped_queries,
        "rows": len(rows),
    }
    return rows, stats


def split_queries(rows, seed):
    """Divide por query_id para evitar fuga de información."""
    query_ids = sorted({int(row["query_id"]) for row in rows})
    rng = random.Random(seed)
    rng.shuffle(query_ids)

    total = len(query_ids)
    train_cut = max(1, int(total * 0.6))
    valid_cut = max(train_cut + 1, int(total * 0.8)) if total > 2 else total

    train_ids = set(query_ids[:train_cut])
    valid_ids = set(query_ids[train_cut:valid_cut])
    test_ids = set(query_ids[valid_cut:])
    if not valid_ids:
        valid_ids = set(query_ids[train_cut:train_cut + 1])
    if not test_ids:
        test_ids = set(query_ids[-1:])
    return train_ids, valid_ids, test_ids


def filter_rows(rows, query_ids):
    return [row for row in rows if int(row["query_id"]) in query_ids]


def heuristic_top1_accuracy(rows, scores):
    grouped = {}
    for row, score in zip(rows, scores):
        query_id = int(row["query_id"])
        current = grouped.get(query_id)
        if current is None or score > current[0] or (score == current[0] and float(row["distance_m"]) < current[2]):
            grouped[query_id] = (score, int(row["label"]), float(row["distance_m"]))
    if not grouped:
        return 0.0
    return sum(label for _, label, _ in grouped.values()) / len(grouped)


def average_precision(y_true, scores):
    ranked = sorted(zip(scores, y_true), key=lambda item: item[0], reverse=True)
    positives = sum(y_true)
    if positives == 0:
        return 0.0

    hit_count = 0
    precision_sum = 0.0
    for rank, (_, label) in enumerate(ranked, start=1):
        if label:
            hit_count += 1
            precision_sum += hit_count / rank
    return precision_sum / positives


def evaluate_rows(model, rows):
    if not rows:
        return {
            "candidate_f1": 0.0,
            "candidate_precision": 0.0,
            "candidate_recall": 0.0,
            "candidate_accuracy": 0.0,
            "query_top1": 0.0,
            "average_precision": 0.0,
            "scores": [],
        }

    labels = [int(row["label"]) for row in rows]
    probabilities = [probability[1] if isinstance(probability, (list, tuple)) else float(probability) for probability in model.predict_proba(rows)]
    predictions = [1 if probability >= 0.5 else 0 for probability in probabilities]

    true_positive = sum(1 for label, prediction in zip(labels, predictions) if label == 1 and prediction == 1)
    false_positive = sum(1 for label, prediction in zip(labels, predictions) if label == 0 and prediction == 1)
    false_negative = sum(1 for label, prediction in zip(labels, predictions) if label == 1 and prediction == 0)
    true_negative = sum(1 for label, prediction in zip(labels, predictions) if label == 0 and prediction == 0)

    precision = true_positive / (true_positive + false_positive) if (true_positive + false_positive) else 0.0
    recall = true_positive / (true_positive + false_negative) if (true_positive + false_negative) else 0.0
    f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
    accuracy = (true_positive + true_negative) / len(rows)
    query_top1 = heuristic_top1_accuracy(rows, probabilities)
    ap = average_precision(labels, probabilities)

    return {
        "candidate_f1": f1,
        "candidate_precision": precision,
        "candidate_recall": recall,
        "candidate_accuracy": accuracy,
        "query_top1": query_top1,
        "average_precision": ap,
        "scores": probabilities,
    }


def grid_product(grid):
    keys = list(grid.keys())
    values = [grid[key] for key in keys]
    for combination in itertools.product(*values):
        yield dict(zip(keys, combination))


def train_validation_test(rows, seed):
    train_ids, valid_ids, test_ids = split_queries(rows, seed)
    return filter_rows(rows, train_ids), filter_rows(rows, valid_ids), filter_rows(rows, test_ids)


def model_candidates(seed):
    return [
        {
            "name": "heuristic_ranker",
            "factory": lambda params: HeuristicRanker(**params),
            "param_grid": {
                "distance_weight": [0.8, 1.0, 1.2],
                "fame_weight": [0.1, 0.15, 0.2],
                "angle_weight": [0.8, 1.0],
                "azimuth_weight": [0.1, 0.2],
            },
            "needs_scaler": False,
        },
        {
            "name": "linear_logistic",
            "factory": lambda params: LinearRanker(loss="logistic", seed=seed, **params),
            "param_grid": {
                "learning_rate": [0.02, 0.05],
                "epochs": [8, 12],
                "l2": [0.0, 0.001],
            },
            "needs_scaler": True,
        },
        {
            "name": "linear_hinge",
            "factory": lambda params: LinearRanker(loss="hinge", seed=seed, **params),
            "param_grid": {
                "learning_rate": [0.01, 0.03],
                "epochs": [8, 12],
                "l2": [0.0, 0.001],
            },
            "needs_scaler": True,
        },
    ]


def tune_and_evaluate(train_rows, valid_rows, test_rows, seed):
    results = []
    selected_bundle = None
    selected_result = None

    for spec in model_candidates(seed):
        best_validation = None
        best_model = None
        best_params = None

        for params in grid_product(spec["param_grid"]):
            model = spec["factory"](params)
            if spec["needs_scaler"]:
                model.fit(train_rows, [int(row["label"]) for row in train_rows])
            else:
                model.fit(train_rows, [int(row["label"]) for row in train_rows])

            validation_metrics = evaluate_rows(model, valid_rows)
            current_score = (validation_metrics["query_top1"], validation_metrics["candidate_f1"])
            if best_validation is None or current_score > best_validation:
                best_validation = current_score
                best_model = model
                best_params = params
                best_validation_metrics = validation_metrics

        if best_model is None:
            continue

        test_metrics = evaluate_rows(best_model, test_rows)
        result = RunResult(
            name=spec["name"],
            best_params=best_params,
            validation_candidate_f1=best_validation_metrics["candidate_f1"],
            validation_candidate_precision=best_validation_metrics["candidate_precision"],
            validation_candidate_recall=best_validation_metrics["candidate_recall"],
            validation_candidate_accuracy=best_validation_metrics["candidate_accuracy"],
            validation_query_top1=best_validation_metrics["query_top1"],
            validation_average_precision=best_validation_metrics["average_precision"],
            test_candidate_f1=test_metrics["candidate_f1"],
            test_candidate_precision=test_metrics["candidate_precision"],
            test_candidate_recall=test_metrics["candidate_recall"],
            test_candidate_accuracy=test_metrics["candidate_accuracy"],
            test_query_top1=test_metrics["query_top1"],
            test_average_precision=test_metrics["average_precision"],
        )
        results.append((result, best_model))

    if not results:
        raise RuntimeError("No se pudo entrenar ningún modelo.")

    results.sort(key=lambda item: (item[0].test_query_top1, item[0].test_candidate_f1), reverse=True)
    selected_result, selected_model = results[0]
    return results, selected_result, selected_model


def retrain_selected_model(selected_name, selected_params, train_rows, valid_rows, seed):
    combined_rows = train_rows + valid_rows
    spec = next(item for item in model_candidates(seed) if item["name"] == selected_name)
    model = spec["factory"](selected_params)
    model.fit(combined_rows, [int(row["label"]) for row in combined_rows])
    return model


def export_artifacts(selected_result, selected_model, all_results, dataset_stats, seed):
    os.makedirs(ARTIFACT_DIR, exist_ok=True)

    bundle = {
        "name": selected_result.name,
        "best_params": selected_result.best_params,
        "model": selected_model,
        "feature_columns": NUMERIC_FEATURES,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "seed": seed,
    }

    with open(BUNDLE_PATH, "wb") as file:
        pickle.dump(bundle, file)

    with open(COMPARISON_PATH, "w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow([
            "name",
            "best_params",
            "validation_candidate_f1",
            "validation_candidate_precision",
            "validation_candidate_recall",
            "validation_candidate_accuracy",
            "validation_query_top1",
            "validation_average_precision",
            "test_candidate_f1",
            "test_candidate_precision",
            "test_candidate_recall",
            "test_candidate_accuracy",
            "test_query_top1",
            "test_average_precision",
        ])
        for result in all_results:
            writer.writerow([
                result.name,
                json.dumps(result.best_params, ensure_ascii=False),
                result.validation_candidate_f1,
                result.validation_candidate_precision,
                result.validation_candidate_recall,
                result.validation_candidate_accuracy,
                result.validation_query_top1,
                result.validation_average_precision,
                result.test_candidate_f1,
                result.test_candidate_precision,
                result.test_candidate_recall,
                result.test_candidate_accuracy,
                result.test_query_top1,
                result.test_average_precision,
            ])

    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "seed": seed,
        "dataset": dataset_stats,
        "selected_model": asdict(selected_result),
        "feature_columns": NUMERIC_FEATURES,
        "artifacts": {
            "bundle": BUNDLE_PATH,
            "comparison_csv": COMPARISON_PATH,
            "summary_json": SUMMARY_PATH,
            "results_json": RESULTS_PATH,
        },
        "comparison": [asdict(result) for result in all_results],
    }

    with open(SUMMARY_PATH, "w", encoding="utf-8") as file:
        json.dump(summary, file, ensure_ascii=False, indent=2)

    with open(RESULTS_PATH, "w", encoding="utf-8") as file:
        json.dump(summary, file, ensure_ascii=False, indent=2)

    return summary


def main():
    parser = argparse.ArgumentParser(description="Entrena y exporta modelos para LandmarkLens.")
    parser.add_argument("--max-targets", type=int, default=400, help="Número máximo de landmarks de anclaje.")
    parser.add_argument("--samples-per-target", type=int, default=4, help="Queries sintéticas por landmark.")
    parser.add_argument("--max-candidates", type=int, default=8, help="Número máximo de candidatos por query.")
    parser.add_argument("--max-dist", type=float, default=500.0, help="Radio máximo de búsqueda en metros.")
    parser.add_argument("--seed", type=int, default=42, help="Semilla aleatoria.")
    args = parser.parse_args()

    with open(LANDMARKS_PATH, "r", encoding="utf-8") as file:
        landmarks_data = json.load(file)
    landmarks = landmarks_data.get("landmarks", [])
    rows, dataset_stats = build_training_rows(
        landmarks,
        max_targets=args.max_targets,
        samples_per_target=args.samples_per_target,
        max_candidates=args.max_candidates,
        max_dist=args.max_dist,
        seed=args.seed,
    )

    if not rows:
        raise RuntimeError("No se pudieron generar ejemplos de entrenamiento.")

    train_rows, valid_rows, test_rows = train_validation_test(rows, seed=args.seed)
    if not train_rows or not valid_rows or not test_rows:
        raise RuntimeError("La partición train/validation/test no es válida.")

    all_results_with_models, selected_result, selected_model = tune_and_evaluate(
        train_rows,
        valid_rows,
        test_rows,
        seed=args.seed,
    )

    # Reentrenar el modelo ganador con train + validation para exportarlo.
    selected_model = retrain_selected_model(
        selected_result.name,
        selected_result.best_params,
        train_rows,
        valid_rows,
        seed=args.seed,
    )

    summary = export_artifacts(
        selected_result=selected_result,
        selected_model=selected_model,
        all_results=[item[0] for item in all_results_with_models],
        dataset_stats=dataset_stats,
        seed=args.seed,
    )

    print("\n✅ Experimento completado")
    print(f"   Modelo seleccionado: {summary['selected_model']['name']}")
    print(f"   Bundle exportado: {BUNDLE_PATH}")
    print(f"   Comparación: {COMPARISON_PATH}")
    print(f"   Resumen: {SUMMARY_PATH}")


if __name__ == "__main__":
    main()