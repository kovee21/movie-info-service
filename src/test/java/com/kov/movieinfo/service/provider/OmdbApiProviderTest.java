package com.kov.movieinfo.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import com.kov.movieinfo.exception.ApiProviderException;
import com.kov.movieinfo.service.provider.MovieApiProvider.SearchResult;

class OmdbApiProviderTest {

    private static final String BASE_URL = "http://www.omdbapi.com";
    private static final String API_KEY = "testkey";

    private MockRestServiceServer mockServer;
    private OmdbApiProvider provider;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        RestClient restClient = RestClient.create(restTemplate);
        provider = new OmdbApiProvider(restClient, API_KEY, BASE_URL, 10, Runnable::run);
    }

    @Test
    void getName_returnsOmdb() {
        assertThat(provider.getName()).isEqualTo("omdb");
    }

    @Test
    void searchMovies_returnsMoviesWithDirectors() {
        String searchJson =
                """
                {
                  "Search": [
                    {"Title": "The Avengers", "Year": "2012", "imdbID": "tt0848228"},
                    {"Title": "Avengers: Age of Ultron", "Year": "2015", "imdbID": "tt2395427"}
                  ],
                  "totalResults": "2",
                  "Response": "True"
                }
                """;
        String detail1Json =
                """
                {
                  "Title": "The Avengers",
                  "Year": "2012",
                  "Director": "Joss Whedon",
                  "Response": "True"
                }
                """;
        String detail2Json =
                """
                {
                  "Title": "Avengers: Age of Ultron",
                  "Year": "2015",
                  "Director": "Joss Whedon",
                  "Response": "True"
                }
                """;

        mockServer
                .expect(requestTo(BASE_URL + "?s=Avengers&apikey=" + API_KEY))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt0848228&apikey=" + API_KEY))
                .andRespond(withSuccess(detail1Json, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt2395427&apikey=" + API_KEY))
                .andRespond(withSuccess(detail2Json, MediaType.APPLICATION_JSON));

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
                  "Search": [{"Title": "Test Movie", "Year": "2020", "imdbID": "tt1234567"}],
                  "totalResults": "1",
                  "Response": "True"
                }
                """;
        String detailJson =
                """
                {
                  "Title": "Test Movie",
                  "Year": "2020",
                  "Director": "John Doe, Jane Doe",
                  "Response": "True"
                }
                """;

        mockServer
                .expect(requestTo(BASE_URL + "?s=Test&apikey=" + API_KEY))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt1234567&apikey=" + API_KEY))
                .andRespond(withSuccess(detailJson, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("Test");

        assertThat(result.movies()).hasSize(1);
        assertThat(result.movies().getFirst().director()).containsExactly("John Doe", "Jane Doe");
    }

    @Test
    void searchMovies_noResults_returnsEmptyList() {
        String noResultsJson =
                """
                {"Response": "False", "Error": "Movie not found!"}
                """;

        mockServer
                .expect(requestTo(BASE_URL + "?s=xyznonexistent&apikey=" + API_KEY))
                .andRespond(withSuccess(noResultsJson, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("xyznonexistent");

        assertThat(result.movies()).isEmpty();
        assertThat(result.failedFetches()).isZero();
    }

    @Test
    void searchMovies_directorNA_returnsEmptyDirectorList() {
        String searchJson =
                """
                {
                  "Search": [{"Title": "No Director", "Year": "2020", "imdbID": "tt0000001"}],
                  "totalResults": "1",
                  "Response": "True"
                }
                """;
        String detailJson =
                """
                {
                  "Title": "No Director",
                  "Year": "2020",
                  "Director": "N/A",
                  "Response": "True"
                }
                """;

        mockServer
                .expect(requestTo(BASE_URL + "?s=NoDirector&apikey=" + API_KEY))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt0000001&apikey=" + API_KEY))
                .andRespond(withSuccess(detailJson, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("NoDirector");

        assertThat(result.movies()).hasSize(1);
        assertThat(result.movies().getFirst().director()).isEmpty();
    }

    @Test
    void searchMovies_detailResponseFalse_dropsEntry() {
        String searchJson =
                """
                {
                  "Search": [
                    {"Title": "Movie A", "Year": "2020", "imdbID": "tt0000001"},
                    {"Title": "Movie B", "Year": "2021", "imdbID": "tt0000002"}
                  ],
                  "totalResults": "2",
                  "Response": "True"
                }
                """;
        String okDetail =
                """
                {"Title": "Movie A", "Year": "2020", "Director": "Dir A", "Response": "True"}
                """;
        // Detail endpoint returns HTTP 200 but with Response:False payload. Provider must skip.
        String notFoundDetail =
                """
                {"Response": "False", "Error": "Incorrect IMDb ID."}
                """;

        mockServer
                .expect(requestTo(BASE_URL + "?s=Mix&apikey=" + API_KEY))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt0000001&apikey=" + API_KEY))
                .andRespond(withSuccess(okDetail, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt0000002&apikey=" + API_KEY))
                .andRespond(withSuccess(notFoundDetail, MediaType.APPLICATION_JSON));

        SearchResult result = provider.searchMovies("Mix");

        // Response=False is silently dropped, not counted as a failure.
        assertThat(result.movies()).hasSize(1);
        assertThat(result.movies().getFirst().title()).isEqualTo("Movie A");
        assertThat(result.failedFetches()).isZero();
    }

    @Test
    void searchMovies_serverError_throwsApiProviderException() {
        mockServer
                .expect(requestTo(BASE_URL + "?s=Avengers&apikey=" + API_KEY))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.searchMovies("Avengers"))
                .isInstanceOf(ApiProviderException.class)
                .hasMessageContaining("OMDB search failed");
    }

    @Test
    void searchMovies_detailFetchFailure_tracksFailedCount() {
        String searchJson =
                """
                {
                  "Search": [
                    {"Title": "Movie A", "Year": "2020", "imdbID": "tt0000001"},
                    {"Title": "Movie B", "Year": "2021", "imdbID": "tt0000002"}
                  ],
                  "totalResults": "2",
                  "Response": "True"
                }
                """;
        String detailJson =
                """
                {"Title": "Movie A", "Year": "2020", "Director": "Dir A", "Response": "True"}
                """;

        mockServer
                .expect(requestTo(BASE_URL + "?s=Mixed&apikey=" + API_KEY))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt0000001&apikey=" + API_KEY))
                .andRespond(withSuccess(detailJson, MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo(BASE_URL + "?i=tt0000002&apikey=" + API_KEY))
                .andRespond(withServerError());

        SearchResult result = provider.searchMovies("Mixed");

        assertThat(result.movies()).hasSize(1);
        assertThat(result.failedFetches()).isEqualTo(1);
    }
}
