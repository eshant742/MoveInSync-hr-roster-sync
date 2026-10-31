package com.moveinsync.roster.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-level configuration for the HR Roster Sync service.
 *
 * <p>Binds properties under the {@code roster} prefix from
 * {@code application.yml} and provides shared infrastructure beans.</p>
 *
 * <h3>Configuration Properties</h3>
 * <pre>
 * roster:
 *   safety-brake:
 *     threshold: 0.30    # max fraction of roster that can be deactivated in one run
 *   reconciliation:
 *     batch-size: 1000   # number of records processed per reconciliation batch
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "roster")
@Getter
@Setter
public class AppConfig {

    /** Safety-brake configuration to prevent mass deactivation. */
    private SafetyBrake safetyBrake = new SafetyBrake();

    /** Reconciliation processing configuration. */
    private Reconciliation reconciliation = new Reconciliation();

    /**
     * Safety-brake settings that halt reconciliation when too many
     * employees would be deactivated in a single run.
     */
    @Getter
    @Setter
    public static class SafetyBrake {

        /**
         * Maximum fraction of the active roster that can be deactivated
         * in a single reconciliation run before the safety brake engages.
         *
         * <p>For example, a threshold of {@code 0.30} means reconciliation
         * is halted if more than 30% of active employees would be removed.</p>
         */
        private double threshold = 0.30;
    }

    /**
     * Reconciliation processing settings.
     */
    @Getter
    @Setter
    public static class Reconciliation {

        /**
         * Number of records to process in each reconciliation batch.
         * Larger values improve throughput but increase memory usage.
         */
        private int batchSize = 1000;
    }

    /**
     * Configures the application-wide {@link ObjectMapper} with Java 8
     * date/time support and lenient deserialization.
     *
     * <p>Settings:</p>
     * <ul>
     *   <li>{@link JavaTimeModule} registered for {@code java.time} serialization</li>
     *   <li>{@code WRITE_DATES_AS_TIMESTAMPS} disabled — ISO-8601 strings preferred</li>
     *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES} disabled — forward-compatible deserialization</li>
     * </ul>
     *
     * @return the configured {@link ObjectMapper}
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
