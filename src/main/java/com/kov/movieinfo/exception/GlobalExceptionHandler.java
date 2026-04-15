package com.kov.movieinfo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.kov.movieinfo.dto.ErrorResponse;
import com.kov.movieinfo.util.SensitiveValueRedactor;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(HandlerMethodValidationException e) {
        log.warn("Validation failed: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Invalid request parameters: " + e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException e) {
        log.warn("Missing request parameter: {}", e.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Missing required parameter: " + e.getParameterName()));
    }

    @ExceptionHandler(ApiProviderException.class)
    public ResponseEntity<ErrorResponse> handleApiProvider(ApiProviderException e) {
        // Only the top-level message is logged at ERROR to avoid forwarding the cause's
        // RestClient exception message, which can contain unredacted URLs. A redacted copy is
        // available at DEBUG for operator diagnosis.
        log.error("External API error: {}", SensitiveValueRedactor.redact(e.getMessage()));
        log.debug(
                "External API error full trace",
                new RuntimeException(SensitiveValueRedactor.redact(e.toString()), e.getCause()));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(
                        new ErrorResponse(
                                "External movie API is unavailable. Please try again later."));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(CallNotPermittedException e) {
        log.warn("Circuit breaker open: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        new ErrorResponse(
                                "Upstream movie API is temporarily unavailable. Please try again"
                                        + " later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred."));
    }
}
