"""Correlation-id context variable (no dependencies; safe to import anywhere)."""

from __future__ import annotations

import contextvars
import uuid

_correlation_id: contextvars.ContextVar[str] = contextvars.ContextVar(
    "correlation_id", default=""
)


def get_correlation_id() -> str:
    """Return the correlation id for the current context (may be empty)."""
    return _correlation_id.get()


def set_correlation_id(value: str) -> contextvars.Token:
    return _correlation_id.set(value)


def reset_correlation_id(token: contextvars.Token) -> None:
    _correlation_id.reset(token)


def resolve_correlation_id(incoming: str | None) -> str:
    """Use the inbound UUID when valid; otherwise mint a fresh one."""
    if incoming and incoming.strip():
        candidate = incoming.strip()
        try:
            return str(uuid.UUID(candidate))
        except ValueError:
            pass
    return str(uuid.uuid4())
