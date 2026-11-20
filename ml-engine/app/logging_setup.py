"""Structured JSON logging with correlation id (docs/DEVELOPMENT_GUIDE.md §3)."""

from __future__ import annotations

import json
import logging
import sys
from datetime import datetime, timezone
from typing import Any

from app.context import get_correlation_id

# Attributes present on every LogRecord; excluded from JSON "extra" dumps.
_STANDARD_RECORD_ATTRS = {
    "name",
    "msg",
    "args",
    "levelname",
    "levelno",
    "pathname",
    "filename",
    "module",
    "exc_info",
    "exc_text",
    "stack_info",
    "lineno",
    "funcName",
    "created",
    "msecs",
    "relativeCreated",
    "thread",
    "threadName",
    "process",
    "processName",
    "taskName",
    "message",
    "asctime",
}


class JsonFormatter(logging.Formatter):
    """One JSON object per log line; includes the active correlation id."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc)
            .isoformat()
            .replace("+00:00", "Z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "correlationId": get_correlation_id() or None,
        }
        for key, value in record.__dict__.items():
            if key not in _STANDARD_RECORD_ATTRS and key not in payload and not key.startswith("_"):
                payload[key] = value
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str, ensure_ascii=False)


def configure_logging(level: str = "INFO") -> None:
    """Install the JSON formatter on the root logger (idempotent)."""
    root = logging.getLogger()
    root.setLevel(level.upper())

    existing = next(
        (h for h in root.handlers if getattr(h, "_changeguard_json", False)),
        None,
    )
    if existing is None:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(JsonFormatter())
        handler._changeguard_json = True  # type: ignore[attr-defined]
        root.addHandler(handler)

    # The middleware emits structured request logs; suppress uvicorn's plain ones.
    access_log = logging.getLogger("uvicorn.access")
    access_log.handlers = []
    access_log.propagate = False
