package com.kov.movieinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kov.movieinfo.dto.MovieDto;
import com.kov.movieinfo.repository.SearchLogRepository;
import com.kov.movieinfo.service.provider.MovieApiProvider.SearchResult;
import com.kov.movieinfo.service.provider.OmdbApiProvider;
import com.kov.movieinfo.service.provider.TmdbApiProvider;

@SpringBootTest
@AutoConfigureMockMvc
class MovieSearchIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoSpyBean private OmdbApiProvider omdbProvider;

    @MockitoSpyBean private TmdbApiProvider tmdbProvider;

    @Autowired private SearchLogRepository searchLogRepository;

    @BeforeEach
    void setUp() {
        searchLogRepository.deleteAll();
    }

    @Test
    void searchMovies_fullFlow_returnsResultsAndLogsSearch() throws Exception {
        var movies = List.of(new MovieDto("The Avengers", "2012", List.of("Joss Whedon")));
        doReturn(new SearchResult(movies, 0)).when(omdbProvider).searchMovies("Avengers");

        mockMvc.perform(get("/movies/Avengers").param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].Title").value("The Avengers"))
                .andExpect(jsonPath("$.movies[0].Year").value("2012"))
                .andExpect(jsonPath("$.movies[0].Director[0]").value("Joss Whedon"));

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(
                        () -> {
                            var logs = searchLogRepository.findAll();
                            assertThat(logs).hasSize(1);
                            assertThat(logs.getFirst().getQuery()).isEqualTo("Avengers");
                            assertThat(logs.getFirst().getApi()).isEqualTo("omdb");
                            assertThat(logs.getFirst().getResultCount()).isEqualTo(1);
                        });
    }

    @Test
    void searchMovies_cachedResponse_doesNotCallProviderAgain() throws Exception {
        var movies = List.of(new MovieDto("The Matrix", "1999", List.of("Lana Wachowski")));
        doReturn(new SearchResult(movies, 0)).when(omdbProvider).searchMovies("Matrix");

        mockMvc.perform(get("/movies/Matrix").param("api", "omdb")).andExpect(status().isOk());

        mockMvc.perform(get("/movies/Matrix").param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].Title").value("The Matrix"));

        verify(omdbProvider, times(1)).searchMovies("Matrix");
    }

    @Test
    void searchMovies_cachedResponse_stillLogsEveryRequest() throws Exception {
        var movies = List.of(new MovieDto("Inception", "2010", List.of("Christopher Nolan")));
        doReturn(new SearchResult(movies, 0)).when(omdbProvider).searchMovies("Inception");

        mockMvc.perform(get("/movies/Inception").param("api", "omdb")).andExpect(status().isOk());
        mockMvc.perform(get("/movies/Inception").param("api", "omdb")).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(searchLogRepository.findAll()).hasSize(2));
    }

    @Test
    void searchMovies_invalidApi_returns400() throws Exception {
        mockMvc.perform(get("/movies/Avengers").param("api", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
