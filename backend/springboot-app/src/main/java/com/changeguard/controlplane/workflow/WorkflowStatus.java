package com.changeguard.controlplane.workflow;

import java.util.Set;

/**
 * Canonical workflow/job status (docs/API_DESIGN.md §4).
 * Terminal states have no outgoing transitions. An incomplete execution is never PASS.
 */
public enum WorkflowStatus {
    PENDING,
    RUNNING,
    PASS,
    FAIL,
    INCONCLUSIVE,
    CANCELLED;

    private static final Set<WorkflowStatus> TERMINAL =
            Set.of(PASS, FAIL, INCONCLUSIVE, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * State machine from docs/DATABASE_DESIGN.md §5:
     * PENDING → RUNNING | CANCELLED; RUNNING → PASS | FAIL | INCONCLUSIVE | CANCELLED.
     */
    public boolean canTransitionTo(WorkflowStatus next) {
        if (isTerminal() || next == null || next == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == RUNNING || next == CANCELLED;
            case RUNNING -> next == PASS || next == FAIL || next == INCONCLUSIVE || next == CANCELLED;
            default -> false;
        };
    }
}
