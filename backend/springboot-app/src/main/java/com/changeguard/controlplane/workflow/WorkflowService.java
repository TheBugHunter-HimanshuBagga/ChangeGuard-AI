package com.changeguard.controlplane.workflow;

import com.changeguard.controlplane.common.error.ApiException;
import com.changeguard.controlplane.common.error.ErrorCode;
import com.changeguard.controlplane.repository.TrackedRepository;
import com.changeguard.controlplane.repository.TrackedRepositoryRepository;
import com.changeguard.controlplane.security.SecurityUtils;
import com.changeguard.controlplane.web.CorrelationIdFilter;
import com.changeguard.controlplane.workflow.dto.WorkflowDtos.CreateWorkflowRequest;
import com.changeguard.controlplane.workflow.dto.WorkflowDtos.StepResponse;
import com.changeguard.controlplane.workflow.dto.WorkflowDtos.WorkflowResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRunRepository workflowRepository;
    private final WorkflowStepRepository stepRepository;
    private final TrackedRepositoryRepository repositoryRepository;
    private final com.changeguard.controlplane.organization.OrganizationMemberRepository memberRepository;

    public WorkflowService(WorkflowRunRepository workflowRepository,
                           WorkflowStepRepository stepRepository,
                           TrackedRepositoryRepository repositoryRepository,
                           com.changeguard.controlplane.organization.OrganizationMemberRepository memberRepository) {
        this.workflowRepository = workflowRepository;
        this.stepRepository = stepRepository;
        this.repositoryRepository = repositoryRepository;
        this.memberRepository = memberRepository;
    }

    /** Result of an idempotent create: {@code created} is false on replay. */
    public record CreateResult(WorkflowResponse workflow, boolean created) {
    }

    /**
     * Creates a workflow in PENDING. Idempotent on
     * (repositoryId, pullRequestNumber, commitSha, eventType).
     * TODO(later-phase): webhook-driven creation and orchestration of steps.
     */
    @Transactional
    public CreateResult create(CreateWorkflowRequest request) {
        TrackedRepository repository = requireRepositoryAccess(request.repositoryId());
        String idempotencyKey = buildIdempotencyKey(repository.getId(),
                request.pullRequestNumber(), request.commitSha(), request.eventType());
        var existing = workflowRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new CreateResult(toResponse(existing.get(), List.of()), false);
        }
        WorkflowRun workflow = new WorkflowRun();
        workflow.setRepositoryId(repository.getId());
        workflow.setPullRequestNumber(request.pullRequestNumber());
        workflow.setEventType(request.eventType().trim());
        workflow.setCommitSha(request.commitSha());
        workflow.setBaseCommitSha(request.baseCommitSha());
        workflow.setStatus(WorkflowStatus.PENDING);
        workflow.setIdempotencyKey(idempotencyKey);
        workflow.setCorrelationId(currentCorrelationId());
        try {
            workflowRepository.saveAndFlush(workflow);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create with the same key — return the winner.
            return workflowRepository.findByIdempotencyKey(idempotencyKey)
                    .map(w -> new CreateResult(toResponse(w, List.of()), false))
                    .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "Workflow already exists"));
        }
        log.info("Workflow created workflowId={} repoId={} commitSha={}",
                workflow.getId(), repository.getId(), workflow.getCommitSha());
        return new CreateResult(toResponse(workflow, List.of()), true);
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> search(UUID repositoryId, WorkflowStatus status, String commitSha) {
        UUID userId = SecurityUtils.currentUserId();
        return workflowRepository.searchForUser(userId, repositoryId, status, commitSha).stream()
                .map(w -> toResponse(w, loadSteps(w.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowResponse get(UUID workflowId) {
        WorkflowRun workflow = requireWorkflowAccess(workflowId);
        return toResponse(workflow, loadSteps(workflowId));
    }

    @Transactional
    public WorkflowResponse cancel(UUID workflowId) {
        WorkflowRun workflow = requireWorkflowAccess(workflowId);
        WorkflowStatus current = workflow.getStatus();
        if (!current.canTransitionTo(WorkflowStatus.CANCELLED)) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot cancel a workflow in status " + current);
        }
        workflow.setStatus(WorkflowStatus.CANCELLED);
        workflow.setCompletedAt(Instant.now());
        workflowRepository.save(workflow);
        log.info("Workflow cancelled workflowId={}", workflowId);
        return toResponse(workflow, loadSteps(workflowId));
    }

    WorkflowRun requireWorkflowAccess(UUID workflowId) {
        WorkflowRun workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Workflow not found"));
        requireRepositoryAccess(workflow.getRepositoryId());
        return workflow;
    }

    private TrackedRepository requireRepositoryAccess(UUID repositoryId) {
        TrackedRepository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
        boolean member = memberRepository.existsByOrganizationIdAndUserId(
                repository.getOrganizationId(), SecurityUtils.currentUserId());
        if (!member) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found");
        }
        return repository;
    }

    private List<StepResponse> loadSteps(UUID workflowId) {
        return stepRepository.findByWorkflowIdOrderByStepKeyAscAttemptAsc(workflowId).stream()
                .map(s -> new StepResponse(s.getId(), s.getStepKey().name(), s.getStatus(),
                        s.getAttempt(), s.getErrorCode(), s.getErrorMessage(),
                        s.getStartedAt(), s.getFinishedAt()))
                .toList();
    }

    static String buildIdempotencyKey(UUID repositoryId, Long pullRequestNumber,
                                      String commitSha, String eventType) {
        String pr = pullRequestNumber == null ? "-" : pullRequestNumber.toString();
        return repositoryId + ":" + pr + ":" + commitSha + ":" + eventType;
    }

    private UUID currentCorrelationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return UUID.randomUUID();
        }
    }

    private WorkflowResponse toResponse(WorkflowRun workflow, List<StepResponse> steps) {
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getRepositoryId(),
                workflow.getPullRequestNumber(),
                workflow.getEventType(),
                workflow.getCommitSha(),
                workflow.getBaseCommitSha(),
                workflow.getStatus(),
                workflow.getCurrentStep(),
                workflow.getRiskMaxScore(),
                workflow.getStartedAt(),
                workflow.getCompletedAt(),
                workflow.getCreatedAt(),
                steps);
    }
}
