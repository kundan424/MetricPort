package com.platform.controlplane.tenant.service;

import com.platform.controlplane.tenant.context.TenantContext;
import com.platform.controlplane.tenant.entity.Membership;
import com.platform.controlplane.tenant.enums.MembershipStatus;
import com.platform.controlplane.tenant.enums.TenantRole;
import com.platform.controlplane.tenant.exception.InsufficientPermissionsException;
import com.platform.controlplane.tenant.exception.TenantAccessDeniedException;
import com.platform.controlplane.tenant.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

@Service
public class TenantAuthorizationService {

    private final MembershipRepository membershipRepository;

    public TenantAuthorizationService(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public Membership requireMembership(UUID tenantId, UUID userId) {
        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new TenantAccessDeniedException("You do not have access to this tenant"));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new TenantAccessDeniedException("Membership is not active for this tenant");
        }

        TenantContext.setCurrentTenant(tenantId, membership.getRole());
        return membership;
    }

    @Transactional(readOnly = true)
    public Membership requireRole(UUID tenantId, UUID userId, TenantRole minimumRole) {
        Membership membership = requireMembership(tenantId, userId);

        if (!membership.getRole().hasAtLeast(minimumRole)) {
            throw new InsufficientPermissionsException(
                    "Action requires at least " + minimumRole + " role, but current role is " + membership.getRole()
            );
        }

        return membership;
    }

    @Transactional(readOnly = true)
    public Membership requireAnyRole(UUID tenantId, UUID userId, TenantRole... allowedRoles) {
        Membership membership = requireMembership(tenantId, userId);

        boolean hasAllowedRole = Arrays.asList(allowedRoles).contains(membership.getRole());
        if (!hasAllowedRole) {
            throw new InsufficientPermissionsException(
                    "Action requires one of " + Arrays.toString(allowedRoles) + ", but current role is " + membership.getRole()
            );
        }

        return membership;
    }

    @Transactional(readOnly = true)
    public boolean hasRole(UUID tenantId, UUID userId, TenantRole minimumRole) {
        return membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(m -> m.getRole().hasAtLeast(minimumRole))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public TenantRole getRole(UUID tenantId, UUID userId) {
        return requireMembership(tenantId, userId).getRole();
    }

    @Transactional(readOnly = true)
    public long countOwners(UUID tenantId) {
        return membershipRepository.countByTenantIdAndRole(tenantId, TenantRole.OWNER);
    }
}
