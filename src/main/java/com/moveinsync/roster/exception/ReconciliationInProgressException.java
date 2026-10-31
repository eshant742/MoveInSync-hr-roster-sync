package com.moveinsync.roster.exception;

/**
 * Thrown when attempting to start a reconciliation run while another is actively executing.
 */
public class ReconciliationInProgressException extends RuntimeException {

    public ReconciliationInProgressException(String tenantId) {
        super(String.format("Reconciliation run is already in progress for tenant: %s", tenantId));
    }
}

