package com.changeguard.controlplane.organization.dto;

import com.changeguard.controlplane.organization.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Organization and membership DTOs.
 */
public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record CreateOrganizationRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                    message = "slug must be lowercase alphanumeric with optional hyphens")
            @Size(max = 63) String slug) {
    }

    public record OrganizationResponse(
            UUID id,
            String name,
            String slug,
            Instant createdAt) {
    }

    public record AddMemberRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotNull MemberRole role) {
    }

    public record MemberResponse(
            UUID userId,
            String email,
            String displayName,
            MemberRole role,
            Instant addedAt) {
    }
}
