package com.moveinsync.roster.exception;

/**
 * Thrown when an employee cannot be found by employeeId and tenantId.
 */
public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(String employeeId, String tenantId) {
        super(String.format("Employee not found: ID %s in tenant %s", employeeId, tenantId));
    }
}

