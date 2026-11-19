package com.changeguard.controlplane.workflow;

import com.changeguard.controlplane.common.error.ApiException;
import com.changeguard.controlplane.common.error.ErrorCode;
import com.changeguard.controlplane.workflow.dto.WorkflowDtos.CreateJobRequest;
import com.changeguard.controlplane.workflow.dto.WorkflowDtos.JobResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Execution job management (control-plane side).
 * TODO(later-phase): dispatch PENDING jobs to the isolated execution plane
 * (docs/API_DESIGN.md §7) and ingest completion evidence.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final ExecutionJobRepository jobRepository;
    private final WorkflowService workflowService;

    public JobService(ExecutionJobRepository jobRepository, WorkflowService workflowService) {
        this.jobRepository = jobRepository;
        this.workflowService = workflowService;
    }

    @Transactional
    public JobResponse create(UUID workflowId, CreateJobRequest request) {
        WorkflowRun workflow = workflowService.requireWorkflowAccess(workflowId);
        if (workflow.getStatus() != WorkflowStatus.PENDING
                && workflow.getStatus() != WorkflowStatus.RUNNING) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot create a job for a workflow in status " + workflow.getStatus());
        }
        ExecutionJob job = new ExecutionJob();
        job.setWorkflowId(workflowId);
        job.setCommitSha(workflow.getCommitSha());
        job.setLanguage(request.language());
        job.setStatus(WorkflowStatus.PENDING);
        jobRepository.save(job);
        log.info("Job created jobId={} workflowId={} language={}",
                job.getId(), workflowId, request.language());
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listByWorkflow(UUID workflowId) {
        workflowService.requireWorkflowAccess(workflowId);
        return jobRepository.findByWorkflowIdOrderBySubmittedAt(workflowId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID jobId) {
        ExecutionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
        workflowService.requireWorkflowAccess(job.getWorkflowId());
        return toResponse(job);
    }

    private JobResponse toResponse(ExecutionJob job) {
        return new JobResponse(
                job.getId(),
                job.getWorkflowId(),
                job.getCommitSha(),
                job.getLanguage(),
                job.getStatus(),
                job.getRunnerId(),
                job.getExitCode(),
                job.isTimedOut(),
                job.getErrorCode(),
                job.getSubmittedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt());
    }
}
