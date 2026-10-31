package com.moveinsync.roster.domain.enums;

/**
 * Represents the status of a nightly reconciliation run.
 *
 * <ul>
 *   <li>{@link #RUNNING} — Reconciliation is currently in progress.</li>
 *   <li>{@link #COMPLETED} — Reconciliation finished successfully.</li>
 *   <li>{@link #SAFETY_BRAKE_TRIGGERED} — The missing-employee percentage exceeded the
 *       configured threshold; changes were not applied and require manual approval.</li>
 *   <li>{@link #FAILED} — Reconciliation terminated due to an unexpected error.</li>
 * </ul>
 */
public enum ReconciliationStatus {
    RUNNING,
    COMPLETED,
    SAFETY_BRAKE_TRIGGERED,
    FAILED
}
