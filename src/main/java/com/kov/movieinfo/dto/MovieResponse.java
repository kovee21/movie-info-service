package com.kov.movieinfo.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record MovieResponse(
        List<MovieDto> movies, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> warnings)
        implements Serializable {

    public MovieResponse {
        if (movies == null) {
            movies = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }

    public MovieResponse(List<MovieDto> movies) {
        this(movies, List.of());
    }
}
