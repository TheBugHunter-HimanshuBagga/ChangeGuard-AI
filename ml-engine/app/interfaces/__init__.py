"""Provider interfaces for future ML models and test generation.

Phase 2 defines contracts only. Concrete implementations (trained models,
CodeBERT embeddings, test synthesis) are added in later phases and must be
registered via the application container — never faked here.
"""

from app.interfaces.failure_analyzer import FailureAnalyzer
from app.interfaces.risk_predictor import RiskPredictor
from app.interfaces.test_generator import TestGenerator

__all__ = ["FailureAnalyzer", "RiskPredictor", "TestGenerator"]
