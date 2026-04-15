package com.kov.movieinfo.service.provider;

import java.util.List;

import com.kov.movieinfo.dto.MovieDto;

public interface MovieApiProvider {

    String getName();

    SearchResult searchMovies(String title);

    record SearchResult(List<MovieDto> movies, int failedFetches) {}
}
