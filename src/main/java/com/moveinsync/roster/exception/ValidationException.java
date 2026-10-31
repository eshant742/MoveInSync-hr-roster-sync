package com.moveinsync.roster.exception;

import java.util.List;

/**
 * Thrown when an incoming employee record fails boundary validation.
 */
public class ValidationException extends RuntimeException {

    private final String employeeId;
    private final List<String> errors;

    public ValidationException(String employeeId, List<String> errors) {
        super(String.format("Validation failed for employee %s: %s", employeeId, String.join(", ", errors)));
        this.employeeId = employeeId;
        this.errors = errors;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public List<String> getErrors() {
        return errors;
    }
}

