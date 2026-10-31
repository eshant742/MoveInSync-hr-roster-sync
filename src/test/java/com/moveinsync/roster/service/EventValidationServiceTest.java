package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.dto.EmployeeEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Event Boundary Validation Tests")
class EventValidationServiceTest {

    private EventValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new EventValidationService();
    }

    @Test
    @DisplayName("Should accept fully valid CREATE event")
    void shouldAcceptValidCreateEvent() {
        EmployeeEventDto event = EmployeeEventDto.builder()
                .employeeId("EMP-1001")
                .tenantId("TENANT-ACME")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Aarav")
                .lastName("Sharma")
                .email("aarav.sharma@acme.com")
                .phone("+919876543210")
                .officeCode("BLR-01")
                .department("Engineering")
                .shiftCode("GEN")
                .build();

        List<String> errors = validationService.validate(event);
        assertTrue(errors.isEmpty(), "Valid event should have no validation errors");
    }

    @Test
    @DisplayName("Should reject event when employeeId or tenantId is missing")
    void shouldRejectMissingEnvelopeFields() {
        EmployeeEventDto missingId = EmployeeEventDto.builder()
                .tenantId("TENANT-ACME")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Aarav")
                .lastName("Sharma")
                .email("aarav.sharma@acme.com")
                .officeCode("BLR-01")
                .build();

        List<String> errors = validationService.validate(missingId);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("employeeId")));

        EmployeeEventDto missingTenant = EmployeeEventDto.builder()
                .employeeId("EMP-1001")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Aarav")
                .lastName("Sharma")
                .email("aarav.sharma@acme.com")
                .officeCode("BLR-01")
                .build();

        List<String> tenantErrors = validationService.validate(missingTenant);
        assertFalse(tenantErrors.isEmpty());
        assertTrue(tenantErrors.stream().anyMatch(e -> e.contains("tenantId")));
    }

    @Test
    @DisplayName("Should reject non-positive or null version")
    void shouldRejectInvalidVersion() {
        EmployeeEventDto zeroVersion = EmployeeEventDto.builder()
                .employeeId("EMP-1001")
                .tenantId("TENANT-ACME")
                .eventType(EventType.CREATE)
                .version(0L)
                .firstName("Aarav")
                .lastName("Sharma")
                .email("aarav.sharma@acme.com")
                .officeCode("BLR-01")
                .build();

        List<String> errors = validationService.validate(zeroVersion);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("version")));
    }

    @Test
    @DisplayName("Should reject invalid email format")
    void shouldRejectInvalidEmail() {
        EmployeeEventDto invalidEmail = EmployeeEventDto.builder()
                .employeeId("EMP-1001")
                .tenantId("TENANT-ACME")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Aarav")
                .lastName("Sharma")
                .email("not-an-email")
                .officeCode("BLR-01")
                .build();

        List<String> errors = validationService.validate(invalidEmail);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    @DisplayName("Should reject malformed phone numbers while allowing valid international formats")
    void shouldValidatePhoneFormats() {
        EmployeeEventDto malformedPhone = EmployeeEventDto.builder()
                .employeeId("EMP-1001")
                .tenantId("TENANT-ACME")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Aarav")
                .lastName("Sharma")
                .email("aarav@acme.com")
                .phone("123") // too short
                .officeCode("BLR-01")
                .build();

        List<String> errors = validationService.validate(malformedPhone);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("phone")));

        assertTrue(validationService.isValidPhone("+919876543210"));
        assertTrue(validationService.isValidPhone("9876543210"));
        assertFalse(validationService.isValidPhone("abc-123-4567"));
    }

    @Test
    @DisplayName("Should require officeCode for CREATE events")
    void shouldRequireOfficeCodeForCreate() {
        EmployeeEventDto noOffice = EmployeeEventDto.builder()
                .employeeId("EMP-1001")
                .tenantId("TENANT-ACME")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Aarav")
                .lastName("Sharma")
                .email("aarav@acme.com")
                .build();

        List<String> errors = validationService.validate(noOffice);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("officeCode")));
    }
}

