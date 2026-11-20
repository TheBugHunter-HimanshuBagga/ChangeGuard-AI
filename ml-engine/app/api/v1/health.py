"""Health and readiness endpoints (docs/API_DESIGN.md §6.1)."""

from __future__ import annotations

import platform
import sys

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.config import get_settings

router = APIRouter(tags=["Health"])


class HealthResponse(BaseModel):
    """Liveness payload with loaded model versions (none in Phase 2)."""

    status: str
    service: str
    version: str
    apiVersion: str
    models: list[str] = Field(default_factory=list)


class ReadyResponse(BaseModel):
    status: str
    checks: dict[str, str]
    details: dict[str, str] | None = None


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Liveness + loaded model versions",
)
def health() -> HealthResponse:
    settings = get_settings()
    return HealthResponse(
        status="UP",
        service=settings.service_name,
        version=settings.service_version,
        apiVersion=settings.api_version,
        models=[],  # no models loaded in Phase 2 — never reported as present
    )


@router.get(
    "/ready",
    response_model=ReadyResponse,
    summary="Readiness (no external dependencies in Phase 2)",
)
def ready(verbose: bool = False) -> ReadyResponse:
    """Readiness probe.

    Phase 2 has no database or model dependencies, so readiness reflects
    configuration only. Later phases add real dependency checks here.
    """
    settings = get_settings()
    response = ReadyResponse(
        status="READY",
        checks={"configuration": "OK"},
    )
    if verbose:
        response.details = {
            "pythonVersion": sys.version.split()[0],
            "platform": platform.platform(),
            "logLevel": settings.log_level.upper(),
            "serviceVersion": settings.service_version,
        }
    return response
