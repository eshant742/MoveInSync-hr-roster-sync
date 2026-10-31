package com.moveinsync.roster.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine for the HR Roster Sync service.
 *
 * <p>Provides a TTL-based in-memory cache for frequently accessed employee
 * lookups, reducing database load for read-heavy operations such as
 * duplicate detection and employee queries.</p>
 *
 * <h3>Caches</h3>
 * <ul>
 *   <li><strong>employees</strong> — Caches employee lookups by composite key
 *       (employeeId + tenantId). Evicted automatically after the configured
 *       TTL and explicitly on employee updates via {@code @CacheEvict}.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * &#64;Cacheable(value = "employees", key = "#employeeId + '_' + #tenantId")
 * public Optional&lt;Employee&gt; findEmployee(String employeeId, String tenantId) { ... }
 *
 * &#64;CacheEvict(value = "employees", key = "#employeeId + '_' + #tenantId")
 * public void updateEmployee(String employeeId, String tenantId, ...) { ... }
 * </pre>
 *
 * <p><strong>Dependency note:</strong> Requires {@code com.github.ben-manes.caffeine:caffeine}
 * on the classpath. Add to {@code pom.xml}:
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;com.github.ben-manes.caffeine&lt;/groupId&gt;
 *     &lt;artifactId&gt;caffeine&lt;/artifactId&gt;
 * &lt;/dependency&gt;
 * </pre>
 * </p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name constant for employee lookups. */
    public static final String EMPLOYEES_CACHE = "employees";

    /**
     * Configures the Caffeine-backed {@link CacheManager} with TTL-based eviction.
     *
     * @param ttlMinutes the time-to-live in minutes for cache entries
     *                   (default: 5, configurable via {@code roster.cache.ttl-minutes})
     * @param maxSize    the maximum number of entries in the cache
     *                   (default: 10000, configurable via {@code roster.cache.max-size})
     * @return the configured {@link CacheManager}
     */
    @Bean
    public CacheManager cacheManager(
            @Value("${roster.cache.ttl-minutes:5}") long ttlMinutes,
            @Value("${roster.cache.max-size:10000}") long maxSize) {

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of(EMPLOYEES_CACHE));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .recordStats());

        return cacheManager;
    }
}
