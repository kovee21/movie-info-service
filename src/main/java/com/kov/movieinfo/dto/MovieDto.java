package com.kov.movieinfo.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MovieDto(
        @JsonProperty("Title") String title,
        @JsonProperty("Year") String year,
        @JsonProperty("Director") List<String> director)
        implements Serializable {}
