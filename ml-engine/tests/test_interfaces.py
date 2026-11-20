"""Provider interface tests — contracts exist and cannot be faked casually."""

from __future__ import annotations

import pytest

from app.interfaces import FailureAnalyzer, RiskPredictor, TestGenerator
from app.schemas.analysis import (
    RiskPredictionRequest,
    RiskPredictionResult,
)
from app.schemas.tests import (
    TestGenerationRequest,
    TestGenerationResult,
    Language,
)

SHA40 = "b" * 40


class TestAbstractContracts:
    @pytest.mark.parametrize(
        "interface", [RiskPredictor, TestGenerator, FailureAnalyzer]
    )
    def test_interfaces_cannot_be_instantiated(self, interface: type) -> None:
        with pytest.raises(TypeError):
            interface()  # type: ignore[abstract]

    def test_risk_predictor_declares_contract(self) -> None:
        assert "model_version" in RiskPredictor.__abstractmethods__
        assert "predict" in RiskPredictor.__abstractmethods__

    def test_test_generator_declares_contract(self) -> None:
        assert "provider_version" in TestGenerator.__abstractmethods__
        assert "generate" in TestGenerator.__abstractmethods__

    def test_failure_analyzer_declares_contract(self) -> None:
        assert "model_version" in FailureAnalyzer.__abstractmethods__
        assert "analyze" in FailureAnalyzer.__abstractmethods__


class _StubRiskPredictor(RiskPredictor):
    """Minimal test double proving the contract is implementable."""

    @property
    def model_version(self) -> str:
        return "stub-0"

    def predict(self, request: RiskPredictionRequest) -> RiskPredictionResult:
        return RiskPredictionResult(
            workflowId=request.workflowId,
            commitSha=request.commitSha,
            modelVersion=self.model_version,
            predictions=[],
        )


class _StubTestGenerator(TestGenerator):
    @property
    def provider_version(self) -> str:
        return "stub-0"

    def generate(self, request: TestGenerationRequest) -> TestGenerationResult:
        return TestGenerationResult(
            workflowId=request.workflowId,
            commitSha=request.commitSha,
            tests=[],
        )


class TestContractImplementability:
    def test_risk_predictor_stub_round_trip(self) -> None:
        predictor = _StubRiskPredictor()
        assert isinstance(predictor, RiskPredictor)
        result = predictor.predict(
            RiskPredictionRequest(
                workflowId="wf-1", commitSha=SHA40,
                units=[{"language": "JAVA", "path": "A.java", "unitId": "JAVA:A"}],
            )
        )
        assert result.modelVersion == "stub-0"

    def test_test_generator_stub_round_trip(self) -> None:
        generator = _StubTestGenerator()
        result = generator.generate(
            TestGenerationRequest(
                workflowId="wf-1",
                commitSha=SHA40,
                language=Language.PYTHON,
                targetUnitIds=["PYTHON:app"],
            )
        )
        assert result.tests == []

    def test_failure_analyzer_requires_implementation(self) -> None:
        # No stub: the abstract method must be defined for subclasses.
        assert callable(FailureAnalyzer.analyze)
