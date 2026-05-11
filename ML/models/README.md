# Artefactos de modelo

Esta carpeta almacena los primeros artefactos de modelo entrenado/configurado.

## Modelo actual

- `landmark-finder-e4/`
  - `Modelfile`
  - `training_config.json`
  - `model_build_result.json`

El runtime de la aplicación usa el bundle exportado en `landmark_model/artifacts/selected_model_bundle.joblib` y el modelo de Ollama creado desde `landmark_model/Modelfile`.

Para construir el modelo localmente con Ollama:

```bash
python ML/scripts/train_model.py --create-model
```
