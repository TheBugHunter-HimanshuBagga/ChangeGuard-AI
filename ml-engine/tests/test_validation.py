"""API request-validation tests (Pydantic via FastAPI -> canonical envelope)."""

from __future__ import annotations

from fastapi.testclient import TestClient


def test_invalid_query_parameter_returns_validation_failed(client: TestClient) -> None:
    response = client.get("/api/v1/ready?verbose=not-a-bool")
    assert response.status_code == 400
    error = response.json()["error"]
    assert error["code"] == "VALIDATION_FAILED"
    assert error["message"] == "Request validation failed"
    assert len(error["details"]) >= 1
    assert error["details"][0]["field"] == "verbose"
    assert isinstance(error["details"][0]["issue"], str)
    assert error["correlationId"]
    assert error["timestamp"].endswith("Z")


def test_valid_boolean_query_parameter_accepted(client: TestClient) -> None:
    for value in ("true", "false", "True", "1", "0"):
        response = client.get(f"/api/v1/ready?verbose={value}")
        assert response.status_code == 200, f"verbose={value} should be accepted"


def test_validation_envelope_has_no_stack_traces(client: TestClient) -> None:
    response = client.get("/api/v1/ready?verbose=oops")
    raw = response.text
    assert "Traceback" not in raw
    assert "File \"" not in raw
