package com.kov.movieinfo.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.kov.movieinfo.config.AsyncConfig;
import com.kov.movieinfo.entity.SearchLog;
import com.kov.movieinfo.repository.SearchLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchLogService {

    private final SearchLogRepository repository;

    /**
     * Persists a search audit row asynchronously on the bounded {@link
     * AsyncConfig#SEARCH_LOG_EXECUTOR} so a burst of traffic cannot exhaust the DB connection pool.
     *
     * <p><b>AOP requirement:</b> {@code @Async} only works when the method is invoked through the
     * Spring-managed proxy (i.e., from another bean). Calling {@code this.logSearch(...)} from
     * within this class would bypass the proxy and run synchronously on the caller's thread.
     */
    @Async(AsyncConfig.SEARCH_LOG_EXECUTOR)
    public void logSearch(String query, String api, int resultCount) {
        try {
            repository.save(new SearchLog(query, api, resultCount, LocalDateTime.now()));
            log.debug(
                    "Persisted search log: api={} query='{}' results={}", api, query, resultCount);
        } catch (Exception e) {
            log.error("Failed to persist search log for query='{}', api='{}'", query, api, e);
        }
    }
}
