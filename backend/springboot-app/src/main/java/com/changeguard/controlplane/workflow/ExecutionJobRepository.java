package com.changeguard.controlplane.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, UUID> {

    List<ExecutionJob> findByWorkflowIdOrderBySubmittedAt(UUID workflowId);
}
