"""Canonical error envelope tests (docs/API_DESIGN.md §3)."""

from __future__ import annotations

import uuid

from fastapi.testclient import TestClient


def _assert_envelope(body: dict, expected_code: str) -> None:
    assert "error" in body
    error = body["error"]
    assert error["code"] == expected_code
    assert isinstance(error["message"], str) and error["message"]
    assert isinstance(error["details"], list)
    assert error["correlationId"]
    # ISO-8601 UTC timestamp
    assert error["timestamp"].endswith("Z")
    uuid.UUID(error["correlationId"])


def test_unknown_path_returns_resource_not_found(client: TestClient) -> None:
    response = client.get("/api/v1/no-such-endpoint")
    assert response.status_code == 404
    _assert_envelope(response.json(), "RESOURCE_NOT_FOUND")


def test_method_not_allowed_returns_envelope(client: TestClient) -> None:
    response = client.post("/api/v1/health")
    assert response.status_code == 405
    _assert_envelope(response.json(), "VALIDATION_FAILED")


def test_error_responses_have_api_version_header(client: TestClient) -> None:
    response = client.get("/api/v1/no-such-endpoint")
    assert response.headers["X-API-Version"] == "v1"


def test_envelope_builder_uses_active_correlation_id() -> None:
    from app.context import reset_correlation_id, set_correlation_id
    from app.errors import build_envelope

    token = set_correlation_id("11111111-2222-4333-8444-555555555555")
    try:
        envelope = build_envelope(code="CONFLICT", message="boom")
    finally:
        reset_correlation_id(token)
    assert envelope["error"]["code"] == "CONFLICT"
    assert envelope["error"]["correlationId"] == "11111111-2222-4333-8444-555555555555"


def test_status_to_code_mapping() -> None:
    from app.errors import code_for_status

    assert code_for_status(400) == "VALIDATION_FAILED"
    assert code_for_status(401) == "UNAUTHENTICATED"
    assert code_for_status(403) == "FORBIDDEN"
    assert code_for_status(404) == "RESOURCE_NOT_FOUND"
    assert code_for_status(409) == "CONFLICT"
    assert code_for_status(422) == "SEMANTIC_REJECTED"
    assert code_for_status(500) == "INTERNAL_ERROR"
    assert code_for_status(418) == "VALIDATION_FAILED"  # unmapped client error
    assert code_for_status(599) == "INTERNAL_ERROR"  # unmapped server error
