package com.kov.movieinfo.service.provider;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kov.movieinfo.config.AsyncConfig;
import com.kov.movieinfo.dto.MovieDto;
import com.kov.movieinfo.exception.ApiProviderException;
import com.kov.movieinfo.util.SensitiveValueRedactor;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Field-level {@code @Value}/{@code @Qualifier} annotations are copied onto the Lombok-generated
 * constructor's parameters via {@code lombok.copyableAnnotations} (see {@code lombok.config} at the
 * project root). Without that config the DI wiring here would silently break.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OmdbApiProvider implements MovieApiProvider {

    private static final String NAME = "omdb";

    private final RestClient restClient;

    @Value("${api.omdb.key}")
    private final String apiKey;

    @Value("${api.omdb.url}")
    private final String baseUrl;

    @Value("${api.omdb.max-detail-requests:10}")
    private final int maxDetailRequests;

    @Qualifier(AsyncConfig.DETAIL_FETCH_EXECUTOR)
    private final Executor executor;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    @Retry(name = "omdb")
    @CircuitBreaker(name = "omdb")
    public SearchResult searchMovies(String title) {
        URI uri =
                UriComponentsBuilder.fromUriString(baseUrl)
                        .queryParam("s", title)
                        .queryParam("apikey", apiKey)
                        .encode()
                        .build()
                        .toUri();

        OmdbSearchResponse searchResponse;
        try {
            searchResponse = restClient.get().uri(uri).retrieve().body(OmdbSearchResponse.class);
        } catch (Exception e) {
            throw sanitize("OMDB search failed for title: " + title, e);
        }

        if (searchResponse == null || !"True".equals(searchResponse.response())) {
            return new SearchResult(List.of(), 0);
        }

        var fetched =
                ConcurrentDetailFetcher.fetchAll(
                        searchResponse.search(),
                        maxDetailRequests,
                        result -> fetchDetail(result.imdbID()),
                        executor,
                        log,
                        "Failed to fetch OMDB movie detail");

        // Signal upstream degradation to the circuit breaker when every detail fetch failed.
        if (fetched.items().isEmpty() && fetched.failures() > 0) {
            throw new ApiProviderException(
                    "OMDB detail endpoint failed all "
                            + fetched.failures()
                            + " fetch attempts for title: "
                            + title);
        }
        return new SearchResult(fetched.items(), fetched.failures());
    }

    private MovieDto fetchDetail(String imdbId) {
        URI uri =
                UriComponentsBuilder.fromUriString(baseUrl)
                        .queryParam("i", imdbId)
                        .queryParam("apikey", apiKey)
                        .encode()
                        .build()
                        .toUri();

        OmdbDetailResponse detail =
                restClient.get().uri(uri).retrieve().body(OmdbDetailResponse.class);

        if (detail == null || !"True".equals(detail.response())) {
            return null;
        }

        List<String> directors =
                detail.director() == null || "N/A".equals(detail.director())
                        ? List.of()
                        : Arrays.stream(detail.director().split(",")).map(String::trim).toList();

        return new MovieDto(detail.title(), detail.year(), directors);
    }

    /**
     * Wraps {@code cause} so the OMDB apikey (embedded in RestClient exception messages) is never
     * forwarded further up the stack or into logs.
     */
    private static ApiProviderException sanitize(String message, Exception cause) {
        var sanitized = new RuntimeException(SensitiveValueRedactor.redact(cause.getMessage()));
        sanitized.setStackTrace(cause.getStackTrace());
        return new ApiProviderException(message, sanitized);
    }

    record OmdbSearchResponse(
            @JsonProperty("Search") List<OmdbSearchResult> search,
            @JsonProperty("totalResults") String totalResults,
            @JsonProperty("Response") String response) {}

    record OmdbSearchResult(
            @JsonProperty("Title") String title,
            @JsonProperty("Year") String year,
            @JsonProperty("imdbID") String imdbID) {}

    record OmdbDetailResponse(
            @JsonProperty("Title") String title,
            @JsonProperty("Year") String year,
            @JsonProperty("Director") String director,
            @JsonProperty("Response") String response) {}
}
