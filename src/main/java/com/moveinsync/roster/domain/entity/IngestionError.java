package com.moveinsync.roster.domain.entity;

import com.moveinsync.roster.domain.enums.IngestionSource;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing an error encountered during employee data ingestion.
 *
 * <p>Maps to the {@code ingestion_errors} table. Every validation failure, parsing error,
 * or unexpected exception during event processing or reconciliation is recorded here
 * for auditing, debugging, and operational monitoring.</p>
 *
 * <p><strong>Privacy note:</strong> This entity stores only the employee ID for
 * reference — no PII (name, email, phone) is persisted in error records.</p>
 */
@Entity
@Table(
        name = "ingestion_errors",
        indexes = {
                @Index(name = "idx_ingest_err_tenant", columnList = "tenant_id"),
                @Index(name = "idx_ingest_err_employee", columnList = "employee_id"),
                @Index(name = "idx_ingest_err_source", columnList = "source"),
                @Index(name = "idx_ingest_err_created", columnList = "created_at")
        }
)
public class IngestionError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The employee ID associated with this error (safe to log — not PII).
     */
    @Column(name = "employee_id", length = 64)
    private String employeeId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /**
     * The type/category of error (e.g., "VALIDATION_ERROR", "PARSE_ERROR", "STALE_EVENT").
     */
    @Column(name = "error_type", nullable = false, length = 64)
    private String errorType;

    /**
     * Human-readable error message. Must not contain PII.
     */
    @Column(name = "error_message", nullable = false, length = 1024)
    private String errorMessage;

    /**
     * The source from which the erroneous data was received.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private IngestionSource source;

    /**
     * Optional reference to the reconciliation run that produced this error.
     */
    @Column(name = "reconciliation_run_id")
    private Long reconciliationRunId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Default constructor required by JPA. */
    public IngestionError() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Override
    public String toString() {
        return "IngestionError{id=" + id + ", employeeId='" + employeeId
                + "', errorType='" + errorType + "', source=" + source + '}';
    }

    // ========== Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public IngestionSource getSource() {
        return source;
    }

    public void setSource(IngestionSource source) {
        this.source = source;
    }

    public Long getReconciliationRunId() {
        return reconciliationRunId;
    }

    public void setReconciliationRunId(Long reconciliationRunId) {
        this.reconciliationRunId = reconciliationRunId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
