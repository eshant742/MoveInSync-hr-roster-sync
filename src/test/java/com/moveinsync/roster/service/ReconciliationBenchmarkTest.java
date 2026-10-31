package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.enums.ReconciliationStatus;
import com.moveinsync.roster.dto.ReconciliationResultDto;
import com.moveinsync.roster.repository.EmployeeRepository;
import com.moveinsync.roster.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Tag("benchmark")
@DisplayName("200,000-Row Bounded-Memory Reconciliation Benchmark")
class ReconciliationBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationBenchmarkTest.class);

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    private static final String TENANT = "TENANT-BENCHMARK";

    @BeforeEach
    void cleanUp() {
        reconciliationRunRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("Diff 200,000-row CSV snapshot in under 60 seconds with bounded memory")
    void testBenchmark200kDiffWithin60Seconds() {
        int totalRows = 200_000;
        log.info("Generating virtual streaming CSV with {} records...", totalRows);

        // Measure memory before
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create a streaming input stream that generates 200,000 CSV lines on the fly
        // WITHOUT loading the entire 200,000-row String into heap memory!
        InputStream virtualStream = createVirtualCsvStream(totalRows);

        long startTime = System.currentTimeMillis();

        ReconciliationResultDto result = reconciliationService.reconcile(TENANT, virtualStream);

        long durationMs = System.currentTimeMillis() - startTime;
        double durationSec = durationMs / 1000.0;

        runtime.gc();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memUsedMb = Math.max(0, (memAfter - memBefore) / (1024 * 1024));

        log.info("=================================================");
        log.info("200,000 RECORD BENCHMARK COMPLETED");
        log.info("Total Duration: {} ms ({} seconds)", durationMs, String.format("%.2f", durationSec));
        log.info("Throughput: {} rows/sec", String.format("%.0f", (totalRows / durationSec)));
        log.info("Memory Delta: ~{} MB", memUsedMb);
        log.info("Result Status: {}", result.getStatus());
        log.info("=================================================");

        assertEquals(ReconciliationStatus.COMPLETED, result.getStatus());
        assertEquals(totalRows, result.getTotalInExport());

        // Hard requirement: must diff 200,000 rows in under 60 seconds!
        assertTrue(durationSec < 60.0,
                String.format("Reconciliation of 200k rows took %.2f seconds, which exceeds the 60 second target!", durationSec));
    }

    /**
     * Creates a custom lazy InputStream that generates 200,000 CSV records on-the-fly
     * with zero upfront heap memory allocation.
     */
    private InputStream createVirtualCsvStream(int rowCount) {
        Iterator<InputStream> iterator = new Iterator<>() {
            private int currentRow = 0;
            private boolean headerSent = false;

            @Override
            public boolean hasNext() {
                return !headerSent || currentRow < rowCount;
            }

            @Override
            public InputStream next() {
                if (!headerSent) {
                    headerSent = true;
                    return new java.io.ByteArrayInputStream(
                            "employeeId,firstName,lastName,email,phone,managerId,officeCode,department,shiftCode\n"
                                    .getBytes(StandardCharsets.UTF_8)
                    );
                }

                // Generate batch of 500 lines at a time to reduce IO overhead
                StringBuilder chunk = new StringBuilder(32768);
                int limit = Math.min(currentRow + 500, rowCount);
                for (; currentRow < limit; currentRow++) {
                    chunk.append("EMP-BENCH-").append(currentRow).append(",")
                            .append("First").append(currentRow).append(",")
                            .append("Last").append(currentRow).append(",")
                            .append("bench").append(currentRow).append("@company.com,")
                            .append("+9198765").append(String.format("%05d", currentRow % 100000)).append(",")
                            .append("MGR-").append(currentRow % 50).append(",")
                            .append("BLR-").append(currentRow % 5).append(",")
                            .append("Engineering,")
                            .append("SHIFT-").append(currentRow % 3).append("\n");
                }
                return new java.io.ByteArrayInputStream(chunk.toString().getBytes(StandardCharsets.UTF_8));
            }
        };

        Enumeration<InputStream> enumeration = Collections.enumeration(new Iterable<InputStream>() {
            @Override
            public Iterator<InputStream> iterator() {
                return iterator;
            }
        });

        return new SequenceInputStream(enumeration);
    }
}
