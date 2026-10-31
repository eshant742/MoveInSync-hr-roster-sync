package com.moveinsync.roster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload submitted by an operations user to approve a safety-braked reconciliation run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyBrakeApprovalDto {

    @NotNull(message = "Reconciliation run ID is required")
    private Long reconciliationRunId;

    @NotBlank(message = "Operator username/ID is required")
    private String approvedBy;

    private String reason;
}

