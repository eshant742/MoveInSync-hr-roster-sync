package com.moveinsync.roster.controller;

import com.moveinsync.roster.service.OutboxPublisherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for system operations, health inspection,
 * and outbox publishing management.
 */
@RestController
@RequestMapping("/api/v1/operations")
@Tag(name = "Operations & Diagnostics", description = "Operational health, metrics and outbox triggers")
public class OperationsController {

    private final OutboxPublisherService outboxPublisherService;

    public OperationsController(OutboxPublisherService outboxPublisherService) {
        this.outboxPublisherService = outboxPublisherService;
    }

    /**
     * System diagnostic overview including outbox lag and status.
     */
    @GetMapping("/status")
    @Operation(summary = "System status and outbox lag", description = "Returns operational health and pending outbox queue depth.")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("outboxPendingCount", outboxPublisherService.getPendingCount());
        status.put("version", "1.0.0");
        return ResponseEntity.ok(status);
    }

    /**
     * Manually triggers immediate dispatch of all pending outbox events.
     * Essential for automated outbox crash-recovery testing.
     */
    @PostMapping("/outbox/publish")
    @Operation(summary = "Trigger immediate outbox event dispatch",
               description = "Dispatches all pending outbox events to downstream channels and marks them published.")
    public ResponseEntity<Map<String, Object>> publishOutboxNow() {
        int published = outboxPublisherService.publishNow();
        Map<String, Object> response = new HashMap<>();
        response.put("publishedCount", published);
        response.put("remainingPending", outboxPublisherService.getPendingCount());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
