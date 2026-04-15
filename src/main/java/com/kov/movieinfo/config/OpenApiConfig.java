package com.kov.movieinfo.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI movieInfoOpenApi() {
        // Empty servers list suppresses springdoc's auto-generated "http://localhost" entry,
        // which would otherwise get baked into docs/openapi.yml when the test regenerates it.
        return new OpenAPI()
                .servers(List.of())
                .info(
                        new Info()
                                .title("Movie Info Service API")
                                .version("1.0.0")
                                .description(
                                        """
                                        Aggregates movie metadata from OMDB and TMDB public APIs \
                                        with Redis caching and MySQL-backed search auditing. \
                                        Responses are cached per (api, title) for 10 minutes; \
                                        partial upstream failures bypass the cache.
                                        """));
    }
}
