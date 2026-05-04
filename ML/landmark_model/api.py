from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel
from typing import Optional
import uvicorn
import query_model

from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Load landmarks and initialize the index
    query_model.load_landmarks()
    if not query_model.check_ollama():
        print("Warning: Ollama is not running or the model is missing.")
    yield

app = FastAPI(title="LandmarkLens ML API", description="API to query the LandmarkLens model", lifespan=lifespan)

@app.get("/")
def read_root():
    return {"message": "Welcome to LandmarkLens ML API. Visit /docs for documentation."}

@app.get("/favicon.ico", include_in_schema=False)
def favicon():
    return Response(status_code=204)

class QueryRequest(BaseModel):
    lat: float
    lon: float
    azimuth: Optional[float] = None
    fov: float = query_model.DEFAULT_FOV

@app.post("/api/v1/query")
def query_landmarks(request: QueryRequest):
    try:
        # We can either use query_model.query or just find_nearby
        # query_model.query streams to stdout but also returns the string.
        # Let's call it and parse the response if it returns a string.
        response = query_model.query(request.lat, request.lon, request.azimuth, request.fov)
        
        if response is None:
            raise HTTPException(status_code=500, detail="Failed to query the model or invalid response.")
        
        parsed_response = query_model._validate_response(response)
        
        if parsed_response is not None:
            return {"status": "success", "data": parsed_response}
        else:
            # If parsing fails, just return the raw string
            return {"status": "success", "raw_response": response}
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/v1/health")
def health_check():
    ollama_ok = query_model.check_ollama()
    return {"status": "ok" if ollama_ok else "degraded", "ollama_connected": ollama_ok}

if __name__ == "__main__":
    uvicorn.run("api:app", host="0.0.0.0", port=8000, reload=True)
