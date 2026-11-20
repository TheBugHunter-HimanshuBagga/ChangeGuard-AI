"""Interface for AI test-generation providers (future phases).

Implementations synthesize JUnit 5 / Pytest cases for changed units.
Generated tests must pass ``TestValidationRequest`` sanity checks before
the control plane schedules isolated execution.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.schemas.tests import TestGenerationRequest, TestGenerationResult


class TestGenerator(ABC):
    """Contract for a test-generation provider."""

    @property
    @abstractmethod
    def provider_version(self) -> str:
        """Version identifier of the generation pipeline (for audit trails)."""

    @abstractmethod
    def generate(self, request: TestGenerationRequest) -> TestGenerationResult:
        """Generate test cases for the requested units.

        Implementations must:
        - emit only language/framework-appropriate code for ``request.language``;
        - set ``origin=AI_GENERATED`` on produced tests;
        - leave ``validationStatus=PENDING`` (validation happens separately).
        """
        raise NotImplementedError
