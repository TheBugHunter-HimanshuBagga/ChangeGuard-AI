package com.changeguard.controlplane.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

    Optional<WorkflowRun> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select w from WorkflowRun w
            where w.repositoryId in (
                select r.id from TrackedRepository r
                where r.organizationId in (
                    select m.organization.id from OrganizationMember m where m.userId = :userId))
              and (:repositoryId is null or w.repositoryId = :repositoryId)
              and (:status is null or w.status = :status)
              and (:commitSha is null or w.commitSha = :commitSha)
            order by w.createdAt desc
            """)
    List<WorkflowRun> searchForUser(
            @Param("userId") UUID userId,
            @Param("repositoryId") UUID repositoryId,
            @Param("status") WorkflowStatus status,
            @Param("commitSha") String commitSha);
}
