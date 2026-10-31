package com.moveinsync.roster.exception;

/**
 * Thrown when an incoming event has a version less than the currently stored version.
 */
public class StaleEventException extends RuntimeException {

    private final String employeeId;
    private final long incomingVersion;
    private final long currentVersion;

    public StaleEventException(String employeeId, long incomingVersion, long currentVersion) {
        super(String.format("Stale event for employee %s: incoming version %d is older than current version %d",
                employeeId, incomingVersion, currentVersion));
        this.employeeId = employeeId;
        this.incomingVersion = incomingVersion;
        this.currentVersion = currentVersion;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public long getIncomingVersion() {
        return incomingVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}

