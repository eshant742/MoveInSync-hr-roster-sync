package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.entity.Employee;
import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.dto.EmployeeEventDto;
import com.moveinsync.roster.dto.TransportFieldsUpdateDto;
import com.moveinsync.roster.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Field Ownership Rule Tests - HR Updates Must Never Overwrite Transport-Admin Fields")
class FieldOwnershipTest {

    @Autowired
    private EmployeeEventService employeeEventService;

    @Autowired
    private TransportFieldsService transportFieldsService;

    @Autowired
    private EmployeeRepository employeeRepository;

    private static final String TENANT = "TENANT-ACME";

    @BeforeEach
    void cleanUp() {
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("HR update must NEVER overwrite transport-admin owned fields (pickupPoint, specialAssistanceNeeds)")
    void hrUpdateNeverOverwritesTransportFields() {
        // 1. Initial employee created via HR feed
        EmployeeEventDto createEvent = EmployeeEventDto.builder()
                .employeeId("EMP-FIELD-01")
                .tenantId(TENANT)
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Siddharth")
                .lastName("Rao")
                .email("siddharth.rao@acme.com")
                .officeCode("BLR-01")
                .shiftCode("GEN")
                .build();
        employeeEventService.processSingleEvent(createEvent);

        // 2. Transport admin sets custom pickup point and special assistance needs
        TransportFieldsUpdateDto transportUpdate = TransportFieldsUpdateDto.builder()
                .employeeId("EMP-FIELD-01")
                .tenantId(TENANT)
                .pickupPoint("Outer Ring Road, Kadubeesanahalli Junction, Landmark: Near Petrol Pump")
                .specialAssistanceNeeds("Wheelchair Ramp & Lower Floor Seating Required")
                .build();
        transportFieldsService.updateTransportFields(transportUpdate);

        // Verify transport fields are persisted
        Employee empAfterTransport = employeeRepository.findByEmployeeIdAndTenantId("EMP-FIELD-01", TENANT).orElseThrow();
        assertEquals("Outer Ring Road, Kadubeesanahalli Junction, Landmark: Near Petrol Pump", empAfterTransport.getPickupPoint());
        assertEquals("Wheelchair Ramp & Lower Floor Seating Required", empAfterTransport.getSpecialAssistanceNeeds());

        // 3. HR system sends an UPDATE (e.g., promotion, new manager, new email)
        EmployeeEventDto hrUpdate = EmployeeEventDto.builder()
                .employeeId("EMP-FIELD-01")
                .tenantId(TENANT)
                .eventType(EventType.UPDATE)
                .version(2L)
                .firstName("Siddharth")
                .lastName("Rao")
                .email("siddharth.rao.lead@acme.com") // updated email
                .managerId("MGR-999")                // updated manager
                .shiftCode("EVENING")                // updated shift
                .build();
        employeeEventService.processSingleEvent(hrUpdate);

        // 4. Verify HR fields updated, BUT transport-admin fields are completely untouched!
        Employee finalEmp = employeeRepository.findByEmployeeIdAndTenantId("EMP-FIELD-01", TENANT).orElseThrow();
        assertEquals("siddharth.rao.lead@acme.com", finalEmp.getEmail(), "HR-owned field email must be updated");
        assertEquals("MGR-999", finalEmp.getManagerId(), "HR-owned field managerId must be updated");
        assertEquals("EVENING", finalEmp.getShiftCode(), "HR-owned field shiftCode must be updated");
        assertEquals(2L, finalEmp.getHrVersion());

        // CRITICAL CHECK: Transport-owned fields MUST NOT HAVE CHANGED
        assertEquals("Outer Ring Road, Kadubeesanahalli Junction, Landmark: Near Petrol Pump",
                finalEmp.getPickupPoint(), "Transport-admin pickup point must be preserved!");
        assertEquals("Wheelchair Ramp & Lower Floor Seating Required",
                finalEmp.getSpecialAssistanceNeeds(), "Transport-admin special assistance must be preserved!");
    }
}
