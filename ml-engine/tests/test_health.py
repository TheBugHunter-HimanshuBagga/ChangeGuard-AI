"""Health and readiness endpoint tests."""

from __future__ import annotations

from fastapi.testclient import TestClient


def test_health_returns_up_with_empty_model_list(client: TestClient) -> None:
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == "changeguard-ml-engine"
    assert body["version"] == "0.1.0"
    assert body["apiVersion"] == "v1"
    # Phase 2 loads no models — the list must be honestly empty.
    assert body["models"] == []


def test_health_sets_api_version_and_correlation_headers(client: TestClient) -> None:
    response = client.get("/api/v1/health")
    assert response.headers["X-API-Version"] == "v1"
    assert response.headers["X-Correlation-ID"]


def test_ready_returns_ready_without_verbose(client: TestClient) -> None:
    response = client.get("/api/v1/ready")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "READY"
    assert body["checks"]["configuration"] == "OK"
    assert body.get("details") is None


def test_ready_verbose_includes_details(client: TestClient) -> None:
    response = client.get("/api/v1/ready?verbose=true")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "READY"
    assert body["details"]["pythonVersion"].startswith("3.12")
    assert body["details"]["serviceVersion"] == "0.1.0"
