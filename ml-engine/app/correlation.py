"""Correlation ID propagation (docs/API_DESIGN.md §2).

Every request carries a UUID v4 correlation id (generated when absent or
invalid). It is stored in a context variable for structured logging and
echoed on the response together with the API version header.
"""

from __future__ import annotations

import logging
import time
from collections.abc import Awaitable, Callable

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.types import ASGIApp

from app.context import (
    get_correlation_id,
    reset_correlation_id,
    resolve_correlation_id,
    set_correlation_id,
)
from app.errors import build_envelope

CORRELATION_HEADER = "X-Correlation-ID"
API_VERSION_HEADER = "X-API-Version"

logger = logging.getLogger("app.correlation")


class CorrelationMiddleware(BaseHTTPMiddleware):
    """Assigns, propagates and echoes the correlation id; logs requests."""

    def __init__(self, app: ASGIApp, api_version: str = "v1") -> None:
        super().__init__(app)
        self.api_version = api_version

    async def dispatch(
        self,
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        correlation_id = resolve_correlation_id(request.headers.get(CORRELATION_HEADER))
        token = set_correlation_id(correlation_id)
        started = time.perf_counter()
        try:
            try:
                response = await call_next(request)
                status_code = response.status_code
            except Exception:
                logger.exception(
                    "unhandled_error",
                    extra={
                        "method": request.method,
                        "path": request.url.path,
                    },
                )
                response = JSONResponse(
                    status_code=500,
                    content=build_envelope(
                        code="INTERNAL_ERROR",
                        message="An unexpected error occurred",
                    ),
                )
                status_code = 500
            duration_ms = round((time.perf_counter() - started) * 1000, 1)
            logger.info(
                "request_completed",
                extra={
                    "method": request.method,
                    "path": request.url.path,
                    "statusCode": status_code,
                    "durationMs": duration_ms,
                },
            )
            response.headers[CORRELATION_HEADER] = correlation_id
            response.headers[API_VERSION_HEADER] = self.api_version
            return response
        finally:
            reset_correlation_id(token)


__all__ = [
    "CORRELATION_HEADER",
    "API_VERSION_HEADER",
    "CorrelationMiddleware",
    "get_correlation_id",
    "resolve_correlation_id",
]
