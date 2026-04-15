package com.kov.movieinfo.service.provider;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kov.movieinfo.config.AsyncConfig;
import com.kov.movieinfo.dto.MovieDto;
import com.kov.movieinfo.exception.ApiProviderException;

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
public class TmdbApiProvider implements MovieApiProvider {

    private static final String NAME = "tmdb";

    private final RestClient restClient;

    @Value("${api.tmdb.bearer-token}")
    private final String bearerToken;

    @Value("${api.tmdb.url}")
    private final String baseUrl;

    @Value("${api.tmdb.include-adult:true}")
    private final boolean includeAdult;

    @Value("${api.tmdb.max-detail-requests:10}")
    private final int maxDetailRequests;

    @Qualifier(AsyncConfig.DETAIL_FETCH_EXECUTOR)
    private final Executor executor;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    @Retry(name = "tmdb")
    @CircuitBreaker(name = "tmdb")
    public SearchResult searchMovies(String title) {
        URI uri =
                UriComponentsBuilder.fromUriString(baseUrl)
                        .path("/search/movie")
                        .queryParam("query", title)
                        .queryParam("include_adult", includeAdult)
                        .encode()
                        .build()
                        .toUri();

        TmdbSearchResponse searchResponse;
        try {
            searchResponse =
                    restClient
                            .get()
                            .uri(uri)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                            .retrieve()
                            .body(TmdbSearchResponse.class);
        } catch (Exception e) {
            throw new ApiProviderException("TMDB search failed for title: " + title, e);
        }

        if (searchResponse == null || searchResponse.results() == null) {
            return new SearchResult(List.of(), 0);
        }

        var fetched =
                ConcurrentDetailFetcher.fetchAll(
                        searchResponse.results(),
                        maxDetailRequests,
                        this::fetchMovieWithCredits,
                        executor,
                        log,
                        "Failed to fetch TMDB movie credits");

        // Signal upstream degradation to the circuit breaker when every credits fetch failed.
        if (fetched.items().isEmpty() && fetched.failures() > 0) {
            throw new ApiProviderException(
                    "TMDB credits endpoint failed all "
                            + fetched.failures()
                            + " fetch attempts for title: "
                            + title);
        }
        return new SearchResult(fetched.items(), fetched.failures());
    }

    private MovieDto fetchMovieWithCredits(TmdbSearchResult movie) {
        URI uri =
                UriComponentsBuilder.fromUriString(baseUrl)
                        .path("/movie/{id}/credits")
                        .build(movie.id());

        TmdbCreditsResponse credits =
                restClient
                        .get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .retrieve()
                        .body(TmdbCreditsResponse.class);

        List<String> directors =
                (credits != null && credits.crew() != null)
                        ? credits.crew().stream()
                                .filter(c -> "Director".equals(c.job()))
                                .map(TmdbCrewMember::name)
                                .toList()
                        : List.of();

        String year =
                (movie.releaseDate() != null && movie.releaseDate().length() >= 4)
                        ? movie.releaseDate().substring(0, 4)
                        : "";

        return new MovieDto(movie.title(), year, directors);
    }

    record TmdbSearchResponse(List<TmdbSearchResult> results) {}

    record TmdbSearchResult(
            int id, String title, @JsonProperty("release_date") String releaseDate) {}

    record TmdbCreditsResponse(List<TmdbCrewMember> crew) {}

    record TmdbCrewMember(String job, String name) {}
}
