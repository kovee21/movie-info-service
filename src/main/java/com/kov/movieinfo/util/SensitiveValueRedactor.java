package com.kov.movieinfo.util;

import java.util.regex.Pattern;

/** Utility to strip sensitive values (e.g. API keys) from strings before logging. */
public final class SensitiveValueRedactor {

    private static final Pattern API_KEY_PATTERN =
            Pattern.compile("(?i)(apikey|api_key|api-key)=[^&\\s\"]+");

    private static final String REPLACEMENT = "$1=***";

    private SensitiveValueRedactor() {}

    /** Returns the input with any {@code apikey=...} query parameters redacted. */
    public static String redact(String input) {
        if (input == null) {
            return null;
        }
        return API_KEY_PATTERN.matcher(input).replaceAll(REPLACEMENT);
    }
}
