package com.moveinsync.roster.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating transport-administration-owned fields on an employee.
 *
 * <p>These fields include pickup point and special assistance requirements,
 * which are governed exclusively by transport administrators and are preserved
 * across all HR updates and nightly reconciliation runs.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportFieldsUpdateDto {

    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    private String pickupPoint;

    private String specialAssistanceNeeds;
}

