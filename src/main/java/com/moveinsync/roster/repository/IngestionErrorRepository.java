package com.moveinsync.roster.repository;

import com.moveinsync.roster.domain.entity.IngestionError;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for {@link IngestionError} entity operations.
 *
 * <p>Tracks errors that occur during event ingestion — validation failures,
 * schema mismatches, or processing exceptions. These records provide an
 * audit trail for debugging and operational monitoring.</p>
 */
@Repository
public interface IngestionErrorRepository extends JpaRepository<IngestionError, Long> {

    /**
     * Retrieves ingestion errors for a tenant ordered by most recent first.
     *
     * @param tenantId the tenant identifier
     * @param pageable pagination parameters controlling the result set size
     * @return list of ingestion errors
     */
    List<IngestionError> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Counts the total number of ingestion errors for a given tenant.
     *
     * <p>Used for dashboard metrics and alerting thresholds.</p>
     *
     * @param tenantId the tenant identifier
     * @return the count of ingestion errors
     */
    long countByTenantId(String tenantId);

    /**
     * Deletes ingestion errors older than the specified timestamp for a tenant.
     *
     * <p>Supports retention-policy enforcement to prevent unbounded growth
     * of the error log table.</p>
     *
     * @param tenantId the tenant identifier
     * @param before   the cutoff timestamp; errors created before this are deleted
     */
    void deleteByTenantIdAndCreatedAtBefore(String tenantId, LocalDateTime before);
}
