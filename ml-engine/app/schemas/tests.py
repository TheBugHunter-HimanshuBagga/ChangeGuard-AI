"""Test generation and validation data contract (docs/API_DESIGN.md §8.7)."""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field

from app.schemas.analysis import Language


class TestOrigin(str, Enum):
    SELECTED_EXISTING = "SELECTED_EXISTING"
    AI_GENERATED = "AI_GENERATED"


class TestFramework(str, Enum):
    JUNIT5 = "JUNIT5"
    PYTEST = "PYTEST"


class ValidationStatus(str, Enum):
    PENDING = "PENDING"
    VALID = "VALID"
    INVALID = "INVALID"
    SKIPPED = "SKIPPED"


class TestGenerationRequest(BaseModel):
    workflowId: str = Field(min_length=1, max_length=64)
    commitSha: str = Field(pattern=r"^[0-9a-f]{40}$")
    language: Language
    targetUnitIds: list[str] = Field(min_length=1, max_length=200)
    framework: TestFramework | None = None


class GeneratedTest(BaseModel):
    testId: str = Field(min_length=1, max_length=64)
    origin: TestOrigin = TestOrigin.AI_GENERATED
    language: Language
    targetUnitId: str | None = Field(default=None, max_length=512)
    title: str = Field(min_length=1, max_length=512)
    framework: TestFramework
    code: str = Field(min_length=1, max_length=200_000)
    sourcePath: str = Field(min_length=1, max_length=512)
    validationStatus: ValidationStatus = ValidationStatus.PENDING
    validationNotes: list[str] = Field(default_factory=list)


class TestGenerationResult(BaseModel):
    workflowId: str = Field(min_length=1, max_length=64)
    commitSha: str = Field(pattern=r"^[0-9a-f]{40}$")
    modelVersion: str | None = Field(default=None, max_length=128)
    tests: list[GeneratedTest]


class TestValidationRequest(BaseModel):
    workflowId: str = Field(min_length=1, max_length=64)
    tests: list[GeneratedTest] = Field(min_length=1, max_length=500)


class ValidatedTest(BaseModel):
    testId: str = Field(min_length=1, max_length=64)
    status: ValidationStatus
    notes: list[str] = Field(default_factory=list)


class TestValidationResult(BaseModel):
    workflowId: str = Field(min_length=1, max_length=64)
    results: list[ValidatedTest]
