from typing import Any

from pydantic import BaseModel, Field


class ExtractTextRequest(BaseModel):
    text: str = Field(min_length=1)
    schema_id: str | None = None


class Quantity(BaseModel):
    name: str
    symbol: str | None = None
    value: float
    original_unit: str
    normalized_value: float
    normalized_unit: str
    confidence: float
    source_text: str


class Ambiguity(BaseModel):
    code: str
    field: str
    question: str
    options: list[str] = Field(default_factory=list)


class Specification(BaseModel):
    schema_version: str = "1.0"
    schema_id: str | None = None
    topic: str | None = None
    objects: list[dict[str, Any]] = Field(default_factory=list)
    quantities: list[Quantity] = Field(default_factory=list)
    relations: list[dict[str, Any]] = Field(default_factory=list)
    confidence: float = 0.0
    ambiguities: list[Ambiguity] = Field(default_factory=list)


class ExtractResponse(BaseModel):
    specification: Specification
    provider: str
    message: str | None = None
