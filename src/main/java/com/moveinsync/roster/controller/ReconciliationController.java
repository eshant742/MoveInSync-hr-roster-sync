package com.moveinsync.roster.controller;

import com.moveinsync.roster.dto.ReconciliationResultDto;
import com.moveinsync.roster.dto.SafetyBrakeApprovalDto;
import com.moveinsync.roster.service.EmployeeQueryService;
import com.moveinsync.roster.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST controller for executing nightly reconciliation runs, inspecting history,
 * and managing safety-brake approvals.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Nightly Reconciliation", description = "Endpoints for batch CSV reconciliation and safety brake workflows")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationService reconciliationService;
    private final EmployeeQueryService employeeQueryService;

    public ReconciliationController(ReconciliationService reconciliationService,
                                    EmployeeQueryService employeeQueryService) {
        this.reconciliationService = reconciliationService;
        this.employeeQueryService = employeeQueryService;
    }

    /**
     * Uploads and executes a nightly CSV export reconciliation.
     *
     * <p>CSV headers expected:</p>
     * {@code employeeId,firstName,lastName,email,phone,managerId,officeCode,department,shiftCode}
     */
    @PostMapping(value = "/upload/{tenantId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload nightly CSV export for reconciliation",
               description = "Diffs the export file with current database state using bounded memory. "
                           + "Engages safety brake if missing employees exceed threshold.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reconciliation completed or safety brake engaged"),
            @ApiResponse(responseCode = "400", description = "Invalid file or parameters"),
            @ApiResponse(responseCode = "409", description = "Reconciliation already in progress")
    })
    public ResponseEntity<ReconciliationResultDto> uploadAndReconcile(
            @PathVariable String tenantId,
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Received reconciliation CSV upload for tenant {}: filename={}, size={} bytes",
                tenantId, file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded CSV file cannot be empty");
        }

        ReconciliationResultDto result = reconciliationService.reconcile(tenantId, file.getInputStream());
        return ResponseEntity.ok(result);
    }

    /**
     * Approves a reconciliation run that was paused by the safety brake.
     * Requires the OPERATOR role.
     */
    @PostMapping("/approve")
    @Operation(summary = "Approve safety-braked reconciliation",
               description = "Allows an authorized operator to approve a paused reconciliation run and permit pending deactivations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Safety brake approved successfully"),
            @ApiResponse(responseCode = "404", description = "Run ID not found"),
            @ApiResponse(responseCode = "409", description = "Run is not in SAFETY_BRAKE_TRIGGERED status")
    })
    public ResponseEntity<ReconciliationResultDto> approveSafetyBrake(
            @Valid @RequestBody SafetyBrakeApprovalDto approval) {
        log.info("Operator {} approving safety brake for run ID {}", approval.getApprovedBy(), approval.getReconciliationRunId());
        ReconciliationResultDto result = reconciliationService.approveSafetyBrake(approval);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns reconciliation run history for a tenant.
     */
    @GetMapping("/history/{tenantId}")
    @Operation(summary = "Get reconciliation run history",
               description = "Returns paginated historical records of past reconciliation executions for auditing.")
    public ResponseEntity<List<ReconciliationResultDto>> getHistory(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<ReconciliationResultDto> history = employeeQueryService.getReconciliationHistory(
                tenantId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(history);
    }
}
