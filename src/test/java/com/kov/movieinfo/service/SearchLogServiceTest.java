package com.kov.movieinfo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kov.movieinfo.entity.SearchLog;
import com.kov.movieinfo.repository.SearchLogRepository;

@ExtendWith(MockitoExtension.class)
class SearchLogServiceTest {

    @Mock private SearchLogRepository repository;

    @InjectMocks private SearchLogService searchLogService;

    @Test
    void logSearch_persistsSearchLog() {
        searchLogService.logSearch("Avengers", "omdb", 5);

        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);
        verify(repository).save(captor.capture());

        SearchLog saved = captor.getValue();
        assertThat(saved.getQuery()).isEqualTo("Avengers");
        assertThat(saved.getApi()).isEqualTo("omdb");
        assertThat(saved.getResultCount()).isEqualTo(5);
        assertThat(saved.getSearchedAt()).isNotNull();
    }

    @Test
    void logSearch_swallowsExceptionOnSaveFailure() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB connection lost"));

        assertThatNoException().isThrownBy(() -> searchLogService.logSearch("Avengers", "omdb", 5));
    }
}
