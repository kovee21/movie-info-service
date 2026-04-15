package com.kov.movieinfo.exception;

public class ApiProviderException extends RuntimeException {

    public ApiProviderException(String message) {
        super(message);
    }

    public ApiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
