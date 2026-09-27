package com.master.exception;

/**
 * Thrown when a real Google Routes API call is needed but today's request budget
 * ({@code google.maps.daily-request-limit}) is already used up. Mapped to 429 Too Many Requests by
 * GlobalExceptionHandler -- distinct from ExternalApiException (502) because Google was never called.
 */
public class GoogleRoutesQuotaExceededException extends RuntimeException {

    public GoogleRoutesQuotaExceededException(String message) {
        super(message);
    }
}
