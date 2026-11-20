"""Data-contract schema validation tests (docs/API_DESIGN.md §8)."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.analysis import (
    AnalysisUnit,
    Language,
    RiskLevel,
    RiskPredictionRequest,
    RiskPredictionResult,
    UnitRisk,
)
from app.schemas.tests import (
    GeneratedTest,
    TestFramework,
    TestGenerationRequest,
    TestOrigin,
    ValidationStatus,
)

SHA40 = "a" * 40


def _unit(unit_id: str = "JAVA:com.example.Foo") -> AnalysisUnit:
    return AnalysisUnit(
        language=Language.JAVA,
        path="src/main/java/com/example/Foo.java",
        unitId=unit_id,
    )


class TestRiskSchemas:
    def test_valid_risk_request(self) -> None:
        request = RiskPredictionRequest(
            workflowId="wf-1", commitSha=SHA40, units=[_unit()]
        )
        assert request.commitSha == SHA40
        assert request.units[0].language is Language.JAVA

    @pytest.mark.parametrize("bad_sha", ["", "xyz", "a" * 39, "A" * 40, SHA40 + "a"])
    def test_invalid_commit_sha_rejected(self, bad_sha: str) -> None:
        with pytest.raises(ValidationError):
            RiskPredictionRequest(
                workflowId="wf-1", commitSha=bad_sha, units=[_unit()]
            )

    def test_empty_units_rejected(self) -> None:
        with pytest.raises(ValidationError):
            RiskPredictionRequest(workflowId="wf-1", commitSha=SHA40, units=[])

    def test_result_requires_model_version(self) -> None:
        with pytest.raises(ValidationError):
            RiskPredictionResult(
                workflowId="wf-1",
                commitSha=SHA40,
                predictions=[
                    UnitRisk(unitId="u", score=0.5, level=RiskLevel.MEDIUM)
                ],
                # modelVersion intentionally omitted
            ).model_dump(exclude_unset=True)

    @pytest.mark.parametrize("score", [-0.01, 1.01])
    def test_score_out_of_bounds_rejected(self, score: float) -> None:
        with pytest.raises(ValidationError):
            UnitRisk(unitId="u", score=score, level=RiskLevel.LOW)

    @pytest.mark.parametrize("score", [0.0, 0.5, 1.0])
    def test_score_in_bounds_accepted(self, score: float) -> None:
        risk = UnitRisk(unitId="u", score=score, level=RiskLevel.LOW)
        assert risk.score == score

    def test_invalid_language_rejected(self) -> None:
        with pytest.raises(ValidationError):
            AnalysisUnit(
                language="RUST",  # type: ignore[arg-type]
                path="src/lib.rs",
                unitId="RUST:lib",
            )


class TestTestGenerationSchemas:
    def test_valid_generation_request(self) -> None:
        request = TestGenerationRequest(
            workflowId="wf-1",
            commitSha=SHA40,
            language=Language.PYTHON,
            targetUnitIds=["PYTHON:app.service"],
        )
        assert request.language is Language.PYTHON

    def test_generated_test_defaults_to_ai_generated_and_pending(self) -> None:
        test = GeneratedTest(
            testId="t-1",
            language=Language.PYTHON,
            title="test login",
            framework=TestFramework.PYTEST,
            code="def test_login():\n    assert True\n",
            sourcePath="tests/test_login.py",
        )
        assert test.origin is TestOrigin.AI_GENERATED
        assert test.validationStatus is ValidationStatus.PENDING

    def test_empty_code_rejected(self) -> None:
        with pytest.raises(ValidationError):
            GeneratedTest(
                testId="t-1",
                language=Language.JAVA,
                title="t",
                framework=TestFramework.JUNIT5,
                code="",
                sourcePath="src/test/T.java",
            )

    def test_invalid_framework_rejected(self) -> None:
        with pytest.raises(ValidationError):
            GeneratedTest(
                testId="t-1",
                language=Language.JAVA,
                title="t",
                framework="MOCKITO",  # type: ignore[arg-type]
                code="class T {}",
                sourcePath="src/test/T.java",
            )
