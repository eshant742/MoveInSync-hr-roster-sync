package com.moveinsync.roster.exception;

/**
 * Thrown when an incoming event has the exact same version as the currently stored version.
 */
public class DuplicateEventException extends RuntimeException {

    private final String employeeId;
    private final long version;

    public DuplicateEventException(String employeeId, long version) {
        super(String.format("Duplicate event for employee %s: version %d already processed",
                employeeId, version));
        this.employeeId = employeeId;
        this.version = version;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public long getVersion() {
        return version;
    }
}

