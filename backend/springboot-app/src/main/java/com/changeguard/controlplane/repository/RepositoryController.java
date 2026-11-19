package com.changeguard.controlplane.repository;

import com.changeguard.controlplane.repository.dto.RepositoryDtos.CreateRepositoryRequest;
import com.changeguard.controlplane.repository.dto.RepositoryDtos.RepositoryResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Repositories", description = "GitHub repository linkage")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Operation(summary = "Link a repository to an organization (OWNER/ADMIN only)")
    @PostMapping("/organizations/{orgId}/repositories")
    public ResponseEntity<RepositoryResponse> create(
            @PathVariable UUID orgId, @Valid @RequestBody CreateRepositoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repositoryService.create(orgId, request));
    }

    @Operation(summary = "List repositories of an organization")
    @GetMapping("/organizations/{orgId}/repositories")
    public ResponseEntity<List<RepositoryResponse>> listByOrganization(@PathVariable UUID orgId) {
        return ResponseEntity.ok(repositoryService.listByOrganization(orgId));
    }

    @Operation(summary = "Repository detail including GitHub linkage state")
    @GetMapping("/repositories/{repoId}")
    public ResponseEntity<RepositoryResponse> get(@PathVariable UUID repoId) {
        return ResponseEntity.ok(repositoryService.get(repoId));
    }
}
