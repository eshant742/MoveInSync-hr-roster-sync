package com.moveinsync.roster.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing an outbox event for the transactional outbox pattern.
 *
 * <p>Maps to the {@code outbox_events} table. Every significant domain change
 * (employee created, updated, deactivated) produces an outbox event within the
 * same database transaction. A separate poller or CDC mechanism reads these
 * events and publishes them to downstream consumers.</p>
 *
 * <p>Use the static factory method {@link #create(String, String, String, String, String)}
 * to construct new instances.</p>
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_processed", columnList = "processed"),
                @Index(name = "idx_outbox_created_at", columnList = "created_at"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
        }
)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for this event, used for idempotency by consumers.
     */
    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    /**
     * The type of aggregate that produced this event (e.g., "Employee").
     */
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    /**
     * The identifier of the aggregate instance (e.g., the employee ID).
     */
    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    /**
     * The type of domain event (e.g., "EMPLOYEE_CREATED", "EMPLOYEE_UPDATED").
     */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /**
     * The tenant that this event belongs to.
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /**
     * JSON payload containing the event data.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Whether this event has been published to downstream consumers.
     */
    @Column(name = "published", nullable = false)
    private boolean published = false;

    /**
     * Timestamp when this event was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when this event was published.
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** Default constructor required by JPA. */
    public OutboxEvent() {
    }

    /**
     * Creates a new {@link OutboxEvent} with a generated UUID, the current timestamp,
     * and {@code published} set to {@code false}.
     *
     * @param aggregateType the type of aggregate (e.g., "Employee")
     * @param aggregateId   the aggregate's unique identifier (e.g., employee ID)
     * @param eventType     the domain event type (e.g., "EMPLOYEE_CREATED")
     * @param tenantId      the tenant that owns this aggregate
     * @param payload       JSON-serialised event data
     * @return a new, unpersisted {@link OutboxEvent}
     */
    public static OutboxEvent create(String aggregateType,
                                     String aggregateId,
                                     String eventType,
                                     String tenantId,
                                     String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UUID.randomUUID().toString();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.tenantId = tenantId;
        event.payload = payload;
        event.published = false;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    /**
     * Marks this event as published and records the publication timestamp.
     */
    public void markPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }

    public void markProcessed() {
        markPublished();
    }

    @Override
    public String toString() {
        return "OutboxEvent{eventId='" + eventId + "', aggregateType='" + aggregateType
                + "', aggregateId='" + aggregateId + "', eventType='" + eventType
                + "', tenantId='" + tenantId + "', published=" + published + '}';
    }

    // ========== Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public boolean isProcessed() {
        return published;
    }

    public void setProcessed(boolean processed) {
        this.published = processed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getProcessedAt() {
        return publishedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.publishedAt = processedAt;
    }
}
