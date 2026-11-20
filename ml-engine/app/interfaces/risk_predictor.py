"""Interface for defect-risk prediction models (future phases).

Implementations must expose an explicit model version: every prediction
persisted by the control plane records modelVersion (architecture rule 9).
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.schemas.analysis import RiskPredictionRequest, RiskPredictionResult


class RiskPredictor(ABC):
    """Contract for a defect-risk prediction model provider."""

    @property
    @abstractmethod
    def model_version(self) -> str:
        """Stable version identifier of the loaded model (never empty)."""

    @abstractmethod
    def predict(self, request: RiskPredictionRequest) -> RiskPredictionResult:
        """Score the given analysis units for defect risk.

        Implementations must:
        - return scores normalized to [0.0, 1.0];
        - stamp ``model_version`` on the result;
        - attach explainability when available.
        A risk score alone never determines merge eligibility
        (docs/ARCHITECTURE.md §6.2).
        """
        raise NotImplementedError
