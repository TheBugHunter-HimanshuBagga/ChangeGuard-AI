package com.changeguard.controlplane.repository.dto;

import com.changeguard.controlplane.repository.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository management DTOs (docs/API_DESIGN.md §5.2).
 */
public final class RepositoryDtos {

    private RepositoryDtos() {
    }

    public record CreateRepositoryRequest(
            @NotNull Long githubRepoId,
            @NotBlank @Size(max = 255) String fullName,
            @Size(max = 255) String defaultBranch,
            @NotEmpty List<Language> languagesEnabled) {
    }

    public record RepositoryResponse(
            UUID id,
            UUID organizationId,
            Long githubRepoId,
            String fullName,
            String defaultBranch,
            List<Language> languagesEnabled,
            boolean webhookSecretSet,
            boolean active,
            Instant createdAt) {
    }
}
