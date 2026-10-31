package com.moveinsync.roster.repository;

import com.moveinsync.roster.domain.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Repository for {@link Employee} entity operations.
 *
 * <p>Provides efficient data-access methods for employee lifecycle management
 * including activation/deactivation, tenant-scoped lookups, and streaming
 * queries for large-dataset reconciliation.</p>
 *
 * <p><strong>Note:</strong> Methods returning {@link Stream} must be used
 * within a read-only {@code @Transactional} context and the stream must
 * be closed after use (try-with-resources recommended).</p>
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Finds an employee by their business identifier within a specific tenant.
     *
     * @param employeeId the HR-system employee identifier
     * @param tenantId   the tenant (organisation) identifier
     * @return the employee if found
     */
    Optional<Employee> findByEmployeeIdAndTenantId(String employeeId, String tenantId);

    /**
     * Retrieves all employees for a tenant filtered by active status.
     *
     * @param tenantId the tenant identifier
     * @param active   whether to retrieve active or inactive employees
     * @return list of matching employees
     */
    List<Employee> findByTenantIdAndActive(String tenantId, boolean active);

    /**
     * Counts employees for a tenant filtered by active status.
     *
     * @param tenantId the tenant identifier
     * @param active   whether to count active or inactive employees
     * @return the count of matching employees
     */
    long countByTenantIdAndActive(String tenantId, boolean active);

    /**
     * Checks whether an employee exists for the given business identifier and tenant.
     *
     * @param employeeId the HR-system employee identifier
     * @param tenantId   the tenant identifier
     * @return {@code true} if the employee exists
     */
    boolean existsByEmployeeIdAndTenantId(String employeeId, String tenantId);

    /**
     * Streams active employee IDs for a given tenant.
     *
     * <p>Used during reconciliation to build the current-state set without
     * loading full entity graphs into memory. Must be consumed within
     * a {@code @Transactional(readOnly = true)} boundary.</p>
     *
     * @param tenantId the tenant identifier
     * @return a stream of active employee identifiers
     */
    @Query("SELECT e.employeeId FROM Employee e WHERE e.tenantId = :tenantId AND e.active = true")
    Stream<String> streamActiveEmployeeIdsByTenantId(@Param("tenantId") String tenantId);

    /**
     * Streams all active employee entities for a given tenant.
     *
     * <p>Designed for efficient diff-based reconciliation where full entity
     * data is needed for comparison. Must be consumed within a
     * {@code @Transactional(readOnly = true)} boundary.</p>
     *
     * @param tenantId the tenant identifier
     * @return a stream of active employees
     */
    @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId AND e.active = true")
    Stream<Employee> streamActiveByTenantId(@Param("tenantId") String tenantId);

    /**
     * Deactivates an active employee by setting {@code active = false}
     * and updating the {@code updatedAt} timestamp.
     *
     * <p>This is an idempotent operation — it only affects rows where
     * {@code active = true}, preventing redundant updates.</p>
     *
     * @param employeeId the HR-system employee identifier
     * @param tenantId   the tenant identifier
     * @return the number of rows updated (0 or 1)
     */
    @Modifying
    @Query("UPDATE Employee e SET e.active = false, e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.employeeId = :employeeId AND e.tenantId = :tenantId AND e.active = true")
    int deactivateEmployee(@Param("employeeId") String employeeId,
                           @Param("tenantId") String tenantId);

    /**
     * Returns a paginated list of all employees for a given tenant.
     *
     * @param tenantId the tenant identifier
     * @param pageable pagination and sorting parameters
     * @return a page of employees
     */
    Page<Employee> findByTenantId(String tenantId, Pageable pageable);
}
