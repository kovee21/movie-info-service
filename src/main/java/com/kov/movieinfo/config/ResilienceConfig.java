package com.kov.movieinfo.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.kov.movieinfo.exception.ApiProviderException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Programmatic Resilience4j configuration. The YAML-based {@code resilience4j.circuitbreaker.*} /
 * {@code resilience4j.retry.*} bindings in {@code application.yml} proved unreliable under Spring
 * Boot 4 (instance settings were being silently replaced with library defaults), so we configure
 * both providers' breakers and retries here as beans — which also keeps the source of truth in code
 * reviewable alongside its callers.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerConfigCustomizer omdbCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer.of("omdb", ResilienceConfig::breakerConfig);
    }

    @Bean
    public CircuitBreakerConfigCustomizer tmdbCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer.of("tmdb", ResilienceConfig::breakerConfig);
    }

    @Bean
    public RetryConfigCustomizer omdbRetryCustomizer() {
        return RetryConfigCustomizer.of("omdb", ResilienceConfig::retryConfig);
    }

    @Bean
    public RetryConfigCustomizer tmdbRetryCustomizer() {
        return RetryConfigCustomizer.of("tmdb", ResilienceConfig::retryConfig);
    }

    private static void breakerConfig(CircuitBreakerConfig.Builder builder) {
        builder.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                // Slow-call detection: if half of calls take longer than our read timeout,
                // treat the upstream as unhealthy even if it technically returns.
                .slowCallDurationThreshold(Duration.ofSeconds(8))
                .slowCallRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(
                        ResourceAccessException.class,
                        HttpServerErrorException.class,
                        ApiProviderException.class);
    }

    private static void retryConfig(RetryConfig.Builder<Object> builder) {
        builder.maxAttempts(3)
                .waitDuration(Duration.ofMillis(200))
                .retryExceptions(
                        ResourceAccessException.class,
                        HttpServerErrorException.class,
                        ApiProviderException.class)
                .ignoreExceptions(org.springframework.web.client.HttpClientErrorException.class);
    }
}
