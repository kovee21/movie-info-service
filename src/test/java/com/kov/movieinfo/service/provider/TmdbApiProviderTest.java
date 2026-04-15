package com.kov.movieinfo.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import com.kov.movieinfo.exception.ApiProviderException;
import com.kov.movieinfo.service.provider.MovieApiProvider.SearchResult;

class TmdbApiProviderTest {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String BEARER_TOKEN = "test-bearer";
    private static final String EXPECTED_AUTH_HEADER = "Bearer " + BEARER_TOKEN;

    private MockRestServiceServer mockServer;
    private TmdbApiProvider provider;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        RestClient restClient = RestClient.create(restTemplate);
        provider = new TmdbApiProvider(restClient, BEARER_TOKEN, BASE_URL, true, 10, Runnable::run);
    }

    @Test
    void getName_returnsTmdb() {
        assertThat(provider.getName()).isEqualTo("tmdb");
    }

    @Test
    void searchMovies_returnsMoviesWithDirectors_andSendsBearerAuth() {
        String searchJson =
                """
                {
                  "results": [
                    {"id": 24428, "title": "The Avengers", "release_date": "2012-04-25"},
                    {"id": 99861, "title": "Avengers: Age of Ultron", "release_date": "2015-04-22"}
                  ]
                }
                """;
        String credits1Json =
                """
                {
                  "crew": [
                    {"job": "Director", "name": "Joss Whedon"},
                    {"job": "Producer", "name": "Kevin Feige"}
                  ]
                }
                """;
        String credits2Json =
                """
                {
                  "crew": [
                    {"job": "Director", "name": "Joss Whedon"}
                  ]
                }
                """;

        mockServer
                .expect(requestTo(BASE_URL + "/search/movie?query=Avengers&include_adult=true"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTH_HEADER))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "/movie/24428/credits"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTH_HEADER))
                .andRespond(withSuccess(credits1Json, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "/movie/99861/credits"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTH_HEADER))
                .andRespond(withSuccess(credits2Json, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("Avengers");

        assertThat(result.movies()).hasSize(2);
        assertThat(result.failedFetches()).isZero();
        assertThat(result.movies().get(0).title()).isEqualTo("The Avengers");
        assertThat(result.movies().get(0).year()).isEqualTo("2012");
        assertThat(result.movies().get(0).director()).containsExactly("Joss Whedon");
        mockServer.verify();
    }

    @Test
    void searchMovies_multipleDirectors() {
        String searchJson =
                """
                {
                  "results": [{"id": 100, "title": "Test Movie", "release_date": "2020-06-15"}]
                }
                """;
        String creditsJson =
                """
                {
                  "crew": [
                    {"job": "Director", "name": "John Doe"},
                    {"job": "Director", "name": "Jane Doe"},
                    {"job": "Writer", "name": "Someone Else"}
                  ]
                }
                """;

        mockServer
                .expect(requestTo(BASE_URL + "/search/movie?query=Test&include_adult=true"))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "/movie/100/credits"))
                .andRespond(withSuccess(creditsJson, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("Test");

        assertThat(result.movies()).hasSize(1);
        assertThat(result.movies().getFirst().director()).containsExactly("John Doe", "Jane Doe");
    }

    @Test
    void searchMovies_noResults_returnsEmptyList() {
        String emptyJson =
                """
                {"results": []}
                """;

        mockServer
                .expect(
                        requestTo(
                                BASE_URL + "/search/movie?query=xyznonexistent&include_adult=true"))
                .andRespond(withSuccess(emptyJson, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("xyznonexistent");

        assertThat(result.movies()).isEmpty();
        assertThat(result.failedFetches()).isZero();
    }

    @Test
    void searchMovies_extractsYearFromReleaseDate() {
        String searchJson =
                """
                {
                  "results": [{"id": 200, "title": "Future Movie", "release_date": "2025-12-01"}]
                }
                """;
        String creditsJson =
                """
                {"crew": [{"job": "Director", "name": "Some Director"}]}
                """;

        mockServer
                .expect(requestTo(BASE_URL + "/search/movie?query=Future&include_adult=true"))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "/movie/200/credits"))
                .andRespond(withSuccess(creditsJson, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("Future");

        assertThat(result.movies().getFirst().year()).isEqualTo("2025");
    }

    @Test
    void searchMovies_emptyReleaseDate_returnsEmptyYear() {
        String searchJson =
                """
                {
                  "results": [{"id": 300, "title": "Unknown Date", "release_date": ""}]
                }
                """;
        String creditsJson =
                """
                {"crew": []}
                """;

        mockServer
                .expect(requestTo(BASE_URL + "/search/movie?query=Unknown&include_adult=true"))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "/movie/300/credits"))
                .andRespond(withSuccess(creditsJson, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("Unknown");

        assertThat(result.movies().getFirst().year()).isEmpty();
    }

    @Test
    void searchMovies_serverError_throwsApiProviderException() {
        mockServer
                .expect(requestTo(BASE_URL + "/search/movie?query=Avengers&include_adult=true"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.searchMovies("Avengers"))
                .isInstanceOf(ApiProviderException.class)
                .hasMessageContaining("TMDB search failed");
    }

    @Test
    void searchMovies_creditsFetchFailure_tracksFailedCount() {
        String searchJson =
                """
                {
                  "results": [
                    {"id": 1, "title": "Movie A", "release_date": "2020-01-01"},
                    {"id": 2, "title": "Movie B", "release_date": "2021-01-01"}
                  ]
                }
                """;
        String creditsJson =
                """
                {"crew": [{"job": "Director", "name": "Dir A"}]}
                """;

        mockServer
                .expect(requestTo(BASE_URL + "/search/movie?query=Mixed&include_adult=true"))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "/movie/1/credits"))
                .andRespond(withSuccess(creditsJson, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/movie/2/credits")).andRespond(withServerError());

        SearchResult result = provider.searchMovies("Mixed");

        assertThat(result.movies()).hasSize(1);
        assertThat(result.failedFetches()).isEqualTo(1);
    }

    @Test
    void searchMovies_includeAdultFalse_sendsCorrectParam() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.create(restTemplate);
        TmdbApiProvider noAdultProvider =
                new TmdbApiProvider(restClient, BEARER_TOKEN, BASE_URL, false, 10, Runnable::run);

        String emptyJson =
                """
                {"results": []}
                """;
        server.expect(requestTo(BASE_URL + "/search/movie?query=Test&include_adult=false"))
                .andRespond(withSuccess(emptyJson, MediaType.APPLICATION_JSON));

        SearchResult result = noAdultProvider.searchMovies("Test");

        assertThat(result.movies()).isEmpty();
        server.verify();
    }
}
