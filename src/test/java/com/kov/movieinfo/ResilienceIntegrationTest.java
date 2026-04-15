package com.kov.movieinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;

import com.kov.movieinfo.dto.MovieDto;
import com.kov.movieinfo.exception.ApiProviderException;
import com.kov.movieinfo.service.provider.MovieApiProvider.SearchResult;
import com.kov.movieinfo.service.provider.OmdbApiProvider;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Verifies Resilience4j retry + circuit breaker wiring against the OMDB provider.
 *
 * <p>Uses H2 + simple cache (light context) — we only care about the AOP-proxied retry behaviour,
 * not Redis/MySQL persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResilienceIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoSpyBean private OmdbApiProvider omdbProvider;

    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    /** Reset all breakers so state from a prior test (e.g. forced-open) cannot leak. */
    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void transientServerError_isRetriedAndEventuallySucceeds() throws Exception {
        // First two calls fail with 500, third returns data. Retry(maxAttempts=3) should
        // convert this into a single visible success.
        var success =
                new SearchResult(
                        List.of(new MovieDto("The Matrix", "1999", List.of("Lana Wachowski"))), 0);

        Mockito.doThrow(httpServerError())
                .doThrow(httpServerError())
                .doReturn(success)
                .when(omdbProvider)
                .searchMovies("Resilience-Transient");

        mockMvc.perform(get("/movies/Resilience-Transient").param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].Title").value("The Matrix"));

        // The spy should have been called at least 3 times (2 failures + 1 success).
        verify(omdbProvider, atLeast(3)).searchMovies("Resilience-Transient");
    }

    @Test
    void apiProviderException_isRetriedButEventuallyReturned() throws Exception {
        // Always fail — after maxAttempts=3 Retry gives up and the 502 is returned to the client.
        // ApiProviderException is listed in resilience4j.retry.retryExceptions so this is retried.
        Mockito.doThrow(new ApiProviderException("simulated upstream failure"))
                .when(omdbProvider)
                .searchMovies("Resilience-Permanent");

        mockMvc.perform(get("/movies/Resilience-Permanent").param("api", "omdb"))
                .andExpect(status().isBadGateway());

        // 3 total attempts (1 original + 2 retries) because ApiProviderException is retry-eligible.
        verify(omdbProvider, atLeast(3)).searchMovies("Resilience-Permanent");
    }

    @Test
    void circuitBreaker_opensAfterSustainedFailures_andShortCircuitsSubsequentCalls()
            throws Exception {
        Mockito.doThrow(new ApiProviderException("upstream down"))
                .when(omdbProvider)
                .searchMovies("CB-Force-Open");

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("omdb");

        // Config is minimumNumberOfCalls=5 + failureRateThreshold=50%, but with Retry as the
        // outer aspect each request produces 3 CB observations — so ~3 requests is enough.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/movies/CB-Force-Open").param("api", "omdb"));
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Subsequent call must short-circuit: the CB throws CallNotPermittedException without
        // invoking the provider. GlobalExceptionHandler maps that to 503.
        long callsBefore = cb.getMetrics().getNumberOfBufferedCalls();
        mockMvc.perform(get("/movies/CB-Force-Open").param("api", "omdb"))
                .andExpect(status().isServiceUnavailable());
        assertThat(cb.getMetrics().getNumberOfBufferedCalls()).isEqualTo(callsBefore);
    }

    @Test
    void circuitBreakerIsRegistered() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("omdb");
        assertThat(cb).isNotNull();
        assertThat(cb.getName()).isEqualTo("omdb");
    }

    private static HttpServerErrorException httpServerError() {
        return HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "upstream blip",
                HttpHeaders.EMPTY,
                new byte[0],
                null);
    }
}
