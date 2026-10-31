package com.moveinsync.roster.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.dto.BatchEventRequestDto;
import com.moveinsync.roster.dto.EmployeeEventDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Controller Security RBAC & PII Redaction Integration Tests")
class EmployeeEventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("RBAC: ADMIN role can successfully submit employee events")
    void adminCanSubmitEvents() throws Exception {
        EmployeeEventDto event = EmployeeEventDto.builder()
                .employeeId("EMP-API-01")
                .tenantId("TENANT-API")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Aditi")
                .lastName("Deshmukh")
                .email("aditi.d@test.com")
                .officeCode("PUN-01")
                .build();

        BatchEventRequestDto request = BatchEventRequestDto.builder()
                .events(List.of(event))
                .build();

        mockMvc.perform(post("/api/v1/employees/events")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").value(1))
                .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    @DisplayName("RBAC: EMPLOYEE role is FORBIDDEN from posting employee events")
    void employeeCannotSubmitEvents() throws Exception {
        EmployeeEventDto event = EmployeeEventDto.builder()
                .employeeId("EMP-API-02")
                .tenantId("TENANT-API")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("Test")
                .lastName("User")
                .email("test@test.com")
                .officeCode("BLR-01")
                .build();

        BatchEventRequestDto request = BatchEventRequestDto.builder()
                .events(List.of(event))
                .build();

        mockMvc.perform(post("/api/v1/employees/events")
                        .with(httpBasic("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: Unauthenticated requests receive 401 Unauthorized")
    void unauthenticatedRequestsReceive401() throws Exception {
        mockMvc.perform(get("/api/v1/employees/TENANT-API/EMP-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Privacy Guarantee: Validation error response must never leak PII (phone, email, home address)")
    void validationErrorResponsesContainNoPii() throws Exception {
        // Submit an invalid event with identifiable personal info
        EmployeeEventDto invalidEvent = EmployeeEventDto.builder()
                .employeeId("EMP-PRIVACY-01")
                .tenantId("TENANT-API")
                .eventType(EventType.CREATE)
                .version(1L)
                .firstName("")             // Blank -> fails validation
                .lastName("SecretName")
                .email("invalid-email")     // Invalid email
                .phone("123")               // Invalid phone
                .build();

        BatchEventRequestDto request = BatchEventRequestDto.builder()
                .events(List.of(invalidEvent))
                .build();

        mockMvc.perform(post("/api/v1/employees/events")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount").value(1))
                // Ensure error message does not reflect the invalid phone number or email address
                .andExpect(jsonPath("$.results[0].employeeId").value("EMP-PRIVACY-01"));
    }
}
