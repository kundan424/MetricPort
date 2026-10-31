package com.platform.controlplane.tenant.exception;

public class DuplicateMembershipException extends RuntimeException {

    public DuplicateMembershipException(String message) {
        super(message);
    }
}
