package com.moveinsync.roster.service;

import com.moveinsync.roster.config.CacheConfig;
import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.entity.IngestionError;
import com.moveinsync.roster.domain.entity.ReconciliationRun;
import com.moveinsync.roster.dto.EmployeeResponseDto;
import com.moveinsync.roster.dto.ErrorReportDto;
import com.moveinsync.roster.dto.ReconciliationResultDto;
import com.moveinsync.roster.exception.EmployeeNotFoundException;
import com.moveinsync.roster.repository.EmployeeRepository;
import com.moveinsync.roster.repository.IngestionErrorRepository;
import com.moveinsync.roster.repository.ReconciliationRunRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only query service for employee profiles, audit error logs,
 * and historical reconciliation runs.
 *
 * <p>Employs Caffeine caching for single-employee lookups to support high-throughput,
 * sub-millisecond retrieval by transport dispatchers.</p>
 */
@Service
@Transactional(readOnly = true)
public class EmployeeQueryService {

    private final EmployeeRepository employeeRepository;
    private final IngestionErrorRepository ingestionErrorRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;

    public EmployeeQueryService(EmployeeRepository employeeRepository,
                                IngestionErrorRepository ingestionErrorRepository,
                                ReconciliationRunRepository reconciliationRunRepository) {
        this.employeeRepository = employeeRepository;
        this.ingestionErrorRepository = ingestionErrorRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
    }

    /**
     * Looks up an employee by business ID and tenant.
     * Results are cached in the Caffeine in-memory cache.
     *
     * @param employeeId the employee business identifier
     * @param tenantId   the tenant identifier
     * @return the employee DTO
     * @throws EmployeeNotFoundException if no record exists
     */
    @Cacheable(value = CacheConfig.EMPLOYEES_CACHE, key = "#employeeId + '_' + #tenantId")
    public EmployeeResponseDto getEmployee(String employeeId, String tenantId) {
        Employee employee = employeeRepository.findByEmployeeIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId, tenantId));
        return mapToDto(employee);
    }

    /**
     * Returns a paginated list of employees for a tenant.
     */
    public Page<EmployeeResponseDto> listEmployees(String tenantId, Pageable pageable) {
        return employeeRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToDto);
    }

    /**
     * Returns ingestion audit errors for a tenant (PII-free).
     */
    public List<ErrorReportDto> getIngestionErrors(String tenantId, Pageable pageable) {
        return ingestionErrorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .stream()
                .map(this::mapErrorToDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns historical reconciliation run records for a tenant.
     */
    public List<ReconciliationResultDto> getReconciliationHistory(String tenantId, Pageable pageable) {
        return reconciliationRunRepository.findByTenantIdOrderByStartedAtDesc(tenantId, pageable)
                .stream()
                .map(this::mapRunToDto)
                .collect(Collectors.toList());
    }

    private EmployeeResponseDto mapToDto(Employee employee) {
        return EmployeeResponseDto.builder()
                .employeeId(employee.getEmployeeId())
                .tenantId(employee.getTenantId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .managerId(employee.getManagerId())
                .officeCode(employee.getOfficeCode())
                .department(employee.getDepartment())
                .shiftCode(employee.getShiftCode())
                .pickupPoint(employee.getPickupPoint())
                .specialAssistanceNeeds(employee.getSpecialAssistanceNeeds())
                .version(employee.getHrVersion())
                .active(employee.isActive())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }

    private ErrorReportDto mapErrorToDto(IngestionError err) {
        return ErrorReportDto.builder()
                .id(err.getId())
                .tenantId(err.getTenantId())
                .employeeId(err.getEmployeeId())
                .errorType(err.getErrorType())
                .errorMessage(err.getErrorMessage())
                .source(err.getSource() != null ? err.getSource().name() : null)
                .createdAt(err.getCreatedAt())
                .build();
    }

    private ReconciliationResultDto mapRunToDto(ReconciliationRun run) {
        return ReconciliationResultDto.builder()
                .runId(run.getId())
                .tenantId(run.getTenantId())
                .status(run.getStatus())
                .totalInExport(run.getTotalInExport())
                .totalInStore(run.getTotalInStore())
                .matched(run.getMatched())
                .createdCount(run.getCreatedCount())
                .updatedCount(run.getUpdatedCount())
                .deactivatedCount(run.getDeactivatedCount())
                .skipped(run.getSkipped())
                .errorCount(run.getErrorCount())
                .missingPercentage(run.getMissingPercentage())
                .safetyBrakeTriggered(run.isSafetyBrakeTriggered())
                .operatorApproved(run.isOperatorApproved())
                .operatorApprovedBy(run.getOperatorApprovedBy())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .message(run.getErrorDetails())
                .build();
    }
}

