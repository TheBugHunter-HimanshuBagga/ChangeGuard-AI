"""Correlation ID propagation tests (docs/API_DESIGN.md §2)."""

from __future__ import annotations

import uuid

from fastapi.testclient import TestClient

CORRELATION_HEADER = "X-Correlation-ID"


def test_inbound_correlation_id_is_echoed(client: TestClient) -> None:
    correlation_id = str(uuid.uuid4())
    response = client.get("/api/v1/health", headers={CORRELATION_HEADER: correlation_id})
    assert response.headers[CORRELATION_HEADER] == correlation_id


def test_missing_correlation_id_is_generated(client: TestClient) -> None:
    response = client.get("/api/v1/health")
    value = response.headers[CORRELATION_HEADER]
    # Must be a valid UUID v4-shaped string.
    uuid.UUID(value)


def test_invalid_correlation_id_is_replaced(client: TestClient) -> None:
    response = client.get(
        "/api/v1/health", headers={CORRELATION_HEADER: "not-a-uuid"}
    )
    value = response.headers[CORRELATION_HEADER]
    assert value != "not-a-uuid"
    uuid.UUID(value)


def test_correlation_header_present_on_error_responses(client: TestClient) -> None:
    correlation_id = str(uuid.uuid4())
    response = client.get(
        "/api/v1/does-not-exist", headers={CORRELATION_HEADER: correlation_id}
    )
    assert response.status_code == 404
    assert response.headers[CORRELATION_HEADER] == correlation_id
    # Envelope echoes the same id.
    assert response.json()["error"]["correlationId"] == correlation_id
