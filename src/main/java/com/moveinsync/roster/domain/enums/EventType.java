package com.moveinsync.roster.domain.enums;

/**
 * Represents the type of employee lifecycle event received from the HR system.
 *
 * <ul>
 *   <li>{@link #CREATE} — A new employee record should be created.</li>
 *   <li>{@link #UPDATE} — An existing employee record should be updated.</li>
 *   <li>{@link #DEACTIVATE} — An employee should be marked as inactive.</li>
 * </ul>
 */
public enum EventType {
    CREATE,
    UPDATE,
    DEACTIVATE
}
