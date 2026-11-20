"""Canonical error envelope and exception handlers (docs/API_DESIGN.md §3).

All non-2xx responses share one shape:
{"error": {"code", "message", "details", "correlationId", "timestamp"}}
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.responses import JSONResponse

from app.context import get_correlation_id

logger = logging.getLogger("app.errors")


class ErrorDetail(BaseModel):
    field: str | None = None
    issue: str


class ErrorBody(BaseModel):
    code: str
    message: str
    details: list[ErrorDetail] = Field(default_factory=list)
    correlationId: str | None = None
    timestamp: str


class ErrorResponse(BaseModel):
    error: ErrorBody


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def build_envelope(
    code: str,
    message: str,
    details: list[ErrorDetail] | None = None,
) -> dict[str, Any]:
    """Build the wire-format error envelope as a plain dict."""
    return ErrorResponse(
        error=ErrorBody(
            code=code,
            message=message,
            details=details or [],
            correlationId=get_correlation_id() or None,
            timestamp=_utc_now_iso(),
        )
    ).model_dump()


# HTTP status -> stable machine code (docs/API_DESIGN.md §3.1).
_STATUS_CODES: dict[int, str] = {
    400: "VALIDATION_FAILED",
    401: "UNAUTHENTICATED",
    403: "FORBIDDEN",
    404: "RESOURCE_NOT_FOUND",
    405: "VALIDATION_FAILED",
    409: "CONFLICT",
    413: "PAYLOAD_TOO_LARGE",
    422: "SEMANTIC_REJECTED",
    429: "RATE_LIMITED",
    500: "INTERNAL_ERROR",
    502: "UPSTREAM_BAD_RESPONSE",
    503: "SERVICE_UNAVAILABLE",
    504: "UPSTREAM_TIMEOUT",
}

_DEFAULT_MESSAGES: dict[int, str] = {
    400: "Request validation failed",
    401: "Authentication required",
    403: "Access denied",
    404: "Resource not found",
    405: "HTTP method not supported for this endpoint",
    409: "Conflict",
    413: "Payload too large",
    422: "Request rejected by business rule",
    429: "Too many requests",
}


def code_for_status(status: int) -> str:
    if status in _STATUS_CODES:
        return _STATUS_CODES[status]
    return "INTERNAL_ERROR" if status >= 500 else "VALIDATION_FAILED"


async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    details = [
        ErrorDetail(
            field=_field_from_loc(err.get("loc", ())),
            issue=err.get("msg", "Invalid value"),
        )
        for err in exc.errors()
    ]
    content = build_envelope(
        code="VALIDATION_FAILED",
        message="Request validation failed",
        details=details,
    )
    return JSONResponse(status_code=400, content=content)


async def http_exception_handler(
    request: Request, exc: StarletteHTTPException
) -> JSONResponse:
    status = exc.status_code
    code = code_for_status(status)
    message = exc.detail if isinstance(exc.detail, str) and exc.detail else (
        _DEFAULT_MESSAGES.get(status, "Request failed")
    )
    content = build_envelope(code=code, message=message)
    return JSONResponse(
        status_code=status,
        content=content,
        headers=getattr(exc, "headers", None),
    )


def _field_from_loc(loc: tuple[Any, ...]) -> str:
    parts = [str(p) for p in loc if p not in ("body", "query", "path")]
    return ".".join(parts) if parts else "request"


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(RequestValidationError, validation_exception_handler)
    app.add_exception_handler(StarletteHTTPException, http_exception_handler)
