package com.changeguard.controlplane.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowStatusTest {

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (WorkflowStatus terminal : new WorkflowStatus[]{
                WorkflowStatus.PASS, WorkflowStatus.FAIL,
                WorkflowStatus.INCONCLUSIVE, WorkflowStatus.CANCELLED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (WorkflowStatus next : WorkflowStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s must be illegal", terminal, next)
                        .isFalse();
            }
        }
    }

    @Test
    void pendingTransitions() {
        assertThat(WorkflowStatus.PENDING.canTransitionTo(WorkflowStatus.RUNNING)).isTrue();
        assertThat(WorkflowStatus.PENDING.canTransitionTo(WorkflowStatus.CANCELLED)).isTrue();
        assertThat(WorkflowStatus.PENDING.canTransitionTo(WorkflowStatus.PASS)).isFalse();
        assertThat(WorkflowStatus.PENDING.canTransitionTo(WorkflowStatus.FAIL)).isFalse();
        assertThat(WorkflowStatus.PENDING.canTransitionTo(WorkflowStatus.INCONCLUSIVE)).isFalse();
        assertThat(WorkflowStatus.PENDING.canTransitionTo(WorkflowStatus.PENDING)).isFalse();
    }

    @Test
    void runningTransitions() {
        assertThat(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.PASS)).isTrue();
        assertThat(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.FAIL)).isTrue();
        assertThat(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.INCONCLUSIVE)).isTrue();
        assertThat(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.CANCELLED)).isTrue();
        assertThat(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.PENDING)).isFalse();
        assertThat(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.RUNNING)).isFalse();
    }

    @Test
    void incompleteExecutionIsNeverPass() {
        // Guards the Phase 0 invariant: only RUNNING may reach PASS, never PENDING.
        assertThat(WorkflowStatus.PENDING.canTransitionTo(WorkflowStatus.PASS)).isFalse();
        assertThat(WorkflowStatus.CANCELLED.canTransitionTo(WorkflowStatus.PASS)).isFalse();
    }

    @Test
    void idempotencyKeyShape() {
        java.util.UUID repoId = java.util.UUID.randomUUID();
        String key = WorkflowService.buildIdempotencyKey(repoId, 42L,
                "a".repeat(40), "pull_request.synchronize");
        assertThat(key).isEqualTo(repoId + ":42:" + "a".repeat(40) + ":pull_request.synchronize");
        assertThat(key.length()).isLessThanOrEqualTo(255);

        String noPr = WorkflowService.buildIdempotencyKey(repoId, null,
                "b".repeat(40), "push");
        assertThat(noPr).startsWith(repoId + ":-:");
    }
}
