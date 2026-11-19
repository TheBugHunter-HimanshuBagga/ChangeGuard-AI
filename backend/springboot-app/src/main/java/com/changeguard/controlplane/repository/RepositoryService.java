package com.changeguard.controlplane.repository;

import com.changeguard.controlplane.common.error.ApiException;
import com.changeguard.controlplane.common.error.ErrorCode;
import com.changeguard.controlplane.organization.MemberRole;
import com.changeguard.controlplane.organization.OrganizationMember;
import com.changeguard.controlplane.organization.OrganizationMemberRepository;
import com.changeguard.controlplane.repository.dto.RepositoryDtos.CreateRepositoryRequest;
import com.changeguard.controlplane.repository.dto.RepositoryDtos.RepositoryResponse;
import com.changeguard.controlplane.security.SecurityUtils;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    private final TrackedRepositoryRepository repositoryRepository;
    private final OrganizationMemberRepository memberRepository;

    public RepositoryService(TrackedRepositoryRepository repositoryRepository,
                             OrganizationMemberRepository memberRepository) {
        this.repositoryRepository = repositoryRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public RepositoryResponse create(UUID organizationId, CreateRepositoryRequest request) {
        OrganizationMember caller = requireMembership(organizationId);
        requireOrgAdmin(caller);
        if (repositoryRepository.existsByGithubRepoId(request.githubRepoId())) {
            throw new ApiException(ErrorCode.CONFLICT, "Repository already linked");
        }
        TrackedRepository repository = new TrackedRepository();
        repository.setOrganizationId(organizationId);
        repository.setGithubRepoId(request.githubRepoId());
        repository.setFullName(request.fullName().trim());
        repository.setDefaultBranch(blankToNull(request.defaultBranch()));
        repository.setLanguagesEnabled(request.languagesEnabled().stream()
                .map(Language::name)
                .toArray(String[]::new));
        repository.setWebhookSecretSet(false);
        repository.setActive(true);
        try {
            repositoryRepository.saveAndFlush(repository);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.CONFLICT, "Repository already linked");
        }
        log.info("Repository linked repoId={} orgId={} fullName={}",
                repository.getId(), organizationId, repository.getFullName());
        return toResponse(repository);
    }

    @Transactional(readOnly = true)
    public List<RepositoryResponse> listByOrganization(UUID organizationId) {
        requireMembership(organizationId);
        return repositoryRepository.findByOrganizationIdOrderByFullName(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepositoryResponse get(UUID repositoryId) {
        TrackedRepository repository = requireRepositoryAccess(repositoryId);
        return toResponse(repository);
    }

    TrackedRepository requireRepositoryAccess(UUID repositoryId) {
        TrackedRepository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
        requireMembership(repository.getOrganizationId());
        return repository;
    }

    OrganizationMember requireMembership(UUID organizationId) {
        return memberRepository
                .findByOrganizationIdAndUserId(organizationId, SecurityUtils.currentUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
    }

    private void requireOrgAdmin(OrganizationMember membership) {
        MemberRole role = membership.getRole();
        if (role != MemberRole.OWNER && role != MemberRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Organization admin role required");
        }
    }

    private RepositoryResponse toResponse(TrackedRepository repository) {
        List<Language> languages = Arrays.stream(repository.getLanguagesEnabled())
                .map(Language::valueOf)
                .toList();
        return new RepositoryResponse(
                repository.getId(),
                repository.getOrganizationId(),
                repository.getGithubRepoId(),
                repository.getFullName(),
                repository.getDefaultBranch(),
                languages,
                repository.isWebhookSecretSet(),
                repository.isActive(),
                repository.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
