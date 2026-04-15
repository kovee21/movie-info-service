package com.kov.movieinfo.dto;

import java.io.Serializable;

/** Standard error body returned by {@link com.kov.movieinfo.exception.GlobalExceptionHandler}. */
public record ErrorResponse(String error) implements Serializable {}
