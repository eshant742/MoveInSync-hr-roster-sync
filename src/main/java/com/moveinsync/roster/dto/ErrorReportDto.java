package com.moveinsync.roster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit record of an ingestion or reconciliation error.
 * Strictly free of PII (contains only employeeId, error type, and sanitized message).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorReportDto {

    private Long id;
    private String tenantId;
    private String employeeId;
    private String errorType;
    private String errorMessage;
    private String source;
    private LocalDateTime createdAt;
}

