package com.changeguard.controlplane.organization;

import com.changeguard.controlplane.common.error.ApiException;
import com.changeguard.controlplane.common.error.ErrorCode;
import com.changeguard.controlplane.common.error.ErrorResponse;
import com.changeguard.controlplane.organization.dto.OrganizationDtos.AddMemberRequest;
import com.changeguard.controlplane.organization.dto.OrganizationDtos.CreateOrganizationRequest;
import com.changeguard.controlplane.organization.dto.OrganizationDtos.MemberResponse;
import com.changeguard.controlplane.organization.dto.OrganizationDtos.OrganizationResponse;
import com.changeguard.controlplane.security.SecurityUtils;
import com.changeguard.controlplane.user.User;
import com.changeguard.controlplane.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationMemberRepository memberRepository,
                               UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        UUID currentUserId = SecurityUtils.currentUserId();
        String name = request.name().trim();
        String slug = request.slug().trim();
        if (organizationRepository.existsBySlug(slug)) {
            throw new ApiException(ErrorCode.CONFLICT, "Organization slug already exists",
                    List.of(new ErrorResponse.ErrorDetail("slug", "Already in use")));
        }
        if (organizationRepository.existsByName(name)) {
            throw new ApiException(ErrorCode.CONFLICT, "Organization name already exists",
                    List.of(new ErrorResponse.ErrorDetail("name", "Already in use")));
        }
        Organization organization = new Organization();
        organization.setName(name);
        organization.setSlug(slug);
        try {
            organizationRepository.saveAndFlush(organization);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.CONFLICT, "Organization already exists");
        }
        OrganizationMember membership = new OrganizationMember();
        membership.setOrganization(organization);
        membership.setUserId(currentUserId);
        membership.setRole(MemberRole.OWNER);
        memberRepository.save(membership);
        log.info("Organization created orgId={} ownerUserId={}", organization.getId(), currentUserId);
        return toResponse(organization);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listMine() {
        return organizationRepository.findAllForUser(SecurityUtils.currentUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID organizationId) {
        OrganizationMember membership = requireMembership(organizationId, SecurityUtils.currentUserId());
        return toResponse(membership.getOrganization());
    }

    @Transactional
    public MemberResponse addMember(UUID organizationId, AddMemberRequest request) {
        OrganizationMember caller = requireMembership(organizationId, SecurityUtils.currentUserId());
        requireOrgAdmin(caller);
        User target = userRepository.findByEmail(
                        request.email().trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found",
                        List.of(new ErrorResponse.ErrorDetail("email", "No user with this email"))));
        if (memberRepository.existsByOrganizationIdAndUserId(organizationId, target.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, "User is already a member of this organization");
        }
        OrganizationMember membership = new OrganizationMember();
        membership.setOrganization(caller.getOrganization());
        membership.setUserId(target.getId());
        membership.setRole(request.role());
        try {
            memberRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.CONFLICT, "User is already a member of this organization");
        }
        log.info("Member added orgId={} userId={} role={}", organizationId, target.getId(), request.role());
        return new MemberResponse(target.getId(), target.getEmail(), target.getDisplayName(),
                membership.getRole(), membership.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID organizationId) {
        requireMembership(organizationId, SecurityUtils.currentUserId());
        return memberRepository.findByOrganizationIdOrderByCreatedAt(organizationId).stream()
                .map(m -> {
                    User user = userRepository.findById(m.getUserId())
                            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
                    return new MemberResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                            m.getRole(), m.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID userId) {
        OrganizationMember caller = requireMembership(organizationId, SecurityUtils.currentUserId());
        requireOrgAdmin(caller);
        OrganizationMember target = memberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Membership not found"));
        if (target.getRole() == MemberRole.OWNER
                && memberRepository.countByOrganizationIdAndRole(organizationId, MemberRole.OWNER) == 1) {
            throw new ApiException(ErrorCode.LAST_OWNER_REMOVAL,
                    "Cannot remove the last owner of the organization");
        }
        memberRepository.delete(target);
        log.info("Member removed orgId={} userId={}", organizationId, userId);
    }

    /**
     * Returns the membership when the user belongs to the org; 404 otherwise
     * (membership is not leaked for non-members).
     */
    OrganizationMember requireMembership(UUID organizationId, UUID userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Organization not found"));
    }

    void requireOrgAdmin(OrganizationMember membership) {
        MemberRole role = membership.getRole();
        if (role != MemberRole.OWNER && role != MemberRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Organization admin role required");
        }
    }

    private OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName(),
                organization.getSlug(), organization.getCreatedAt());
    }
}
