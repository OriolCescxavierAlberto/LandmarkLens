# Módulo de ML

Esta carpeta contiene todos los recursos de Machine Learning para LandmarkLens:

- `data/`: archivos del conjunto de datos y particiones procesadas.
- `scripts/`: scripts de preparación de datos, entrenamiento, evaluación y flujo reproducible.
- `experiments/`: salidas de experimentos y cuadernos de análisis.
- `models/`: primeros artefactos de modelo y configuraciones de entrenamiento.
- `ML_EXPERIMENTS.md`: informe reproducible de experimentación ML.

Scripts principales:

- `ML/scripts/prepare_data.py`
- `ML/scripts/train_model.py`
- `ML/scripts/evaluate_model.py`
- `ML/scripts/evaluate_online_ollama.py`
- `ML/scripts/pipeline.py`

## Inicio rápido

```bash
python ML/scripts/pipeline.py
```

## Estructura de carpetas

```text
ML/
├── data/
├── experiments/
├── models/
├── scripts/
└── ML_EXPERIMENTS.md
```
