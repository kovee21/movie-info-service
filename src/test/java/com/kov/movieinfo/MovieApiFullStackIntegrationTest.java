package com.kov.movieinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.kov.movieinfo.dto.MovieDto;
import com.kov.movieinfo.entity.SearchLog;
import com.kov.movieinfo.repository.SearchLogRepository;
import com.kov.movieinfo.service.provider.MovieApiProvider.SearchResult;
import com.kov.movieinfo.service.provider.OmdbApiProvider;
import com.kov.movieinfo.service.provider.TmdbApiProvider;

/**
 * Full-stack integration tests running against real MySQL and Redis containers.
 *
 * <p>Catches issues the in-memory integration tests cannot:
 *
 * <ul>
 *   <li>Flyway migration actually runs against a MySQL dialect (implicit: context would fail to
 *       start if the migration or schema validation fails).
 *   <li>Redis JSON serialization roundtrip preserves DTO structure (including {@code @JsonProperty}
 *       field names, nested arrays, and optional fields).
 *   <li>Cache keying behaves correctly across locale and case variations.
 * </ul>
 *
 * <p>Each test uses a unique query string so async-logged rows cannot interfere across tests.
 */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.cache.type=redis",
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate"
        })
@AutoConfigureMockMvc
@Testcontainers
class MovieApiFullStackIntegrationTest {

    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private MockMvc mockMvc;

    @Autowired private SearchLogRepository searchLogRepository;

    @MockitoSpyBean private OmdbApiProvider omdbProvider;

    @MockitoSpyBean private TmdbApiProvider tmdbProvider;

    private List<SearchLog> logsFor(String query) {
        return searchLogRepository.findAll().stream()
                .filter(log -> log.getQuery().equals(query))
                .toList();
    }

