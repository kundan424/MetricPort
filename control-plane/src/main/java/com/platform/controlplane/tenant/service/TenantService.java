package com.platform.controlplane.tenant.service;

import com.platform.controlplane.identity.entity.User;
import com.platform.controlplane.identity.repository.UserRepository;
import com.platform.controlplane.tenant.dto.AddMemberRequest;
import com.platform.controlplane.tenant.dto.CreateTenantRequest;
import com.platform.controlplane.tenant.dto.MembershipResponse;
import com.platform.controlplane.tenant.dto.TenantResponse;
import com.platform.controlplane.tenant.dto.UpdateRoleRequest;
import com.platform.controlplane.tenant.dto.UpdateTenantRequest;
import com.platform.controlplane.tenant.entity.Membership;
import com.platform.controlplane.tenant.entity.Tenant;
import com.platform.controlplane.tenant.enums.MembershipStatus;
import com.platform.controlplane.tenant.enums.TenantRole;
import com.platform.controlplane.tenant.enums.TenantStatus;
import com.platform.controlplane.tenant.exception.DuplicateMembershipException;
import com.platform.controlplane.tenant.exception.DuplicateTenantSlugException;
import com.platform.controlplane.tenant.exception.InsufficientPermissionsException;
import com.platform.controlplane.tenant.exception.TenantNotFoundException;
import com.platform.controlplane.tenant.exception.UserNotFoundException;
import com.platform.controlplane.tenant.repository.MembershipRepository;
import com.platform.controlplane.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public TenantService(
            TenantRepository tenantRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            TenantAuthorizationService tenantAuthorizationService
    ) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request, UUID userId) {
        String normalizedSlug = request.getSlug().trim().toLowerCase();
        String normalizedName = request.getName().trim();

        if (tenantRepository.existsBySlug(normalizedSlug)) {
            throw new DuplicateTenantSlugException("Tenant slug is already taken: " + normalizedSlug);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        Tenant tenant = new Tenant(normalizedName, normalizedSlug, TenantStatus.ACTIVE);
        Tenant savedTenant = tenantRepository.save(tenant);

        Membership ownerMembership = new Membership(savedTenant, user, TenantRole.OWNER, MembershipStatus.ACTIVE);
        membershipRepository.save(ownerMembership);

        return TenantResponse.from(savedTenant, TenantRole.OWNER);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantById(UUID tenantId, UUID userId) {
        Membership membership = tenantAuthorizationService.requireMembership(tenantId, userId);
        return TenantResponse.from(membership.getTenant(), membership.getRole());
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> getUserTenants(UUID userId) {
        List<Membership> memberships = membershipRepository.findByUserIdWithTenant(userId);
        return memberships.stream()
                .map(m -> TenantResponse.from(m.getTenant(), m.getRole()))
                .toList();
    }

    @Transactional
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request, UUID requestingUserId) {
        tenantAuthorizationService.requireRole(tenantId, requestingUserId, TenantRole.ADMIN);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with id: " + tenantId));

        tenant.setName(request.getName().trim());
        Tenant updatedTenant = tenantRepository.save(tenant);

        TenantRole role = tenantAuthorizationService.getRole(tenantId, requestingUserId);
        return TenantResponse.from(updatedTenant, role);
    }

    @Transactional
    public void deleteTenant(UUID tenantId, UUID requestingUserId) {
        tenantAuthorizationService.requireRole(tenantId, requestingUserId, TenantRole.OWNER);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with id: " + tenantId));

        List<Membership> memberships = membershipRepository.findByTenantId(tenantId);
        membershipRepository.deleteAll(memberships);
        tenantRepository.delete(tenant);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getTenantMembers(UUID tenantId, UUID userId) {
        tenantAuthorizationService.requireMembership(tenantId, userId);

        List<Membership> memberships = membershipRepository.findByTenantIdWithUser(tenantId);
        return memberships.stream()
                .map(MembershipResponse::from)
                .toList();
    }

    @Transactional
    public MembershipResponse addTenantMember(UUID tenantId, AddMemberRequest request, UUID requestingUserId) {
        Membership callerMembership = tenantAuthorizationService.requireRole(tenantId, requestingUserId, TenantRole.ADMIN);

        if (request.getRole() == TenantRole.OWNER && callerMembership.getRole() != TenantRole.OWNER) {
            throw new InsufficientPermissionsException("Only an OWNER can add another member as OWNER");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with id: " + tenantId));

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User userToAdd = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("No registered user found with email: " + normalizedEmail));

        if (membershipRepository.existsByTenantIdAndUserId(tenantId, userToAdd.getId())) {
            throw new DuplicateMembershipException("User is already a member of this tenant: " + normalizedEmail);
        }

        Membership newMembership = new Membership(tenant, userToAdd, request.getRole(), MembershipStatus.ACTIVE);
        Membership savedMembership = membershipRepository.save(newMembership);

        return MembershipResponse.from(savedMembership);
    }

    @Transactional
    public MembershipResponse updateMemberRole(UUID tenantId, UUID targetUserId, UpdateRoleRequest request, UUID requestingUserId) {
        Membership callerMembership = tenantAuthorizationService.requireRole(tenantId, requestingUserId, TenantRole.ADMIN);

        Membership targetMembership = membershipRepository.findByTenantIdAndUserId(tenantId, targetUserId)
                .orElseThrow(() -> new UserNotFoundException("Membership not found for user: " + targetUserId + " in tenant: " + tenantId));

        if (callerMembership.getRole() != TenantRole.OWNER) {
            if (targetMembership.getRole() == TenantRole.OWNER) {
                throw new InsufficientPermissionsException("ADMIN cannot modify an OWNER's role");
            }
            if (request.getRole() == TenantRole.OWNER) {
                throw new InsufficientPermissionsException("ADMIN cannot promote a member to OWNER");
            }
            if (targetMembership.getRole() == TenantRole.ADMIN && !targetUserId.equals(requestingUserId)) {
                throw new InsufficientPermissionsException("ADMIN cannot modify another ADMIN's role");
            }
        }

        if (targetMembership.getRole() == TenantRole.OWNER && request.getRole() != TenantRole.OWNER) {
            long ownerCount = tenantAuthorizationService.countOwners(tenantId);
            if (ownerCount <= 1) {
                throw new InsufficientPermissionsException("Cannot demote the sole OWNER of a tenant. Transfer ownership first.");
            }
        }

        targetMembership.setRole(request.getRole());
        Membership updated = membershipRepository.save(targetMembership);
        return MembershipResponse.from(updated);
    }

    @Transactional
    public void removeTenantMember(UUID tenantId, UUID targetUserId, UUID requestingUserId) {
        Membership targetMembership = membershipRepository.findByTenantIdAndUserId(tenantId, targetUserId)
                .orElseThrow(() -> new UserNotFoundException("Membership not found for user: " + targetUserId + " in tenant: " + tenantId));

        if (targetUserId.equals(requestingUserId)) {
            if (targetMembership.getRole() == TenantRole.OWNER) {
                long ownerCount = tenantAuthorizationService.countOwners(tenantId);
                if (ownerCount <= 1) {
                    throw new InsufficientPermissionsException("Sole owner cannot leave tenant without assigning another owner");
                }
            }
            membershipRepository.delete(targetMembership);
            return;
        }

        Membership callerMembership = tenantAuthorizationService.requireRole(tenantId, requestingUserId, TenantRole.ADMIN);

        if (callerMembership.getRole() != TenantRole.OWNER) {
            if (targetMembership.getRole() == TenantRole.OWNER) {
                throw new InsufficientPermissionsException("ADMIN cannot remove an OWNER");
            }
            if (targetMembership.getRole() == TenantRole.ADMIN) {
                throw new InsufficientPermissionsException("ADMIN cannot remove another ADMIN");
            }
        }

        membershipRepository.delete(targetMembership);
    }
}
