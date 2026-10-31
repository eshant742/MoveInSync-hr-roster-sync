package com.moveinsync.roster.repository;

import com.moveinsync.roster.domain.entity.ReconciliationRun;
import com.moveinsync.roster.domain.enums.ReconciliationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ReconciliationRun} entity operations.
 *
 * <p>Manages reconciliation run records that track the periodic comparison
 * between the HR source-of-truth and the local roster state. Each run
 * captures the diff, safety-brake decisions, and approval workflow.</p>
 */
@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    /**
     * Finds the most recent reconciliation run for a given tenant.
     *
     * <p>Used to display the latest reconciliation status on dashboards
     * and to determine if a new reconciliation is needed.</p>
     *
     * @param tenantId the tenant identifier
     * @return the most recent run if any exist
     */
    Optional<ReconciliationRun> findTopByTenantIdOrderByStartedAtDesc(String tenantId);

    /**
     * Checks whether a reconciliation run with the given status exists for a tenant.
     *
     * <p>Primarily used to prevent concurrent reconciliation runs by checking
     * for an existing {@code IN_PROGRESS} run before starting a new one.</p>
     *
     * @param tenantId the tenant identifier
     * @param status   the reconciliation status to check for
     * @return {@code true} if a run with the given status exists
     */
    boolean existsByTenantIdAndStatus(String tenantId, ReconciliationStatus status);

    /**
     * Retrieves reconciliation runs for a tenant ordered by most recent first.
     *
     * @param tenantId the tenant identifier
     * @param pageable pagination parameters
     * @return list of reconciliation runs
     */
    List<ReconciliationRun> findByTenantIdOrderByStartedAtDesc(String tenantId, Pageable pageable);
}
