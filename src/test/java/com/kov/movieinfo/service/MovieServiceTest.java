package com.kov.movieinfo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kov.movieinfo.dto.MovieDto;
import com.kov.movieinfo.dto.MovieResponse;
import com.kov.movieinfo.service.provider.MovieApiProvider;
import com.kov.movieinfo.service.provider.MovieApiProvider.SearchResult;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock private MovieApiProvider omdbProvider;

    @Mock private MovieApiProvider tmdbProvider;

    private MovieService movieService;

    @BeforeEach
    void setUp() {
        when(omdbProvider.getName()).thenReturn("omdb");
        when(tmdbProvider.getName()).thenReturn("tmdb");
        movieService = new MovieService(List.of(omdbProvider, tmdbProvider));
    }

    @Test
    void searchMovies_delegatesToCorrectProvider() {
        var movies = List.of(new MovieDto("The Avengers", "2012", List.of("Joss Whedon")));
        when(omdbProvider.searchMovies("Avengers")).thenReturn(new SearchResult(movies, 0));

        MovieResponse response = movieService.searchMovies("Avengers", "omdb");

        assertThat(response.movies()).hasSize(1);
        assertThat(response.movies().getFirst().title()).isEqualTo("The Avengers");
        verify(omdbProvider).searchMovies("Avengers");
    }

    @Test
    void searchMovies_useTmdbProvider() {
        var movies = List.of(new MovieDto("The Avengers", "2012", List.of("Joss Whedon")));
        when(tmdbProvider.searchMovies("Avengers")).thenReturn(new SearchResult(movies, 0));

        MovieResponse response = movieService.searchMovies("Avengers", "tmdb");

        assertThat(response.movies()).hasSize(1);
        verify(tmdbProvider).searchMovies("Avengers");
    }

    @Test
    void searchMovies_unknownApi_throwsException() {
        assertThatThrownBy(() -> movieService.searchMovies("Avengers", "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown API");
    }

    @Test
    void searchMovies_emptyResults() {
        when(omdbProvider.searchMovies("xyznonexistent"))
                .thenReturn(new SearchResult(List.of(), 0));

        MovieResponse response = movieService.searchMovies("xyznonexistent", "omdb");

        assertThat(response.movies()).isEmpty();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void searchMovies_withFailedFetches_includesWarnings() {
        var movies = List.of(new MovieDto("The Avengers", "2012", List.of("Joss Whedon")));
        when(omdbProvider.searchMovies("Avengers")).thenReturn(new SearchResult(movies, 3));

        MovieResponse response = movieService.searchMovies("Avengers", "omdb");

        assertThat(response.movies()).hasSize(1);
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().getFirst()).contains("3 movie detail(s)");
    }

    @Test
    void searchMovies_noFailedFetches_noWarnings() {
        var movies = List.of(new MovieDto("Matrix", "1999", List.of("Lana Wachowski")));
        when(omdbProvider.searchMovies("Matrix")).thenReturn(new SearchResult(movies, 0));

        MovieResponse response = movieService.searchMovies("Matrix", "omdb");

        assertThat(response.warnings()).isEmpty();
    }
}
