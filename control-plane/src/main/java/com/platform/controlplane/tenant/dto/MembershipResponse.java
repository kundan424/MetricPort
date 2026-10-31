package com.platform.controlplane.tenant.dto;

import com.platform.controlplane.tenant.entity.Membership;
import com.platform.controlplane.tenant.enums.MembershipStatus;
import com.platform.controlplane.tenant.enums.TenantRole;

import java.time.Instant;
import java.util.UUID;

public class MembershipResponse {

    private UUID id;
    private UUID userId;
    private String email;
    private String fullName;
    private TenantRole role;
    private MembershipStatus status;
    private Instant createdAt;

    public MembershipResponse() {
    }

    public MembershipResponse(UUID id, UUID userId, String email, String fullName, TenantRole role, MembershipStatus status, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static MembershipResponse from(Membership membership) {
        return new MembershipResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getFullName(),
                membership.getRole(),
                membership.getStatus(),
                membership.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public TenantRole getRole() {
        return role;
    }

    public void setRole(TenantRole role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
