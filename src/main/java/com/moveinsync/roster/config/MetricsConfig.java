package com.moveinsync.roster.config;

import com.moveinsync.roster.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer metrics configuration for the HR Roster Sync service.
 *
 * <p>Registers custom counters, gauges, and timers to provide operational
 * visibility into event processing, reconciliation, and error rates.
 * All metrics are exported via the Prometheus actuator endpoint.</p>
 *
 * <h3>Registered Metrics</h3>
 * <table>
 *   <tr><th>Name</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>{@code roster.events.received}</td><td>Counter</td><td>Total events received (tagged by tenant, event_type)</td></tr>
 *   <tr><td>{@code roster.events.applied}</td><td>Counter</td><td>Events successfully applied to the roster</td></tr>
 *   <tr><td>{@code roster.events.rejected}</td><td>Counter</td><td>Events rejected due to validation failure</td></tr>
 *   <tr><td>{@code roster.events.stale}</td><td>Counter</td><td>Stale events skipped (older version)</td></tr>
 *   <tr><td>{@code roster.events.duplicate}</td><td>Counter</td><td>Duplicate events detected and skipped</td></tr>
 *   <tr><td>{@code roster.outbox.pending}</td><td>Gauge</td><td>Number of unpublished outbox events</td></tr>
 *   <tr><td>{@code roster.reconciliation.duration}</td><td>Timer</td><td>Reconciliation run execution time</td></tr>
 *   <tr><td>{@code roster.reconciliation.safety_brake_triggered}</td><td>Counter</td><td>Times safety brake was engaged</td></tr>
 *   <tr><td>{@code roster.errors.total}</td><td>Counter</td><td>Total processing errors</td></tr>
 * </table>
 */
@Configuration
public class MetricsConfig {

    /**
     * Registers all custom Micrometer metrics with the provided registry.
     *
     * <p>The outbox pending gauge is backed by a live query to the
     * {@link OutboxEventRepository}, ensuring the metric always reflects
     * the current database state.</p>
     *
     * @param registry           the Micrometer meter registry
     * @param outboxEventRepository the outbox repository for pending-event gauge
     * @return a holder containing all registered metric instances
     */
    @Bean
    public RosterMetrics rosterMetrics(MeterRegistry registry,
                                       OutboxEventRepository outboxEventRepository) {
        return new RosterMetrics(registry, outboxEventRepository);
    }

    /**
     * Holder class for all custom HR Roster Sync metrics.
     *
     * <p>Provides typed access to metric instances for use in service classes
     * without requiring direct interaction with the {@link MeterRegistry}.</p>
     */
    public static class RosterMetrics {

        private final MeterRegistry registry;

        /** Counter for events received — use {@code .tag("tenant", t).tag("event_type", et)} when recording. */
        private final Counter eventsApplied;
        private final Counter eventsRejected;
        private final Counter eventsStale;
        private final Counter eventsDuplicate;
        private final Timer reconciliationDuration;
        private final Counter safetyBrakeTriggered;
        private final Counter errorsTotal;

        /**
         * Creates and registers all custom metrics.
         *
         * @param registry           the Micrometer meter registry
         * @param outboxEventRepository repository for outbox pending count
         */
        public RosterMetrics(MeterRegistry registry,
                             OutboxEventRepository outboxEventRepository) {
            this.registry = registry;

            // Event processing counters
            this.eventsApplied = Counter.builder("roster.events.applied")
                    .description("Events successfully applied to the roster")
                    .register(registry);

            this.eventsRejected = Counter.builder("roster.events.rejected")
                    .description("Events rejected due to validation failure")
                    .register(registry);

            this.eventsStale = Counter.builder("roster.events.stale")
                    .description("Stale events skipped due to older version")
                    .register(registry);

            this.eventsDuplicate = Counter.builder("roster.events.duplicate")
                    .description("Duplicate events detected and skipped")
                    .register(registry);

            // Outbox gauge — live query
            registry.gauge("roster.outbox.pending",
                    outboxEventRepository,
                    repo -> repo.countByPublishedFalse());

            // Reconciliation metrics
            this.reconciliationDuration = Timer.builder("roster.reconciliation.duration")
                    .description("Reconciliation run execution time")
                    .register(registry);

            this.safetyBrakeTriggered = Counter.builder("roster.reconciliation.safety_brake_triggered")
                    .description("Number of times the safety brake was engaged")
                    .register(registry);

            // Error tracking
            this.errorsTotal = Counter.builder("roster.errors.total")
                    .description("Total processing errors")
                    .register(registry);
        }

        /**
         * Records an event-received metric with tenant and event-type tags.
         *
         * <p>Uses the registry directly because tags vary per invocation.</p>
         *
         * @param tenant    the tenant identifier
         * @param eventType the type of HR event (e.g., ONBOARD, TERMINATE)
         */
        public void recordEventReceived(String tenant, String eventType) {
            Counter.builder("roster.events.received")
                    .description("Total events received")
                    .tag("tenant", tenant)
                    .tag("event_type", eventType)
                    .register(registry)
                    .increment();
        }

        /** @return the counter for successfully applied events */
        public Counter getEventsApplied() {
            return eventsApplied;
        }

        /** @return the counter for rejected events */
        public Counter getEventsRejected() {
            return eventsRejected;
        }

        /** @return the counter for stale events */
        public Counter getEventsStale() {
            return eventsStale;
        }

        /** @return the counter for duplicate events */
        public Counter getEventsDuplicate() {
            return eventsDuplicate;
        }

        /** @return the timer for reconciliation run duration */
        public Timer getReconciliationDuration() {
            return reconciliationDuration;
        }

        /** @return the counter for safety-brake activations */
        public Counter getSafetyBrakeTriggered() {
            return safetyBrakeTriggered;
        }

        /** @return the counter for total errors */
        public Counter getErrorsTotal() {
            return errorsTotal;
        }

        /** @return the underlying meter registry for advanced operations */
        public MeterRegistry getRegistry() {
            return registry;
        }
    }
}
