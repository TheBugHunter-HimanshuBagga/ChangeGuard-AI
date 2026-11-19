package com.changeguard.controlplane.workflow;

/**
 * Pipeline step keys (docs/DATABASE_DESIGN.md §3.7).
 * Steps are persisted by orchestration as the workflow progresses (later phases).
 */
public enum WorkflowStepKey {
    PARSE,
    CHANGE_DETECTION,
    DEPENDENCY_IMPACT,
    RISK_PREDICTION,
    TEST_SELECTION,
    TEST_GENERATION,
    TEST_VALIDATION,
    EXECUTION,
    COVERAGE,
    FAILURE_ANALYSIS,
    DECISION,
    PUBLISH
}
