package com.platform.controlplane.tenant.dto;

import com.platform.controlplane.tenant.enums.TenantRole;
import jakarta.validation.constraints.NotNull;

public class UpdateRoleRequest {

    @NotNull(message = "Role is required")
    private TenantRole role;

    public UpdateRoleRequest() {
    }

    public UpdateRoleRequest(TenantRole role) {
        this.role = role;
    }

    public TenantRole getRole() {
        return role;
    }

    public void setRole(TenantRole role) {
        this.role = role;
    }
}
