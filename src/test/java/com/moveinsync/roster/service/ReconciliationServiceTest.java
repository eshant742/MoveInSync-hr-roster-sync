package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.enums.ReconciliationStatus;
import com.moveinsync.roster.dto.ReconciliationResultDto;
import com.moveinsync.roster.dto.SafetyBrakeApprovalDto;
import com.moveinsync.roster.repository.EmployeeRepository;
import com.moveinsync.roster.repository.OutboxEventRepository;
import com.moveinsync.roster.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Nightly Reconciliation & Safety Brake Circuit Breaker Tests")
class ReconciliationServiceTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final String TENANT = "TENANT-RECON";

    @BeforeEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        reconciliationRunRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("Clean Diff: Correctly computes matches, updates, creations, and deactivations")
    void testStandardReconciliationDiff() {
        // Setup initial DB state:
        // E1: unchanged (matched)
        // E2: will be updated
        // E3: missing from export (will be deactivated)
        createDbEmployee("EMP-1", "Aarav", "Kumar", "aarav@test.com", "BLR-01", "GEN");
        createDbEmployee("EMP-2", "Bhavna", "Sharma", "bhavna@test.com", "BLR-01", "GEN");
        createDbEmployee("EMP-3", "Chetan", "Mehta", "chetan@test.com", "BLR-01", "GEN");

        // CSV export:
        // EMP-1: identical attributes
        // EMP-2: department changed to 'Product', office to 'BLR-02'
        // EMP-4: new employee
        // (EMP-3 is omitted -> 1 of 3 missing = 33.3% missing, which would trigger 30% brake!)
        // To test clean diff without triggering 30% brake, let's have 10 employees so 1 missing is only 10%
        for (int i = 4; i <= 10; i++) {
            createDbEmployee("EMP-" + i, "Name" + i, "Last" + i, "email" + i + "@test.com", "BLR-01", "GEN");
        }

        StringBuilder csv = new StringBuilder();
        csv.append("employeeId,firstName,lastName,email,phone,managerId,officeCode,department,shiftCode\n");
        // EMP-1: unchanged
        csv.append("EMP-1,Aarav,Kumar,aarav@test.com,,BLR-01,,GEN\n");
        // EMP-2: updated office
        csv.append("EMP-2,Bhavna,Sharma,bhavna@test.com,,BLR-02,Product,GEN\n");
        // EMP-4 to EMP-10: unchanged
        for (int i = 4; i <= 10; i++) {
            csv.append("EMP-").append(i).append(",Name").append(i).append(",Last").append(i)
               .append(",email").append(i).append("@test.com,,BLR-01,,GEN\n");
        }
        // EMP-11: brand new employee
        csv.append("EMP-11,NewEmp,Person,new.emp@test.com,,BLR-01,DevOps,GEN\n");

        ByteArrayInputStream stream = new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8));
        ReconciliationResultDto result = reconciliationService.reconcile(TENANT, stream);

        assertEquals(ReconciliationStatus.COMPLETED, result.getStatus());
        assertFalse(result.isSafetyBrakeTriggered());
        assertEquals(8, result.getMatched(), "EMP-1 and EMP-4..10 should be matched");
        assertEquals(1, result.getUpdatedCount(), "EMP-2 should be updated");
        assertEquals(1, result.getCreatedCount(), "EMP-11 should be created");
        assertEquals(1, result.getDeactivatedCount(), "EMP-3 should be deactivated");

        // Verify EMP-3 was actually deactivated in the database
        Employee emp3 = employeeRepository.findByEmployeeIdAndTenantId("EMP-3", TENANT).orElseThrow();
        assertFalse(emp3.isActive(), "EMP-3 must be inactive");

        // Verify EMP-2 has updated attributes
        Employee emp2 = employeeRepository.findByEmployeeIdAndTenantId("EMP-2", TENANT).orElseThrow();
        assertEquals("BLR-02", emp2.getOfficeCode());
        assertEquals("Product", emp2.getDepartment());

        // Verify EMP-11 exists in DB
        assertTrue(employeeRepository.findByEmployeeIdAndTenantId("EMP-11", TENANT).isPresent());
    }

    @Test
    @DisplayName("Safety Brake: Halts reconciliation and deactivates NO ONE if >= 30% active employees missing")
    void testSafetyBrakeTriggersOnBrokenExport() {
        // Setup 10 active employees in DB
        for (int i = 1; i <= 10; i++) {
            createDbEmployee("EMP-" + i, "First" + i, "Last" + i, "user" + i + "@test.com", "BLR-01", "GEN");
        }

        // Broken export with only 6 employees (missing 4 out of 10 = 40% missing >= 30% threshold)
        StringBuilder brokenCsv = new StringBuilder();
        brokenCsv.append("employeeId,firstName,lastName,email,phone,managerId,officeCode,department,shiftCode\n");
        for (int i = 1; i <= 6; i++) {
            brokenCsv.append("EMP-").append(i).append(",First").append(i).append(",Last").append(i)
                    .append(",user").append(i).append("@test.com,,BLR-01,,GEN\n");
        }

        ByteArrayInputStream stream = new ByteArrayInputStream(brokenCsv.toString().getBytes(StandardCharsets.UTF_8));
        ReconciliationResultDto result = reconciliationService.reconcile(TENANT, stream);

        // Safety Brake MUST engage
        assertEquals(ReconciliationStatus.SAFETY_BRAKE_TRIGGERED, result.getStatus());
        assertTrue(result.isSafetyBrakeTriggered());
        assertEquals(0.40, result.getMissingPercentage(), 0.01);
        assertEquals(0, result.getDeactivatedCount(), "Safety brake must deactivate zero employees!");

        // CRITICAL CHECK: Every single employee in DB must still be active!
        long activeCount = employeeRepository.countByTenantIdAndActive(TENANT, true);
        assertEquals(10, activeCount, "All 10 employees must remain active while safety brake is engaged");
    }

    @Test
    @DisplayName("Operator Approval: Operator can approve a safety-braked run")
    void testOperatorApprovalFlow() {
        for (int i = 1; i <= 10; i++) {
            createDbEmployee("EMP-SB-" + i, "First" + i, "Last" + i, "sb" + i + "@test.com", "BLR-01", "GEN");
        }

        // Export missing 50%
        StringBuilder brokenCsv = new StringBuilder();
        brokenCsv.append("employeeId,firstName,lastName,email,phone,managerId,officeCode,department,shiftCode\n");
        for (int i = 1; i <= 5; i++) {
            brokenCsv.append("EMP-SB-").append(i).append(",First").append(i).append(",Last").append(i)
                    .append(",sb").append(i).append("@test.com,,BLR-01,,GEN\n");
        }

        ByteArrayInputStream stream = new ByteArrayInputStream(brokenCsv.toString().getBytes(StandardCharsets.UTF_8));
        ReconciliationResultDto initialResult = reconciliationService.reconcile(TENANT, stream);
        assertEquals(ReconciliationStatus.SAFETY_BRAKE_TRIGGERED, initialResult.getStatus());

        // Operator approves the run
        SafetyBrakeApprovalDto approval = SafetyBrakeApprovalDto.builder()
                .reconciliationRunId(initialResult.getRunId())
                .approvedBy("transport_lead_operator")
                .reason("Confirmed quarterly organizational restructuring")
                .build();

        ReconciliationResultDto approvedResult = reconciliationService.approveSafetyBrake(approval);
        assertEquals(ReconciliationStatus.COMPLETED, approvedResult.getStatus());
        assertTrue(approvedResult.isOperatorApproved());
        assertEquals("transport_lead_operator", approvedResult.getOperatorApprovedBy());
    }

    private void createDbEmployee(String id, String first, String last, String email, String office, String shift) {
        Employee emp = new Employee();
        emp.setEmployeeId(id);
        emp.setTenantId(TENANT);
        emp.setFirstName(first);
        emp.setLastName(last);
        emp.setEmail(email);
        emp.setOfficeCode(office);
        emp.setShiftCode(shift);
        emp.setHrVersion(1L);
        emp.setActive(true);
        employeeRepository.save(emp);
    }
}

