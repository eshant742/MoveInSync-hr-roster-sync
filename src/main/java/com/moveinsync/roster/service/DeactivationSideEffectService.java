package com.moveinsync.roster.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.entity.OutboxEvent;
import com.moveinsync.roster.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes downstream domain side effects when an employee is deactivated.
 *
 * <p>Requirements addressed:</p>
 * <ul>
 *   <li>Cancel all future cab bookings for the departed employee</li>
 *   <li>Release any assigned vehicle or office seating reservations</li>
 * </ul>
 *
 * <p>Uses the <strong>Transactional Outbox Pattern</strong>: side-effect events
 * are inserted into the {@code outbox_events} table within the same database transaction
 * as the employee status update, guaranteeing that downstream systems never miss
 * a cancellation even across crashes.</p>
 */
@Service
public class DeactivationSideEffectService {

    private static final Logger log = LoggerFactory.getLogger(DeactivationSideEffectService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public DeactivationSideEffectService(OutboxEventRepository outboxEventRepository,
                                         ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates and stages downstream cancellation events in the transactional outbox.
     * Must participate in the caller's active database transaction.
     *
     * @param employee the deactivated employee
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void handleDeactivation(Employee employee) {
        log.info("Triggering deactivation side effects for employee ID: {}, tenant: {}",
                employee.getEmployeeId(), employee.getTenantId());

        try {
            // 1. Emit BOOKING_CANCELLATION event
            Map<String, Object> bookingPayload = new HashMap<>();
            bookingPayload.put("employeeId", employee.getEmployeeId());
            bookingPayload.put("tenantId", employee.getTenantId());
            bookingPayload.put("action", "CANCEL_FUTURE_BOOKINGS");
            bookingPayload.put("effectiveTimestamp", LocalDateTime.now().toString());

            OutboxEvent bookingEvent = OutboxEvent.create(
                    "CAB_BOOKING",
                    employee.getEmployeeId(),
                    "BOOKING_CANCELLATION",
                    employee.getTenantId(),
                    objectMapper.writeValueAsString(bookingPayload)
            );
            outboxEventRepository.save(bookingEvent);

            // 2. Emit SEAT_FREED event
            Map<String, Object> seatPayload = new HashMap<>();
            seatPayload.put("employeeId", employee.getEmployeeId());
            seatPayload.put("tenantId", employee.getTenantId());
            seatPayload.put("shiftCode", employee.getShiftCode());
            seatPayload.put("action", "RELEASE_ALLOCATED_SEAT");
            seatPayload.put("effectiveTimestamp", LocalDateTime.now().toString());

            OutboxEvent seatEvent = OutboxEvent.create(
                    "SEAT_ALLOCATION",
                    employee.getEmployeeId(),
                    "SEAT_FREED",
                    employee.getTenantId(),
                    objectMapper.writeValueAsString(seatPayload)
            );
            outboxEventRepository.save(seatEvent);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize deactivation outbox event for employee {}", employee.getEmployeeId(), e);
            throw new RuntimeException("Could not create deactivation outbox events", e);
        }
    }
}

