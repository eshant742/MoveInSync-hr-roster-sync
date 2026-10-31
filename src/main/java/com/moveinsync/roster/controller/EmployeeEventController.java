package com.moveinsync.roster.controller;

import com.moveinsync.roster.dto.BatchEventRequestDto;
import com.moveinsync.roster.dto.BatchEventResponseDto;
import com.moveinsync.roster.dto.EmployeeResponseDto;
import com.moveinsync.roster.dto.TransportFieldsUpdateDto;
import com.moveinsync.roster.service.EmployeeEventService;
import com.moveinsync.roster.service.TransportFieldsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for ingesting employee events and updating transport-admin profiles.
 */
@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employee Events & Profile", description = "Ingestion of HR events and transport profile administration")
public class EmployeeEventController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventController.class);

    private final EmployeeEventService employeeEventService;
    private final TransportFieldsService transportFieldsService;

    public EmployeeEventController(EmployeeEventService employeeEventService,
                                   TransportFieldsService transportFieldsService) {
        this.employeeEventService = employeeEventService;
        this.transportFieldsService = transportFieldsService;
    }

    /**
     * Ingests a batch of employee lifecycle events from an external HR system.
     *
     * <p>Features:</p>
     * <ul>
     *   <li>Idempotent handling of duplicate versions</li>
     *   <li>Ignoring stale out-of-order updates</li>
     *   <li>Preservation of transport-admin fields</li>
     *   <li>Transactional Outbox event generation</li>
     * </ul>
     */
    @PostMapping("/events")
    @Operation(summary = "Process batch of employee events",
               description = "Accepts a batch of CREATE, UPDATE, or DEACTIVATE events. One bad record never fails the entire batch.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch successfully evaluated and processed"),
            @ApiResponse(responseCode = "400", description = "Malformed request payload")
    })
    public ResponseEntity<BatchEventResponseDto> processBatch(
            @Valid @RequestBody BatchEventRequestDto request) {
        log.info("Received event batch of size {}", request.getEvents() != null ? request.getEvents().size() : 0);
        BatchEventResponseDto response = employeeEventService.processBatch(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates transport-admin-owned fields (pickup point, special assistance needs).
     *
     * <p>These fields are independent of the HR event version and will never
     * be overwritten by subsequent HR events or nightly reconciliation runs.</p>
     */
    @PutMapping("/transport-fields")
    @Operation(summary = "Update transport-admin owned fields",
               description = "Updates pickupPoint and specialAssistanceNeeds. Strictly independent of HR versioning.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transport fields successfully updated"),
            @ApiResponse(responseCode = "404", description = "Employee record not found")
    })
    public ResponseEntity<EmployeeResponseDto> updateTransportFields(
            @Valid @RequestBody TransportFieldsUpdateDto request) {
        log.info("Updating transport fields for employee ID {} in tenant {}",
                request.getEmployeeId(), request.getTenantId());
        EmployeeResponseDto response = transportFieldsService.updateTransportFields(request);
        return ResponseEntity.ok(response);
    }
}
