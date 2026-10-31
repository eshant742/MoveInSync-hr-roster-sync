package com.moveinsync.roster.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers.
 *
 * <p>Key guarantees:</p>
 * <ul>
 *   <li>Consistent JSON error format across all endpoints</li>
 *   <li>Appropriate HTTP status codes (400, 404, 409, 403, 500)</li>
 *   <li><strong>Zero PII leakage:</strong> Error responses never include phone numbers,
 *       emails, or residential locations. Only sanitized employee identifiers and
 *       field names are exposed.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        log.warn("Validation error for employee {}: {}", ex.getEmployeeId(), ex.getErrors());
        Map<String, Object> body = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Employee data validation failed: " + String.join("; ", ex.getErrors())
        );
        body.put("employeeId", ex.getEmployeeId());
        body.put("errors", ex.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (msg1, msg2) -> msg1
                ));
        log.warn("Request body validation failed on fields: {}", fieldErrors.keySet());
        Map<String, Object> body = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body validation failed"
        );
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEmployeeNotFound(EmployeeNotFoundException ex) {
        log.info("Employee lookup failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTenantNotFound(TenantNotFoundException ex) {
        log.warn("Tenant lookup failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(SafetyBrakeException.class)
    public ResponseEntity<Map<String, Object>> handleSafetyBrake(SafetyBrakeException ex) {
        log.error("Reconciliation safety brake engaged: {}", ex.getMessage());
        Map<String, Object> body = buildErrorResponse(
                HttpStatus.CONFLICT,
                "SAFETY_BRAKE_TRIGGERED",
                ex.getMessage()
        );
        body.put("missingPercentage", ex.getMissingPercentage());
        body.put("threshold", ex.getThreshold());
        body.put("totalInStore", ex.getTotalInStore());
        body.put("missingCount", ex.getMissingCount());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ReconciliationInProgressException.class)
    public ResponseEntity<Map<String, Object>> handleReconciliationInProgress(ReconciliationInProgressException ex) {
        log.warn("Concurrent reconciliation prevented: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(HttpStatus.CONFLICT, "RECONCILIATION_IN_PROGRESS", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEvent(DuplicateEventException ex) {
        log.debug("Duplicate event skipped for employee {}: version {}", ex.getEmployeeId(), ex.getVersion());
        Map<String, Object> body = buildErrorResponse(HttpStatus.OK, "DUPLICATE_EVENT", ex.getMessage());
        body.put("employeeId", ex.getEmployeeId());
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @ExceptionHandler(StaleEventException.class)
    public ResponseEntity<Map<String, Object>> handleStaleEvent(StaleEventException ex) {
        log.debug("Stale event ignored for employee {}: incoming {} vs current {}",
                ex.getEmployeeId(), ex.getIncomingVersion(), ex.getCurrentVersion());
        Map<String, Object> body = buildErrorResponse(HttpStatus.OK, "STALE_EVENT", ex.getMessage());
        body.put("employeeId", ex.getEmployeeId());
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: insufficient privileges");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildErrorResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Insufficient permissions"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "An unexpected internal error occurred. Please contact system support."));
    }

    private Map<String, Object> buildErrorResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", errorCode);
        body.put("message", message);
        return body;
    }
}

