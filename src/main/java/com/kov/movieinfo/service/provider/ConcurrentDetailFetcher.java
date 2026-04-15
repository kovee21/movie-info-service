package com.kov.movieinfo.service.provider;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.slf4j.Logger;

/**
 * Runs a per-item detail fetch in parallel on the supplied {@link Executor} and aggregates the
 * successes together with a failure count. Used by both {@link OmdbApiProvider} and {@link
 * TmdbApiProvider} to keep the search-fan-out pattern in one place.
 */
final class ConcurrentDetailFetcher {

    private ConcurrentDetailFetcher() {}

    /**
     * @param items items to fetch details for
     * @param maxItems upper bound on fan-out (protects against a provider returning hundreds)
     * @param fetcher per-item fetch function (may return {@code null} to indicate "skip")
     * @param executor executor to dispatch parallel fetches on
     * @param logger logger to record per-item fetch failures on
     * @param failureMessage human-readable message for failed fetch log lines
     */
    static <T, R> FetchResult<R> fetchAll(
            List<T> items,
            int maxItems,
            Function<T, R> fetcher,
            Executor executor,
            Logger logger,
            String failureMessage) {
        var failures = new AtomicInteger(0);

        List<CompletableFuture<R>> futures =
                items.stream()
                        .limit(maxItems)
                        .map(
                                item ->
                                        CompletableFuture.supplyAsync(
                                                () -> fetcher.apply(item), executor))
                        .toList();

        List<R> results =
                futures.stream()
                        .map(
                                future -> {
                                    try {
                                        return future.join();
                                    } catch (Exception e) {
                                        logger.warn(failureMessage, e);
                                        failures.incrementAndGet();
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .toList();

        return new FetchResult<>(results, failures.get());
    }

    record FetchResult<R>(List<R> items, int failures) {}
}
