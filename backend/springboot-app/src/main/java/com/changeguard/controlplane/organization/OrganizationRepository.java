package com.changeguard.controlplane.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    @Query("""
            select o from Organization o
            where o.id in (select m.organization.id from OrganizationMember m where m.userId = :userId)
            order by o.createdAt
            """)
    List<Organization> findAllForUser(@Param("userId") UUID userId);
}
