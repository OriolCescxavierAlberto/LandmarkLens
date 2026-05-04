#!/usr/bin/env python3
"""Export EDA figures from raw LandmarkLens dataset."""

from __future__ import annotations

import json
import re
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def parse_mentions(dataset_path: Path) -> pd.DataFrame:
    with dataset_path.open("r", encoding="utf-8") as f:
        raw_samples = json.load(f)

    item_re = re.compile(
        r"(?m)^(\d+)\.\s+\*\*(.+?)\*\*\s+—\s+a\s+([\d\.]+)\s+metros\s+hacia\s+([^\(\n]+)"
    )
    type_re = re.compile(r"(?m)^\s*Tipo:\s*(.+)$")

    records: list[dict] = []
    for sample_id, row in enumerate(raw_samples, start=1):
        response = row.get("response", "")
        matches = list(item_re.finditer(response))
        type_lines = type_re.findall(response)

        for idx, match in enumerate(matches):
            rank = int(match.group(1))
            name = match.group(2).strip()
            distance_m = float(match.group(3))
            direction = match.group(4).strip().lower()

            raw_type = type_lines[idx].strip() if idx < len(type_lines) else None
            type_parts = [p.strip() for p in raw_type.split(",")] if raw_type else []
            type_dict: dict[str, str] = {}
            for part in type_parts:
                if "=" in part:
                    key, value = part.split("=", 1)
                    type_dict[key.strip()] = value.strip()

            records.append(
                {
                    "sample_id": sample_id,
                    "rank": rank,
                    "building_name": name,
                    "distance_m": distance_m,
                    "direction": direction,
                    "raw_type": raw_type,
                    "has_historic": "historic" in type_dict,
                    "has_tourism": "tourism" in type_dict,
                    "has_amenity": "amenity" in type_dict,
                    "has_building": "building" in type_dict,
                    "has_leisure": "leisure" in type_dict,
                    "has_man_made": "man_made" in type_dict,
                    "is_untitled": name.lower() == "untitled",
                }
            )

    return pd.DataFrame(records)


def save_basic_eda(mentions_df: pd.DataFrame, output_dir: Path) -> None:
    fig, axes = plt.subplots(2, 2, figsize=(16, 12))

    name_counts = mentions_df["building_name"].value_counts().head(15).sort_values()
    axes[0, 0].barh(name_counts.index, name_counts.values, color="#2E86AB")
    axes[0, 0].set_title("Top 15 buildings/POIs by frequency")
    axes[0, 0].set_xlabel("Appearances")

    group_counts = pd.Series(
        {
            "tourism": int(mentions_df["has_tourism"].sum()),
            "historic": int(mentions_df["has_historic"].sum()),
            "building": int(mentions_df["has_building"].sum()),
            "amenity": int(mentions_df["has_amenity"].sum()),
            "leisure": int(mentions_df["has_leisure"].sum()),
            "man_made": int(mentions_df["has_man_made"].sum()),
        }
    )
    axes[0, 1].bar(group_counts.index, group_counts.values, color="#F18F01")
    axes[0, 1].set_title("Grouping by tag family")
    axes[0, 1].set_ylabel("Mentions")
    axes[0, 1].tick_params(axis="x", rotation=25)

    axes[1, 0].hist(mentions_df["distance_m"], bins=20, color="#7B2CBF", alpha=0.85)
    axes[1, 0].set_title("Distance distribution (meters)")
    axes[1, 0].set_xlabel("Distance (m)")
    axes[1, 0].set_ylabel("Frequency")

    completeness = pd.Series(
        {
            "raw_type": float(mentions_df["raw_type"].notna().mean()),
            "has_tourism": float(mentions_df["has_tourism"].mean()),
            "has_historic": float(mentions_df["has_historic"].mean()),
            "has_building": float(mentions_df["has_building"].mean()),
            "has_amenity": float(mentions_df["has_amenity"].mean()),
        }
    ).sort_values(ascending=True)
    axes[1, 1].barh(completeness.index, completeness.values * 100, color="#1B998B")
    axes[1, 1].set_xlim(0, 100)
    axes[1, 1].set_title("Attribute completeness (%)")
    axes[1, 1].set_xlabel("Percent of mentions")

    plt.tight_layout()
    fig.savefig(output_dir / "eda_basic_overview.png", dpi=160, bbox_inches="tight")
    plt.close(fig)


