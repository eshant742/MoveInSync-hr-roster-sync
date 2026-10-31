package com.moveinsync.roster.dto;

import com.moveinsync.roster.domain.enums.ReconciliationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result and metrics for a nightly reconciliation run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResultDto {

    private Long runId;
    private String tenantId;
    private ReconciliationStatus status;
    private long totalInExport;
    private long totalInStore;
    private long matched;
    private long createdCount;
    private long updatedCount;
    private long deactivatedCount;
    private long skipped;
    private long errorCount;
    private Double missingPercentage;
    private boolean safetyBrakeTriggered;
    private boolean operatorApproved;
    private String operatorApprovedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String message;
}

