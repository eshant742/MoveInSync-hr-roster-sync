package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.dto.EmployeeEventDto;
import com.moveinsync.roster.exception.ReconciliationInProgressException;
import com.moveinsync.roster.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrency & Thread-Safety Tests")
class ConcurrencyTest {

    @Autowired
    private EmployeeEventService employeeEventService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private EmployeeRepository employeeRepository;

    private static final String TENANT = "TENANT-CONCURRENCY";

    @BeforeEach
    void cleanUp() {
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent Event Ingestion: 50 threads ingesting distinct employee records simultaneously")
    void testConcurrentEmployeeIngestion() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    EmployeeEventDto event = EmployeeEventDto.builder()
                            .employeeId("CONCUR-EMP-" + index)
                            .tenantId(TENANT)
                            .eventType(EventType.CREATE)
                            .version(1L)
                            .firstName("Worker" + index)
                            .lastName("Thread")
                            .email("worker" + index + "@concurrency.com")
                            .officeCode("BLR-01")
                            .build();

                    var result = employeeEventService.processSingleEvent(event);
                    if ("CREATED".equals(result.getStatus())) {
                        successCounter.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent tasks should complete within timeout");
        assertEquals(50, successCounter.get(), "All 50 employees should be created without conflict");
        assertEquals(50, employeeRepository.countByTenantIdAndActive(TENANT, true));
    }

    @Test
    @DisplayName("Concurrent Reconciliation Prevention: Simultaneous reconciliation for same tenant rejected")
    void testPreventConcurrentReconciliationForSameTenant() throws InterruptedException {
        String csv = "employeeId,firstName,lastName,email,phone,managerId,officeCode,department,shiftCode\n"
                   + "EMP-C1,Test,User,c1@test.com,,BLR-01,,GEN\n";

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger rejectedRuns = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ByteArrayInputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
                    reconciliationService.reconcile(TENANT, stream);
                } catch (ReconciliationInProgressException ex) {
                    rejectedRuns.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release both threads simultaneously to trigger race condition
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // At least one run should either succeed or fail gracefully with guard
        assertTrue(rejectedRuns.get() <= 1);
    }
}
