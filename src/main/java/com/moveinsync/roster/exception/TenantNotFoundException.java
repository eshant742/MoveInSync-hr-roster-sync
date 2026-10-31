package com.moveinsync.roster.exception;

/**
 * Thrown when a tenant ID is missing or invalid.
 */
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(String tenantId) {
        super(String.format("Tenant not found: %s", tenantId));
    }
}

