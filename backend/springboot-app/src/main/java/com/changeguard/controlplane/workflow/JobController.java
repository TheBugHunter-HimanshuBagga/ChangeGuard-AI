package com.changeguard.controlplane.workflow;

import com.changeguard.controlplane.workflow.dto.WorkflowDtos.CreateJobRequest;
import com.changeguard.controlplane.workflow.dto.WorkflowDtos.JobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Jobs", description = "Execution job management")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @Operation(summary = "Register an execution job for a workflow (PENDING until dispatched)")
    @PostMapping("/workflows/{workflowId}/jobs")
    public ResponseEntity<JobResponse> create(
            @PathVariable UUID workflowId, @Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.create(workflowId, request));
    }

    @Operation(summary = "List jobs of a workflow")
    @GetMapping("/workflows/{workflowId}/jobs")
    public ResponseEntity<List<JobResponse>> listByWorkflow(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(jobService.listByWorkflow(workflowId));
    }

    @Operation(summary = "Job detail")
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobResponse> get(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobService.get(jobId));
    }
}
