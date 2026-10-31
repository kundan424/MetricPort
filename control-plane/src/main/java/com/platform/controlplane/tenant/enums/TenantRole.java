package com.platform.controlplane.tenant.enums;

public enum TenantRole {
    OWNER(4),
    ADMIN(3),
    DEVELOPER(2),
    VIEWER(1);

    private final int level;

    TenantRole(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean hasAtLeast(TenantRole requiredRole) {
        return this.level >= requiredRole.level;
    }
}
