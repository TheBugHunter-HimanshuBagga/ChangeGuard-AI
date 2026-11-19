package com.changeguard.controlplane.organization;

import com.changeguard.controlplane.organization.dto.OrganizationDtos.AddMemberRequest;
import com.changeguard.controlplane.organization.dto.OrganizationDtos.CreateOrganizationRequest;
import com.changeguard.controlplane.organization.dto.OrganizationDtos.MemberResponse;
import com.changeguard.controlplane.organization.dto.OrganizationDtos.OrganizationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organizations and membership")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Operation(summary = "Create an organization (creator becomes OWNER)")
    @PostMapping
    public ResponseEntity<OrganizationResponse> create(
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.create(request));
    }

    @Operation(summary = "List organizations the caller belongs to")
    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> listMine() {
        return ResponseEntity.ok(organizationService.listMine());
    }

    @Operation(summary = "Organization detail (membership required)")
    @GetMapping("/{orgId}")
    public ResponseEntity<OrganizationResponse> get(@PathVariable UUID orgId) {
        return ResponseEntity.ok(organizationService.get(orgId));
    }

    @Operation(summary = "Add a member (OWNER/ADMIN only)")
    @PostMapping("/{orgId}/members")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable UUID orgId, @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.addMember(orgId, request));
    }

    @Operation(summary = "List organization members")
    @GetMapping("/{orgId}/members")
    public ResponseEntity<List<MemberResponse>> listMembers(@PathVariable UUID orgId) {
        return ResponseEntity.ok(organizationService.listMembers(orgId));
    }

    @Operation(summary = "Remove a member (OWNER/ADMIN only; last owner protected)")
    @DeleteMapping("/{orgId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID orgId, @PathVariable UUID userId) {
        organizationService.removeMember(orgId, userId);
        return ResponseEntity.noContent().build();
    }
}
