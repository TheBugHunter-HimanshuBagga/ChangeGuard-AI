"""Interface for failure-analysis models (future phases).

Classifies test failures into stable categories so the control-plane
decision engine can cite evidence-backed findings.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.schemas.analysis import FailureAnalysisRequest, FailureAnalysisResult


class FailureAnalyzer(ABC):
    """Contract for a failure-analysis provider."""

    @property
    @abstractmethod
    def model_version(self) -> str:
        """Model/version identifier recorded on findings when ML is used."""

    @abstractmethod
    def analyze(self, request: FailureAnalysisRequest) -> FailureAnalysisResult:
        """Analyse execution failures for a workflow job.

        Implementations must set ``modelVersion`` whenever a model
        produced the classification (architecture rule 9).
        """
        raise NotImplementedError
