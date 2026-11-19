package com.changeguard.controlplane.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<OrganizationMember> findByOrganizationIdOrderByCreatedAt(UUID organizationId);

    long countByOrganizationIdAndRole(UUID organizationId, MemberRole role);

    long countByOrganizationId(UUID organizationId);

    void deleteByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    @Query("select m.organization.id from OrganizationMember m where m.userId = :userId")
    List<UUID> findOrganizationIdsByUserId(@Param("userId") UUID userId);
}
