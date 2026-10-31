package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.dto.BatchEventRequestDto;
import com.moveinsync.roster.dto.BatchEventResponseDto;
import com.moveinsync.roster.dto.EmployeeEventDto;
import com.moveinsync.roster.dto.EventResultDto;
import com.moveinsync.roster.repository.EmployeeRepository;
import com.moveinsync.roster.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Employee Event Processing - Idempotency & Out-of-Order Tests")
class EmployeeEventServiceTest {

    @Autowired
    private EmployeeEventService employeeEventService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final String TENANT = "TENANT-TEST";

    @BeforeEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create employee on valid CREATE event and stage Outbox event")
    void shouldCreateEmployee() {
        EmployeeEventDto event = EmployeeEventDto.builder()
                .employeeId("EMP-001")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Rohan")
                .lastName("Verma")
                .email("rohan.verma@test.com")
                .phone("+919123456780")
                .officeCode("BLR-MAIN")
                .shiftCode("GEN")
                .build();

        EventResultDto result = employeeEventService.processSingleEvent(event);
        assertEquals("CREATED", result.getStatus());

        var opt = employeeRepository.findByEmployeeIdAndTenantId("EMP-001", TENANT);
        assertTrue(opt.isPresent());
        Employee emp = opt.get();
        assertEquals("Rohan", emp.getFirstName());
        assertEquals("BLR-MAIN", emp.getOfficeCode());
        assertEquals(1L, emp.getHrVersion());
        assertTrue(emp.isActive());

        // Verify transactional outbox event was generated
        assertEquals(1, outboxEventRepository.count());
    }

    @Test
    @DisplayName("Idempotency: Replaying identical version must be a no-op")
    void shouldHandleDuplicateEventIdempotently() {
        EmployeeEventDto v1 = EmployeeEventDto.builder()
                .employeeId("EMP-002")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Priya")
                .lastName("Nair")
                .email("priya.nair@test.com")
                .officeCode("BLR-MAIN")
                .build();

        EventResultDto res1 = employeeEventService.processSingleEvent(v1);
        assertEquals("CREATED", res1.getStatus());

        // Replay exact same event
        EventResultDto res2 = employeeEventService.processSingleEvent(v1);
        assertEquals("DUPLICATE", res2.getStatus());

        // Assert employee state remains unchanged and version is still 1
        var emp = employeeRepository.findByEmployeeIdAndTenantId("EMP-002", TENANT).orElseThrow();
        assertEquals(1L, emp.getHrVersion());
    }

    @Test
    @DisplayName("Out-of-Order Handling: Office transfer (v7) before shift change (v6) - v6 must be ignored")
    void shouldHandleOutOfOrderEventsCorrectly() {
        // Step 1: Initial creation at version 5
        EmployeeEventDto v5 = EmployeeEventDto.builder()
                .employeeId("EMP-003")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(5L)
                .firstName("Ananya")
                .lastName("Sen")
                .email("ananya.sen@test.com")
                .officeCode("MUM-01")
                .shiftCode("MORNING")
                .build();
        employeeEventService.processSingleEvent(v5);

        // Step 2: Version 7 arrives BEFORE Version 6 (transfer to BLR-01)
        EmployeeEventDto v7 = EmployeeEventDto.builder()
                .employeeId("EMP-003")
                .tenantId(TENANT)
                .eventType(EventType.UPDATE)
                .version(7L)
                .officeCode("BLR-01")
                .shiftCode("NIGHT")
                .build();
        EventResultDto res7 = employeeEventService.processSingleEvent(v7);
        assertEquals("APPLIED", res7.getStatus());

        // Step 3: Delayed Version 6 arrives now (stale shift change)
        EmployeeEventDto v6 = EmployeeEventDto.builder()
                .employeeId("EMP-003")
                .tenantId(TENANT)
                .eventType(EventType.UPDATE)
                .version(6L)
                .shiftCode("AFTERNOON")
                .build();
        EventResultDto res6 = employeeEventService.processSingleEvent(v6);
        assertEquals("STALE", res6.getStatus());

        // Verify final state reflects Version 7, not stale Version 6
        Employee finalEmp = employeeRepository.findByEmployeeIdAndTenantId("EMP-003", TENANT).orElseThrow();
        assertEquals(7L, finalEmp.getHrVersion());
        assertEquals("BLR-01", finalEmp.getOfficeCode());
        assertEquals("NIGHT", finalEmp.getShiftCode());
    }

    @Test
    @DisplayName("Partial failure isolation: One bad record in a batch must never fail the whole batch")
    void shouldIsolateInvalidRecordsInBatch() {
        EmployeeEventDto valid1 = EmployeeEventDto.builder()
                .employeeId("EMP-BATCH-1")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Valid")
                .lastName("One")
                .email("valid1@test.com")
                .officeCode("BLR-01")
                .build();

        // Invalid record: malformed email and negative version
        EmployeeEventDto invalid = EmployeeEventDto.builder()
                .employeeId("EMP-BATCH-BAD")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(-5L)
                .firstName("Bad")
                .lastName("Record")
                .email("not-an-email")
                .build();

        EmployeeEventDto valid2 = EmployeeEventDto.builder()
                .employeeId("EMP-BATCH-2")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Valid")
                .lastName("Two")
                .email("valid2@test.com")
                .officeCode("BLR-02")
                .build();

        BatchEventRequestDto batch = BatchEventRequestDto.builder()
                .events(List.of(valid1, invalid, valid2))
                .build();

        BatchEventResponseDto response = employeeEventService.processBatch(batch);

        assertEquals(3, response.getTotalReceived());
        assertEquals(2, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());

        // The two valid employees must be present in DB
        assertTrue(employeeRepository.findByEmployeeIdAndTenantId("EMP-BATCH-1", TENANT).isPresent());
        assertTrue(employeeRepository.findByEmployeeIdAndTenantId("EMP-BATCH-2", TENANT).isPresent());
        assertFalse(employeeRepository.findByEmployeeIdAndTenantId("EMP-BATCH-BAD", TENANT).isPresent());
    }

    @Test
    @DisplayName("Deactivation: Sets active=false and triggers downstream booking cancellations")
    void shouldDeactivateEmployeeAndTriggerDownstreamSideEffects() {
        EmployeeEventDto create = EmployeeEventDto.builder()
                .employeeId("EMP-EXIT-1")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Dev")
                .lastName("Patel")
                .email("dev.patel@test.com")
                .officeCode("DEL-01")
                .build();
        employeeEventService.processSingleEvent(create);

        // Deactivation event
        EmployeeEventDto deactivate = EmployeeEventDto.builder()
                .employeeId("EMP-EXIT-1")
                .tenantId(TENANT)
                .eventType(EventType.DEACTIVATE)
                .version(2L)
                .build();

        EventResultDto result = employeeEventService.processSingleEvent(deactivate);
        assertEquals("APPLIED", result.getStatus());

        Employee emp = employeeRepository.findByEmployeeIdAndTenantId("EMP-EXIT-1", TENANT).orElseThrow();
        assertFalse(emp.isActive());
        assertEquals(2L, emp.getHrVersion());

        // Verify outbox has BOOKING_CANCELLATION and SEAT_FREED events
        var events = outboxEventRepository.findAll();
        assertTrue(events.stream().anyMatch(e -> "BOOKING_CANCELLATION".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> "SEAT_FREED".equals(e.getEventType())));
    }
}
