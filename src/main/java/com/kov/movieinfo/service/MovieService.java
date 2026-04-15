package com.kov.movieinfo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.kov.movieinfo.dto.MovieResponse;
import com.kov.movieinfo.service.provider.MovieApiProvider;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MovieService {

    /** Sorted so error messages referencing the supported APIs have deterministic ordering. */
    private final Map<String, MovieApiProvider> providers;

    public MovieService(List<MovieApiProvider> providerList) {
        this.providers =
                providerList.stream()
                        .collect(
                                Collectors.toMap(
                                        MovieApiProvider::getName,
                                        Function.identity(),
                                        (a, b) -> a,
                                        TreeMap::new));
    }

    /**
     * Results with warnings (partial upstream failures) are NOT cached — the next request should
     * retry and hopefully get a complete result.
     *
     * <p>The body of this method only executes on a cache miss; presence of the "cache miss" log
     * line is therefore a reliable proxy for cache-miss/hit behaviour.
     */
    @Cacheable(
            value = "movies",
            key = "#api + ':' + #title.toLowerCase(T(java.util.Locale).ROOT)",
            unless = "#result == null || !#result.warnings().isEmpty()")
    public MovieResponse searchMovies(String title, String api) {
        MovieApiProvider provider = providers.get(api);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unknown API: '" + api + "'. Supported values: " + providers.keySet());
        }

        log.debug("Cache miss — calling provider: api={} title='{}'", api, title);
        long start = System.currentTimeMillis();

        var result = provider.searchMovies(title);

        log.info(
                "Provider returned: api={} title='{}' items={} failedFetches={} duration={}ms",
                api,
                title,
                result.movies().size(),
                result.failedFetches(),
                System.currentTimeMillis() - start);

        List<String> warnings = new ArrayList<>();
        if (result.failedFetches() > 0) {
            warnings.add(result.failedFetches() + " movie detail(s) could not be retrieved");
        }
        return new MovieResponse(result.movies(), warnings);
    }
}
