package com.moveinsync.roster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Clean employee DTO returned in API responses.
 * Never exposes database primary keys or internal metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponseDto {

    private String employeeId;
    private String tenantId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String managerId;
    private String officeCode;
    private String department;
    private String shiftCode;
    private String pickupPoint;
    private String specialAssistanceNeeds;
    private long version;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