def save_advanced_eda(mentions_df: pd.DataFrame, output_dir: Path) -> None:
    tag_cols = [
        "has_historic",
        "has_tourism",
        "has_amenity",
        "has_building",
        "has_leisure",
        "has_man_made",
    ]

    tag_df = mentions_df[tag_cols].astype(int)
    cooc = tag_df.T @ tag_df

    fig, axes = plt.subplots(1, 2, figsize=(16, 6))

    im = axes[0].imshow(cooc.values, cmap="YlGnBu")
    axes[0].set_xticks(range(len(tag_cols)))
    axes[0].set_xticklabels([c.replace("has_", "") for c in tag_cols], rotation=30)
    axes[0].set_yticks(range(len(tag_cols)))
    axes[0].set_yticklabels([c.replace("has_", "") for c in tag_cols])
    axes[0].set_title("Tag family co-occurrence")

    for i in range(cooc.shape[0]):
        for j in range(cooc.shape[1]):
            axes[0].text(j, i, int(cooc.values[i, j]), ha="center", va="center", fontsize=9)

    fig.colorbar(im, ax=axes[0], fraction=0.045, pad=0.04)

    box_data = []
    box_labels = []
    for col in tag_cols:
        vals = mentions_df.loc[mentions_df[col], "distance_m"].dropna().values
        if len(vals) > 0:
            box_data.append(vals)
            box_labels.append(col.replace("has_", ""))

    axes[1].boxplot(box_data, tick_labels=box_labels, vert=True, showfliers=False)
    axes[1].set_title("Distance by tag family (no outliers)")
    axes[1].set_ylabel("Distance (m)")
    axes[1].tick_params(axis="x", rotation=25)
    axes[1].grid(axis="y", alpha=0.25)

    plt.tight_layout()
    fig.savefig(output_dir / "eda_advanced_cooccurrence_boxplot.png", dpi=160, bbox_inches="tight")
    plt.close(fig)


def save_long_tail(mentions_df: pd.DataFrame, output_dir: Path) -> None:
    sorted_counts = mentions_df["building_name"].value_counts().values
    cum_share_mentions = np.cumsum(sorted_counts) / np.sum(sorted_counts)
    cum_share_buildings = np.arange(1, len(sorted_counts) + 1) / len(sorted_counts)

    fig, ax = plt.subplots(figsize=(8, 6))
    ax.plot(cum_share_buildings * 100, cum_share_mentions * 100, color="#6A4C93", linewidth=2)
    ax.plot([0, 100], [0, 100], "--", color="gray", alpha=0.7)
    ax.set_title("Cumulative mention coverage by buildings")
    ax.set_xlabel("Cumulative % of buildings (sorted by frequency)")
    ax.set_ylabel("Cumulative % of mentions")
    ax.grid(alpha=0.25)

    idx_50 = int(np.argmax(cum_share_mentions >= 0.5))
    x50 = 100 * cum_share_buildings[idx_50]
    y50 = 100 * cum_share_mentions[idx_50]
    buildings_for_50 = idx_50 + 1
    pct_buildings_for_50 = 100 * buildings_for_50 / len(sorted_counts)

    ax.scatter([x50], [y50], color="crimson", zorder=3)
    ax.annotate(
        f"50% mentions with {buildings_for_50} buildings ({pct_buildings_for_50:.1f}%)",
        (x50, y50),
        textcoords="offset points",
        xytext=(10, -22),
    )

    plt.tight_layout()
    fig.savefig(output_dir / "eda_long_tail_coverage.png", dpi=160, bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    dataset_path = root / "ML" / "data" / "training_examples.json"
    figures_dir = root / "ML" / "experiments" / "figures"
    figures_dir.mkdir(parents=True, exist_ok=True)

    mentions_df = parse_mentions(dataset_path)
    save_basic_eda(mentions_df, figures_dir)
    save_advanced_eda(mentions_df, figures_dir)
    save_long_tail(mentions_df, figures_dir)

    print("Figures exported:")
    for name in [
        "eda_basic_overview.png",
        "eda_advanced_cooccurrence_boxplot.png",
        "eda_long_tail_coverage.png",
    ]:
        print(f"- {figures_dir / name}")


if __name__ == "__main__":
    main()
