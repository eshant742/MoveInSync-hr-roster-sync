package com.moveinsync.roster.controller;

import com.moveinsync.roster.dto.EmployeeResponseDto;
import com.moveinsync.roster.dto.ErrorReportDto;
import com.moveinsync.roster.service.EmployeeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for querying employee records and ingestion error audit logs.
 */
@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employee Queries", description = "Querying employee profiles and ingestion error logs")
public class EmployeeQueryController {

    private final EmployeeQueryService employeeQueryService;

    public EmployeeQueryController(EmployeeQueryService employeeQueryService) {
        this.employeeQueryService = employeeQueryService;
    }

    /**
     * Looks up an individual employee by tenant and business ID.
     */
    @GetMapping("/{tenantId}/{employeeId}")
    @Operation(summary = "Get employee by ID", description = "Retrieves employee profile; answers from cache where available.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee profile found"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public ResponseEntity<EmployeeResponseDto> getEmployee(
            @PathVariable String tenantId,
            @PathVariable String employeeId) {
        EmployeeResponseDto dto = employeeQueryService.getEmployee(employeeId, tenantId);
        return ResponseEntity.ok(dto);
    }

    /**
     * Lists employees for a tenant with pagination.
     */
    @GetMapping("/{tenantId}")
    @Operation(summary = "List employees for tenant", description = "Paginated list of employees in a given tenant.")
    public ResponseEntity<Page<EmployeeResponseDto>> listEmployees(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy) {
        Page<EmployeeResponseDto> employees = employeeQueryService.listEmployees(
                tenantId, PageRequest.of(page, size, Sort.by(sortBy).ascending())
        );
        return ResponseEntity.ok(employees);
    }

    /**
     * Retrieves ingestion errors for a tenant (guaranteed free of PII).
     */
    @GetMapping("/{tenantId}/errors")
    @Operation(summary = "Get ingestion errors", description = "Auditing endpoint for rejected events. Never leaks PII.")
    public ResponseEntity<List<ErrorReportDto>> getErrors(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<ErrorReportDto> errors = employeeQueryService.getIngestionErrors(
                tenantId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(errors);
    }
}

