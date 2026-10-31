package com.moveinsync.roster.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.roster.config.CacheConfig;
import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.entity.OutboxEvent;
import com.moveinsync.roster.dto.EmployeeResponseDto;
import com.moveinsync.roster.dto.TransportFieldsUpdateDto;
import com.moveinsync.roster.exception.EmployeeNotFoundException;
import com.moveinsync.roster.repository.EmployeeRepository;
import com.moveinsync.roster.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service dedicated to managing Transport-Admin-owned employee fields.
 *
 * <p>Requirements addressed:</p>
 * <ul>
 *   <li>Transport administrators independently manage {@code pickupPoint} and
 *       {@code specialAssistanceNeeds}.</li>
 *   <li>These fields are never overwritten by HR events or nightly reconciliation runs.</li>
 *   <li>Updates produce an outbox event so routing and vehicle allocation services
 *       are notified immediately.</li>
 * </ul>
 */
@Service
public class TransportFieldsService {

    private static final Logger log = LoggerFactory.getLogger(TransportFieldsService.class);

    private final EmployeeRepository employeeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransportFieldsService(EmployeeRepository employeeRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  ObjectMapper objectMapper) {
        this.employeeRepository = employeeRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Updates the transport-specific fields for an employee.
     *
     * @param dto the update request
     * @return the updated employee state representation
     */
    @Transactional
    @CacheEvict(value = CacheConfig.EMPLOYEES_CACHE, key = "#dto.employeeId + '_' + #dto.tenantId")
    public EmployeeResponseDto updateTransportFields(TransportFieldsUpdateDto dto) {
        String empId = dto.getEmployeeId();
        String tenantId = dto.getTenantId();

        Employee employee = employeeRepository.findByEmployeeIdAndTenantId(empId, tenantId)
                .orElseThrow(() -> new EmployeeNotFoundException(empId, tenantId));

        employee.applyTransportFields(dto);
        employeeRepository.save(employee);

        createTransportUpdatedOutboxEvent(employee);

        log.info("Transport-admin fields updated for employee ID {} in tenant {}", empId, tenantId);

        return mapToDto(employee);
    }

    private void createTransportUpdatedOutboxEvent(Employee employee) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("employeeId", employee.getEmployeeId());
            payload.put("tenantId", employee.getTenantId());
            payload.put("pickupPoint", employee.getPickupPoint());
            payload.put("specialAssistanceNeeds", employee.getSpecialAssistanceNeeds());
            payload.put("timestamp", LocalDateTime.now().toString());

            OutboxEvent outboxEvent = OutboxEvent.create(
                    "TRANSPORT_PROFILE",
                    employee.getEmployeeId(),
                    "TRANSPORT_FIELDS_UPDATED",
                    employee.getTenantId(),
                    objectMapper.writeValueAsString(payload)
            );
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transport outbox event for employee {}", employee.getEmployeeId(), e);
            throw new RuntimeException("Could not create transport outbox event", e);
        }
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
}
