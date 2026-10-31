package com.moveinsync.roster.dto;

import com.moveinsync.roster.domain.enums.EventType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incoming employee event DTO representing an event from the external HR system.
 *
 * <p>Contains metadata (tenant, employee ID, version, event type) and HR-owned
 * employee attributes. Transport-admin fields are intentionally absent as HR
 * systems do not own or manage those attributes.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeEventDto {

    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotNull(message = "Event type is required")
    private EventType eventType;

    @NotNull(message = "Version is required")
    @Positive(message = "Version must be a positive integer")
    private Long version;

    private String firstName;

    private String lastName;

    @Email(message = "Email must be a valid email address")
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be a valid 10-15 digit phone number")
    private String phone;

    private String managerId;

    private String officeCode;

    private String department;

    private String shiftCode;
}

