"""Language-neutral code-analysis data contract (docs/API_DESIGN.md §8).

These schemas are the shared contract between the control plane and the
intelligence plane for Java and Python analysis. They define types only —
no model inference is performed in Phase 2.
"""

from __future__ import annotations

from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field


class Language(str, Enum):
    JAVA = "JAVA"
    PYTHON = "PYTHON"


class SymbolKind(str, Enum):
    CLASS = "CLASS"
    METHOD = "METHOD"
    FUNCTION = "FUNCTION"
    MODULE = "MODULE"
    FILE = "FILE"


class ChangeType(str, Enum):
    ADDED = "ADDED"
    MODIFIED = "MODIFIED"
    DELETED = "DELETED"
    RENAMED = "RENAMED"


class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class AnalysisUnit(BaseModel):
    """Stable, language-neutral identity of an analysed code unit."""

    language: Language
    path: str = Field(min_length=1, max_length=1024)
    unitId: str = Field(min_length=1, max_length=512)
    symbolName: str | None = Field(default=None, max_length=512)
    symbolKind: SymbolKind | None = None


class FeatureContribution(BaseModel):
    name: str
    value: float | None = None
    contribution: float | None = None


class Explainability(BaseModel):
    method: Literal["SHAP"] = "SHAP"
    features: list[FeatureContribution] = Field(default_factory=list)


class RiskPredictionRequest(BaseModel):
    """Input for defect-risk prediction (workflow-scoped, commit-pinned)."""

    workflowId: str = Field(min_length=1, max_length=64)
    commitSha: str = Field(pattern=r"^[0-9a-f]{40}$")
    units: list[AnalysisUnit] = Field(min_length=1)


class UnitRisk(BaseModel):
    unitId: str = Field(min_length=1, max_length=512)
    score: float = Field(ge=0.0, le=1.0)
    level: RiskLevel
    explainability: Explainability | None = None


class RiskPredictionResult(BaseModel):
    """Prediction output — modelVersion is mandatory, never omitted."""

    workflowId: str = Field(min_length=1, max_length=64)
    commitSha: str = Field(pattern=r"^[0-9a-f]{40}$")
    modelVersion: str = Field(min_length=1, max_length=128)
    predictions: list[UnitRisk]


class FailureCategory(str, Enum):
    ASSERTION = "ASSERTION"
    COMPILE = "COMPILE"
    DEPENDENCY = "DEPENDENCY"
    INFRA = "INFRA"
    FLAKY_SUSPECT = "FLAKY_SUSPECT"
    UNKNOWN = "UNKNOWN"


class FailureFinding(BaseModel):
    category: FailureCategory
    summary: str = Field(min_length=1, max_length=4000)
    suspectUnitIds: list[str] = Field(default_factory=list)
    confidence: float | None = Field(default=None, ge=0.0, le=1.0)


class FailureAnalysisRequest(BaseModel):
    workflowId: str = Field(min_length=1, max_length=64)
    jobId: str = Field(min_length=1, max_length=64)
    commitSha: str = Field(pattern=r"^[0-9a-f]{40}$")
    testReportRefs: list[str] = Field(default_factory=list, max_length=100)


class FailureAnalysisResult(BaseModel):
    workflowId: str = Field(min_length=1, max_length=64)
    jobId: str = Field(min_length=1, max_length=64)
    commitSha: str = Field(pattern=r"^[0-9a-f]{40}$")
    modelVersion: str | None = Field(default=None, max_length=128)
    findings: list[FailureFinding]
