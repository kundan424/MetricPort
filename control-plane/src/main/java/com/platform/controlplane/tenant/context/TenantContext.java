package com.platform.controlplane.tenant.context;

import com.platform.controlplane.tenant.enums.TenantRole;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local context holder for the active tenant during HTTP request execution.
 * Guaranteed to be cleared after each request by {@link TenantContextFilter}.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantContextHolder> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(UUID tenantId, TenantRole role) {
        if (tenantId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(new TenantContextHolder(tenantId, role));
        }
    }

    public static Optional<UUID> getTenantId() {
        TenantContextHolder holder = CURRENT.get();
        return holder != null ? Optional.of(holder.tenantId()) : Optional.empty();
    }

    public static UUID requireTenantId() {
        return getTenantId()
                .orElseThrow(() -> new IllegalStateException("No active tenant context found for current operation"));
    }

    public static Optional<TenantRole> getTenantRole() {
        TenantContextHolder holder = CURRENT.get();
        return holder != null ? Optional.ofNullable(holder.role()) : Optional.empty();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record TenantContextHolder(UUID tenantId, TenantRole role) {
    }
}
