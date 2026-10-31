package com.moveinsync.roster.domain.enums;

/**
 * Identifies the source through which employee data was ingested into the system.
 *
 * <ul>
 *   <li>{@link #API} — Data received via the real-time REST API.</li>
 *   <li>{@link #NIGHTLY_EXPORT} — Data received via the nightly batch reconciliation file.</li>
 * </ul>
 */
public enum IngestionSource {
    API,
    NIGHTLY_EXPORT
}
