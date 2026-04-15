package com.kov.movieinfo.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveValueRedactorTest {

    @Test
    void redact_masksApiKeyQueryParam() {
        String input =
                "I/O error on GET request for http://www.omdbapi.com?s=Avengers&apikey=secret123";

        String redacted = SensitiveValueRedactor.redact(input);

        assertThat(redacted).doesNotContain("secret123");
        assertThat(redacted).contains("apikey=***");
        assertThat(redacted).contains("s=Avengers");
    }

    @Test
    void redact_masksApiKeyWithUnderscoreOrDash() {
        assertThat(SensitiveValueRedactor.redact("x?api_key=xyz&other=1")).contains("api_key=***");
        assertThat(SensitiveValueRedactor.redact("x?api-key=xyz")).contains("api-key=***");
    }

    @Test
    void redact_isCaseInsensitive() {
        assertThat(SensitiveValueRedactor.redact("APIKEY=SECRET")).contains("APIKEY=***");
        assertThat(SensitiveValueRedactor.redact("ApiKey=Secret")).contains("ApiKey=***");
    }

    @Test
    void redact_preservesMessageWithoutApiKey() {
        String input = "Connection refused: www.omdbapi.com";

        assertThat(SensitiveValueRedactor.redact(input)).isEqualTo(input);
    }

    @Test
    void redact_nullInputReturnsNull() {
        assertThat(SensitiveValueRedactor.redact(null)).isNull();
    }
}
