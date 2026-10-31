package com.moveinsync.roster.domain.entity;

import com.moveinsync.roster.domain.enums.ReconciliationStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a nightly reconciliation run.
 *
 * <p>Maps to the {@code reconciliation_runs} table. Each run tracks the progress
 * and outcome of a full reconciliation between the HR export file and the
 * internal employee store, including counts, safety brake status, and any
 * manual approval information.</p>
 */
@Entity
@Table(
        name = "reconciliation_runs",
        indexes = {
                @Index(name = "idx_recon_status", columnList = "status"),
                @Index(name = "idx_recon_tenant", columnList = "tenant_id"),
                @Index(name = "idx_recon_started_at", columnList = "started_at")
        }
)
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReconciliationStatus status;

    @Column(name = "total_in_export")
    private long totalInExport;

    @Column(name = "total_in_store")
    private long totalInStore;

    @Column(name = "matched")
    private long matched;

    @Column(name = "created_count")
    private long createdCount;

    @Column(name = "updated_count")
    private long updatedCount;

    @Column(name = "deactivated_count")
    private long deactivatedCount;

    @Column(name = "skipped")
    private long skipped;

    @Column(name = "error_count")
    private long errorCount;

    @Column(name = "missing_percentage")
    private Double missingPercentage;

    @Column(name = "safety_brake_triggered", nullable = false)
    private boolean safetyBrakeTriggered = false;

    @Column(name = "operator_approved", nullable = false)
    private boolean operatorApproved = false;

    @Column(name = "operator_approved_by", length = 128)
    private String operatorApprovedBy;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    /**
     * If the safety brake was triggered, this records who approved the override.
     */
    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    /**
     * Optional reason provided when approving a safety brake override.
     */
    @Column(name = "approval_reason", length = 512)
    private String approvalReason;

    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Default constructor required by JPA. */
    public ReconciliationRun() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    @Override
    public String toString() {
        return "ReconciliationRun{id=" + id + ", tenantId='" + tenantId
                + "', status=" + status + ", safetyBrakeTriggered=" + safetyBrakeTriggered + '}';
    }

    // ========== Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
    }

    public long getTotalInExport() {
        return totalInExport;
    }

    public void setTotalInExport(long totalInExport) {
        this.totalInExport = totalInExport;
    }

    public long getTotalInStore() {
        return totalInStore;
    }

    public void setTotalInStore(long totalInStore) {
        this.totalInStore = totalInStore;
    }

    public long getMatched() {
        return matched;
    }

    public void setMatched(long matched) {
        this.matched = matched;
    }

    public long getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(long createdCount) {
        this.createdCount = createdCount;
    }

    public long getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(long updatedCount) {
        this.updatedCount = updatedCount;
    }

    public long getDeactivatedCount() {
        return deactivatedCount;
    }

    public void setDeactivatedCount(long deactivatedCount) {
        this.deactivatedCount = deactivatedCount;
    }

    public long getSkipped() {
        return skipped;
    }

    public void setSkipped(long skipped) {
        this.skipped = skipped;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public Double getMissingPercentage() {
        return missingPercentage;
    }

    public void setMissingPercentage(Double missingPercentage) {
        this.missingPercentage = missingPercentage;
    }

    public boolean isSafetyBrakeTriggered() {
        return safetyBrakeTriggered;
    }

    public void setSafetyBrakeTriggered(boolean safetyBrakeTriggered) {
        this.safetyBrakeTriggered = safetyBrakeTriggered;
    }

    public boolean isOperatorApproved() {
        return operatorApproved;
    }

    public void setOperatorApproved(boolean operatorApproved) {
        this.operatorApproved = operatorApproved;
    }

    public String getOperatorApprovedBy() {
        return operatorApprovedBy;
    }

    public void setOperatorApprovedBy(String operatorApprovedBy) {
        this.operatorApprovedBy = operatorApprovedBy;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovalReason() {
        return approvalReason;
    }

    public void setApprovalReason(String approvalReason) {
        this.approvalReason = approvalReason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
