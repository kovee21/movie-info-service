package com.kov.movieinfo.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kov.movieinfo.dto.MovieDto;
import com.kov.movieinfo.dto.MovieResponse;
import com.kov.movieinfo.exception.ApiProviderException;
import com.kov.movieinfo.service.MovieService;
import com.kov.movieinfo.service.SearchLogService;

@WebMvcTest(MovieController.class)
class MovieControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MovieService movieService;

    @MockitoBean private SearchLogService searchLogService;

    @Test
    void searchMovies_returnsMovieList() throws Exception {
        var movies =
                List.of(
                        new MovieDto("The Avengers", "2012", List.of("Joss Whedon")),
                        new MovieDto("Avengers: Age of Ultron", "2015", List.of("Joss Whedon")));
        when(movieService.searchMovies("Avengers", "omdb")).thenReturn(new MovieResponse(movies));
        doNothing().when(searchLogService).logSearch(anyString(), anyString(), anyInt());

        mockMvc.perform(get("/movies/Avengers").param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies").isArray())
                .andExpect(jsonPath("$.movies.length()").value(2))
                .andExpect(jsonPath("$.movies[0].Title").value("The Avengers"))
                .andExpect(jsonPath("$.movies[0].Year").value("2012"))
                .andExpect(jsonPath("$.movies[0].Director[0]").value("Joss Whedon"));

        verify(searchLogService).logSearch("Avengers", "omdb", 2);
    }

    @Test
    void searchMovies_emptyResults() throws Exception {
        when(movieService.searchMovies("xyznonexistent", "omdb"))
                .thenReturn(new MovieResponse(List.of()));
        doNothing().when(searchLogService).logSearch(anyString(), anyString(), anyInt());

        mockMvc.perform(get("/movies/xyznonexistent").param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies").isArray())
                .andExpect(jsonPath("$.movies").isEmpty());
    }

    @Test
    void searchMovies_invalidApi_returns400() throws Exception {
        when(movieService.searchMovies("Avengers", "invalid"))
                .thenThrow(
                        new IllegalArgumentException(
                                "Unknown API: 'invalid'. Supported values: [omdb, tmdb]"));

        mockMvc.perform(get("/movies/Avengers").param("api", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void searchMovies_apiFailure_returns502() throws Exception {
        when(movieService.searchMovies("Avengers", "omdb"))
                .thenThrow(new ApiProviderException("OMDB search failed"));

        mockMvc.perform(get("/movies/Avengers").param("api", "omdb"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void searchMovies_missingApiParam_returns400() throws Exception {
        mockMvc.perform(get("/movies/Avengers")).andExpect(status().isBadRequest());
    }

    @Test
    void searchMovies_unexpectedException_returns500() throws Exception {
        when(movieService.searchMovies("Avengers", "omdb"))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/movies/Avengers").param("api", "omdb"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("An unexpected error occurred."));
    }

    @Test
    void searchMovies_withWarnings_includesWarningsInResponse() throws Exception {
        var movies = List.of(new MovieDto("The Avengers", "2012", List.of("Joss Whedon")));
        var warnings = List.of("2 movie detail(s) could not be retrieved");
        when(movieService.searchMovies("Avengers", "omdb"))
                .thenReturn(new MovieResponse(movies, warnings));
        doNothing().when(searchLogService).logSearch(anyString(), anyString(), anyInt());

        mockMvc.perform(get("/movies/Avengers").param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.warnings[0]")
                                .value("2 movie detail(s) could not be retrieved"));
    }

    @Test
    void searchMovies_noWarnings_omitsWarningsField() throws Exception {
        when(movieService.searchMovies("Avengers", "omdb"))
                .thenReturn(new MovieResponse(List.of()));
        doNothing().when(searchLogService).logSearch(anyString(), anyString(), anyInt());

        mockMvc.perform(get("/movies/Avengers").param("api", "omdb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings").doesNotExist());
    }
}
