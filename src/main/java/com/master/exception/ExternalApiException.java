package com.master.exception;

/**
 * Thrown when a call to an external API (e.g. Google Routes API) fails --
 * missing/invalid config, network error, or a non-2xx response from the
 * external service. Mapped to 502 Bad Gateway by GlobalExceptionHandler,
 * distinct from ResourceNotFoundException (404, our own data) and
 * IllegalArgumentException (400, bad input from the caller).
 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
