"""FastAPI application factory for the ChangeGuard AI intelligence plane."""

from __future__ import annotations

import logging

from fastapi import FastAPI

from app.api.v1.router import v1_router
from app.config import get_settings
from app.correlation import CorrelationMiddleware
from app.errors import register_exception_handlers
from app.logging_setup import configure_logging

logger = logging.getLogger("app.main")


def create_app() -> FastAPI:
    """Build the application: logging, correlation, errors, versioned routes."""
    settings = get_settings()
    configure_logging(settings.log_level)

    app = FastAPI(
        title="ChangeGuard AI — Intelligence Plane",
        description=(
            "Source-code analysis, defect-risk prediction, test generation "
            "and failure analysis for ChangeGuard AI (docs/API_DESIGN.md §6). "
            "This service never accesses the application database."
        ),
        version=settings.service_version,
        docs_url="/swagger-ui",
        openapi_url="/v3/api-docs",
    )

    app.add_middleware(CorrelationMiddleware, api_version=settings.api_version)
    register_exception_handlers(app)
    app.include_router(v1_router, prefix="/api/v1")

    logger.info(
        "application_started",
        extra={"service": settings.service_name, "version": settings.service_version},
    )
    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        app,  # already constructed above; avoid re-importing the module
        host=settings.host,
        port=settings.port,
        log_config=None,  # structured JSON logging is configured by create_app()
    )