    @Test
    void fullStack_requestPersistsToMysqlAndCachesInRedis() throws Exception {
        String query = "FullStack-Avengers";
        var movies = List.of(new MovieDto("The Avengers", "2012", List.of("Joss Whedon")));
        doReturn(new SearchResult(movies, 0)).when(omdbProvider).searchMovies(query);

        mockMvc.perform(get("/movies/" + query).param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].Title").value("The Avengers"));

        mockMvc.perform(get("/movies/" + query).param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].Title").value("The Avengers"));

        // Provider hit exactly once — second request served from real Redis.
        verify(omdbProvider, times(1)).searchMovies(query);

        // Both requests persist to real MySQL (cache hits still log).
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            var logs = logsFor(query);
                            assertThat(logs).hasSize(2);
                            assertThat(logs)
                                    .allSatisfy(
                                            log -> {
                                                assertThat(log.getApi()).isEqualTo("omdb");
                                                assertThat(log.getResultCount()).isEqualTo(1);
                                            });
                        });
    }

    @Test
    void redisSerialization_preservesDtoStructureOnRoundtrip() throws Exception {
        String query = "FullStack-Roundtrip";
        // Nested arrays + multiple directors + warnings — the shape most prone to
        // serialization bugs like the LinkedHashMap cast we hit during setup.
        var movies =
                List.of(
                        new MovieDto("Film A", "2020", List.of("Solo Director")),
                        new MovieDto("Film B", "2021", List.of("Dir One", "Dir Two")));
        doReturn(new SearchResult(movies, 0)).when(omdbProvider).searchMovies(query);

        // First request populates the Redis cache.
        mockMvc.perform(get("/movies/" + query).param("api", "omdb")).andExpect(status().isOk());

        // Second request deserializes from Redis — every field must match exactly.
        mockMvc.perform(get("/movies/" + query).param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies.length()").value(2))
                .andExpect(jsonPath("$.movies[0].Title").value("Film A"))
                .andExpect(jsonPath("$.movies[0].Year").value("2020"))
                .andExpect(jsonPath("$.movies[0].Director[0]").value("Solo Director"))
                .andExpect(jsonPath("$.movies[1].Title").value("Film B"))
                .andExpect(jsonPath("$.movies[1].Director.length()").value(2))
                .andExpect(jsonPath("$.movies[1].Director[0]").value("Dir One"))
                .andExpect(jsonPath("$.movies[1].Director[1]").value("Dir Two"))
                .andExpect(jsonPath("$.warnings").doesNotExist());

        verify(omdbProvider, times(1)).searchMovies(query);
    }

    @Test
    void bothProviders_useSeparateCacheEntriesForSameTitle() throws Exception {
        String query = "FullStack-BothProviders";
        var omdbMovies = List.of(new MovieDto("Omdb Result", "2012", List.of("Dir X")));
        var tmdbMovies = List.of(new MovieDto("Tmdb Result", "2012", List.of("Dir Y")));
        doReturn(new SearchResult(omdbMovies, 0)).when(omdbProvider).searchMovies(query);
        doReturn(new SearchResult(tmdbMovies, 0)).when(tmdbProvider).searchMovies(query);

        mockMvc.perform(get("/movies/" + query).param("api", "omdb"))
                .andExpect(jsonPath("$.movies[0].Title").value("Omdb Result"));
        mockMvc.perform(get("/movies/" + query).param("api", "tmdb"))
                .andExpect(jsonPath("$.movies[0].Title").value("Tmdb Result"));

        // Second round — both served from their respective Redis entries.
        mockMvc.perform(get("/movies/" + query).param("api", "omdb"))
                .andExpect(jsonPath("$.movies[0].Title").value("Omdb Result"));
        mockMvc.perform(get("/movies/" + query).param("api", "tmdb"))
                .andExpect(jsonPath("$.movies[0].Title").value("Tmdb Result"));

        verify(omdbProvider, times(1)).searchMovies(query);
        verify(tmdbProvider, times(1)).searchMovies(query);
    }

    @Test
    void cacheKey_isCaseInsensitiveAcrossRequests() throws Exception {
        String upper = "FULLSTACK-CASE";
        var movies = List.of(new MovieDto("Matrix", "1999", List.of("Wachowski")));
        doReturn(new SearchResult(movies, 0)).when(omdbProvider).searchMovies(upper);

        // Cache key lowercases via Locale.ROOT — all three requests share one entry.
        mockMvc.perform(get("/movies/" + upper).param("api", "omdb")).andExpect(status().isOk());
        mockMvc.perform(get("/movies/fullstack-case").param("api", "omdb"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/movies/Fullstack-Case").param("api", "omdb"))
                .andExpect(status().isOk());

        verify(omdbProvider, times(1)).searchMovies(anyString());
    }

    @Test
    void invalidApi_returns400_andDoesNotPersistSearchLog() throws Exception {
        String query = "FullStack-Invalid";

        mockMvc.perform(get("/movies/" + query).param("api", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // Give any stray async persistence a chance, then confirm nothing landed.
        Thread.sleep(500);
        assertThat(logsFor(query)).isEmpty();
    }

    @Test
    void unicodeTitle_isPersistedAndReturnedVerbatim() throws Exception {
        // Hungarian (Latin Extended-A) + CJK (outside BMP for 4-byte UTF-8) exercises utf8mb4.
        String hungarianTitle = "Macskafogó";
        String cjkTitle = "千と千尋の神隠し";

        var hungarianMovies =
                List.of(new MovieDto("Macskafogó", "1986", List.of("Ternovszky Béla")));
        var cjkMovies = List.of(new MovieDto("千と千尋の神隠し", "2001", List.of("宮崎駿")));
        doReturn(new SearchResult(hungarianMovies, 0))
                .when(omdbProvider)
                .searchMovies(hungarianTitle);
        doReturn(new SearchResult(cjkMovies, 0)).when(tmdbProvider).searchMovies(cjkTitle);

        mockMvc.perform(get("/movies/" + hungarianTitle).param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].Title").value("Macskafogó"))
                .andExpect(jsonPath("$.movies[0].Director[0]").value("Ternovszky Béla"));

        mockMvc.perform(get("/movies/" + cjkTitle).param("api", "tmdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].Title").value("千と千尋の神隠し"))
                .andExpect(jsonPath("$.movies[0].Director[0]").value("宮崎駿"));

        // Round-trip through MySQL must preserve the bytes exactly.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(logsFor(hungarianTitle))
                                    .singleElement()
                                    .satisfies(
                                            log ->
                                                    assertThat(log.getQuery())
                                                            .isEqualTo(hungarianTitle));
                            assertThat(logsFor(cjkTitle))
                                    .singleElement()
                                    .satisfies(
                                            log -> assertThat(log.getQuery()).isEqualTo(cjkTitle));
                        });
    }

    @Test
    void unicodeCacheKey_isCaseInsensitiveAcrossAccentedForms() throws Exception {
        // Locale.ROOT lower-casing must map MACSKAFOGÓ and macskafogó to the same cache key —
        // otherwise the Turkish-i class of bugs resurfaces on any non-ASCII locale.
        // Use a string not seen by any other test so we hit a clean cache key and the
        // Macskafogó cache entry from unicodeTitle_isPersistedAndReturnedVerbatim cannot leak in.
        String upper = "SÁTÁNTANGÓ";
        String lower = "sátántangó";
        var movies = List.of(new MovieDto("Sátántangó", "1994", List.of("Béla Tarr")));
        // anyString() avoids fragile argument-match issues with Unicode encoding/normalization
        // that can differ between the test's String literal and what MockMvc decodes from the URL.
        doReturn(new SearchResult(movies, 0)).when(omdbProvider).searchMovies(anyString());

        // URI-template form URL-encodes the non-ASCII characters so MockMvc can match the route.
        mockMvc.perform(get("/movies/{title}", upper).param("api", "omdb"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/movies/{title}", lower).param("api", "omdb"))
                .andExpect(status().isOk());

        // Second request must hit the Redis cache entry created by the first.
        verify(omdbProvider, times(1)).searchMovies(anyString());
    }

    @Test
    void partialFailure_surfacesWarningsAndBypassesCache() throws Exception {
        String query = "FullStack-Partial";
        var movies = List.of(new MovieDto("Available", "2020", List.of("Dir")));
        doReturn(new SearchResult(movies, 3)).when(tmdbProvider).searchMovies(query);

        mockMvc.perform(get("/movies/" + query).param("api", "tmdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies.length()").value(1))
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(
                        jsonPath("$.warnings[0]")
                                .value("3 movie detail(s) could not be retrieved"));

        // Partial results are deliberately NOT cached — retry should hit the provider again.
        mockMvc.perform(get("/movies/" + query).param("api", "tmdb"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.warnings[0]")
                                .value("3 movie detail(s) could not be retrieved"));

        verify(tmdbProvider, times(2)).searchMovies(query);
    }
}
