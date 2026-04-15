package com.kov.movieinfo.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * Application-wide async configuration.
 *
 * <ul>
 *   <li>{@link #searchLogExecutor} — bounded executor for async search-log writes. Provides
 *       backpressure before the Hikari connection pool can be exhausted by a burst of requests.
 *   <li>{@link #detailFetchExecutor()} — virtual-thread executor shared by the external-API
 *       providers for parallel detail fetches (cheap threads, I/O-bound work).
 *   <li>{@link #getAsyncUncaughtExceptionHandler()} — logs exceptions thrown from {@code @Async}
 *       {@code void} methods, which would otherwise be swallowed.
 * </ul>
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    public static final String SEARCH_LOG_EXECUTOR = "searchLogExecutor";
    public static final String DETAIL_FETCH_EXECUTOR = "detailFetchExecutor";

    @Bean(name = SEARCH_LOG_EXECUTOR)
    public Executor searchLogExecutor(
            @Value("${app.async.search-log.core-pool-size}") int corePoolSize,
            @Value("${app.async.search-log.max-pool-size}") int maxPoolSize,
            @Value("${app.async.search-log.queue-capacity}") int queueCapacity,
            @Value("${app.async.search-log.thread-name-prefix}") String threadNamePrefix) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        // If the queue fills up, run the task on the calling thread. Back-pressure protects the DB.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = DETAIL_FETCH_EXECUTOR, destroyMethod = "close")
    public Executor detailFetchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error(
                        "Uncaught exception in @Async method {}.{}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        ex);
    }
}
