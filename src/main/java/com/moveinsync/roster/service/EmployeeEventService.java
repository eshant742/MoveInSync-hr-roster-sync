package com.moveinsync.roster.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.roster.config.CacheConfig;
import com.moveinsync.roster.config.MetricsConfig;
import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.entity.IngestionError;
import com.moveinsync.roster.domain.entity.OutboxEvent;
import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.domain.enums.IngestionSource;
import com.moveinsync.roster.dto.BatchEventRequestDto;
import com.moveinsync.roster.dto.BatchEventResponseDto;
import com.moveinsync.roster.dto.EmployeeEventDto;
import com.moveinsync.roster.dto.EventResultDto;
import com.moveinsync.roster.repository.EmployeeRepository;
import com.moveinsync.roster.repository.IngestionErrorRepository;
import com.moveinsync.roster.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core event processing engine implementing idempotent, out-of-order,
 * and self-healing employee synchronisation.
 *
 * <h3>Key Guarantees</h3>
 * <ul>
 *   <li><strong>Per-Employee Version Ordering:</strong> Updates are applied only if the incoming
 *       version is strictly greater than the stored version. Stale events are safely ignored.</li>
 *   <li><strong>Idempotency:</strong> Replaying the identical event with the same version is a no-op.</li>
 *   <li><strong>Field-Level Ownership:</strong> HR updates modify only HR-owned fields (name, email,
 *       office, manager, department, shift). Transport-admin fields (pickup point, special assistance)
 *       are strictly protected from being overwritten.</li>
 *   <li><strong>Transactional Outbox:</strong> All state changes and corresponding downstream events
 *       are written within the same database transaction.</li>
 *   <li><strong>Partial Failure Isolation:</strong> In validation and batch processing, a failure in
 *       one record does not abort processing of the remaining batch.</li>
 * </ul>
 */
