package com.platform.controlplane.tenant.dto;

import com.platform.controlplane.tenant.entity.Tenant;
import com.platform.controlplane.tenant.enums.TenantRole;
import com.platform.controlplane.tenant.enums.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public class TenantResponse {

    private UUID id;
    private String name;
    private String slug;
    private TenantStatus status;
    private TenantRole role;
    private Instant createdAt;

    public TenantResponse() {
    }

    public TenantResponse(UUID id, String name, String slug, TenantStatus status, TenantRole role, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static TenantResponse from(Tenant tenant, TenantRole role) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus(),
                role,
                tenant.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public TenantRole getRole() {
        return role;
    }

    public void setRole(TenantRole role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
