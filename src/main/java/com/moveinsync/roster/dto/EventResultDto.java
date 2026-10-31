package com.moveinsync.roster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of processing an individual employee event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResultDto {

    private String employeeId;

    /**
     * Processing status:
     * APPLIED   - Successfully updated existing employee
     * CREATED   - Successfully created new employee
     * DUPLICATE - Duplicate event version detected; idempotent no-op
     * STALE     - Incoming version older than stored; skipped
     * REJECTED  - Validation failed or record invalid
     */
    private String status;

    private String message;
}