@Service
public class EmployeeEventService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventService.class);

    private final EmployeeRepository employeeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IngestionErrorRepository ingestionErrorRepository;
    private final EventValidationService validationService;
    private final DeactivationSideEffectService deactivationSideEffectService;
    private final MetricsConfig.RosterMetrics metrics;
    private final ObjectMapper objectMapper;

    public EmployeeEventService(EmployeeRepository employeeRepository,
                                OutboxEventRepository outboxEventRepository,
                                IngestionErrorRepository ingestionErrorRepository,
                                EventValidationService validationService,
                                DeactivationSideEffectService deactivationSideEffectService,
                                MetricsConfig.RosterMetrics metrics,
                                ObjectMapper objectMapper) {
        this.employeeRepository = employeeRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.ingestionErrorRepository = ingestionErrorRepository;
        this.validationService = validationService;
        this.deactivationSideEffectService = deactivationSideEffectService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a batch of employee events from an HR source.
     * Each event is processed in isolation; validation errors on one record
     * do not prevent other valid records in the batch from being committed.
     *
     * @param request the batch request containing one or more employee events
     * @return the detailed batch processing summary
     */
    @Transactional
    public BatchEventResponseDto processBatch(BatchEventRequestDto request) {
        if (request == null || request.getEvents() == null || request.getEvents().isEmpty()) {
            return BatchEventResponseDto.builder()
                    .totalReceived(0)
                    .successCount(0)
                    .failedCount(0)
                    .duplicateCount(0)
                    .staleCount(0)
                    .results(List.of())
                    .build();
        }

        List<EventResultDto> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int duplicateCount = 0;
        int staleCount = 0;

        for (EmployeeEventDto event : request.getEvents()) {
            EventResultDto result = processSingleEvent(event);
            results.add(result);

            switch (result.getStatus()) {
                case "CREATED":
                case "APPLIED":
                    successCount++;
                    break;
                case "DUPLICATE":
                    duplicateCount++;
                    break;
                case "STALE":
                    staleCount++;
                    break;
                case "REJECTED":
                default:
                    failedCount++;
                    break;
            }
        }

        log.info("Batch processed: total={}, success={}, duplicate={}, stale={}, failed={}",
                request.getEvents().size(), successCount, duplicateCount, staleCount, failedCount);

        return BatchEventResponseDto.builder()
                .totalReceived(request.getEvents().size())
                .successCount(successCount)
                .failedCount(failedCount)
                .duplicateCount(duplicateCount)
                .staleCount(staleCount)
                .results(results)
                .build();
    }

    /**
     * Processes a single employee event with validation, idempotency checks,
     * and transactional outbox event creation.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.EMPLOYEES_CACHE, key = "#event.employeeId + '_' + #event.tenantId", condition = "#event != null && #event.employeeId != null && #event.tenantId != null")
    public EventResultDto processSingleEvent(EmployeeEventDto event) {
        if (event == null) {
            return EventResultDto.builder()
                    .status("REJECTED")
                    .message("Event payload was null")
                    .build();
        }

        String empId = event.getEmployeeId();
        String tenantId = event.getTenantId();

        // 1. Boundary Validation
        List<String> validationErrors = validationService.validate(event);
        if (!validationErrors.isEmpty()) {
            recordError(tenantId, empId, "VALIDATION_FAILED", String.join("; ", validationErrors));
            metrics.getEventsRejected().increment();
            return EventResultDto.builder()
                    .employeeId(empId)
                    .status("REJECTED")
                    .message("Validation failed: " + String.join(", ", validationErrors))
                    .build();
        }

        Optional<Employee> existingOpt = employeeRepository.findByEmployeeIdAndTenantId(empId, tenantId);

        // 2. Handle CREATE event
        if (event.getEventType() == EventType.CREATE) {
            return handleCreate(event, existingOpt);
        }

        // 3. Handle UPDATE event
        if (event.getEventType() == EventType.UPDATE) {
            return handleUpdate(event, existingOpt);
        }

        // 4. Handle DEACTIVATE event
        if (event.getEventType() == EventType.DEACTIVATE) {
            return handleDeactivate(event, existingOpt);
        }

        return EventResultDto.builder()
                .employeeId(empId)
                .status("REJECTED")
                .message("Unsupported event type: " + event.getEventType())
                .build();
    }

    private EventResultDto handleCreate(EmployeeEventDto event, Optional<Employee> existingOpt) {
        String empId = event.getEmployeeId();
        String tenantId = event.getTenantId();

        if (existingOpt.isPresent()) {
            Employee existing = existingOpt.get();
            // Check version against existing record
            if (event.getVersion() == existing.getHrVersion()) {
                metrics.getEventsDuplicate().increment();
                log.debug("Duplicate CREATE event received for employee ID {}, version {}", empId, event.getVersion());
                return EventResultDto.builder()
                        .employeeId(empId)
                        .status("DUPLICATE")
                        .message("Record already exists with identical version " + event.getVersion())
                        .build();
            } else if (event.getVersion() < existing.getHrVersion()) {
                metrics.getEventsStale().increment();
                log.debug("Stale CREATE event for employee ID {} (incoming {}, current {})",
                        empId, event.getVersion(), existing.getHrVersion());
                return EventResultDto.builder()
                        .employeeId(empId)
                        .status("STALE")
                        .message("Incoming version " + event.getVersion() + " is older than current " + existing.getHrVersion())
                        .build();
            } else {
                // Incoming version is newer; upgrade/reactivate existing record
                applyHrFieldsOnly(existing, event);
                existing.setActive(true);
                existing.setHrVersion(event.getVersion());
                employeeRepository.save(existing);
                createOutboxEvent(existing, "EMPLOYEE_UPDATED");
                metrics.getEventsApplied().increment();
                return EventResultDto.builder()
                        .employeeId(empId)
                        .status("APPLIED")
                        .message("Existing employee updated via newer CREATE event version " + event.getVersion())
                        .build();
            }
        }

        // Fresh employee creation
        Employee employee = new Employee();
        employee.setEmployeeId(empId);
        employee.setTenantId(tenantId);
        employee.setActive(true);
        employee.setHrVersion(event.getVersion());
        applyHrFieldsOnly(employee, event);

        employeeRepository.save(employee);
        createOutboxEvent(employee, "EMPLOYEE_CREATED");
        metrics.getEventsApplied().increment();

        log.info("Created new employee ID {} in tenant {}, version {}", empId, tenantId, event.getVersion());
        return EventResultDto.builder()
                .employeeId(empId)
                .status("CREATED")
                .message("Employee record created successfully")
                .build();
    }

    private EventResultDto handleUpdate(EmployeeEventDto event, Optional<Employee> existingOpt) {
        String empId = event.getEmployeeId();
        String tenantId = event.getTenantId();

        if (existingOpt.isEmpty()) {
            recordError(tenantId, empId, "EMPLOYEE_NOT_FOUND", "Cannot update non-existent employee");
            metrics.getEventsRejected().increment();
            return EventResultDto.builder()
                    .employeeId(empId)
                    .status("REJECTED")
                    .message("Employee not found for update")
                    .build();
        }

        Employee employee = existingOpt.get();

        if (event.getVersion() == employee.getHrVersion()) {
            metrics.getEventsDuplicate().increment();
            log.debug("Duplicate UPDATE event for employee ID {}, version {}", empId, event.getVersion());
            return EventResultDto.builder()
                    .employeeId(empId)
                    .status("DUPLICATE")
                    .message("Update skipped: identical version " + event.getVersion())
                    .build();
        }

        if (event.getVersion() < employee.getHrVersion()) {
            metrics.getEventsStale().increment();
            log.debug("Stale UPDATE event ignored for employee ID {} (incoming {}, current {})",
                    empId, event.getVersion(), employee.getHrVersion());
            return EventResultDto.builder()
                    .employeeId(empId)
                    .status("STALE")
                    .message("Stale update ignored: incoming " + event.getVersion() + " < current " + employee.getHrVersion())
                    .build();
        }

        // Apply HR-owned fields ONLY — preserving transport admin fields
        applyHrFieldsOnly(employee, event);
        employee.setHrVersion(event.getVersion());
        employeeRepository.save(employee);

        createOutboxEvent(employee, "EMPLOYEE_UPDATED");
        metrics.getEventsApplied().increment();

        log.debug("Updated employee ID {} to version {}", empId, event.getVersion());
        return EventResultDto.builder()
                .employeeId(empId)
                .status("APPLIED")
                .message("Employee updated successfully")
                .build();
    }

    private EventResultDto handleDeactivate(EmployeeEventDto event, Optional<Employee> existingOpt) {
        String empId = event.getEmployeeId();
        String tenantId = event.getTenantId();

        if (existingOpt.isEmpty()) {
            metrics.getEventsDuplicate().increment();
            return EventResultDto.builder()
                    .employeeId(empId)
                    .status("DUPLICATE")
                    .message("Employee already does not exist")
                    .build();
        }

        Employee employee = existingOpt.get();

        if (event.getVersion() < employee.getHrVersion()) {
            metrics.getEventsStale().increment();
            return EventResultDto.builder()
                    .employeeId(empId)
                    .status("STALE")
                    .message("Stale deactivation ignored")
                    .build();
        }

        if (!employee.isActive() && event.getVersion() == employee.getHrVersion()) {
            metrics.getEventsDuplicate().increment();
            return EventResultDto.builder()
                    .employeeId(empId)
                    .status("DUPLICATE")
                    .message("Employee already deactivated at version " + employee.getHrVersion())
                    .build();
        }

        employee.setActive(false);
        employee.setHrVersion(event.getVersion());
        employeeRepository.save(employee);

        // Emit deactivation outbox event
        createOutboxEvent(employee, "EMPLOYEE_DEACTIVATED");

        // Trigger downstream side effects (cancel cab bookings, release seat)
        deactivationSideEffectService.handleDeactivation(employee);

        metrics.getEventsApplied().increment();
        log.info("Deactivated employee ID {} in tenant {}, version {}", empId, tenantId, event.getVersion());

        return EventResultDto.builder()
                .employeeId(empId)
                .status("APPLIED")
                .message("Employee deactivated and downstream reservations cancelled")
                .build();
    }

    /**
     * Strictly modifies only HR-owned attributes.
     * Transport-admin-owned fields (pickupPoint, specialAssistanceNeeds) are never touched.
     */
    private void applyHrFieldsOnly(Employee employee, EmployeeEventDto event) {
        if (event.getFirstName() != null) {
            employee.setFirstName(event.getFirstName());
        }
        if (event.getLastName() != null) {
            employee.setLastName(event.getLastName());
        }
        if (event.getEmail() != null) {
            employee.setEmail(event.getEmail());
        }
        if (event.getPhone() != null) {
            employee.setPhone(event.getPhone());
        }
        if (event.getManagerId() != null) {
            employee.setManagerId(event.getManagerId());
        }
        if (event.getOfficeCode() != null) {
            employee.setOfficeCode(event.getOfficeCode());
        }
        if (event.getDepartment() != null) {
            employee.setDepartment(event.getDepartment());
        }
        if (event.getShiftCode() != null) {
            employee.setShiftCode(event.getShiftCode());
        }
    }

    private void createOutboxEvent(Employee employee, String eventType) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("employeeId", employee.getEmployeeId());
            payload.put("tenantId", employee.getTenantId());
            payload.put("version", employee.getHrVersion());
            payload.put("active", employee.isActive());
            payload.put("officeCode", employee.getOfficeCode());
            payload.put("shiftCode", employee.getShiftCode());
            payload.put("department", employee.getDepartment());
            payload.put("pickupPoint", employee.getPickupPoint());
            payload.put("specialAssistanceNeeds", employee.getSpecialAssistanceNeeds());
            payload.put("timestamp", LocalDateTime.now().toString());

            OutboxEvent outboxEvent = OutboxEvent.create(
                    "EMPLOYEE",
                    employee.getEmployeeId(),
                    eventType,
                    employee.getTenantId(),
                    objectMapper.writeValueAsString(payload)
            );
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to construct outbox payload for employee {}", employee.getEmployeeId(), e);
            throw new RuntimeException("Could not serialize outbox payload", e);
        }
    }

    private void recordError(String tenantId, String employeeId, String errorType, String errorMessage) {
        try {
            IngestionError error = new IngestionError();
            error.setTenantId(tenantId);
            error.setEmployeeId(employeeId);
            error.setErrorType(errorType);
            error.setErrorMessage(errorMessage);
            error.setSource(IngestionSource.API);
            ingestionErrorRepository.save(error);
        } catch (Exception e) {
            log.warn("Failed to persist ingestion error audit log for employee {}: {}", employeeId, e.getMessage());
        }
    }
}
