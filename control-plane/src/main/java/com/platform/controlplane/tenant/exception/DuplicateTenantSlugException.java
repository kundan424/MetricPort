package com.platform.controlplane.tenant.exception;

public class DuplicateTenantSlugException extends RuntimeException {

    public DuplicateTenantSlugException(String message) {
        super(message);
    }
}
