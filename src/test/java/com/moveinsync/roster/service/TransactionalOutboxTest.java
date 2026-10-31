package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.entity.OutboxEvent;
import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.dto.EmployeeEventDto;
import com.moveinsync.roster.repository.EmployeeRepository;
import com.moveinsync.roster.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Transactional Outbox Pattern & Crash Recovery Tests")
class TransactionalOutboxTest {

    @Autowired
    private EmployeeEventService employeeEventService;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final String TENANT = "TENANT-CRASH-TEST";

    @BeforeEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("Crash Simulation: Proves outbox recovers and dispatches events if process crashes between DB write and external publish")
    void testCrashBetweenDatabaseWriteAndExternalPublish() {
        // Step 1: Ingest event — DB transaction writes employee AND stages outbox event
        EmployeeEventDto event = EmployeeEventDto.builder()
                .employeeId("EMP-CRASH-01")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.singh@crash.com")
                .officeCode("HYD-01")
                .build();

        employeeEventService.processSingleEvent(event);

        // Step 2: Simulate process crash before message broker publishing occurred
        // In this state: Employee is saved in DB, OutboxEvent is saved with published = false
        var empOpt = employeeRepository.findByEmployeeIdAndTenantId("EMP-CRASH-01", TENANT);
        assertTrue(empOpt.isPresent(), "Employee state must be persisted");

        var unpublishedEvents = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc(
                org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertEquals(1, unpublishedEvents.size(), "Outbox event must be staged and unpublished");
        OutboxEvent stagedEvent = unpublishedEvents.get(0);
        assertFalse(stagedEvent.isPublished(), "Event must still be unpublished (simulating post-crash state)");
        assertEquals("EMPLOYEE_CREATED", stagedEvent.getEventType());
        assertEquals("EMP-CRASH-01", stagedEvent.getAggregateId());

        // Step 3: Application recovers (or next background polling cycle runs)
        int publishedCount = outboxPublisherService.publishNow();
        assertEquals(1, publishedCount, "Outbox poller must pick up and publish the recovered event");

        // Step 4: Verify outbox event is now marked published and zero pending events remain
        assertEquals(0, outboxPublisherService.getPendingCount(), "Pending count must be 0 after recovery");
        OutboxEvent publishedEvent = outboxEventRepository.findById(stagedEvent.getId()).orElseThrow();
        assertTrue(publishedEvent.isPublished(), "Outbox event must now be marked published");
        assertNotNull(publishedEvent.getPublishedAt(), "Publication timestamp must be recorded");
    }

    @Test
    @DisplayName("Rollback Safety: If DB transaction fails, no orphan outbox events are ever created")
    void testNoOrphanOutboxEventOnRollback() {
        long initialOutboxCount = outboxEventRepository.count();

        // Attempt invalid event that fails boundary validation
        EmployeeEventDto invalid = EmployeeEventDto.builder()
                .employeeId("EMP-ROLLBACK")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .email("invalid-email") // fails validation
                .officeCode(null)       // fails validation
                .build();

        employeeEventService.processSingleEvent(invalid);

        // Verify no outbox event was staged
        assertEquals(initialOutboxCount, outboxEventRepository.count(),
                "No outbox events must be created when transaction does not commit employee state");
    }
}

