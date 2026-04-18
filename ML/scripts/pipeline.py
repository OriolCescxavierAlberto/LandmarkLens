#!/usr/bin/env python3
"""Run the full ML pipeline for LandmarkLens experiments."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def run_step(cmd: list[str]) -> None:
    print("Running:", " ".join(cmd))
    subprocess.run(cmd, check=True)


def main() -> None:
    python = sys.executable

    run_step([python, str(ROOT / "ml/scripts/prepare_data.py")])
    run_step([python, str(ROOT / "ml/scripts/train_model.py")])
    run_step([python, str(ROOT / "ml/scripts/evaluate_model.py")])

    print("Pipeline finished successfully")


if __name__ == "__main__":
    main()
