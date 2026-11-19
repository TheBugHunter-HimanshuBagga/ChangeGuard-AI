package com.changeguard.controlplane.workflow.dto;

import com.changeguard.controlplane.repository.Language;
import com.changeguard.controlplane.workflow.WorkflowStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Workflow and job DTOs (docs/API_DESIGN.md §5.4).
 */
public final class WorkflowDtos {

    private WorkflowDtos() {
    }

    public static final String SHA_PATTERN = "^[0-9a-f]{40}$";

    public record CreateWorkflowRequest(
            @NotNull UUID repositoryId,
            @Positive Long pullRequestNumber,
            @NotBlank @Size(max = 64) String eventType,
            @NotBlank @Pattern(regexp = SHA_PATTERN,
                    message = "commitSha must be a 40-character lowercase hex SHA") String commitSha,
            @Pattern(regexp = SHA_PATTERN,
                    message = "baseCommitSha must be a 40-character lowercase hex SHA") String baseCommitSha) {
    }

    public record StepResponse(
            UUID id,
            String stepKey,
            WorkflowStatus status,
            int attempt,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {
    }

    public record WorkflowResponse(
            UUID id,
            UUID repositoryId,
            Long pullRequestNumber,
            String eventType,
            String commitSha,
            String baseCommitSha,
            WorkflowStatus status,
            String currentStep,
            BigDecimal riskMaxScore,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            List<StepResponse> steps) {
    }

    public record CreateJobRequest(
            @NotNull Language language) {
    }

    public record JobResponse(
            UUID id,
            UUID workflowId,
            String commitSha,
            Language language,
            WorkflowStatus status,
            String runnerId,
            Integer exitCode,
            boolean timedOut,
            String errorCode,
            Instant submittedAt,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt) {
    }
}
