package com.kov.movieinfo.controller;

import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kov.movieinfo.dto.ErrorResponse;
import com.kov.movieinfo.dto.MovieResponse;
import com.kov.movieinfo.service.MovieService;
import com.kov.movieinfo.service.SearchLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Movies", description = "Search movie metadata across aggregated upstream APIs")
public class MovieController {

    private final MovieService movieService;
    private final SearchLogService searchLogService;

    @Operation(
            summary = "Search movies by title",
            description =
                    """
                    Returns matching movies (title, year, directors) from the selected upstream \
                    provider. Results for a given (api, title) pair are cached in Redis for 10 \
                    minutes; partial upstream failures skip the cache so the next request can \
                    recover. Every request (including cache hits) is persisted asynchronously \
                    to the search audit log.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Search completed",
                content = @Content(schema = @Schema(implementation = MovieResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid API name, missing api parameter, or title too long",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "502",
                description = "Upstream movie API is unavailable after retries",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Circuit breaker open — upstream temporarily suspended",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/movies/{movieTitle}")
    public ResponseEntity<MovieResponse> searchMovies(
            @Parameter(description = "Movie title to search for", example = "Avengers")
                    @PathVariable
                    @Size(max = 200)
                    String movieTitle,
            @Parameter(
                            description = "Upstream provider to query",
                            example = "omdb",
                            schema = @Schema(allowableValues = {"omdb", "tmdb"}))
                    @RequestParam
                    String api) {
        long start = System.currentTimeMillis();
        log.debug("Search request received: api={} title='{}'", api, movieTitle);

        MovieResponse response = movieService.searchMovies(movieTitle, api);
        searchLogService.logSearch(movieTitle, api, response.movies().size());

        long elapsed = System.currentTimeMillis() - start;
        log.info(
                "Search complete: api={} title='{}' results={} warnings={} duration={}ms",
                api,
                movieTitle,
                response.movies().size(),
                response.warnings().size(),
                elapsed);
        return ResponseEntity.ok(response);
    }
}
