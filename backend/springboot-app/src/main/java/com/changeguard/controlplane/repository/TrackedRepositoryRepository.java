package com.changeguard.controlplane.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackedRepositoryRepository extends JpaRepository<TrackedRepository, UUID> {

    boolean existsByGithubRepoId(Long githubRepoId);

    List<TrackedRepository> findByOrganizationIdOrderByFullName(UUID organizationId);
}
