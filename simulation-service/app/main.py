from fastapi import FastAPI
from pydantic import BaseModel, Field
from .solver import solve

app = FastAPI(title="PhysLive Simulation", version="1.0.0")

class SimulationRequest(BaseModel):
    schema_id: str = Field(min_length=1)
    parameters: dict[str, float] = {}
    duration_seconds: float = Field(default=5.0, ge=1, le=60)
    step_seconds: float = Field(default=0.05, ge=0.001, le=0.2)

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "simulation"}

@app.post("/api/v1/simulate")
def simulate(request: SimulationRequest):
    return solve(request.schema_id, request.parameters, request.duration_seconds, request.step_seconds)

@app.post("/api/v1/simulate/adjust")
def adjust(request: SimulationRequest):
    return solve(request.schema_id, request.parameters, request.duration_seconds, request.step_seconds)
