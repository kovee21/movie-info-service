package com.kov.movieinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies the springdoc-generated OpenAPI spec contains the contract we advertise. By default it
 * does NOT modify the working tree — run {@code mvn test -Dopenapi.write=true} to regenerate the
 * committed {@code docs/openapi.yml} file.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

    private static final Path OPENAPI_FILE = Path.of("docs", "openapi.yml");
    private static final String WRITE_PROPERTY = "openapi.write";

    @Autowired private MockMvc mockMvc;

    @Test
    void openApiYamlIsGeneratedAndContainsExpectedEndpoints() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk()).andReturn();

        String yaml =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // Sanity: the spec must describe our endpoint and response schema.
        assertThat(yaml).contains("/movies/{movieTitle}");
        assertThat(yaml).contains("Movie Info Service API");
        assertThat(yaml).contains("MovieResponse");
        assertThat(yaml).contains("ErrorResponse");
        assertThat(yaml).contains("Director");

        if (Boolean.getBoolean(WRITE_PROPERTY)) {
            Files.createDirectories(OPENAPI_FILE.getParent());
            Files.writeString(OPENAPI_FILE, yaml);
        }
    }
}
