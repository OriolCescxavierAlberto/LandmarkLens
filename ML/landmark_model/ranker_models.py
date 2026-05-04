#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Shared lightweight ranking models for LandmarkLens."""

from __future__ import annotations

import math


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


def sigmoid(value):
    if value >= 0:
        z = math.exp(-value)
        return 1.0 / (1.0 + z)
    z = math.exp(value)
    return z / (1.0 + z)


class FeatureScaler:
    """Normaliza features numéricas usando media y desviación típica."""

    def __init__(self):
        self.means = {}
        self.stds = {}

    def fit(self, rows):
        if not rows:
            raise ValueError("No hay filas para ajustar el escalador.")

        for feature in NUMERIC_FEATURES:
            values = [float(row.get(feature, 0.0)) for row in rows]
            mean = sum(values) / len(values)
            variance = sum((value - mean) ** 2 for value in values) / max(1, len(values) - 1)
            std = math.sqrt(variance) if variance > 0 else 1.0
            self.means[feature] = mean
            self.stds[feature] = std
        return self

    def transform_row(self, row):
        return {
            feature: (float(row.get(feature, 0.0)) - self.means.get(feature, 0.0)) / self.stds.get(feature, 1.0)
            for feature in NUMERIC_FEATURES
        }

    def transform(self, rows):
        return [self.transform_row(row) for row in rows]


class HeuristicRanker:
    """Baseline manual para comparar el resto de modelos."""

    def __init__(self, distance_weight=1.0, fame_weight=0.15, angle_weight=1.0, azimuth_weight=0.2):
        self.distance_weight = distance_weight
        self.fame_weight = fame_weight
        self.angle_weight = angle_weight
        self.azimuth_weight = azimuth_weight

    def fit(self, rows, labels):
        return self

    def score_row(self, row):
        return (
            -(float(row.get("distance_m", 0.0)) / 120.0) * self.distance_weight
            + float(row.get("fame_score", 0.0)) * self.fame_weight
            - float(row.get("angle_offset_deg", 180.0)) / 180.0 * self.angle_weight
            + float(row.get("azimuth_present", 0.0)) * self.azimuth_weight
        )

    def decision_function(self, rows):
        return [self.score_row(row) for row in rows]

    def predict_proba(self, rows):
        return [[1.0 - sigmoid(score), sigmoid(score)] for score in self.decision_function(rows)]


class LinearRanker:
    """Modelo lineal entrenado con SGD."""

    def __init__(self, learning_rate=0.05, epochs=10, l2=0.0, loss="logistic", seed=42):
        self.learning_rate = learning_rate
        self.epochs = epochs
        self.l2 = l2
        self.loss = loss
        self.seed = seed
        self.weights = {feature: 0.0 for feature in NUMERIC_FEATURES}
        self.bias = 0.0
        self.scaler = None

    def fit(self, rows, labels):
        self.scaler = FeatureScaler().fit(rows)
        features = self.scaler.transform(rows)
        targets = [int(label) for label in labels]

        import random

        rng = random.Random(self.seed)
        indices = list(range(len(features)))
        for _ in range(self.epochs):
            rng.shuffle(indices)
            for index in indices:
                row = features[index]
                target = targets[index]
                score = self._score_scaled(row)

                if self.loss == "hinge":
                    signed_target = 1 if target == 1 else -1
                    margin = signed_target * score
                    if margin < 1.0:
                        for feature in NUMERIC_FEATURES:
                            self.weights[feature] += self.learning_rate * (
                                signed_target * row[feature] - self.l2 * self.weights[feature]
                            )
                        self.bias += self.learning_rate * signed_target
                    else:
                        for feature in NUMERIC_FEATURES:
                            self.weights[feature] -= self.learning_rate * self.l2 * self.weights[feature]
                else:
                    probability = sigmoid(score)
                    error = probability - target
                    for feature in NUMERIC_FEATURES:
                        self.weights[feature] -= self.learning_rate * (
                            error * row[feature] + self.l2 * self.weights[feature]
                        )
                    self.bias -= self.learning_rate * error

        return self

    def _score_scaled(self, scaled_row):
        return self.bias + sum(self.weights[feature] * scaled_row.get(feature, 0.0) for feature in NUMERIC_FEATURES)

    def score_row(self, row):
        scaled_row = self.scaler.transform_row(row)
        return self._score_scaled(scaled_row)

    def decision_function(self, rows):
        return [self.score_row(row) for row in rows]

    def predict_proba(self, rows):
        return [[1.0 - sigmoid(score), sigmoid(score)] for score in self.decision_function(rows)]