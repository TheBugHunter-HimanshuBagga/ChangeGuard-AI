package com.changeguard.controlplane.workflow;

import com.changeguard.controlplane.workflow.dto.WorkflowDtos.CreateWorkflowRequest;
import com.changeguard.controlplane.workflow.dto.WorkflowDtos.WorkflowResponse;
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
@RequestMapping("/api/v1/workflows")
@Tag(name = "Workflows", description = "Workflow lifecycle management")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Operation(summary = "Create a workflow (idempotent on repo+PR+sha+event)")
    @PostMapping
    public ResponseEntity<WorkflowResponse> create(
            @Valid @RequestBody CreateWorkflowRequest request) {
        WorkflowService.CreateResult result = workflowService.create(request);
        // Fresh create → 201; idempotent replay → 200 with the existing workflow.
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.workflow());
    }

    @Operation(summary = "Search workflows visible to the caller")
    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> search(
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) WorkflowStatus status,
            @RequestParam(required = false) String commitSha) {
        return ResponseEntity.ok(workflowService.search(repositoryId, status, commitSha));
    }

    @Operation(summary = "Workflow detail with steps")
    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowResponse> get(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowService.get(workflowId));
    }

    @Operation(summary = "Cancel a PENDING or RUNNING workflow")
    @PostMapping("/{workflowId}/cancel")
    public ResponseEntity<WorkflowResponse> cancel(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowService.cancel(workflowId));
    }
}
