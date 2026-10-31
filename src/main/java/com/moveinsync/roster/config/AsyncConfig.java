package com.moveinsync.roster.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Asynchronous execution configuration for the HR Roster Sync service.
 *
 * <p>Provides a dedicated thread pool for asynchronous outbox event publishing.
 * The pool is sized conservatively to avoid resource exhaustion while still
 * enabling concurrent event dispatch to downstream systems.</p>
 *
 * <h3>Thread Pool Settings</h3>
 * <ul>
 *   <li><strong>Core pool size:</strong> 2 — baseline threads always available</li>
 *   <li><strong>Max pool size:</strong> 5 — elastic scaling under load</li>
 *   <li><strong>Queue capacity:</strong> 100 — buffer before rejection</li>
 *   <li><strong>Thread prefix:</strong> {@code outbox-publisher-}</li>
 * </ul>
 *
 * <p>If the queue fills and all threads are busy, the
 * {@code CallerRunsPolicy} is used to apply back-pressure by executing
 * the task in the calling thread.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /** Bean name for the outbox publisher executor. */
    public static final String OUTBOX_PUBLISHER_EXECUTOR = "outboxPublisherExecutor";

    /**
     * Creates the thread pool executor for outbox event publishing.
     *
     * @return the configured {@link Executor}
     */
    @Bean(name = OUTBOX_PUBLISHER_EXECUTOR)
    public Executor outboxPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("outbox-publisher-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Returns the default async executor.
     *
     * @return the outbox publisher executor
     */
    @Override
    public Executor getAsyncExecutor() {
        return outboxPublisherExecutor();
    }

    /**
     * Handles uncaught exceptions from {@code @Async} methods.
     *
     * <p>Logs the exception details without exposing PII — only the
     * method name and exception message are recorded.</p>
     *
     * @return the exception handler
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async execution failed in method [{}]: {}",
                        method.getName(), throwable.getMessage(), throwable);
    }
}
