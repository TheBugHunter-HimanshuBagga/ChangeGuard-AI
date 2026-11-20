"""Shared fixtures for ml-engine tests."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture()
def client() -> TestClient:
    """A fresh application instance per test."""
    app = create_app()
    with TestClient(app) as test_client:
        yield test_client
